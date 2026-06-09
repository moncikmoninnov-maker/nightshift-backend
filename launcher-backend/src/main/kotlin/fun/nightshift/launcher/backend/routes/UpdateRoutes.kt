package `fun`.nightshift.launcher.backend.routes

import `fun`.nightshift.launcher.backend.update.LauncherReleaseInfo
import `fun`.nightshift.launcher.shared.dto.ApiResponse
import `fun`.nightshift.launcher.shared.dto.ScreenshotManifestEntry
import `fun`.nightshift.launcher.shared.dto.UpdateInfo
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * Wires `/update/check` and `/update/screenshots`.
 *
 * Both routes are public. They return static metadata loaded from
 * `application.conf` at boot, so the handlers are pure projections of the
 * supplied [release] and [screenshots] values — no database access required.
 *
 * @param release    Launcher self-update metadata (Requirement 2.1, 2.3).
 * @param screenshots Manifest used by the launcher's screenshot carousel
 *                   (Requirement 14.3, 14.4). Empty list is valid and is
 *                   returned as-is so the client falls back to bundled assets.
 */
fun Route.updateRoutes(
    release: LauncherReleaseInfo,
    screenshots: List<ScreenshotManifestEntry>,
) {
    route("/update") {

        // -------------------------------------------------------------------
        // GET /update/check — public (Requirements 2.1, 2.3)
        // -------------------------------------------------------------------
        get("/check") {
            call.respond(
                HttpStatusCode.OK,
                ApiResponse(
                    success = true,
                    data = UpdateInfo(
                        version = release.version,
                        downloadUrl = release.downloadUrl,
                        sha256 = release.sha256,
                        releaseNotes = release.releaseNotes,
                        rollbackVersion = release.rollbackVersion,
                        rollbackDownloadUrl = release.rollbackDownloadUrl,
                        rollbackSha256 = release.rollbackSha256,
                    ),
                ),
            )
        }

        // -------------------------------------------------------------------
        // GET /update/screenshots — public (Requirements 14.3, 14.4)
        // -------------------------------------------------------------------
        get("/screenshots") {
            call.respond(
                HttpStatusCode.OK,
                ApiResponse(success = true, data = screenshots),
            )
        }
    }
}
