package `fun`.nightshift.launcher.client.update

import `fun`.nightshift.launcher.client.api.BackendApiClient
import `fun`.nightshift.launcher.shared.dto.UpdateInfo
import `fun`.nightshift.launcher.shared.update.SemVer
import `fun`.nightshift.launcher.shared.update.UpdateDownload
import `fun`.nightshift.launcher.shared.update.resolveActiveDownload
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * Phase B self-update orchestrator.
 *
 * Responsibilities:
 *  1. Ask the backend for the latest [UpdateInfo] manifest.
 *  2. Run [resolveActiveDownload] to decide whether to apply a forward
 *     update, a rollback or nothing at all.
 *  3. Stream the chosen .exe into a temp file next to the running launcher
 *     and verify its SHA-256.
 *  4. Drop the bundled `update.bat` next to the launcher binary, exec it
 *     detached with `(parentPid, oldExe, newExe)` so the stub takes over
 *     after the launcher exits.
 *
 * The launcher then calls [exitProcess(0)] separately — this module
 * intentionally does not kill its own process so the caller can flush UI,
 * close API clients and run any teardown hooks first.
 *
 * All filesystem and network IO runs on `Dispatchers.IO` so callers can
 * invoke from any coroutine context, including the Compose main thread.
 */
class LauncherUpdateModule(
    private val api: BackendApiClient,
    private val currentVersion: String,
    private val launcherExePath: Path,
    private val httpClient: HttpClient = defaultHttpClient(),
) {

    /**
     * Returned by [planUpdate] to describe the next action.
     */
    sealed class UpdatePlan {
        /** Backend says we're already up-to-date or rollback target matches us. */
        data object UpToDate : UpdatePlan()

        /** A new (or rollback) build is available. */
        data class Available(
            val download: UpdateDownload,
            val targetVersion: String,
            val isRollback: Boolean,
            val releaseNotes: String,
        ) : UpdatePlan()

        /** Manifest fetch failed (network, parse error, etc.) — caller should
         *  warn-and-continue rather than block startup. */
        data class Failed(val cause: Throwable) : UpdatePlan()
    }

    /**
     * Hits `GET /update/check` and resolves the active download, if any.
     *
     * Always returns; never throws. On any error, the caller continues
     * with the current version (Requirement 2.5 of the parent spec).
     */
    suspend fun planUpdate(): UpdatePlan {
        val current = SemVer.parse(currentVersion) ?: run {
            log.warn("Cannot parse current version '{}'; skipping self-update", currentVersion)
            return UpdatePlan.UpToDate
        }
        val manifest: UpdateInfo = api.updateCheck().getOrElse { cause ->
            log.warn("Update check failed: {}: {}", cause::class.simpleName, cause.message)
            return UpdatePlan.Failed(cause)
        }
        val download = resolveActiveDownload(current, manifest)
            ?: return UpdatePlan.UpToDate.also {
                log.info("Update check: current={} latest={} (no action)", current, manifest.version)
            }

        val rollbackVersion = manifest.rollbackVersion?.let(SemVer::parse)
        val isRollback = rollbackVersion != null && current >= rollbackVersion
        val targetVersion = if (isRollback) {
            manifest.rollbackVersion ?: manifest.version
        } else {
            manifest.version
        }

        if (isRollback) {
            log.info(
                "Rollback active: switching to rollbackDownloadUrl={} (current={}, rollbackVersion={})",
                download.downloadUrl, current, manifest.rollbackVersion,
            )
        } else {
            log.info("Update available: current={} latest={}", current, targetVersion)
        }

        return UpdatePlan.Available(
            download = download,
            targetVersion = targetVersion,
            isRollback = isRollback,
            releaseNotes = manifest.releaseNotes,
        )
    }

    /**
     * Streams the new launcher .exe to a sibling file `<exe>.new`, verifying
     * SHA-256 along the way. On mismatch the temp file is deleted and the
     * function throws [SecurityException] so the UI can show a retry dialog
     * (Requirement 12.4).
     *
     * Returns the absolute path of the verified `<exe>.new` file.
     *
     * @param onProgress Invoked with `(downloadedBytes, totalBytesOrMinusOne)`
     *                   after each chunk. Always called from `Dispatchers.IO`.
     */
    suspend fun downloadAndVerify(
        plan: UpdatePlan.Available,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): Path = withContext(Dispatchers.IO) {
        val target = launcherExePath.resolveSibling(launcherExePath.fileName.toString() + ".new")
        if (Files.exists(target)) Files.delete(target)

        val response = httpClient.get(plan.download.downloadUrl)
        val expectedTotal = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        val channel = response.bodyAsChannel()

        Files.newOutputStream(
            target,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        ).use { out ->
            val buffer = ByteArray(64 * 1024)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read <= 0) {
                    if (read == -1) break
                    continue
                }
                out.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                written += read
                onProgress(written, expectedTotal)
            }
        }

        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        if (!actual.equals(plan.download.sha256, ignoreCase = true)) {
            runCatching { Files.deleteIfExists(target) }
            log.warn(
                "Downloaded launcher SHA-256 mismatch; deleting and showing retry dialog (have={} expected={})",
                actual, plan.download.sha256,
            )
            throw SecurityException(
                "Downloaded launcher SHA-256 mismatch; expected=${plan.download.sha256} actual=$actual",
            )
        }

        log.info("Downloaded {} bytes of {}, sha256={}", written, plan.targetVersion, actual)
        target
    }

    /**
     * Spawns `update.bat` as a detached process, passing it the current
     * launcher PID, the path of the running .exe and the path of the verified
     * new .exe.
     *
     * The bat is read from launcher resources and dropped next to the .exe so
     * Windows can locate cmd.exe relative to the standard %SystemRoot%.
     *
     * The stub waits for our PID to exit, backs up the old .exe, moves the new
     * one over and starts the result. We do **not** kill our own process here
     * — the caller is expected to run any final teardown (online heartbeat,
     * api.close()) before invoking exitProcess(0).
     *
     * Returns the absolute path of the spawned `update.bat` for logging.
     */
    suspend fun spawnUpdaterStub(newExePath: Path): Path = withContext(Dispatchers.IO) {
        val stub = launcherExePath.resolveSibling("update.bat")
        // Always re-extract: if we ever ship a new stub script the old one
        // shouldn't linger.
        javaClass.getResourceAsStream("/update.bat")
            ?.use { input ->
                Files.newOutputStream(
                    stub,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                ).use { out -> input.copyTo(out) }
            }
            ?: error("update.bat resource missing from launcher classpath")

        val pid = ProcessHandle.current().pid().toString()
        val command = listOf(
            "cmd", "/c", "start", "",
            stub.toAbsolutePath().toString(),
            pid,
            launcherExePath.toAbsolutePath().toString(),
            newExePath.toAbsolutePath().toString(),
        )
        log.info("Spawning updater stub: {}", command.joinToString(" "))

        // We deliberately do NOT redirect IO — the bat will outlive us and
        // its stdout/stderr would otherwise tie back to a dying parent.
        val builder = ProcessBuilder(command).apply {
            redirectErrorStream(false)
            redirectOutput(ProcessBuilder.Redirect.DISCARD)
            redirectError(ProcessBuilder.Redirect.DISCARD)
        }
        builder.start()
        // No waitFor — we want the parent to be free to exit. The stub will
        // sit on tasklist looking for our PID until we're gone.
        stub
    }

    companion object {
        private val log = LoggerFactory.getLogger(LauncherUpdateModule::class.java)

        /**
         * HTTP client tuned for downloading multi-megabyte .exe payloads:
         * generous request/socket timeouts, modest connect timeout.
         */
        fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 5 * 60_000
                socketTimeoutMillis = 5 * 60_000
            }
        }
    }
}
