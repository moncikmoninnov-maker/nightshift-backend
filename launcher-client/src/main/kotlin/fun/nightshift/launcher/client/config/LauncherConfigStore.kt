package `fun`.nightshift.launcher.client.config

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Reads and writes [LauncherConfig] to a JSON file.
 *
 * Atomic writes: the new payload is written to a sibling `*.tmp` and then
 * `move`-d over the destination with `REPLACE_EXISTING` + `ATOMIC_MOVE` when
 * the platform supports it. That avoids leaving a half-written `launcher.json`
 * if the JVM dies mid-write.
 *
 * On read errors the store **does not throw** — it logs a WARN and returns a
 * default config so the launcher always starts. The bad file is left on disk
 * so the user can inspect it.
 */
class LauncherConfigStore(
    private val configFile: Path,
    private val json: Json = DEFAULT_JSON,
) {
    /** Loads config from disk, falling back to defaults on missing/invalid file. */
    fun load(): LauncherConfig {
        if (!Files.exists(configFile)) {
            log.info("No launcher.json yet at {}, using defaults", configFile)
            return LauncherConfig()
        }
        return try {
            val text = Files.readString(configFile)
            json.decodeFromString(LauncherConfig.serializer(), text)
        } catch (t: SerializationException) {
            log.warn("launcher.json is malformed at {}: {}", configFile, t.message)
            LauncherConfig()
        } catch (t: Throwable) {
            log.warn("Failed to read launcher.json at {}: {}", configFile, t.message)
            LauncherConfig()
        }
    }

    /** Persists [config] atomically. Returns true on success. */
    fun save(config: LauncherConfig): Boolean {
        return try {
            val parent = configFile.parent
            if (parent != null) Files.createDirectories(parent)
            val tmp = configFile.resolveSibling(configFile.fileName.toString() + ".tmp")
            val payload = json.encodeToString(LauncherConfig.serializer(), config)
            Files.writeString(tmp, payload)
            try {
                Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedExceptionAlias) {
                // Some Windows configurations don't support atomic moves across
                // junctions / symlinks; fall back to a regular replace.
                Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (t: Throwable) {
            log.warn("Failed to write launcher.json at {}: {}", configFile, t.message)
            false
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(LauncherConfigStore::class.java)

        val DEFAULT_JSON: Json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
}

/** Type alias to avoid importing the long `java.nio.file...` name twice. */
private typealias AtomicMoveNotSupportedExceptionAlias = java.nio.file.AtomicMoveNotSupportedException
