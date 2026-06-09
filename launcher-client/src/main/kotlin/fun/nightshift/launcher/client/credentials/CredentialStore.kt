package `fun`.nightshift.launcher.client.credentials

/**
 * Cross-platform contract for persisting the launcher session token.
 *
 * Implementations should ensure the token is encrypted at rest using a
 * platform-appropriate facility — on Windows that is the Credential
 * Manager via DPAPI (see [WindowsCredentialStore]). On non-Windows
 * platforms (used during cross-platform development and tests) the token
 * lives only in memory via [InMemoryCredentialStore].
 *
 * The persisted record is keyed by the entry name `NightShiftClient/session`
 * (Requirement 6.4) and survives reboots but does not roam between machines
 * — matching the "Запомнить меня" semantics from the login screen.
 *
 * Every method must be safe to call from any thread and must never throw:
 * native errors are logged at WARN and surface as `false` / `null`.
 */
interface CredentialStore {
    /**
     * Persists [token] under the well-known entry name. Replaces any prior
     * value. Returns `true` on success, `false` if the underlying store
     * rejected the write (the failure is logged at WARN).
     */
    fun saveToken(token: String): Boolean

    /**
     * Reads the previously saved token, or `null` when no record exists or
     * the read failed. Implementations MUST scrub any intermediate buffers
     * once the resulting [String] has been constructed.
     */
    fun loadToken(): String?

    /**
     * Deletes the saved token if present. Returns `true` on success or when
     * there was nothing to delete; `false` when the underlying store
     * reported a non-"not found" error.
     */
    fun deleteToken(): Boolean
}

/** Fixed entry name for Windows Credential Manager (Requirement 6.4). */
const val CREDENTIAL_TARGET_NAME: String = "NightShiftClient/session"

/**
 * Returns a [CredentialStore] suitable for the current OS.
 *
 * On Windows (any flavour: 10, 11, Server) returns a [WindowsCredentialStore]
 * backed by `Advapi32.CredRead/CredWrite/CredDelete`. On every other OS
 * returns an [InMemoryCredentialStore] so unit tests and cross-platform
 * dev keep compiling. The fallback is process-scoped — restart drops the
 * token, which mirrors the behaviour of "не запомнить меня" anyway.
 */
fun provideCredentialStore(): CredentialStore =
    if (System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)) {
        WindowsCredentialStore()
    } else {
        InMemoryCredentialStore()
    }
