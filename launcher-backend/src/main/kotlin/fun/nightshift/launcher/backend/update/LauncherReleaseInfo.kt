package `fun`.nightshift.launcher.backend.update

/**
 * Static metadata about the currently published launcher build.
 *
 * Loaded from `application.conf` at boot and passed into `updateRoutes`.
 * The launcher polls `/update/check` and compares its own version to
 * [version]; if it lags behind, the launcher downloads [downloadUrl],
 * verifies [sha256] and applies the update (Requirements 2.1 / 2.3).
 *
 * The optional [rollbackVersion] / [rollbackDownloadUrl] / [rollbackSha256]
 * triple gates the Phase B rollback path: when all three are present, a
 * launcher whose own version is **at or above** [rollbackVersion] is
 * redirected to [rollbackDownloadUrl] / [rollbackSha256] instead of the
 * forward update target. Operators publish a rollback by editing
 * `application.conf` and restarting the backend (or by setting the
 * corresponding environment variables — see `loadReleaseInfo`).
 */
data class LauncherReleaseInfo(
    val version: String,
    val downloadUrl: String,
    val sha256: String,
    val releaseNotes: String,
    val rollbackVersion: String? = null,
    val rollbackDownloadUrl: String? = null,
    val rollbackSha256: String? = null,
)
