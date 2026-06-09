package `fun`.nightshift.launcher.client.game

import `fun`.nightshift.launcher.client.crypto.ModClassification
import `fun`.nightshift.launcher.client.crypto.ModDecryptor
import `fun`.nightshift.launcher.client.crypto.ModEncryptor
import `fun`.nightshift.launcher.client.crypto.classifyMod
import `fun`.nightshift.launcher.client.crypto.fromEncryptedFileName
import `fun`.nightshift.launcher.client.crypto.toEncryptedFileName
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * [ModJarSource] backed by the launcher backend's `/mod/manifest` +
 * `/mod/download` routes, with a transparent on-disk cache.
 *
 * Behaviour on every `read()`:
 *  1. Ask the backend for the manifest. If it answers, walk every entry and
 *     check whether the cache already has a file with the matching SHA-256.
 *     Pull only the missing/outdated ones into [cacheDir]
 *     (default `<launcher root>/cache/mods/`).
 *  2. If the backend is unreachable (offline use, fresh install with no
 *     server) OR if any download fails SHA-256 verification twice, fall back
 *     to whatever the [fallback] source offers — that's the
 *     `EmbeddedModJarSource` shipped inside the launcher fat-jar.
 *
 * The OTA path means new mod versions ship to users without re-downloading
 * the .exe — the operator drops a jar into `Mods_Source_Dir` (or pushes via
 * `/admin/mod/upload/<name>`) and the next "Play" press picks it up.
 *
 * All-or-nothing fallback: a network blip mid-stream MUST NOT result in
 * Minecraft launching with half the mods missing. Any error path returns
 * the embedded set in full.
 *
 * Premium mod protection:
 *  - Premium mods (NightShift Client Recode) are encrypted using AES-256-GCM
 *    and stored as `.enc` files in the cache directory.
 *  - Public mods (Fabric API, Sodium, etc.) are stored as plaintext.
 *  - Session token is used for key derivation; empty string for offline mode.
 */
