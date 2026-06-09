package `fun`.nightshift.launcher.backend.db

import `fun`.nightshift.launcher.backend.auth.SessionTokenGenerator
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Snapshot of a row in the `sessions` table.
 */
data class SessionRow(
    val id: UUID,
    val accountId: UUID,
    val token: String,
    val hwid: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val isValid: Boolean,
)

/**
 * Persistence layer for opaque session tokens (Requirement 6.3).
 *
 * Every method below assumes it is being called from inside an Exposed
 * transaction (e.g. `dbQuery { ... }` from `DatabaseFactory`). The repository
 * itself does not open transactions so callers can compose multiple writes
 * atomically (issue session + audit log etc.).
 */
object SessionRepository {

    /**
     * Issues a new session token for [accountId] bound to [hwid] with the
     * given [ttl] and persists it. The freshly generated [SessionRow] is
     * returned so callers can hand the token straight to the client.
     */
    fun create(accountId: UUID, hwid: String, ttl: Duration): SessionRow {
        val now = Instant.now()
        val expiresAt = now.plus(ttl)
        val token = SessionTokenGenerator.generate()

        val inserted = Sessions.insert {
            it[Sessions.accountId] = accountId
            it[Sessions.token] = token
            it[Sessions.hwid] = hwid
            it[Sessions.expiresAt] = expiresAt
            it[Sessions.isValid] = true
        }

        return SessionRow(
            id = inserted[Sessions.id].value,
            accountId = accountId,
            token = token,
            hwid = hwid,
            createdAt = inserted[Sessions.createdAt],
            expiresAt = expiresAt,
            isValid = true,
        )
    }

    /**
     * Legacy overload: persists a session with a caller-generated token and
     * absolute expiry. Used by route handlers that already produced the
     * token earlier in the flow. Returns the inserted row's id.
     */
    fun create(
        accountId: UUID,
        token: String,
        hwid: String,
        expiresAt: Instant,
    ): UUID {
        val inserted = Sessions.insert {
            it[Sessions.accountId] = accountId
            it[Sessions.token] = token
            it[Sessions.hwid] = hwid
            it[Sessions.expiresAt] = expiresAt
            it[Sessions.isValid] = true
        }
        return inserted[Sessions.id].value
    }

    /**
     * Validates an inbound session token.
     *
     * Returns the matching [SessionRow] only when:
     *  1. the token exists,
     *  2. it has not been invalidated (`is_valid = true`),
     *  3. it has not expired (`expires_at > now`), and
     *  4. the bound HWID matches [hwid] (Requirement 5.2 / 6.3).
     *
     * Any other situation yields `null`, which the caller should surface as
     * `invalid_session` or `hwid_mismatch` depending on context.
     */
    fun validate(token: String, hwid: String): SessionRow? {
        if (token.isEmpty()) return null
        val now = Instant.now()
        return Sessions.selectAll()
            .where {
                (Sessions.token eq token) and
                    (Sessions.hwid eq hwid) and
                    (Sessions.isValid eq true) and
                    (Sessions.expiresAt greater now)
            }
            .singleOrNull()
            ?.toRow()
    }

    fun findByToken(token: String): SessionRow? =
        Sessions.selectAll().where { Sessions.token eq token }.singleOrNull()?.toRow()

    fun findById(id: UUID): SessionRow? =
        Sessions.selectAll().where { Sessions.id eq id }.singleOrNull()?.toRow()

    /**
     * Marks a single session token as invalid (used by `/auth/logout`).
     *
     * @return `true` when a row was updated, `false` when no matching token
     * existed. Idempotent: invalidating an already-invalid token still
     * returns `true` because the row was matched.
     */
    fun invalidate(token: String): Boolean =
        Sessions.update({ Sessions.token eq token }) {
            it[isValid] = false
        } > 0

    /** Marks every session for the given account as invalid. */
    fun invalidateAll(accountId: UUID): Int =
        Sessions.update({ Sessions.accountId eq accountId }) {
            it[isValid] = false
        }

    private fun ResultRow.toRow(): SessionRow = SessionRow(
        id = this[Sessions.id].value,
        accountId = this[Sessions.accountId].value,
        token = this[Sessions.token],
        hwid = this[Sessions.hwid],
        createdAt = this[Sessions.createdAt],
        expiresAt = this[Sessions.expiresAt],
        isValid = this[Sessions.isValid],
    )
}
