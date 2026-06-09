package `fun`.nightshift.launcher.backend.routes

import `fun`.nightshift.launcher.backend.auth.AuthValidation
import `fun`.nightshift.launcher.backend.db.ActivationKeyRepository
import `fun`.nightshift.launcher.backend.db.ActivationKeyRow
import `fun`.nightshift.launcher.backend.db.SessionRepository
import `fun`.nightshift.launcher.backend.db.SessionRow
import `fun`.nightshift.launcher.backend.db.dbQuery
import `fun`.nightshift.launcher.shared.dto.ApiResponse
import `fun`.nightshift.launcher.shared.dto.KeyActivateRequest
import `fun`.nightshift.launcher.shared.dto.KeyInfo
import `fun`.nightshift.launcher.shared.dto.KeyType
import `fun`.nightshift.launcher.shared.dto.KeyValidateRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.pipeline.PipelineContext
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

private val log = LoggerFactory.getLogger("fun.nightshift.launcher.backend.routes.KeyRoutes")

/**
 * Wires `/key/activate` and `/key/validate` into the supplied [Route].
 *
 * Both endpoints require a valid bearer session token whose stored HWID
 * matches the one supplied in the request body — see [SessionRepository.validate].
 */
fun Route.keyRoutes() {
    route("/key") {
        post("/activate") { handleActivate() }
        post("/validate") { handleValidate() }
    }
    // Dev-only helper for generating activation keys without an admin
    // console. Active only in non-production builds — guarded by absence
    // of an ADMIN_TOKEN env var (production deployments must set one).
    if (System.getenv("ADMIN_TOKEN").isNullOrBlank()) {
        route("/dev") {
            post("/keys/create") { handleDevKeyCreate() }
            get("/keys/stats") { handleDevKeyStats() }
        }
    }
}

private typealias KeyRouteScope = PipelineContext<Unit, ApplicationCall>

// ---------------------------------------------------------------------------
// /key/activate
// ---------------------------------------------------------------------------

private suspend fun KeyRouteScope.handleActivate() {
    val req = call.receive<KeyActivateRequest>()

    AuthValidation.validateHwid(req.hwid)?.let {
        return call.respondError(HttpStatusCode.BadRequest, "validation_failed", it)
    }
    if (req.keyValue.isBlank()) {
        return call.respondError(HttpStatusCode.BadRequest, "validation_failed", "Key is required")
    }

    val session = call.requireSession(req.hwid) ?: return

    val now = Instant.now()
    val outcome = dbQuery {
        val key = ActivationKeyRepository.findByValue(req.keyValue.trim())
            ?: return@dbQuery ActivationOutcome.NotFound

        // Idempotent re-activation: if the key is already bound to *this*
        // account/HWID and still active, surface the existing KeyInfo instead
        // of failing with 409. Re-activation by anyone else still conflicts.
        if (key.status != "unused") {
            val sameAccount = key.accountId == session.accountId && key.hwid == session.hwid
            return@dbQuery if (sameAccount && key.status == "active") {
                ActivationOutcome.Activated(key)
            } else {
                ActivationOutcome.AlreadyUsed
            }
        }
        val type = parseKeyType(key.keyType)
            ?: return@dbQuery ActivationOutcome.InvalidType(key.keyType)
        val expiresAt = expiryForRaw(key.keyType, now)
        val updated = ActivationKeyRepository.activate(
            keyId = key.id,
            accountId = session.accountId,
            hwid = session.hwid,
            activatedAt = now,
            expiresAt = expiresAt,
        )
        if (updated == 0) {
            // Race: someone else activated it between findByValue and activate.
            ActivationOutcome.AlreadyUsed
        } else {
            ActivationOutcome.Activated(
                key.copy(
                    accountId = session.accountId,
                    hwid = session.hwid,
                    activatedAt = now,
                    expiresAt = expiresAt,
                    status = "active",
                )
            )
        }
    }

    when (outcome) {
        is ActivationOutcome.NotFound ->
            call.respondError(HttpStatusCode.NotFound, "key_not_found", "Key not found")
        is ActivationOutcome.AlreadyUsed ->
            call.respondError(HttpStatusCode.Conflict, "key_already_used", "Key has already been used")
        is ActivationOutcome.InvalidType -> {
            log.error("Activation key has unsupported key_type: {}", outcome.raw)
            call.respondError(HttpStatusCode.InternalServerError, "internal", "Invalid key configuration")
        }
        is ActivationOutcome.Activated ->
            call.respond(
                HttpStatusCode.OK,
                ApiResponse(success = true, data = outcome.row.toKeyInfo(now)),
            )
    }
}

