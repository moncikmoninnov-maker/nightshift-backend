package `fun`.nightshift.launcher.bootstrapper

import java.awt.EventQueue
import java.nio.file.Path
import java.nio.file.Paths

fun main() {
    val backendUrl = System.getenv("NIGHTSHIFT_BACKEND_URL").orEmpty()
        .ifBlank { "https://df6afa7f399888.lhr.life" }
    val installDir = installDir()
    installDir.toFile().mkdirs()
    val downloader = LauncherDownloader(backendUrl, installDir)
    EventQueue.invokeLater {
        val window = DownloadWindow(downloader)
        window.isVisible = true
    }
}

fun installDir(): Path {
    val appData = System.getenv("APPDATA") ?: (
        System.getProperty("user.home") + "/AppData/Roaming"
    )
    return Paths.get(appData, "NightShiftClient", "launcher")
}