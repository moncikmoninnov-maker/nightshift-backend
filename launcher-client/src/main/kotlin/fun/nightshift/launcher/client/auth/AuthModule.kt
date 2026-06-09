package `fun`.nightshift.launcher.client.auth

import `fun`.nightshift.launcher.client.api.BackendApiClient
import `fun`.nightshift.launcher.client.api.BackendException
import `fun`.nightshift.launcher.client.credentials.CredentialStore
import `fun`.nightshift.launcher.client.hwid.HwidCollector
import `fun`.nightshift.launcher.shared.dto.AccountInfo
import `fun`.nightshift.launcher.shared.dto.LoginRequest
import `fun`.nightshift.launcher.shared.dto.LoginResponse
import `fun`.nightshift.launcher.shared.dto.PasswordResetConfirm
import `fun`.nightshift.launcher.shared.dto.PasswordResetRequest
import `fun`.nightshift.launcher.shared.dto.RegisterRequest
import `fun`.nightshift.launcher.shared.dto.ValidateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

/**
 * High-level wrapper around register / login / logout / validate / password
 * reset flows.
 *
 * Responsibilities:
 *  * Delegates HTTP work to [BackendApiClient].
 *  * Persists the bearer session token to the platform [CredentialStore]
 *    (only when the user opted into "Запомнить меня").
 *  * Caches [AccountInfo] in memory so other modules can render quickly
 *    without re-validating on every paint.
 *  * Holds the current token via the [tokenProvider] hook the API client
 *    reads — so once we log in, every subsequent API call carries the bearer.
 *
 * **Threading.** All public methods are `suspend`; they switch to
 * `Dispatchers.IO` internally for the blocking HWID probe and credential
 * store calls. Safe to call from the UI thread inside `LaunchedEffect` /
 * `rememberCoroutineScope`.
 */
class AuthModule(
    private val api: BackendApiClient,
    private val hwid: HwidCollector,
    private val credentials: CredentialStore,
) {

    private val tokenRef = AtomicReference<String?>(null)
    private val accountRef = AtomicReference<AccountInfo?>(null)

    /** Hook to install in [BackendApiClient.tokenProvider] so the bearer flows automatically. */
    fun tokenProvider(): () -> String? = { tokenRef.get() }

    /** Currently cached account info (post-login) — null when not authenticated. */
    val cachedAccount: AccountInfo? get() = accountRef.get()

    /** True when there is a session token in memory (validated or freshly issued). */
    val isAuthenticated: Boolean get() = tokenRef.get() != null

    suspend fun register(login: String, email: String, password: String, rememberMe: Boolean): Result<AccountInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val hwidValue = hwid.collect()
                api.register(RegisterRequest(login, email, password, hwidValue))
                    .onSuccess { storeSession(it, rememberMe) }
                    .map { it.account }
                    .also { logFailure("register", it) }
            }.getOrElse { t ->
                log.warn("register threw {}: {}", t::class.simpleName, t.message)
                Result.failure(BackendException("network_error", t.message ?: "Network error", null))
            }
        }

    suspend fun login(login: String, password: String, rememberMe: Boolean): Result<AccountInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val hwidValue = hwid.collect()
                api.login(LoginRequest(login, password, hwidValue, rememberMe))
                    .onSuccess { storeSession(it, rememberMe) }
                    .map { it.account }
                    .also { logFailure("login", it) }
            }.getOrElse { t ->
                log.warn("login threw {}: {}", t::class.simpleName, t.message)
                Result.failure(BackendException("network_error", t.message ?: "Network error", null))
            }
        }

    /**
     * Tries to revive a session from [CredentialStore]. Returns the up-to-date
     * [AccountInfo] on success, `null` when no token is persisted, or
     * [Result.failure] when the backend rejected the token (caller should drop
     * to LoginScreen).
     */
    suspend fun tryAutoLogin(): Result<AccountInfo?> = withContext(Dispatchers.IO) {
        val saved = credentials.loadToken()
        if (saved.isNullOrBlank()) {
            log.debug("No saved session token; skipping auto-login")
            return@withContext Result.success(null)
        }
        // Install the token before calling validate; the API client reads it.
        tokenRef.set(saved)
        val hwidValue = hwid.collect()
        val result = api.validate(ValidateRequest(hwidValue))
            .onSuccess { accountRef.set(it) }
            .onFailure {
                log.info("Saved session token rejected: {}", it.message)
                tokenRef.set(null)
                accountRef.set(null)
                credentials.deleteToken()
            }
        result.map { it as AccountInfo? }
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        val res = api.logout()
        // Always wipe local state regardless of server response — we don't
        // want a transient network blip to leave a stale token on disk.
        credentials.deleteToken()
        tokenRef.set(null)
        accountRef.set(null)
        res
    }

    suspend fun requestPasswordReset(loginOrEmail: String, comment: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            api.passwordResetRequest(PasswordResetRequest(loginOrEmail, comment))
                .also { logFailure("passwordResetRequest", it) }
        }

    suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            api.passwordResetConfirm(PasswordResetConfirm(code, newPassword))
                .also { logFailure("passwordResetConfirm", it) }
        }

    /**
     * Refreshes [cachedAccount] from the backend — used by the Main screen
     * after activating a key, so the key status displayed there is accurate.
     */
    suspend fun refreshAccount(): Result<AccountInfo> = withContext(Dispatchers.IO) {
        if (tokenRef.get() == null) return@withContext Result.failure(IllegalStateException("Not authenticated"))
        val hwidValue = hwid.collect()
        api.validate(ValidateRequest(hwidValue))
            .onSuccess { accountRef.set(it) }
    }

    private fun storeSession(response: LoginResponse, rememberMe: Boolean) {
        tokenRef.set(response.token)
        accountRef.set(response.account)
        if (rememberMe) {
            credentials.saveToken(response.token)
        } else {
            // Make sure no leftover token survives if the user explicitly
            // deselected "remember me" this session.
            credentials.deleteToken()
        }
    }

    private fun <T> logFailure(op: String, result: Result<T>) {
        result.onFailure {
            val code = (it as? BackendException)?.code ?: it::class.simpleName
            log.info("Auth op '{}' failed: code={} msg={}", op, code, it.message)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AuthModule::class.java)
    }
}
