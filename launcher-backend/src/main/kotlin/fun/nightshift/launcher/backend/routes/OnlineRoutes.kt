package `fun`.nightshift.launcher.backend.routes

import `fun`.nightshift.launcher.backend.db.OnlineHeartbeatRepository
import `fun`.nightshift.launcher.backend.db.SessionRepository
import `fun`.nightshift.launcher.backend.db.dbQuery
import `fun`.nightshift.launcher.shared.dto.ApiResponse
import `fun`.nightshift.launcher.shared.dto.OnlineCountResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.Duration
import java.time.Instant

/**
 * Sliding window used by [OnlineHeartbeatRepository.countSince] to decide
 * which sessions still count as "online". Clients heartbeat every 30 s so a
 * 60 s window tolerates one missed beat without dropping the user.
 */
private val ONLINE_WINDOW: Duration = Duration.ofSeconds(60)

/**
 * Wires the public online counter and the per-session heartbeat endpoint.
 *
 * Routes:
 *  - `GET  /online/count`      — public, returns the count of recently
 *    seen sessions wrapped in [ApiResponse].
 *  - `POST /online/heartbeat`  — requires a bearer session token; upserts
 *    `last_seen` for the bound session so the user counts as online.
 */
fun Route.onlineRoutes() {
    route("/online") {

        // -------------------------------------------------------------------
        // GET /online/count — public (Requirement 13.1)
        // -------------------------------------------------------------------
        get("/count") {
            val cutoff = Instant.now().minus(ONLINE_WINDOW)
            val count = dbQuery { OnlineHeartbeatRepository.countSince(cutoff) }
            call.respond(
                HttpStatusCode.OK,
                ApiResponse(
                    success = true,
                    data = OnlineCountResponse(count = count.toInt()),
                ),
            )
        }

        // -------------------------------------------------------------------
        // POST /online/heartbeat — bearer-authenticated (Requirement 13.5)
        // -------------------------------------------------------------------
        post("/heartbeat") {
            val token = call.bearerToken()
            if (token == null) {
                return@post call.respondError(
                    HttpStatusCode.Unauthorized,
                    "invalid_session",
                    "Missing bearer token",
                )
            }

            // The token is already bound to a HWID at issue time; we just
            // need a still-valid session to upsert against.
            val session = dbQuery { SessionRepository.findByToken(token) }
            if (session == null || !session.isValid || session.expiresAt.isBefore(Instant.now())) {
                return@post call.respondError(
                    HttpStatusCode.Unauthorized,
                    "invalid_session",
                    "Session is invalid or expired",
                )
            }

            dbQuery { OnlineHeartbeatRepository.upsert(session.id) }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
