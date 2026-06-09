package `fun`.nightshift.launcher.shared.update

import `fun`.nightshift.launcher.shared.dto.UpdateInfo

/**
 * Self-contained MAJOR.MINOR.PATCH semver wrapper used by [resolveActiveDownload].
 *
 * Pre-release suffixes (`-rc1`) and build metadata (`+sha`) are dropped before
 * parsing — only the three numeric components participate in comparisons.
 * Anything that doesn't conform yields `null` from [parse].
 *
 * Lives in `launcher-shared` so both the launcher client (deciding whether to
 * self-update) and any future tooling can reuse the exact same comparison
 * semantics as the backend's `ClientVersionPlugin`.
 */
data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        fun parse(value: String): SemVer? {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null
            val core = trimmed.substringBefore('-').substringBefore('+')
            val parts = core.split('.')
            if (parts.size != 3) return null
            val (maj, min, pat) = parts
            return SemVer(
                major = maj.toIntOrNull() ?: return null,
                minor = min.toIntOrNull() ?: return null,
                patch = pat.toIntOrNull() ?: return null,
            )
        }
    }
}

/**
 * Resolved download target for the launcher self-update flow.
 *
 * [downloadUrl] points at the .exe to fetch; [sha256] is the expected hash
 * of the downloaded bytes. The launcher MUST verify the hash before
 * spawning the updater stub.
 */
data class UpdateDownload(val downloadUrl: String, val sha256: String)

/**
 * Decides which `(downloadUrl, sha256)` pair the launcher should fetch given
 * its [current] version and the [manifest] published by the backend.
 *
 * Decision matrix:
 *
 * 1. **Rollback path** (Requirement 13.2). If [manifest] declares all three
 *    `rollbackVersion` / `rollbackDownloadUrl` / `rollbackSha256` AND
 *    [current] is at or above the parsed `rollbackVersion`, return the
 *    rollback pair. The intent: a launcher that is on a too-new build is
 *    walked back to the older binary specified by the operator.
 * 2. **Forward update path** (Requirement 12.2). Otherwise, if `manifest.version`
 *    parses and is **strictly greater** than [current], return the forward
 *    `(downloadUrl, sha256)` pair.
 * 3. **No update path**. Otherwise return `null` — the launcher already
 *    matches the published version (or the manifest is unparseable, which
 *    we treat as "do nothing rather than break").
 *
 * The function is pure: it performs no IO, has no side effects, and is
 * intentionally trivial to property-test (see Property 11 in
 * `.kiro/specs/ota-mod-and-launcher-updates/design.md`).
 */
fun resolveActiveDownload(current: SemVer, manifest: UpdateInfo): UpdateDownload? {
    val rollbackVersion = manifest.rollbackVersion?.let(SemVer::parse)
    val rollbackUrl = manifest.rollbackDownloadUrl
    val rollbackSha = manifest.rollbackSha256
    if (rollbackVersion != null && rollbackUrl != null && rollbackSha != null && current >= rollbackVersion) {
        return UpdateDownload(downloadUrl = rollbackUrl, sha256 = rollbackSha)
    }
    val target = SemVer.parse(manifest.version) ?: return null
    return if (target > current) UpdateDownload(manifest.downloadUrl, manifest.sha256) else null
}
