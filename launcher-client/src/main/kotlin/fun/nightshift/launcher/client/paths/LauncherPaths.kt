package `fun`.nightshift.launcher.client.paths

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Filesystem layout for the launcher's working directory.
 *
 * Roots at `%APPDATA%/NightShiftClient/` on Windows (Requirement 1.2);
 * falls back to `${user.home}/.nightshift-client/` on dev machines so
 * Compose Desktop debugging on macOS / Linux still works.
 *
 * On first access [ensure] creates every subdirectory eagerly. IO failures
 * are logged at WARN and surfaced as [LauncherPathsException] for the
 * caller to render a localised error dialog.
 */
data class LauncherPaths(
    val root: Path,
    val logs: Path,
    val runtime: Path,
    val minecraft: Path,
    val mods: Path,
    val cacheScreenshots: Path,
    val cacheMods: Path,
    val lang: Path,
    val configFile: Path,
) {
    companion object {
        private val log = LoggerFactory.getLogger(LauncherPaths::class.java)

        /**
         * Resolves the platform-specific layout. Does not perform IO; call
         * [ensure] to materialise the directory tree.
         */
        fun resolve(): LauncherPaths {
            val root = pickRoot()
            return LauncherPaths(
                root = root,
                logs = root.resolve("logs"),
                runtime = root.resolve("runtime"),
                minecraft = root.resolve("minecraft"),
                mods = root.resolve("minecraft").resolve("mods"),
                cacheScreenshots = root.resolve("cache").resolve("screenshots"),
                cacheMods = root.resolve("cache").resolve("mods"),
                lang = root.resolve("lang"),
                configFile = root.resolve("launcher.json"),
            )
        }

        private fun pickRoot(): Path {
            val osName = System.getProperty("os.name", "").lowercase()
            if (osName.contains("windows")) {
                val appdata = System.getenv("APPDATA")
                if (!appdata.isNullOrBlank()) {
                    return Paths.get(appdata, "NightShiftClient")
                }
            }
            // Dev / non-Windows fallback.
            val home = System.getProperty("user.home", ".")
            return Paths.get(home, ".nightshift-client")
        }
    }

    /**
     * Materialises every directory in [LauncherPaths]. Does not create
     * [configFile] — that's the job of `LauncherConfigStore`.
     *
     * @throws LauncherPathsException if any directory cannot be created.
     */
    fun ensure() {
        val targets = listOf(root, logs, runtime, minecraft, mods, cacheScreenshots, cacheMods, lang)
        for (path in targets) {
            try {
                Files.createDirectories(path)
            } catch (t: Throwable) {
                log.warn("Failed to create directory {}: {}", path, t.message)
                throw LauncherPathsException(path, t)
            }
        }
        log.info("Launcher paths ready under {}", root)
    }
}

/** Raised when [LauncherPaths.ensure] fails to materialise the directory tree. */
class LauncherPathsException(
    val path: Path,
    cause: Throwable,
) : RuntimeException("Failed to prepare launcher directory: $path", cause)
