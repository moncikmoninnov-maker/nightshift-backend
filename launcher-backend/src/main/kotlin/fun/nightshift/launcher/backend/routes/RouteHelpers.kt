package `fun`.nightshift.launcher.backend.routes

import `fun`.nightshift.launcher.shared.dto.ApiError
import `fun`.nightshift.launcher.shared.dto.ApiResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/**
 * Small route-level helpers shared by every endpoint module.
 *
 * Kept internal so they're visible across the `routes` package without
 * leaking into the public API surface of the backend module.
 */

/**
 * Extracts the bearer token from `Authorization: Bearer <token>`.
 *
 * Returns `null` when:
 *  - the header is missing,
 *  - the header does not start with `Bearer ` (case-insensitive),
 *  - the token portion is blank after trimming.
 */
internal fun ApplicationCall.bearerToken(): String? {
    val header = request.headers["Authorization"] ?: return null
    val prefix = "Bearer "
    if (!header.startsWith(prefix, ignoreCase = true)) return null
    return header.substring(prefix.length).trim().takeIf { it.isNotEmpty() }
}

/**
 * Responds with a uniform [ApiResponse] error envelope.
 *
 * The JSON body matches what the desktop client expects for every failing
 * call: `{ "success": false, "error": { "code": ..., "message": ... } }`.
 */
internal suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    code: String,
    message: String,
) {
    respond(status, ApiResponse<Nothing>(success = false, error = ApiError(code, message)))
}
