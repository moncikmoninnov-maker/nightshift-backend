package `fun`.nightshift.launcher.shared.update

import `fun`.nightshift.launcher.shared.dto.UpdateInfo
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropertyTesting
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property tests for the Phase B `resolveActiveDownload` decision matrix.
 *
 * Generates random `(current SemVer, manifest UpdateInfo)` pairs and verifies
 * the function obeys the three branches documented in design.md /
 * Property 11. Each property runs at least 200 iterations to give the
 * branch-coverage some breathing room without making the test suite slow.
 */
class UpdatePlanTest : StringSpec({

    PropertyTesting.defaultIterationCount = 200

    val versionArb: Arb<SemVer> = Arb.bind(
        Arb.int(0, 9),
        Arb.int(0, 99),
        Arb.int(0, 999),
    ) { maj, min, patch -> SemVer(maj, min, patch) }

    val urlArb = Arb.string(minSize = 1, maxSize = 32)
    val shaArb = Arb.string(minSize = 1, maxSize = 32)
    val notesArb = Arb.string(maxSize = 32)

    val manifestArb: Arb<UpdateInfo> = Arb.bind(
        versionArb,                                 // forward version
        urlArb,                                     // forward downloadUrl
        shaArb,                                     // forward sha256
        notesArb,                                   // releaseNotes
        Arb.boolean(),                              // include rollback?
        versionArb,                                 // rollback version (optional)
        urlArb,                                     // rollback downloadUrl
        shaArb,                                     // rollback sha256
    ) { fwd, url, sha, notes, includeRb, rbVer, rbUrl, rbSha ->
        UpdateInfo(
            version = fwd.toString(),
            downloadUrl = url,
            sha256 = sha,
            releaseNotes = notes,
            rollbackVersion = if (includeRb) rbVer.toString() else null,
            rollbackDownloadUrl = if (includeRb) rbUrl else null,
            rollbackSha256 = if (includeRb) rbSha else null,
        )
    }

    // Feature: ota-mod-and-launcher-updates, Property 11 (rollback branch):
    // when the manifest declares all three rollback fields and the launcher's
    // current version is at or above the parsed rollbackVersion, the function
    // returns the rollback (url, sha256) pair.
    "rollback branch wins when current >= rollbackVersion" {
        checkAll(versionArb, manifestArb) { current, manifest ->
            val rb = manifest.rollbackVersion?.let(SemVer::parse)
            val rbUrl = manifest.rollbackDownloadUrl
            val rbSha = manifest.rollbackSha256
            val activated = rb != null && rbUrl != null && rbSha != null && current >= rb

            val outcome = resolveActiveDownload(current, manifest)
            if (activated) {
                outcome shouldBe UpdateDownload(rbUrl!!, rbSha!!)
            }
        }
    }

    // Feature: ota-mod-and-launcher-updates, Property 11 (forward branch):
    // absent rollback activation, a strictly-greater forward version yields
    // the forward (downloadUrl, sha256) pair; otherwise null.
    "forward branch when no rollback wins and target > current" {
        checkAll(versionArb, manifestArb) { current, manifest ->
            val rb = manifest.rollbackVersion?.let(SemVer::parse)
            val rbUrl = manifest.rollbackDownloadUrl
            val rbSha = manifest.rollbackSha256
            val rollbackActive = rb != null && rbUrl != null && rbSha != null && current >= rb
            if (rollbackActive) return@checkAll  // covered by sibling property

            val target = SemVer.parse(manifest.version)
            val outcome = resolveActiveDownload(current, manifest)
            when {
                target == null -> outcome shouldBe null
                target > current -> outcome shouldBe UpdateDownload(manifest.downloadUrl, manifest.sha256)
                else -> outcome shouldBe null
            }
        }
    }
})
