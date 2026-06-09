package `fun`.nightshift.launcher.client.api

import `fun`.nightshift.launcher.shared.dto.AccountInfo
import `fun`.nightshift.launcher.shared.dto.ApiError
import `fun`.nightshift.launcher.shared.dto.ApiResponse
import `fun`.nightshift.launcher.shared.dto.CrashReportRequest
import `fun`.nightshift.launcher.shared.dto.KeyActivateRequest
import `fun`.nightshift.launcher.shared.dto.KeyInfo
import `fun`.nightshift.launcher.shared.dto.KeyValidateRequest
import `fun`.nightshift.launcher.shared.dto.LoginRequest
import `fun`.nightshift.launcher.shared.dto.LoginResponse
import `fun`.nightshift.launcher.shared.dto.OnlineCountResponse
import `fun`.nightshift.launcher.shared.dto.PasswordResetConfirm
import `fun`.nightshift.launcher.shared.dto.PasswordResetRequest
import `fun`.nightshift.launcher.shared.dto.RegisterRequest
import `fun`.nightshift.launcher.shared.dto.ScreenshotManifestEntry
import `fun`.nightshift.launcher.shared.dto.TelemetryEventRequest
import `fun`.nightshift.launcher.shared.dto.UpdateInfo
import `fun`.nightshift.launcher.shared.dto.ValidateRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Suspending Ktor-based wrapper around the launcher backend.
 *
 * The class is the single point of network access for every other client
 * module (auth, key, online, update, telemetry). It owns one [HttpClient]
 * instance with shared configuration:
 *
 *  * `CIO` engine — pure JVM, no native dependency.
 *  * `ContentNegotiation` + `kotlinx-serialization` for JSON.
 *  * `HttpTimeout` — connect 10 s / request 30 s (per task 8.1 spec).
 *  * `defaultRequest` — sets the brand-version header on every call and
 *    pulls a bearer token from [tokenProvider] when one is currently held.
 *
 * Each public method maps 1:1 to an endpoint declared in the design
 * document. Every method returns `Result<T>` where:
 *
 *  * On HTTP 2xx with `success=true` envelope → `Result.success(data)`.
 *  * On 204 endpoints → `Result.success(Unit)`.
 *  * On non-2xx OR `success=false` → `Result.failure(BackendException)`.
 *  * On HTTP 426 → `Result.failure(ClientOutdatedException)`.
 *  * On network/IO/timeout → `Result.failure(BackendException("network_error",..., null))`.
 *
 * Tokens are never logged, even at DEBUG level — they are PII-equivalent
 * (Requirement 6.4).
 *
 * @param baseUrl       Root URL of the backend (e.g. `https://api.nightshift.fun`
 *                      or `http://localhost:8080`). Trailing slash is optional.
 * @param clientVersion Value of the `X-Client-Version` header sent on every
 *                      request (Requirement 17.4).
 * @param tokenProvider Returns the current bearer session token, or `null` when
 *                      the user is logged out / has not authenticated yet.
 *                      Invoked on every request, so it must be cheap and
 *                      thread-safe.
 * @param json          Optional override for the JSON serializer; defaults
 *                      tolerate unknown keys for forward compatibility.
 * @param httpClient    Optional injected client (for tests). When supplied,
 *                      callers retain ownership and must close it themselves.
 */
