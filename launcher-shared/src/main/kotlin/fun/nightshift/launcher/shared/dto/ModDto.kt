package `fun`.nightshift.launcher.shared.dto

import kotlinx.serialization.Serializable

/**
 * Single bundled-mod entry returned by the backend's `/mod/manifest` route.
 *
 * The launcher uses [sha256] to decide whether to redownload: if the on-disk
 * Minecraft `mods/<fileName>` already has the same content hash, we skip
 * the network round-trip entirely.
 *
 * [downloadUrl] is a relative path under the backend root; the launcher
 * concatenates it with its base URL before issuing the request. Keeping it
 * relative means the same server config works in dev (localhost) and prod
 * (https://api.nightshift.fun) without per-environment overrides.
 */
@Serializable
data class ModManifestEntry(
    val fileName: String,
    val sha256: String,
    val sizeBytes: Long,
    val version: String,
    val downloadUrl: String,
)

/** Top-level response for `GET /mod/manifest`. */
@Serializable
data class ModManifestResponse(
    val mods: List<ModManifestEntry>,
)
