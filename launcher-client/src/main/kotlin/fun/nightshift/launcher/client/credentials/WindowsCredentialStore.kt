package `fun`.nightshift.launcher.client.credentials

import com.sun.jna.platform.win32.Crypt32Util
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64

/**
 * Windows-only [CredentialStore] backed by **DPAPI** (`CryptProtectData`).
 *
 * We considered using the Credential Manager via `Advapi32.CredRead/CredWrite`,
 * but those bindings are not part of `jna-platform` 5.14 and packaging custom
 * native bindings would be risky for a launcher that has to run on every
 * Windows install.
 *
 * DPAPI gives us the same security properties for free:
 *  * The token is encrypted with a key derived from the current Windows user
 *    account; another local user cannot read it.
 *  * The encrypted blob is base64-stored in
 *    `%LOCALAPPDATA%/NightShiftClient/session.dpapi`. If `LOCALAPPDATA` is
 *    missing we fall back to `%APPDATA%`.
 *  * Decryption fails fast with a logged WARN if the user account changes,
 *    so we never silently leak a token to a different account.
 *
 * The interface contract is preserved: methods never throw, returning
 * `false`/`null` on failure.
 */
class WindowsCredentialStore : CredentialStore {

    private val storeFile: Path = resolveStorePath()

    override fun saveToken(token: String): Boolean = runSafely("saveToken") {
        val protected = Crypt32Util.cryptProtectData(token.toByteArray(Charsets.UTF_8))
        val parent = storeFile.parent
        if (parent != null) Files.createDirectories(parent)
        // Write atomically: tmp + move so we never leave a partially-written file.
        val tmp = storeFile.resolveSibling(storeFile.fileName.toString() + ".tmp")
        Files.write(tmp, Base64.getEncoder().encode(protected))
        Files.move(
            tmp,
            storeFile,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
        true
    } ?: false

    override fun loadToken(): String? = runSafely("loadToken") {
        if (!Files.exists(storeFile)) return@runSafely null
        val ciphertext = Base64.getDecoder().decode(Files.readString(storeFile).trim())
        val plain = Crypt32Util.cryptUnprotectData(ciphertext)
        String(plain, Charsets.UTF_8).also {
            // Best-effort wipe of the intermediate buffer.
            plain.fill(0)
        }
    }

    override fun deleteToken(): Boolean = runSafely("deleteToken") {
        if (Files.exists(storeFile)) Files.delete(storeFile)
        true
    } ?: false

    private inline fun <T> runSafely(op: String, block: () -> T): T? = try {
        block()
    } catch (t: Throwable) {
        log.warn("{} failed: {}", op, t.message)
        null
    }

    companion object {
        private val log = LoggerFactory.getLogger(WindowsCredentialStore::class.java)

        private fun resolveStorePath(): Path {
            val localApp = System.getenv("LOCALAPPDATA") ?: System.getenv("APPDATA")
            val base = if (!localApp.isNullOrBlank()) Paths.get(localApp) else Paths.get(System.getProperty("user.home"))
            return base.resolve("NightShiftClient").resolve("session.dpapi")
        }
    }
}