// ---------------------------------------------------------------------------
// /key/validate
// ---------------------------------------------------------------------------

private suspend fun KeyRouteScope.handleValidate() {
    val req = call.receive<KeyValidateRequest>()

    AuthValidation.validateHwid(req.hwid)?.let {
        return call.respondError(HttpStatusCode.BadRequest, "validation_failed", it)
    }

    val session = call.requireSession(req.hwid) ?: return

    val now = Instant.now()
    val outcome = dbQuery {
        val key = ActivationKeyRepository.findActiveByAccount(session.accountId, session.hwid)
            ?: return@dbQuery ValidationOutcome.NoKey
        val expiresAt = key.expiresAt
        if (expiresAt != null && expiresAt.isBefore(now)) {
            ActivationKeyRepository.markExpired(key.id)
            ValidationOutcome.Expired
        } else {
            val type = parseKeyType(key.keyType)
                ?: return@dbQuery ValidationOutcome.InvalidType(key.keyType)
            ValidationOutcome.Active(key, type)
        }
    }

    when (outcome) {
        is ValidationOutcome.NoKey ->
            call.respondError(HttpStatusCode.NotFound, "key_not_found", "No active key bound to this account")
        is ValidationOutcome.Expired ->
            call.respondError(HttpStatusCode.Gone, "key_expired", "Key has expired")
        is ValidationOutcome.InvalidType -> {
            log.error("Activation key has unsupported key_type: {}", outcome.raw)
            call.respondError(HttpStatusCode.InternalServerError, "internal", "Invalid key configuration")
        }
        is ValidationOutcome.Active ->
            call.respond(
                HttpStatusCode.OK,
                ApiResponse(success = true, data = outcome.row.toKeyInfo(now)),
            )
    }
}

// ---------------------------------------------------------------------------
// Outcomes (sealed result hierarchies keep DB tx blocks small)
// ---------------------------------------------------------------------------

private sealed class ActivationOutcome {
    data object NotFound : ActivationOutcome()
    data object AlreadyUsed : ActivationOutcome()
    data class InvalidType(val raw: String) : ActivationOutcome()
    data class Activated(val row: ActivationKeyRow) : ActivationOutcome()
}

