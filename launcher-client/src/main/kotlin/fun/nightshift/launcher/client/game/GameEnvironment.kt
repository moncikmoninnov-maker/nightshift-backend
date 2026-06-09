package `fun`.nightshift.launcher.client.game

import java.nio.file.Path

/** Result of [GameLauncher.prepare] — passes everything launch() needs. */
data class GameEnvironment(
    val minecraftDir: Path,
    val jrePath: Path,
    val classpath: List<Path>,
    val nativesDir: Path,
    val mainClass: String,
    val vanillaArgs: List<String>,
    val fabricArgs: List<String>,
    val jvmDefaults: List<String>,
    val versionId: String,
    val assetsIndex: String,
    val assetsDir: Path,
    val protectedModsDir: Path?, // Temporary directory with decrypted premium mods (null if no premium mods)
)

/** Listener for download progress + stage transitions. */
interface ProgressSink {
    fun onStage(messageKey: String) {}
    fun onProgress(fraction01: Float) {}
    companion object {
        val NONE: ProgressSink = object : ProgressSink {}
    }
}

/** Wraps any preparation failure raised by [GameLauncher]. */
class GameLauncherException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause)

/**
 * Where to find launcher-managed mod jars. Each entry is a `(filename, bytes)`
 * pair. The launcher copies every entry into the Minecraft `mods/` folder
 * verbatim — preserving both the NightShift cheat jar and any mandatory
 * dependencies (Fabric API, Baritone, …) that the cheat or its launchpad
 * depends on.
 *
 * Pluggable so we can either embed jars in the launcher's resources
 * (current default) or fetch them from the CDN later for hot-swappable
 * updates without re-releasing the launcher.
 */
fun interface ModJarSource {
    /** 
     * Returns the mod jars to place into the Minecraft `mods/` folder.
     * 
     * @param sessionToken User's session token for encryption/decryption (empty string for offline mode)
     */
    fun read(sessionToken: String): List<ModJar>
}

/** Single mod-jar payload: target file name plus the bytes that go into it. */
data class ModJar(val fileName: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean = other is ModJar &&
        other.fileName == fileName &&
        other.bytes.contentEquals(bytes)
    override fun hashCode(): Int = fileName.hashCode() * 31 + bytes.contentHashCode()
}