class RemoteModJarSource(
    private val backendBaseUrl: String,
    private val cacheDir: Path,
    private val clientVersion: String,
    private val fallback: ModJarSource,
    private val modEncryptor: ModEncryptor,
    private val modDecryptor: ModDecryptor,
    private val httpClient: HttpClient = defaultHttpClient(),
) : ModJarSource {

    override fun read(sessionToken: String): List<ModJar> {
        val result = runBlocking(Dispatchers.IO) {
            runCatching { fetchFromBackend(sessionToken) }.getOrElse { cause ->
                log.warn(
                    "Backend unreachable; falling back to embedded mods (cause: {}: {})",
                    cause.javaClass.simpleName,
                    cause.message,
                )
                null
            }
        }
        if (result != null) return result

        val embedded = fallback.read(sessionToken)
        log.warn(
            "Backend unreachable; falling back to {} embedded mods",
            embedded.size,
        )
        return embedded
    }

    private suspend fun fetchFromBackend(sessionToken: String): List<ModJar> {
        Files.createDirectories(cacheDir)
        val manifestText = httpClient.get(buildUrl("/mod/manifest")) {
            header("X-Client-Version", clientVersion)
        }.bodyAsText()
        val root = JSON.parseToJsonElement(manifestText).jsonObject
        val data = root["data"]?.jsonObject ?: error("Manifest response missing 'data'")
        val mods = data["mods"]?.jsonArray ?: error("Manifest response missing 'data.mods'")
        log.info("Backend manifest: {} mods", mods.size)

        val out = ArrayList<ModJar>(mods.size)
        for (entry in mods) {
            val obj = entry.jsonObject
            val fileName = obj["fileName"]?.jsonPrimitive?.content ?: continue
            val expectedSha = obj["sha256"]?.jsonPrimitive?.content ?: continue
            val downloadUrl = obj["downloadUrl"]?.jsonPrimitive?.content ?: continue

            // Classify mod as premium or public
            val classification = classifyMod(fileName)
            val isPremium = classification == ModClassification.PREMIUM

            // Determine cache file path based on classification
            val cachedFile = if (isPremium) {
                cacheDir.resolve(toEncryptedFileName(fileName))
            } else {
                cacheDir.resolve(fileName)
            }

            val bytes = if (Files.exists(cachedFile)) {
                // Check if cached file is valid
                if (isPremium) {
                    // For premium mods: decrypt in-memory and verify SHA-256 of plaintext
                    try {
                        val encryptedBytes = Files.readAllBytes(cachedFile)
                        val decryptedBytes = modDecryptor.decrypt(encryptedBytes, sessionToken)
                        val actualSha = sha256(decryptedBytes)
                        if (actualSha.equals(expectedSha, ignoreCase = true)) {
                            log.debug("Premium mod '{}' already cached (encrypted)", fileName)
                            decryptedBytes
                        } else {
                            log.warn(
                                "SHA-256 mismatch for cached encrypted mod '{}' (have={}, expected={})",
                                fileName, actualSha, expectedSha
                            )
                            // Re-download and re-encrypt
                            downloadEncryptAndCache(fileName, downloadUrl, expectedSha, sessionToken)
                        }
                    } catch (e: Exception) {
                        log.warn("Failed to decrypt cached mod '{}': {}", fileName, e.message)
                        // Re-download and re-encrypt
                        downloadEncryptAndCache(fileName, downloadUrl, expectedSha, sessionToken)
                    }
                } else {
                    // For public mods: verify SHA-256 directly
                    val actualSha = sha256OfFile(cachedFile)
                    if (actualSha.equals(expectedSha, ignoreCase = true)) {
                        log.debug("Public mod '{}' already cached", fileName)
                        Files.readAllBytes(cachedFile)
                    } else {
                        log.info("Downloading mod '{}' from backend (reason: sha-mismatch)", fileName)
                        downloadAndVerify(fileName, downloadUrl, expectedSha)
                    }
                }
            } else {
                // File not cached, download it
                if (isPremium) {
                    log.info("Downloading premium mod '{}' from backend (reason: missing)", fileName)
                    downloadEncryptAndCache(fileName, downloadUrl, expectedSha, sessionToken)
                } else {
                    log.info("Downloading mod '{}' from backend (reason: missing)", fileName)
                    downloadAndVerify(fileName, downloadUrl, expectedSha)
                }
            }

            // Clean up any leftover plaintext premium mods (migration)
            if (isPremium) {
                val plaintextFile = cacheDir.resolve(fileName)
                if (Files.exists(plaintextFile)) {
                    log.info("Deleting leftover plaintext premium mod '{}'", fileName)
                    Files.delete(plaintextFile)
                }
            }

            out += ModJar(fileName = fileName, bytes = bytes)
        }
        return out
    }

    /**
     * Downloads a premium mod, encrypts it, and caches the encrypted version.
     * Returns the plaintext bytes for immediate use.
     */
    private suspend fun downloadEncryptAndCache(
        fileName: String,
        downloadUrl: String,
        expectedSha: String,
        sessionToken: String,
    ): ByteArray {
        // Download and verify plaintext
        val plaintextBytes = downloadAndVerify(fileName, downloadUrl, expectedSha)
        
        // Encrypt the plaintext
        val encryptedBytes = modEncryptor.encrypt(plaintextBytes, sessionToken)
        
        // Save encrypted version to cache
        val encryptedFile = cacheDir.resolve(toEncryptedFileName(fileName))
        Files.write(encryptedFile, encryptedBytes)
        log.info("Encrypted premium mod '{}' and saved to cache ({} bytes)", fileName, encryptedBytes.size)
        
        // Delete plaintext if it exists (shouldn't happen, but be safe)
        val plaintextFile = cacheDir.resolve(fileName)
        if (Files.exists(plaintextFile)) {
            Files.delete(plaintextFile)
            log.debug("Deleted plaintext version of premium mod '{}'", fileName)
        }
        
        // Return plaintext for immediate use
        return plaintextBytes
    }

    /**
     * Downloads `downloadUrl` and verifies its SHA-256 against `expectedSha`.
     *
     * Retry strategy:
     *  - First attempt fails verification → WARN with both hashes, retry once.
     *  - Second attempt fails verification → throw [SecurityException] so the
     *    outer `runCatching` triggers the embedded fallback (all-or-nothing).
     *  - Successful attempt is written to the cache before returning.
     *
     * The retried bytes are NEVER persisted to [cacheDir] until the SHA
     * matches, so a corrupted download never poisons the cache.
     */
    private suspend fun downloadAndVerify(
        fileName: String,
        downloadUrl: String,
        expectedSha: String,
    ): ByteArray {
        repeat(2) { attempt ->
            val fresh = httpClient.get(buildUrl(downloadUrl)) {
                header("X-Client-Version", clientVersion)
            }.readBytes()
            val actual = sha256(fresh)
            if (actual.equals(expectedSha, ignoreCase = true)) {
                Files.write(cacheDir.resolve(fileName), fresh)
                return fresh
            }
            log.warn(
                "SHA-256 mismatch for '{}' on attempt {}/{} (have={}, expected={})",
                fileName, attempt + 1, 2, actual, expectedSha,
            )
        }
        throw SecurityException(
            "SHA-256 mismatch for '$fileName' after 2 attempts; expected=$expectedSha",
        )
    }

    private fun buildUrl(path: String): String {
        val base = backendBaseUrl.trimEnd('/')
        val rel = if (path.startsWith("/")) path else "/$path"
        // URL-encode each path segment so filenames with spaces or other
        // non-ASCII characters don't break the request. The backend's
        // routes treat the segments as raw filenames, so we only need to
        // percent-encode the segments themselves, not the slashes.
        val encodedRel = rel.split('/').joinToString("/") { segment ->
            if (segment.isEmpty()) segment
            else java.net.URLEncoder.encode(segment, Charsets.UTF_8).replace("+", "%20")
        }
        return "$base$encodedRel"
    }

    private fun sha256OfFile(file: Path): String = sha256(Files.readAllBytes(file))

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(digest.size * 2) {
            for (b in digest) append(b.toUByte().toString(16).padStart(2, '0'))
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RemoteModJarSource::class.java)

        private val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
        }
    }
}
