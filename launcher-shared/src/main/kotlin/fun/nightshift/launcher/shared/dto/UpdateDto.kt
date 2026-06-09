package `fun`.nightshift.launcher.shared.dto

import kotlinx.serialization.Serializable

/**
 * Launcher self-update metadata returned by `/update/check`.
 *
 * The three [rollbackVersion] / [rollbackDownloadUrl] / [rollbackSha256]
 * fields are optional and used by Phase B's rollback mechanism: if all three
 * are present and the running launcher's version is **at or above**
 * [rollbackVersion], the launcher treats [rollbackDownloadUrl] / [rollbackSha256]
 * as the new authoritative download instead of [downloadUrl] / [sha256].
 *
 * Why all three on the manifest and not in a separate response: a rollback
 * is just "publish a new manifest pointing at an older binary"; making it a
 * one-line config change keeps incident response fast (Requirements 13.1
 * through 13.4 of the OTA spec).
 *
 * Older clients that don't know about `rollback*` simply ignore the fields
 * thanks to `ignoreUnknownKeys = true` in our shared `Json` config.
 */
@Serializable
data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val sha256: String,
    val releaseNotes: String,
    val rollbackVersion: String? = null,
    val rollbackDownloadUrl: String? = null,
    val rollbackSha256: String? = null,
)

/** Single screenshot manifest entry returned by `/update/screenshots`. */
@Serializable
data class ScreenshotManifestEntry(
    val name: String,
    val url: String,
    val sha256: String
)
