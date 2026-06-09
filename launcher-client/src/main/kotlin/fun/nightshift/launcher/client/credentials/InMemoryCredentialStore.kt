package `fun`.nightshift.launcher.client.credentials

import java.util.concurrent.atomic.AtomicReference

/**
 * Process-scoped, no-op credential store used on non-Windows platforms.
 *
 * The token survives only as long as the JVM does. Suitable for unit tests
 * and cross-platform development — production launchers always run on
 * Windows and use [WindowsCredentialStore] instead.
 *
 * Thread-safe via [AtomicReference]; saves and reads never throw.
 */
class InMemoryCredentialStore : CredentialStore {
    private val token = AtomicReference<String?>(null)

    override fun saveToken(token: String): Boolean {
        this.token.set(token)
        return true
    }

    override fun loadToken(): String? = token.get()

    override fun deleteToken(): Boolean {
        token.set(null)
        return true
    }
}
