package `fun`.nightshift.launcher.backend.routes

import `fun`.nightshift.launcher.backend.auth.AuthValidation
import `fun`.nightshift.launcher.backend.auth.PasswordHasher
import `fun`.nightshift.launcher.backend.auth.SESSION_TTL_DAYS
import `fun`.nightshift.launcher.backend.auth.SessionTokenGenerator
import `fun`.nightshift.launcher.backend.db.AccountRepository
import `fun`.nightshift.launcher.backend.db.LoginAttemptRepository
import `fun`.nightshift.launcher.backend.db.PasswordResetRepository
import `fun`.nightshift.launcher.backend.db.SessionRepository
import `fun`.nightshift.launcher.backend.db.dbQuery
import `fun`.nightshift.launcher.backend.keys.KeyStatusService
import `fun`.nightshift.launcher.shared.dto.AccountInfo
import `fun`.nightshift.launcher.shared.dto.ApiResponse
import `fun`.nightshift.launcher.shared.dto.KeyStatus
import `fun`.nightshift.launcher.shared.dto.LoginRequest
import `fun`.nightshift.launcher.shared.dto.LoginResponse
import `fun`.nightshift.launcher.shared.dto.PasswordResetConfirm
import `fun`.nightshift.launcher.shared.dto.PasswordResetRequest
import `fun`.nightshift.launcher.shared.dto.RegisterRequest
import `fun`.nightshift.launcher.shared.dto.ValidateRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.pipeline.PipelineContext
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

private val log = LoggerFactory.getLogger("fun.nightshift.launcher.backend.routes.AuthRoutes")

/** Sliding window for the failed-login lockout (Requirement 6.7). */
private val FAILED_LOGIN_WINDOW: Duration = Duration.ofMinutes(10)
private const val FAILED_LOGIN_THRESHOLD = 5L

/**
 * Wires every `/auth/...` endpoint into the supplied [Route].
 *
 * The caller is responsible for installing the `RateLimit` plugin and
 * registering the `RateLimitName("login")` configuration so the
 * `/auth/login` lookup below can find it.
 */
fun Route.authRoutes() {
    route("/auth") {
        post("/register") { handleRegister() }

        rateLimit(RateLimitName("login")) {
            post("/login") { handleLogin() }
        }

        post("/validate") { handleValidate() }
        post("/logout") { handleLogout() }
        post("/password-reset-request") { handlePasswordResetRequest() }
        post("/password-reset") { handlePasswordResetConfirm() }
    }
}

// ---------------------------------------------------------------------------
// Route handler scope (Ktor 2.x DSL receiver)
// ---------------------------------------------------------------------------

private typealias AuthRouteScope = PipelineContext<Unit, io.ktor.server.application.ApplicationCall>

// ---------------------------------------------------------------------------
// /auth/register
// ---------------------------------------------------------------------------

private suspend fun AuthRouteScope.handleRegister() {
    val req = call.receive<RegisterRequest>()

    val validation = firstNonNullOf(
        AuthValidation.validateLogin(req.login),
        AuthValidation.validateEmail(req.email),
        AuthValidation.validatePassword(req.password),
        AuthValidation.validateHwid(req.hwid),
    )
    if (validation != null) {
        return call.respondError(HttpStatusCode.BadRequest, "validation_failed", validation)
    }

    // Hash off the request thread; `dbQuery` already runs on Dispatchers.IO.
    val passwordHash = dbQuery {
        if (AccountRepository.findByLogin(req.login) != null ||
            AccountRepository.findByEmail(req.email) != null
        ) {
            null
        } else {
            PasswordHasher.hash(req.password.toCharArray())
        }
    } ?: return call.respondError(
        HttpStatusCode.Conflict, "account_exists", "Login or email already taken"
    )

    val now = Instant.now()
    val expiresAt = now.plus(Duration.ofDays(SESSION_TTL_DAYS))
    val token = SessionTokenGenerator.generate()

    val accountId = dbQuery {
        val id = AccountRepository.create(
            login = req.login,
            email = req.email,
            passwordHash = passwordHash,
            hwid = req.hwid,
            hwidStatus = "locked",
        )
        SessionRepository.create(
            accountId = id,
            token = token,
            hwid = req.hwid,
            expiresAt = expiresAt,
        )
        id
    }

    call.respond(
        HttpStatusCode.OK,
        ApiResponse(
            success = true,
            data = LoginResponse(
                token = token,
                expiresAt = expiresAt.toEpochMilli(),
                account = AccountInfo(
                    id = accountId.toString(),
                    login = req.login,
                    keyStatus = KeyStatus.NoKey,
                ),
            ),
        ),
    )
}

