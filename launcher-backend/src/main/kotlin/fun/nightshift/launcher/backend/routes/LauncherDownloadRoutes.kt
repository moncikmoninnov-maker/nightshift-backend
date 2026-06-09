package `fun`.nightshift.launcher.backend.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

private val log = LoggerFactory.getLogger("fun.nightshift.launcher.backend.routes.LauncherDownloadRoutes")

/**
 * `/launcher/download` — serves the full portable launcher as a .zip archive.
 *
 * The archive (expected at [launcherZip]) contains the bundled JRE, native
 * libs, and the launcher fat-jar.  The bootstrapper downloads this file,
 * verifies SHA-256, and extracts it side-by-side with itself.
 *
 * Config:
 * ```
 * launcher {
 *   portableZipPath = "/path/to/NightShift Launcher Portable-1.0.0.zip"
 *   portableZipSha256 = "abc123..."
 * }
 * ```
 */
fun Route.launcherDownloadRoutes(launcherZip: Path, launcherZipSha256: String) {
    route("/launcher") {
        get("/download") {
            if (!Files.exists(launcherZip) || !Files.isRegularFile(launcherZip)) {
                call.respondError(HttpStatusCode.NotFound, "launcher_not_found",
                    "Portable launcher archive not found on server")
                return@get
            }
            val size = Files.size(launcherZip)
            call.response.header(HttpHeaders.ContentLength, size.toString())
            call.response.header("X-SHA-256", launcherZipSha256)
            call.respondOutputStream(ContentType.Application.Zip, HttpStatusCode.OK) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    Files.newInputStream(launcherZip).use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            this@respondOutputStream.write(buf, 0, n)
                        }
                    }
                }
            }
            log.info("Served launcher zip ({} KiB) to {}", size / 1024,
                call.request.local.remoteHost)
        }

        get("/checksum") {
            call.respondText(launcherZipSha256, ContentType.Text.Plain, HttpStatusCode.OK)
        }
    }
}
