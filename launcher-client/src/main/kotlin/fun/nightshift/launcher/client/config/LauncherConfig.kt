package `fun`.nightshift.launcher.client.config

import kotlinx.serialization.Serializable

/**
 * User-editable launcher preferences serialised to `launcher.json` in the
 * launcher's working directory (Requirement 15.4).
 *
 * Fields:
 *  - [language] — `ru` or `en`. Defaults are picked at first run from `Locale`.
 *  - [jvmMemoryMb] — heap budget for the Minecraft child process, 1024-16384
 *    in 512-step increments.
 *  - [telemetryEnabled] — `null` means "not asked yet"; `true` / `false` are
 *    explicit user choices captured by the consent dialog (Req. 21.1-21.6).
 *  - [soundEnabled] — toggles the SoundManager click sounds (Req. 19.4).
 *  - [lastAccountLogin] — convenience auto-fill for the login form.
 */
@Serializable
data class LauncherConfig(
    val language: String = "ru",
    val jvmMemoryMb: Int = 4096,
    val telemetryEnabled: Boolean? = null,
    val soundEnabled: Boolean = true,
    val lastAccountLogin: String? = null,
) {
    companion object {
        /** Same bounds the Settings slider exposes (Requirement 15.3). */
        const val MEMORY_MIN_MB: Int = 1024
        const val MEMORY_MAX_MB: Int = 16_384
        const val MEMORY_STEP_MB: Int = 512
    }
}
