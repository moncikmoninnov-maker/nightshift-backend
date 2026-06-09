package `fun`.nightshift.launcher.backend.routes

import `fun`.nightshift.launcher.backend.db.SessionRepository
import `fun`.nightshift.launcher.backend.db.TelemetryRepository
import `fun`.nightshift.launcher.backend.db.dbQuery
import `fun`.nightshift.launcher.backend.telemetry.JsonSanitizer
import `fun`.nightshift.launcher.shared.dto.CrashReportRequest
import `fun`.nightshift.launcher.shared.dto.TelemetryEventRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.util.UUID

/**
 * Wires `/telemetry/event` and `/telemetry/crash`.
 *
 * Both endpoints accept an OPTIONAL bearer token. When the token is present
 * and resolves to a still-valid session the row is stored with the matching
 * `session_id`; otherwise the row is stored with `session_id = NULL`. We
 * deliberately never return 401 — telemetry is best-effort and a broken
 * client must not be punished for emitting crash reports.
 *
 * Event payloads are sanitised by [JsonSanitizer] before persistence so a
 * misbehaving client cannot accidentally leak credentials or HWIDs into the
 * `telemetry_events.payload` column (Requirement 21.4).
 */
fun Route.telemetryRoutes() {
    route("/telemetry") {

        // -------------------------------------------------------------------
        // POST /telemetry/event (Requirement 21.3)
        // -------------------------------------------------------------------
        post("/event") {
            val req = call.receive<TelemetryEventRequest>()
            val sessionId = call.resolveOptionalSessionId()

            val sanitized: JsonObject? = JsonSanitizer.sanitize(req.payload)
            val payloadJson: String? = sanitized?.let {
                Json.encodeToString(JsonObject.serializer(), it)
            }

            dbQuery {
                TelemetryRepository.recordEvent(
                    sessionId = sessionId,
                    eventType = req.eventType,
                    payloadJson = payloadJson,
                )
            }
            call.respond(HttpStatusCode.NoContent)
        }

        // -------------------------------------------------------------------
        // POST /telemetry/crash (Requirement 21.4)
        // -------------------------------------------------------------------
        post("/crash") {
            val req = call.receive<CrashReportRequest>()
            val sessionId = call.resolveOptionalSessionId()

            dbQuery {
                TelemetryRepository.recordCrash(
                    sessionId = sessionId,
                    stackTrace = req.stackTrace,
                    clientVersion = req.clientVersion,
                )
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

/**
 * Resolves the bearer token (if any) into a session id.
 *
 * Returns `null` when the token is missing, malformed, points at no row, has
 * been invalidated or has expired — telemetry is best-effort and we'd rather
 * record an anonymous row than reject the call entirely.
 */
private suspend fun ApplicationCall.resolveOptionalSessionId(): UUID? {
    val token = bearerToken() ?: return null
    val session = dbQuery { SessionRepository.findByToken(token) } ?: return null
    if (!session.isValid || session.expiresAt.isBefore(Instant.now())) return null
    return session.id
}
