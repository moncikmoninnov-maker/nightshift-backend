package `fun`.nightshift.launcher.shared.dto

import kotlinx.serialization.Serializable

/**
 * Generic envelope for all backend responses.
 * Either [data] is set on success, or [error] describes the failure.
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null
)

/**
 * Machine-readable error code with a human-readable message for display.
 * Codes are stable identifiers like `account_exists`, `hwid_mismatch`,
 * `key_not_found`, `key_expired`, `client_outdated`, etc.
 */
@Serializable
data class ApiError(
    val code: String,
    val message: String
)