class BackendApiClient(
    baseUrl: String,
    private val clientVersion: String,
    private val tokenProvider: () -> String? = { null },
    private val json: Json = DEFAULT_JSON,
    httpClient: HttpClient? = null,
) : AutoCloseable {

    private val baseUrl: String = baseUrl.trimEnd('/')

    /** True when we built the underlying [HttpClient] ourselves and must close it. */
    private val ownsClient: Boolean = httpClient == null

    private val client: HttpClient = httpClient ?: HttpClient(CIO) {
        expectSuccess = false // we inspect statuses ourselves

        install(ContentNegotiation) {
            json(this@BackendApiClient.json)
        }

        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }

        defaultRequest {
            header(HEADER_CLIENT_VERSION, clientVersion)
            contentType(ContentType.Application.Json)
            // `Accept: application/json` ensures we get a JSON envelope back
            // even from misconfigured servers / proxies.
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
    }

    // ---------------------------------------------------------------------
    // Auth
    // ---------------------------------------------------------------------

    /** `POST /auth/register` — create a new account and receive a session. */
    suspend fun register(request: RegisterRequest): Result<LoginResponse> {
        log.info("register: about to POST /auth/register baseUrl={}", baseUrl)
        val res: Result<LoginResponse> = try {
            postJson("auth/register", request)
        } catch (t: Throwable) {
            log.error("register: postJson threw {} : {}", t::class.simpleName, t.message, t)
            return Result.failure(BackendException("network_error", t.message ?: "Network error", null))
        }
        log.info("register: result success={} err={}", res.isSuccess, res.exceptionOrNull()?.message)
        return res
    }

    /** `POST /auth/login` — authenticate and receive a session. */
    suspend fun login(request: LoginRequest): Result<LoginResponse> {
        log.info("login: about to POST /auth/login baseUrl={}", baseUrl)
        val res: Result<LoginResponse> = try {
            postJson("auth/login", request)
        } catch (t: Throwable) {
            log.error("login: postJson threw {} : {}", t::class.simpleName, t.message, t)
            return Result.failure(BackendException("network_error", t.message ?: "Network error", null))
        }
        log.info("login: result success={} err={}", res.isSuccess, res.exceptionOrNull()?.message)
        return res
    }

    /** `POST /auth/validate` — verify the bearer token, returns up-to-date account info. */
    suspend fun validate(request: ValidateRequest): Result<AccountInfo> =
        postJson("auth/validate", request)

    /** `POST /auth/logout` — invalidate the current session token (204). */
    suspend fun logout(): Result<Unit> = postNoContent("auth/logout", body = null)

    /** `POST /auth/password-reset-request` — registers a reset request (200, no payload). */
    suspend fun passwordResetRequest(request: PasswordResetRequest): Result<Unit> =
        postEmptyJson("auth/password-reset-request", request)

    /** `POST /auth/password-reset` — confirms a reset code and sets a new password. */
    suspend fun passwordResetConfirm(request: PasswordResetConfirm): Result<Unit> =
        postEmptyJson("auth/password-reset", request)

    // ---------------------------------------------------------------------
    // Key
    // ---------------------------------------------------------------------

    /** `POST /key/activate` — bind an unused activation key to the current account. */
    suspend fun keyActivate(request: KeyActivateRequest): Result<KeyInfo> =
        postJson("key/activate", request)

    /** `POST /key/validate` — refresh the active key bound to the current account. */
    suspend fun keyValidate(request: KeyValidateRequest): Result<KeyInfo> =
        postJson("key/validate", request)

    // ---------------------------------------------------------------------
    // Online
    // ---------------------------------------------------------------------

    /** `GET /online/count` — public, returns the current online user count. */
    suspend fun onlineCount(): Result<Int> {
        val attempt = executeNetwork { client.get(buildUrl("online/count")) { bearerAuthIfAvailable() } }
        return when (attempt) {
            is NetworkOutcome.Failed -> attempt.toResult()
            is NetworkOutcome.Ok -> {
                val response = attempt.response
                if (response.status.value == HTTP_CLIENT_OUTDATED) {
                    return Result.failure(ClientOutdatedException(extractErrorMessage(response) ?: "Launcher version is no longer supported"))
                }
                val envelope: ApiResponse<OnlineCountResponse>? = try {
                    response.body<ApiResponse<OnlineCountResponse>>()
                } catch (t: Throwable) {
                    log.warn("Failed to parse online/count envelope: {}", t.message)
                    null
                }
                if (envelope == null) return parseFailure(response)
                interpretEnvelope(response, envelope) { it.count }
            }
        }
    }

    /** `POST /online/heartbeat` — bearer-authenticated keepalive (204). */
    suspend fun onlineHeartbeat(): Result<Unit> = postNoContent("online/heartbeat", body = null)

    // ---------------------------------------------------------------------
    // Update
    // ---------------------------------------------------------------------

    /** `GET /update/check` — public, returns latest launcher metadata. */
    suspend fun updateCheck(): Result<UpdateInfo> = getJson("update/check")

    /** `GET /update/screenshots` — public, returns the screenshot manifest. */
    suspend fun updateScreenshots(): Result<List<ScreenshotManifestEntry>> =
        getJson("update/screenshots")

    // ---------------------------------------------------------------------
    // Telemetry
    // ---------------------------------------------------------------------

    /** `POST /telemetry/event` — fire-and-forget event log (204). */
    suspend fun telemetryEvent(request: TelemetryEventRequest): Result<Unit> =
        postNoContent("telemetry/event", body = request)

    /** `POST /telemetry/crash` — submit a crash report (204). */
    suspend fun telemetryCrash(request: CrashReportRequest): Result<Unit> =
        postNoContent("telemetry/crash", body = request)

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    override fun close() {
        if (ownsClient) {
            client.close()
        }
    }

    // ---------------------------------------------------------------------
    // Request helpers
    // ---------------------------------------------------------------------

    /**
     * POSTs [body] as JSON, parses the `ApiResponse<T>` envelope and surfaces
     * the [ApiResponse.data] value.
     */
    private suspend inline fun <reified Req : Any, reified T : Any> postJson(
        path: String,
        body: Req,
    ): Result<T> {
        val attempt = executeNetwork {
            client.post(buildUrl(path)) {
                bearerAuthIfAvailable()
                setBody(body)
            }
        }
        return when (attempt) {
            is NetworkOutcome.Failed -> attempt.toResult()
            is NetworkOutcome.Ok -> {
                val response = attempt.response
                if (response.status.value == HTTP_CLIENT_OUTDATED) {
                    return Result.failure(ClientOutdatedException(extractErrorMessage(response) ?: "Launcher version is no longer supported"))
                }
                val envelope: ApiResponse<T>? = try {
                    response.body<ApiResponse<T>>()
                } catch (t: Throwable) {
                    log.warn("Failed to parse envelope from {}: {}", path, t.message)
                    null
                }
                if (envelope == null) return parseFailure(response)
                interpretEnvelope<T, T>(response, envelope) { it }
            }
        }
    }

    /**
     * GETs [path], parses the `ApiResponse<T>` envelope and surfaces
     * the [ApiResponse.data] value.
     */
    private suspend inline fun <reified T : Any> getJson(path: String): Result<T> {
        val attempt = executeNetwork {
            client.get(buildUrl(path)) { bearerAuthIfAvailable() }
        }
        return when (attempt) {
            is NetworkOutcome.Failed -> attempt.toResult()
            is NetworkOutcome.Ok -> {
                val response = attempt.response
                if (response.status.value == HTTP_CLIENT_OUTDATED) {
                    return Result.failure(ClientOutdatedException(extractErrorMessage(response) ?: "Launcher version is no longer supported"))
                }
                val envelope: ApiResponse<T>? = try {
                    response.body<ApiResponse<T>>()
                } catch (t: Throwable) {
                    log.warn("Failed to parse envelope from {}: {}", path, t.message)
                    null
                }
                if (envelope == null) return parseFailure(response)
                interpretEnvelope<T, T>(response, envelope) { it }
            }
        }
    }

    /**
     * POSTs [body] (JSON-encoded if non-null) to an endpoint where success
     * means either HTTP 204 No Content or `{ "success": true }` with an empty
     * payload.
     */
    private suspend fun postEmptyJson(path: String, body: Any): Result<Unit> {
        val attempt = executeNetwork {
            client.post(buildUrl(path)) {
                bearerAuthIfAvailable()
                setBody(body)
            }
        }
        return when (attempt) {
            is NetworkOutcome.Failed -> attempt.toResult()
            is NetworkOutcome.Ok -> finishUnit(attempt.response)
        }
    }

    /**
     * POSTs to an endpoint that returns 204 on success. When [body] is non-null
     * it is serialized as JSON; otherwise the request has no body but still
     * carries the default headers.
     */
    private suspend fun postNoContent(path: String, body: Any?): Result<Unit> {
        val attempt = executeNetwork {
            client.post(buildUrl(path)) {
                bearerAuthIfAvailable()
                if (body != null) setBody(body)
            }
        }
        return when (attempt) {
            is NetworkOutcome.Failed -> attempt.toResult()
            is NetworkOutcome.Ok -> finishUnit(attempt.response)
        }
    }

    private suspend fun finishUnit(response: HttpResponse): Result<Unit> {
        if (response.status.value == HTTP_CLIENT_OUTDATED) {
            return Result.failure(
                ClientOutdatedException(extractErrorMessage(response) ?: "Launcher version is no longer supported")
            )
        }
        if (!response.status.isSuccess()) {
            return errorFromBody(response)
        }
        if (response.status.value == HTTP_NO_CONTENT) {
            return Result.success(Unit)
        }
        // 200 with envelope (e.g. password-reset endpoints).
        val envelope: ApiResponse<Unit>? = parseEnvelopeNullable(response)
        return when {
            envelope == null -> Result.success(Unit)
            envelope.success -> Result.success(Unit)
            else -> Result.failure(envelope.error.toException(response.status.value))
        }
    }

    /**
     * Runs a Ktor request, catching every network/IO/timeout exception
     * known to the CIO engine and the timeout plugin. Returns a sealed
     * outcome so callers don't repeat boilerplate try/catch blocks.
     */
    private suspend inline fun executeNetwork(
        block: () -> HttpResponse,
    ): NetworkOutcome = try {
        NetworkOutcome.Ok(block())
    } catch (t: HttpRequestTimeoutException) {
        log.warn("Backend request timed out: {}", t.message)
        NetworkOutcome.Failed(t.message)
    } catch (t: ConnectTimeoutException) {
        log.warn("Backend connect timed out: {}", t.message)
        NetworkOutcome.Failed(t.message)
    } catch (t: SocketTimeoutException) {
        log.warn("Backend socket timed out: {}", t.message)
        NetworkOutcome.Failed(t.message)
    } catch (t: TimeoutCancellationException) {
        log.warn("Backend request timed out: {}", t.message)
        NetworkOutcome.Failed(t.message)
    } catch (t: IOException) {
        log.warn("Backend request failed (IO): {}", t.message)
        NetworkOutcome.Failed(t.message)
    }

    /** Parses an `ApiResponse<T>` envelope; returns `null` on parse failure. */
    @Suppress("unused")
    private suspend inline fun <reified T : Any> parseEnvelopeNullable(
        response: HttpResponse,
    ): ApiResponse<T>? = try {
        if (response.status.value == HTTP_CLIENT_OUTDATED) {
            null
        } else {
            response.body<ApiResponse<T>>()
        }
    } catch (t: Throwable) {
        log.warn("Failed to parse backend envelope (status={}): {}", response.status.value, t.message)
        null
    }

    /**
     * Translates a successfully-parsed envelope into a [Result]:
     *  - 426 → `ClientOutdatedException`
     *  - non-2xx OR `success=false` → `BackendException`
     *  - 2xx with data → `Result.success(transform(data))`
     */
    private inline fun <T : Any, R> interpretEnvelope(
        response: HttpResponse,
        envelope: ApiResponse<T>,
        transform: (T) -> R,
    ): Result<R> {
        if (response.status.value == HTTP_CLIENT_OUTDATED) {
            return Result.failure(
                ClientOutdatedException(envelope.error?.message ?: "Launcher version is no longer supported")
            )
        }
        if (!response.status.isSuccess() || !envelope.success) {
            return Result.failure(envelope.error.toException(response.status.value))
        }
        val data = envelope.data
            ?: return Result.failure(
                BackendException(
                    code = "invalid_response",
                    displayMessage = "Backend returned success without a data payload",
                    httpStatus = response.status.value,
                )
            )
        return Result.success(transform(data))
    }

    private fun parseFailure(response: HttpResponse): Result<Nothing> {
        if (response.status.value == HTTP_CLIENT_OUTDATED) {
            return Result.failure(ClientOutdatedException("Launcher version is no longer supported"))
        }
        return Result.failure(
            BackendException(
                code = "invalid_response",
                displayMessage = "Failed to parse backend response (status=${response.status.value})",
                httpStatus = response.status.value,
            )
        )
    }

    private suspend fun errorFromBody(response: HttpResponse): Result<Nothing> {
        val envelope: ApiResponse<Unit>? = try {
            response.body<ApiResponse<Unit>>()
        } catch (t: Throwable) {
            log.debug("Could not decode error envelope: {}", t.message)
            null
        }
        val err = envelope?.error
        return Result.failure(
            BackendException(
                code = err?.code ?: "http_${response.status.value}",
                displayMessage = err?.message ?: "Request failed (HTTP ${response.status.value})",
                httpStatus = response.status.value,
            )
        )
    }

    private suspend fun extractErrorMessage(response: HttpResponse): String? {
        val envelope: ApiResponse<Unit>? = try {
            response.body<ApiResponse<Unit>>()
        } catch (t: Throwable) {
            null
        }
        return envelope?.error?.message
    }

    private fun HttpRequestBuilder.bearerAuthIfAvailable() {
        val token = tokenProvider()
        if (!token.isNullOrBlank()) {
            bearerAuth(token)
        }
    }

    private fun buildUrl(path: String): String {
        // `baseUrl` already has trailing slashes trimmed; `path` is relative
        // and never starts with a slash. We use URLBuilder so we get correct
        // encoding even when `baseUrl` carries a base path (e.g. /api/v1).
        val builder = URLBuilder().takeFrom(baseUrl)
        builder.appendPathSegments(path.trimStart('/').split('/'))
        return builder.buildString()
    }

    // ------------------------------------------------------------------
    // Outcome types
    // ------------------------------------------------------------------

    private sealed class NetworkOutcome {
        data class Ok(val response: HttpResponse) : NetworkOutcome()
        data class Failed(val message: String?) : NetworkOutcome() {
            fun <T> toResult(): Result<T> = Result.failure(
                BackendException(
                    code = "network_error",
                    displayMessage = message ?: "Network unavailable",
                    httpStatus = null,
                )
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(BackendApiClient::class.java)

        const val HEADER_CLIENT_VERSION: String = "X-Client-Version"

        /** 10 second connect, 30 second total request — matches task 8.1 spec. */
        const val CONNECT_TIMEOUT_MS: Long = 10_000L
        const val REQUEST_TIMEOUT_MS: Long = 30_000L

        private const val HTTP_NO_CONTENT: Int = 204
        private const val HTTP_CLIENT_OUTDATED: Int = 426

        /** Lenient JSON: tolerate unknown keys so old launchers keep working. */
        val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            isLenient = false
            encodeDefaults = false
            explicitNulls = false
        }
    }
}

private fun io.ktor.http.HttpStatusCode.isSuccess(): Boolean = value in 200..299

private fun ApiError?.toException(status: Int?): BackendException =
    when {
        this == null -> BackendException(
            code = "http_${status ?: -1}",
            displayMessage = "Request failed${status?.let { " (HTTP $it)" } ?: ""}",
            httpStatus = status,
        )
        this.code == "client_outdated" -> ClientOutdatedException(this.message)
        else -> BackendException(this.code, this.message, status)
    }
