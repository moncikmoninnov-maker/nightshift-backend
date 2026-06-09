package `fun`.nightshift.launcher.client.api

/**
 * Base exception for any failure surfaced by [BackendApiClient].
 *
 * Carries the machine-readable [code] from the backend's `ApiError` envelope
 * (e.g. `account_exists`, `hwid_mismatch`, `key_not_found`, ...), a localised
 * [displayMessage] meant to be shown to the user, and the originating HTTP
 * status when available.
 *
 * For network/IO/timeout failures the synthetic code `network_error` is used
 * and [httpStatus] is `null`.
 */
open class BackendException(
    val code: String,
    val displayMessage: String,
    val httpStatus: Int?,
) : RuntimeException(displayMessage)

/**
 * Special-case [BackendException] for HTTP 426 responses — emitted when the
 * backend rejects a request because the launcher's `X-Client-Version` is
 * below the minimum supported version (Requirement 17.5).
 *
 * The caller (UpdateModule) reacts by triggering a forced launcher update
 * before retrying any other API call.
 */
class ClientOutdatedException(
    message: String,
) : BackendException(code = "client_outdated", displayMessage = message, httpStatus = 426)