// ---------------------------------------------------------------------------
// /auth/login
// ---------------------------------------------------------------------------

private suspend fun AuthRouteScope.handleLogin() {
    val req = call.receive<LoginRequest>()
    val ip = call.request.local.remoteHost

    if (AuthValidation.validateLogin(req.login) != null || req.password.isEmpty()) {
        return call.respondError(
            HttpStatusCode.Unauthorized, "invalid_credentials", "Invalid login or password"
        )
    }
    AuthValidation.validateHwid(req.hwid)?.let {
        return call.respondError(HttpStatusCode.BadRequest, "validation_failed", it)
    }

    // Per-account sliding lockout (5 failures in 10 minutes).
    val failures = dbQuery {
        LoginAttemptRepository.countFailedSince(req.login, Instant.now().minus(FAILED_LOGIN_WINDOW))
    }
    if (failures >= FAILED_LOGIN_THRESHOLD) {
        return call.respondError(
            HttpStatusCode.TooManyRequests,
            "rate_limited",
            "Too many failed attempts. Try again in 10 minutes",
        )
    }

    val account = dbQuery { AccountRepository.findByLogin(req.login) }
    if (account == null) {
        dbQuery { LoginAttemptRepository.record(req.login, ip, success = false) }
        return call.respondError(
            HttpStatusCode.Unauthorized, "invalid_credentials", "Invalid login or password"
        )
    }

    val passwordOk = dbQuery { PasswordHasher.verify(account.passwordHash, req.password.toCharArray()) }
    dbQuery { LoginAttemptRepository.record(req.login, ip, success = passwordOk) }
    if (!passwordOk) {
        return call.respondError(
            HttpStatusCode.Unauthorized, "invalid_credentials", "Invalid login or password"
        )
    }

    // HWID check. `pending_reset` allows the next login to re-bind to a new HWID.
    val effectiveHwid = when {
        account.hwid == req.hwid -> req.hwid
        account.hwidStatus == "pending_reset" -> {
            dbQuery { AccountRepository.updateHwid(account.id, req.hwid, "locked") }
            req.hwid
        }
        else -> return call.respondError(
            HttpStatusCode.Forbidden, "hwid_mismatch", "HWID does not match"
        )
    }

    val now = Instant.now()
    val expiresAt = now.plus(Duration.ofDays(SESSION_TTL_DAYS))
    val token = SessionTokenGenerator.generate()
    dbQuery {
        SessionRepository.create(
            accountId = account.id,
            token = token,
            hwid = effectiveHwid,
            expiresAt = expiresAt,
        )
    }

    val keyStatus = dbQuery {
        KeyStatusService.resolveBlocking(account.id, effectiveHwid)
    }

    call.respond(
        HttpStatusCode.OK,
        ApiResponse(
            success = true,
            data = LoginResponse(
                token = token,
                expiresAt = expiresAt.toEpochMilli(),
                account = AccountInfo(
                    id = account.id.toString(),
                    login = account.login,
                    keyStatus = keyStatus,
                ),
            ),
        ),
    )
}

// ---------------------------------------------------------------------------
// /auth/validate
// ---------------------------------------------------------------------------

