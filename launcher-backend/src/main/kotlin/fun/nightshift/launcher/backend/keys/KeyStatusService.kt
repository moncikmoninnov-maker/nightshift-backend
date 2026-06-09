package `fun`.nightshift.launcher.backend.keys

import `fun`.nightshift.launcher.backend.db.ActivationKeyRepository
import `fun`.nightshift.launcher.backend.db.ActivationKeyRow
import `fun`.nightshift.launcher.backend.db.dbQuery
import `fun`.nightshift.launcher.shared.dto.KeyInfo
import `fun`.nightshift.launcher.shared.dto.KeyStatus
import `fun`.nightshift.launcher.shared.dto.KeyType
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Resolves the [KeyStatus] for a given account/HWID pair.
 *
 * Centralised so `/auth/login`, `/auth/validate` and any future read-side
 * endpoint share the exact same expiration handling: a row whose
 * `expires_at` has already passed is flipped to `expired` in the same
 * transaction, then surfaced as [KeyStatus.Expired]. Active keys become
 * [KeyStatus.Active] with a fully-populated [KeyInfo]; no row at all
 * collapses to [KeyStatus.NoKey].
 *
 * Implements the read-side of Requirements 10.2, 10.3, 11.1 — see
 * [KeyRoutes][`fun`.nightshift.launcher.backend.routes.keyRoutes] for the
 * write-side counterparts.
 */
object KeyStatusService {

    private val log = LoggerFactory.getLogger(KeyStatusService::class.java)

    /**
     * Suspend variant that opens its own Exposed transaction. Safe to call
     * from any Ktor route handler.
     */
    suspend fun resolveFor(accountId: UUID, hwid: String, now: Instant = Instant.now()): KeyStatus =
        dbQuery { resolveBlocking(accountId, hwid, now) }

    /**
     * Blocking variant intended to be called from inside an existing
     * Exposed transaction (`dbQuery { ... }`). Performs at most one read
     * and, when the key has lapsed, one update.
     */
    fun resolveBlocking(accountId: UUID, hwid: String, now: Instant = Instant.now()): KeyStatus {
        val row = ActivationKeyRepository.findActiveByAccount(accountId, hwid) ?: return KeyStatus.NoKey
        val expiresAt = row.expiresAt
        if (expiresAt != null && expiresAt.isBefore(now)) {
            ActivationKeyRepository.markExpired(row.id)
            return KeyStatus.Expired
        }
        val type = parseKeyType(row.keyType)
        if (type == null) {
            log.error("Activation key {} has unsupported key_type='{}'", row.id, row.keyType)
            // Treat malformed rows as missing rather than leaking 500s upstream.
            return KeyStatus.NoKey
        }
        return KeyStatus.Active(row.toKeyInfo(type, now))
    }

    private fun parseKeyType(raw: String): KeyType? = when {
        raw.equals("day", ignoreCase = true) -> KeyType.DAY
        raw.equals("week", ignoreCase = true) -> KeyType.WEEK
        raw.equals("month", ignoreCase = true) -> KeyType.MONTH
        raw.equals("lifetime", ignoreCase = true) -> KeyType.LIFETIME
        // Arbitrary-length keys are stored as `custom_<days>`. The wire enum
        // doesn't have a custom variant, so we surface them as MONTH for the
        // client UI; the actual remainingMs is computed from the row's
        // expiresAt below, so the displayed countdown stays accurate.
        raw.lowercase().startsWith("custom_") -> KeyType.MONTH
        else -> null
    }

    private fun ActivationKeyRow.toKeyInfo(type: KeyType, now: Instant): KeyInfo {
        val activatedAtIso = (activatedAt ?: now).toString()
        val expiresAtIso = expiresAt?.toString()
        val isLifetime = type == KeyType.LIFETIME
        val remainingMs = if (isLifetime) {
            null
        } else {
            expiresAt?.let { Duration.between(now, it).toMillis().coerceAtLeast(0L) }
        }
        return KeyInfo(
            type = type,
            activatedAt = activatedAtIso,
            expiresAt = expiresAtIso,
            remainingTimeMs = remainingMs,
            lifetime = isLifetime,
        )
    }
}
