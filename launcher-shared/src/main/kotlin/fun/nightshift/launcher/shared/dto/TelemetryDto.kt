package `fun`.nightshift.launcher.shared.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Telemetry event submitted by the launcher.
 *
 * Sensitive fields (login, password, email, hwid) MUST NOT appear in [payload];
 * the backend filters them defensively.
 */
@Serializable
data class TelemetryEventRequest(
    val eventType: String,
    val payload: JsonObject? = null
)

@Serializable
data class CrashReportRequest(
    val stackTrace: String,
    val clientVersion: String
)