private suspend fun AuthRouteScope.handleValidate() {
    val token = call.bearerToken() ?: return call.respondError(
        HttpStatusCode.Unauthorized, "invalid_session", "Missing bearer token"
    )
    // Body is required (carries the current HWID).
    val req = call.receive<ValidateRequest>()
    AuthValidation.validateHwid(req.hwid)?.let {
        return call.respondError(HttpStatusCode.BadRequest, "validation_failed", it)
    }

    val session = dbQuery { SessionRepository.findByToken(token) }
    if (session == null || !session.isValid || session.expiresAt.isBefore(Instant.now())) {
        return call.respondError(
            HttpStatusCode.Unauthorized, "invalid_session", "Session is invalid or expired"
        )
    }
    if (session.hwid != req.hwid) {
        return call.respondError(
            HttpStatusCode.Forbidden, "hwid_mismatch", "HWID does not match"
        )
    }

    val account = dbQuery { AccountRepository.findById(session.accountId) }
        ?: return call.respondError(
            HttpStatusCode.Unauthorized, "invalid_session", "Account not found"
        )

    // Real KeyStatus calculation (Active / Expired / NoKey) — see KeyStatusService.
    val keyStatus = dbQuery {
        KeyStatusService.resolveBlocking(account.id, account.hwid)
    }

    call.respond(
        HttpStatusCode.OK,
        ApiResponse(
            success = true,
            data = AccountInfo(
                id = account.id.toString(),
                login = account.login,
                keyStatus = keyStatus,
            ),
        ),
    )
}

// ---------------------------------------------------------------------------
// /auth/logout
// ---------------------------------------------------------------------------

private suspend fun AuthRouteScope.handleLogout() {
    val token = call.bearerToken() ?: return call.respondError(
        HttpStatusCode.Unauthorized, "invalid_session", "Missing bearer token"
    )
    dbQuery { SessionRepository.invalidate(token) }
    call.respond(HttpStatusCode.NoContent)
}

// ---------------------------------------------------------------------------
// /auth/password-reset-request
// ---------------------------------------------------------------------------

private suspend fun AuthRouteScope.handlePasswordResetRequest() {
    val req = call.receive<PasswordResetRequest>()

    if (req.loginOrEmail.isBlank()) {
        return call.respondError(
            HttpStatusCode.BadRequest, "validation_failed", "Login or email is required"
        )
    }
    val comment = req.comment.takeIf { it.isNotBlank() }

    // Always respond 200 to avoid leaking whether the account exists.
    dbQuery {
        val account = AccountRepository.findByLogin(req.loginOrEmail)
            ?: AccountRepository.findByEmail(req.loginOrEmail)
        if (account != null) {
            PasswordResetRepository.createRequest(account.id, comment)
        } else {
            log.info("Password reset requested for unknown account: {}", req.loginOrEmail)
        }
    }

    call.respond(HttpStatusCode.OK, ApiResponse<Unit>(success = true))
}

// ---------------------------------------------------------------------------
// /auth/password-reset
// ---------------------------------------------------------------------------

private suspend fun AuthRouteScope.handlePasswordResetConfirm() {
    val req = call.receive<PasswordResetConfirm>()

    if (req.code.isBlank()) {
        return call.respondError(
            HttpStatusCode.BadRequest, "invalid_code", "Reset code is required"
        )
    }
    AuthValidation.validatePassword(req.newPassword)?.let {
        return call.respondError(HttpStatusCode.BadRequest, "validation_failed", it)
    }

    val request = dbQuery { PasswordResetRepository.findByCode(req.code) }
        ?: return call.respondError(
            HttpStatusCode.BadRequest, "invalid_code", "Reset code is invalid"
        )

    val expiresAt = request.codeExpiresAt
    if (expiresAt == null || expiresAt.isBefore(Instant.now())) {
        dbQuery { PasswordResetRepository.markExpired(request.id) }
        return call.respondError(
            HttpStatusCode.BadRequest, "code_expired", "Reset code has expired"
        )
    }

    val newHash = PasswordHasher.hash(req.newPassword.toCharArray())
    dbQuery {
        AccountRepository.updatePassword(request.accountId, newHash)
        PasswordResetRepository.markUsed(request.id)
        // Invalidate every existing session so the leaked password (if any) is useless.
        SessionRepository.invalidateAll(request.accountId)
    }

    call.respond(HttpStatusCode.OK, ApiResponse<Unit>(success = true))
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Returns the first non-null value, or `null` if every argument is null. */
private fun firstNonNullOf(vararg values: String?): String? {
    for (v in values) if (v != null) return v
    return null
}
