package `fun`.nightshift.launcher.backend

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import `fun`.nightshift.launcher.backend.db.DatabaseFactory
import `fun`.nightshift.launcher.backend.plugins.ClientVersionPlugin
import `fun`.nightshift.launcher.backend.plugins.SemVer
import `fun`.nightshift.launcher.backend.routes.authRoutes
import `fun`.nightshift.launcher.backend.routes.keyRoutes
import `fun`.nightshift.launcher.backend.routes.launcherDownloadRoutes
import `fun`.nightshift.launcher.backend.routes.modRoutes
import `fun`.nightshift.launcher.backend.routes.onlineRoutes
import `fun`.nightshift.launcher.backend.routes.telemetryRoutes
import `fun`.nightshift.launcher.backend.routes.updateRoutes
import `fun`.nightshift.launcher.backend.update.LauncherReleaseInfo
import `fun`.nightshift.launcher.shared.dto.ScreenshotManifestEntry
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("fun.nightshift.launcher.backend")

/**
 * Backend entry point.
 *
 * Loads `application.conf` via Hocon, runs Flyway migrations, connects
 * Exposed to the resulting Hikari pool and finally boots the Ktor server.
 *
 * All route groups are wired here: auth, keys, online, update, telemetry.
 */
fun main() {
    val rawConfig = ConfigFactory.load()
    val config: ApplicationConfig = HoconApplicationConfig(rawConfig)

    DatabaseFactory.init(config)

    val port = config.propertyOrNull("ktor.deployment.port")?.getString()?.toIntOrNull() ?: 8080
    val host = config.propertyOrNull("ktor.deployment.host")?.getString() ?: "0.0.0.0"
    val minClientVersion = config.propertyOrNull("launcher.minClientVersion")?.getString() ?: "1.0.0"

    val release = loadReleaseInfo(rawConfig)
    val screenshots = loadScreenshots(rawConfig)
    log.info(
        "Launcher release loaded: version={} downloadUrl={} screenshots={}",
        release.version, release.downloadUrl, screenshots.size,
    )

    embeddedServer(Netty, port = port, host = host) {
        // Must be installed before routing so it runs ahead of every route handler.
        install(ClientVersionPlugin) {
            this.minVersion = minClientVersion
            // Whitelist mod OTA endpoints so out-of-date clients can still
            // pull a newer mod jar even if the launcher itself is behind on
            // its X-Client-Version. /health stays exact-match.
            this.whitelistedPaths = setOf("/health", "prefix:/mod/")
        }
        install(ContentNegotiation) {
            json()
        }
        // Per-IP login rate limit (10 requests / 60 s) on top of the
        // per-account 5-failures-in-10-minutes lockout enforced inside the route.
        install(RateLimit) {
            register(RateLimitName("login")) {
                rateLimiter(limit = 10, refillPeriod = 60.seconds)
                requestKey { call -> call.request.local.remoteHost }
            }
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                log.error("Unhandled exception", cause)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "internal"))
            }
        }
        routing {
            get("/health") {
                call.respond(mapOf("status" to "ok"))
            }
            authRoutes()
            keyRoutes()
            onlineRoutes()
            updateRoutes(release, screenshots)
            telemetryRoutes()

            // Bootstrapper download: serves the full portable launcher as a zip.
            val launcherZipPath = config.propertyOrNull("launcher.portableZipPath")
                ?.getString()?.let { java.nio.file.Paths.get(it) }
                ?: java.nio.file.Paths.get(
                    System.getProperty("user.home") + "/.nightshift-backend/launcher-portable.zip"
                )
            val launcherZipSha256 = config.propertyOrNull("launcher.portableZipSha256")
                ?.getString() ?: ""
            launcherDownloadRoutes(launcherZipPath, launcherZipSha256)

            // OTA mod distribution: serves jars from <userHome>/.nightshift-backend/mods
            // by default. Override with the MODS_DIR env var for prod.
            val modsDir = java.nio.file.Paths.get(
                System.getenv("MODS_DIR")
                    ?: (System.getProperty("user.home") + "/.nightshift-backend/mods")
            )
            modRoutes(modsDir)
        }
    }.start(wait = true)
}

/**
 * Builds [LauncherReleaseInfo] from the `launcher.*` section of `application.conf`.
 *
 * Fail-loudly behaviour: an unparseable `currentVersion` aborts boot via
 * [error] rather than silently serving a malformed version string to clients.
 */
private fun loadReleaseInfo(config: Config): LauncherReleaseInfo {
    val version = config.getString("launcher.currentVersion")
    if (SemVer.parse(version) == null) {
        error("launcher.currentVersion is not a valid MAJOR.MINOR.PATCH semver: '$version'")
    }
    // Rollback fields are optional — operators publish them only when they
    // need to walk users back to a previous build (Requirements 13.1-13.4).
    val rollbackVersion = config.takeIf { it.hasPath("launcher.rollbackVersion") }
        ?.getString("launcher.rollbackVersion")?.takeIf { it.isNotBlank() }
    val rollbackDownloadUrl = config.takeIf { it.hasPath("launcher.rollbackDownloadUrl") }
        ?.getString("launcher.rollbackDownloadUrl")?.takeIf { it.isNotBlank() }
    val rollbackSha256 = config.takeIf { it.hasPath("launcher.rollbackSha256") }
        ?.getString("launcher.rollbackSha256")?.takeIf { it.isNotBlank() }
    if (rollbackVersion != null && SemVer.parse(rollbackVersion) == null) {
        error("launcher.rollbackVersion is not a valid MAJOR.MINOR.PATCH semver: '$rollbackVersion'")
    }
    return LauncherReleaseInfo(
        version = version,
        downloadUrl = config.getString("launcher.downloadUrl"),
        sha256 = config.getString("launcher.downloadSha256"),
        releaseNotes = config.getString("launcher.releaseNotes"),
        rollbackVersion = rollbackVersion,
        rollbackDownloadUrl = rollbackDownloadUrl,
        rollbackSha256 = rollbackSha256,
    )
}

/**
 * Reads the screenshot manifest from `launcher.screenshots`.
 *
 * Each entry must declare `name`, `url` and `sha256`. The list is allowed to
 * be empty — the launcher then falls back to bundled defaults.
 */
private fun loadScreenshots(config: Config): List<ScreenshotManifestEntry> {
    if (!config.hasPath("launcher.screenshots")) return emptyList()
    return config.getConfigList("launcher.screenshots").map { entry ->
        ScreenshotManifestEntry(
            name = entry.getString("name"),
            url = entry.getString("url"),
            sha256 = entry.getString("sha256"),
        )
    }
}
