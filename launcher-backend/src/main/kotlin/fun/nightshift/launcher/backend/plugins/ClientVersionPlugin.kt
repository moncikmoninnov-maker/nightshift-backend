package `fun`.nightshift.launcher.backend.plugins

import `fun`.nightshift.launcher.shared.dto.ApiError
import `fun`.nightshift.launcher.shared.dto.ApiResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.log
import io.ktor.server.response.respond

/**
 * Configuration for [ClientVersionPlugin].
 *
 * - [minVersion] is the inclusive minimum supported `MAJOR.MINOR.PATCH`
 *   triple. Any client reporting a lower version receives `client_outdated`.
 * - [whitelistedPaths] lists request paths that bypass the check entirely;
 *   useful for `/health` and similar liveness endpoints that should never
 *   become unreachable to monitoring clients (Requirement 17.4 / 17.5).
 * - [headerName] is configurable mostly for testability; clients always send
 *   `X-Client-Version` as documented in design.md.
 */
class ClientVersionPluginConfig {
    var minVersion: String = "1.0.0"
    var whitelistedPaths: Set<String> = setOf("/health")
    var headerName: String = "X-Client-Version"
}

/**
 * Ktor application plugin that enforces the `X-Client-Version` requirement
 * (Requirements 17.4, 17.5).
 *
 * The plugin runs in the `Plugins` phase of [ApplicationCallPipeline], which
 * is _before_ routing. When the inbound `X-Client-Version` header is missing
 * or reports a version below [ClientVersionPluginConfig.minVersion], the
 * pipeline is short-circuited with HTTP 426 `Upgrade Required` and a uniform
 * [ApiResponse] body using the `client_outdated` error code so the desktop
 * client can recognise it and trigger a forced update.
 *
 * Whitelisted paths (e.g. `/health`) bypass the check.
 */
val ClientVersionPlugin = createApplicationPlugin(
    name = "ClientVersionPlugin",
    createConfiguration = ::ClientVersionPluginConfig,
) {
    val minVersion = pluginConfig.minVersion
    val whitelist = pluginConfig.whitelistedPaths
    val headerName = pluginConfig.headerName
    val parsedMin = SemVer.parse(minVersion)
        ?: error("ClientVersionPlugin: invalid minVersion '$minVersion'")

    application.log.info(
        "ClientVersionPlugin installed (min={}, whitelist={}, header={})",
        minVersion, whitelist, headerName,
    )

    application.intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.local.uri.substringBefore('?')
        // Whitelist supports two forms:
        //  * exact "/health"      — single path
        //  * "prefix:/mod/"       — any path that starts with /mod/
        // The prefix variant lets us shield variable routes like
        // /mod/download/<name>.jar without having to enumerate every name.
        val whitelisted = whitelist.any { entry ->
            if (entry.startsWith("prefix:")) {
                path.startsWith(entry.removePrefix("prefix:"))
            } else {
                path == entry
            }
        }
        if (whitelisted) {
            return@intercept
        }

        val raw = call.request.headers[headerName]
        val parsed = raw?.let { SemVer.parse(it) }

        if (parsed == null || parsed < parsedMin) {
            call.respond(
                HttpStatusCode.UpgradeRequired,
                ApiResponse<Nothing>(
                    success = false,
                    error = ApiError(
                        code = "client_outdated",
                        message = if (raw == null) {
                            "Missing $headerName header. Minimum supported version is $minVersion"
                        } else {
                            "Client version $raw is no longer supported. Minimum supported version is $minVersion"
                        },
                    ),
                ),
            )
            finish()
        }
    }
}

/**
 * Minimal `MAJOR.MINOR.PATCH` semver wrapper used by [ClientVersionPlugin].
 *
 * Pre-release / build-metadata suffixes (everything after the first `-` or
 * `+`) are stripped before parsing — the plugin only needs to compare the
 * three numeric components. Anything that cannot be parsed yields `null`.
 */
internal data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    companion object {
        fun parse(value: String): SemVer? {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null
            // Drop pre-release ("-rc1") and build-metadata ("+sha") suffixes.
            val core = trimmed.substringBefore('-').substringBefore('+')
            val parts = core.split('.')
            if (parts.size != 3) return null
            val (maj, min, pat) = parts
            return SemVer(
                major = maj.toIntOrNull() ?: return null,
                minor = min.toIntOrNull() ?: return null,
                patch = pat.toIntOrNull() ?: return null,
            )
        }
    }
}