private sealed class ValidationOutcome {
    data object NoKey : ValidationOutcome()
    data object Expired : ValidationOutcome()
    data class InvalidType(val raw: String) : ValidationOutcome()
    data class Active(val row: ActivationKeyRow, val type: KeyType) : ValidationOutcome()
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Loads the bearer session and confirms its HWID matches [requestHwid].
 * Responds with an appropriate error and returns `null` when invalid; the
 * caller must `return` immediately in that case.
 *
 * Uses the package-internal `bearerToken` / `respondError` helpers from
 * [RouteHelpers].
 */
private suspend fun ApplicationCall.requireSession(requestHwid: String): SessionRow? {
    val token = bearerToken()
    if (token == null) {
        respondError(HttpStatusCode.Unauthorized, "invalid_session", "Missing bearer token")
        return null
    }
    val session = dbQuery { SessionRepository.validate(token, requestHwid) }
    if (session == null) {
        respondError(HttpStatusCode.Unauthorized, "invalid_session", "Session is invalid or expired")
        return null
    }
    return session
}

/** Parses the database `key_type` column into the shared [KeyType] enum. */
private fun parseKeyType(raw: String): KeyType? = when {
    raw.lowercase() == "day" -> KeyType.DAY
    raw.lowercase() == "week" -> KeyType.WEEK
    raw.lowercase() == "month" -> KeyType.MONTH
    raw.lowercase() == "lifetime" -> KeyType.LIFETIME
    // Arbitrary-length keys: stored as `custom_<N>`. Mapped onto MONTH for
    // the wire enum (the enum has no custom variant), but the actual
    // duration is computed by [expiryForRaw] from the stored type string.
    raw.lowercase().startsWith("custom_") -> KeyType.MONTH
    else -> null
}

/**
 * Returns the absolute expiry instant for a freshly activated key, or `null` for lifetime.
 * Accepts the *raw* `key_type` string from the database so it can interpret
 * arbitrary `custom_<N>` durations alongside the canonical day/week/month/lifetime.
 */
private fun expiryForRaw(rawType: String, activatedAt: Instant): Instant? {
    val lower = rawType.lowercase()
    return when {
        lower == "day" -> activatedAt.plus(Duration.ofDays(1))
        lower == "week" -> activatedAt.plus(Duration.ofDays(7))
        lower == "month" -> activatedAt.plus(Duration.ofDays(30))
        lower == "lifetime" -> null
        lower.startsWith("custom_") -> {
            val days = lower.removePrefix("custom_").toLongOrNull()
                ?: error("Malformed custom key type '$rawType'")
            activatedAt.plus(Duration.ofDays(days))
        }
        else -> null
    }
}

/** Converts a row + reference instant into the shared [KeyInfo] DTO. */
private fun ActivationKeyRow.toKeyInfo(now: Instant): KeyInfo {
    val type = parseKeyType(keyType) ?: KeyType.LIFETIME
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

// ---------------------------------------------------------------------------
// Dev helper — POST /dev/keys/create
// ---------------------------------------------------------------------------

@kotlinx.serialization.Serializable
private data class DevKeyCreateRequest(
    /** day | week | month | lifetime — when [days] is set, [type] is ignored. */
    val type: String = "lifetime",
    /** Optional explicit count — overrides [type] and stores `custom_<days>`. */
    val days: Int? = null,
    /** Number of keys to generate; 1..1000. Defaults to 1. */
    val count: Int = 1,
    /** Optional explicit value for a single-key request. */
    val keyValue: String? = null,
)

@kotlinx.serialization.Serializable
private data class DevKeyCreateResponse(
    val keyValue: String,
    val type: String,
)

@kotlinx.serialization.Serializable
private data class DevKeyBulkResponse(
    val type: String,
    val count: Int,
    val keys: List<String>,
)

private suspend fun KeyRouteScope.handleDevKeyCreate() {
    val req = try {
        call.receive<DevKeyCreateRequest>()
    } catch (_: Throwable) {
        DevKeyCreateRequest()
    }

    // Normalise the stored type string. `days` wins over `type` when present.
    val storedType = when {
        req.days != null && req.days > 0 -> "custom_${req.days}"
        else -> req.type.lowercase()
    }
    if (parseKeyType(storedType) == null) {
        return call.respondError(
            HttpStatusCode.BadRequest, "validation_failed",
            "type must be one of day|week|month|lifetime, or pass `days`",
        )
    }

    val count = req.count.coerceIn(1, 1000)
    if (count == 1) {
        val value = req.keyValue?.takeIf { it.isNotBlank() } ?: generateKeyValue()
        dbQuery { ActivationKeyRepository.create(value, storedType) }
        log.info("[dev] generated key {} type={}", value, storedType)
        call.respond(
            HttpStatusCode.OK,
            ApiResponse(success = true, data = DevKeyCreateResponse(value, storedType)),
        )
        return
    }

    // Bulk path — keep each insert in its own transaction so a partial
    // collision (extremely unlikely with 16 random alphanumeric chars)
    // doesn't roll back the whole batch.
    val generated = ArrayList<String>(count)
    repeat(count) {
        val value = generateKeyValue()
        runCatching {
            dbQuery { ActivationKeyRepository.create(value, storedType) }
            generated += value
        }.onFailure { log.warn("[dev] insert collision for {}: {}", value, it.message) }
    }
    log.info("[dev] generated {} keys of type {}", generated.size, storedType)
    call.respond(
        HttpStatusCode.OK,
        ApiResponse(success = true, data = DevKeyBulkResponse(storedType, generated.size, generated)),
    )
}

private fun generateKeyValue(): String {
    val chars = ('A'..'Z') + ('0'..'9')
    return (1..16).map { chars.random() }.joinToString("")
}

@kotlinx.serialization.Serializable
private data class DevKeyStatsResponse(
    val totalKeys: Int,
    val byTypeAndStatus: Map<String, Int>,
)

private suspend fun KeyRouteScope.handleDevKeyStats() {
    val rows: Map<String, Int> = dbQuery {
        org.jetbrains.exposed.sql.transactions.transaction {
            org.jetbrains.exposed.sql.SqlExpressionBuilder.run {
                val typeCol = `fun`.nightshift.launcher.backend.db.ActivationKeys.keyType
                val statusCol = `fun`.nightshift.launcher.backend.db.ActivationKeys.status
                val countCol = org.jetbrains.exposed.sql.Count(`fun`.nightshift.launcher.backend.db.ActivationKeys.id)
                `fun`.nightshift.launcher.backend.db.ActivationKeys
                    .select(typeCol, statusCol, countCol)
                    .groupBy(typeCol, statusCol)
                    .associate { row ->
                        "${row[typeCol]}/${row[statusCol]}" to row[countCol].toInt()
                    }
            }
        }
    }
    val total = rows.values.sum()
    call.respond(
        HttpStatusCode.OK,
        ApiResponse(success = true, data = DevKeyStatsResponse(total, rows)),
    )
}
