package `fun`.nightshift.launcher.client

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.runBlocking
import `fun`.nightshift.launcher.client.api.BackendApiClient
import `fun`.nightshift.launcher.client.auth.AuthModule
import `fun`.nightshift.launcher.client.config.LauncherConfig
import `fun`.nightshift.launcher.client.config.LauncherConfigStore
import `fun`.nightshift.launcher.client.credentials.provideCredentialStore
import `fun`.nightshift.launcher.client.crypto.AesGcmModDecryptor
import `fun`.nightshift.launcher.client.crypto.AesGcmModEncryptor
import `fun`.nightshift.launcher.client.game.EmbeddedModJarSource
import `fun`.nightshift.launcher.client.game.GameLauncher
import `fun`.nightshift.launcher.client.game.RemoteModJarSource
import `fun`.nightshift.launcher.client.hwid.OshiHwidCollector
import `fun`.nightshift.launcher.client.i18n.LocalizationManager
import `fun`.nightshift.launcher.client.keys.KeyModule
import `fun`.nightshift.launcher.client.online.OnlineModule
import `fun`.nightshift.launcher.client.paths.LauncherPaths
import `fun`.nightshift.launcher.client.ui.App
import `fun`.nightshift.launcher.client.ui.screens.UpdateScreen
import `fun`.nightshift.launcher.client.update.LauncherUpdateModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

/**
 * NightShift Launcher entry point.
 *
 * Wires the modules together:
 *  1. Resolve & materialise [LauncherPaths].
 *  2. Point Logback at `logs/launcher.log` before any logger is touched.
 *  3. Load (or default) [LauncherConfig].
 *  4. Build all subsystems (auth, keys, online, game launcher, …).
 *  5. Hand them to `App`, which owns the screen state machine.
 */
fun main() {
    // 1. Resolve filesystem layout and global crash sink BEFORE anything else
    //    so a JVM-level exception does not surface as a generic Swing
    //    "Unknown error" dialog from the jpackage launcher stub.
    val paths = LauncherPaths.resolve()
    try {
        paths.ensure()
    } catch (t: Throwable) {
        System.err.println("Failed to prepare ${paths.root}: ${t.message}")
        exitProcess(1)
    }

    // Must be set BEFORE loading slf4j/logback so the FILE appender lands
    // in a writable directory.
    System.setProperty("LAUNCHER_LOG_DIR", paths.logs.toAbsolutePath().toString())
    System.setProperty("nightshift.logDir", paths.logs.toAbsolutePath().toString())

    installGlobalCrashHandler(paths)

    // AWT/Swing exceptions otherwise surface as a stock "Unknown error"
    // dialog from the jpackage stub. Route them through the Logback file
    // appender + crash file just like the rest.
    System.setProperty(
        "sun.awt.exception.handler",
        "fun.nightshift.launcher.client.AwtUncaughtHandler",
    )

    // Also redirect java.util.logging so libraries that use it (Mojang
    // Authlib, JNA platform, etc.) write to the same file rather than
    // dropping output into the void of a windowed jpackage stub.
    redirectStdStreams(paths)

    val log = LoggerFactory.getLogger("Main")
    log.info("NightShift Launcher starting up; root={}", paths.root)

    val configStore = LauncherConfigStore(paths.configFile)
    val initialConfig = configStore.load()

    val localization = LocalizationManager(paths.lang)
    if (initialConfig.language.isBlank()) {
        localization.applySystemDefault()
    } else {
        localization.setLocale(initialConfig.language)
    }

    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val hwid = OshiHwidCollector()
    val credentials = provideCredentialStore()

    val backendBaseUrl = System.getenv("NIGHTSHIFT_BACKEND_URL").orEmpty()
        .ifBlank { DEFAULT_BACKEND_URL }
    val clientVersion = readClientVersion()

    // Offline portable mode: skip backend auth, use a synthetic lifetime
    // account so the user goes straight to the Play screen. Mods are
    // loaded from the embedded resources baked into the launcher fat-jar.
    val offlineMode = OFFLINE_MODE ||
        System.getenv("NIGHTSHIFT_OFFLINE")?.equals("true", ignoreCase = true) == true

    // We need a forward-reference: the API client takes a token provider, but
    // the AuthModule owns the token. Use a holder lambda that AuthModule swaps in.
    var tokenSupplier: () -> String? = { null }
    val api = BackendApiClient(
        baseUrl = backendBaseUrl,
        clientVersion = clientVersion,
        tokenProvider = { tokenSupplier() },
    )
    val auth = AuthModule(api, hwid, credentials)
    tokenSupplier = auth.tokenProvider()
    val keys = KeyModule(api, hwid)
    val online = OnlineModule(api)

    // ------------------------------------------------------------------
    // Phase B: self-update probe.
    //
    // We run the probe BEFORE Compose starts so a successful update path
    // can swap the .exe and exit without disturbing any UI state. On
    // failure (or simply "no update needed") we fall straight through to
    // the usual launcher boot.
    //
    // The download itself is deferred to a Compose Window so the user gets
    // a brand-styled progress UI rather than a black screen.
    // ------------------------------------------------------------------
    val updateModule = launcherExePath()?.let { exe ->
        LauncherUpdateModule(
            api = api,
            currentVersion = clientVersion,
            launcherExePath = exe,
        )
    }
    val updatePlan = updateModule?.let {
        runBlocking {
            runCatching { it.planUpdate() }.getOrElse { cause ->
                log.warn("planUpdate threw: {}", cause.message)
                LauncherUpdateModule.UpdatePlan.Failed(cause)
            }
        }
    }
    if (updateModule != null && updatePlan is LauncherUpdateModule.UpdatePlan.Available) {
        runSelfUpdate(paths, updateModule, updatePlan)
        // runSelfUpdate either exits the process or returns to fall through.
    }

    // Mod jars come from the backend over OTA when available; we fall back
    // to the jars baked into the launcher fat-jar if the backend is offline
    // or refuses the request. The cache lives under
    // %APPDATA%/NightShiftClient/cache/mods/ and is keyed by SHA-256 so the
    // launcher only re-downloads jars whose contents actually changed.
    //
    // In offline portable mode we skip the remote source entirely and use
    // only the embedded mods, so the launcher never tries to reach a
    // backend that isn't there.
    val modSource: `fun`.nightshift.launcher.client.game.ModJarSource = if (offlineMode) {
        log.info("Offline mode: using embedded mod jars only")
        EmbeddedModJarSource()
    } else {
        RemoteModJarSource(
            backendBaseUrl = backendBaseUrl,
            cacheDir = paths.cacheMods,
            clientVersion = clientVersion,
            fallback = EmbeddedModJarSource(),
            modEncryptor = AesGcmModEncryptor(),
            modDecryptor = AesGcmModDecryptor(),
        )
    }
    val protectedModsPreparer = `fun`.nightshift.launcher.client.crypto.DefaultProtectedModsPreparer(
        modDecryptor = AesGcmModDecryptor()
    )
    val tempDirCleaner = `fun`.nightshift.launcher.client.crypto.DefaultTempDirCleaner()
    val gameLauncher = GameLauncher(
        paths = paths,
        modJarSource = modSource,
        protectedModsPreparer = protectedModsPreparer,
        tempDirCleaner = tempDirCleaner,
    )

    application {
        val windowState = rememberWindowState(width = 960.dp, height = 620.dp)
        var keepRunning by remember { mutableStateOf(true) }

        if (!keepRunning) {
            // Tear down singletons before bowing out.
            online.shutdown()
            api.close()
            backgroundScope.cancel()
            exitApplication()
            return@application
        }

        Window(
            onCloseRequest = {
                backgroundScope.launch {
                    runCatching { online.sendFinalLogoutHeartbeat() }
                }
                keepRunning = false
            },
            title = "NightShift Launcher",
            state = windowState,
        ) {
            App(
                auth = auth,
                keys = keys,
                online = online,
                gameLauncher = gameLauncher,
                paths = paths,
                configStore = configStore,
                initialConfig = initialConfig,
                localization = localization,
                offlineMode = offlineMode,
                onMinimizeWindow = { windowState.isMinimized = true },
                onCloseWindow = {
                    backgroundScope.launch {
                        runCatching { online.sendFinalLogoutHeartbeat() }
                    }
                    keepRunning = false
                },
                onGameLaunch = { memoryMb, account, launcher, statusUpdater ->
                    val gameLog = LoggerFactory.getLogger("GameLaunch")
                    appendPlainLog(paths, "[Play] click memMb=$memoryMb username=${account.login}")
                    gameLog.info("Play clicked, memMb={} username={}", memoryMb, account.login)
                    try {
                        appendPlainLog(paths, "[Play] prepare() start")
                        gameLog.info("calling launcher.prepare()…")
                        val sink = object : `fun`.nightshift.launcher.client.game.ProgressSink {
                            override fun onStage(messageKey: String) {
                                statusUpdater(stageLabel(messageKey))
                            }
                            override fun onProgress(fraction01: Float) {
                                val pct = (fraction01 * 100).toInt().coerceIn(0, 100)
                                statusUpdater("Загрузка: $pct%")
                            }
                        }
                        val env = launcher.prepare(
                            sessionToken = tokenSupplier() ?: "",
                            progress = sink
                        )
                        appendPlainLog(paths, "[Play] prepare() done; classpath=${env.classpath.size} files; mainClass=${env.mainClass}; jre=${env.jrePath}")
                        gameLog.info("prepare done: classpath={} jre={} mainClass={}", env.classpath.size, env.jrePath, env.mainClass)

                        statusUpdater("Запуск игры...")
                        val process = launcher.launch(env, username = account.login, memoryMb = memoryMb)
                        appendPlainLog(paths, "[Play] launch() started, pid=${process.pid()}")
                        gameLog.info("launched pid={}", process.pid())

                        // Capture process output to disk so we can debug when MC fails to start.
                        backgroundScope.launch {
                            runCatching {
                                val out = paths.logs.resolve("minecraft-stdio.log").toFile()
                                process.inputStream.bufferedReader().useLines { lines ->
                                    out.printWriter().use { writer ->
                                        lines.forEach {
                                            writer.println(it)
                                            writer.flush()
                                        }
                                    }
                                }
                            }
                        }

                        windowState.isMinimized = true
                        backgroundScope.launch {
                            val exitCode = process.waitFor()
                            appendPlainLog(paths, "[Play] minecraft exited code=$exitCode")
                            gameLog.info("minecraft exited code={}", exitCode)
                            windowState.isMinimized = false
                        }
                    } catch (t: Throwable) {
                        appendPlainLog(paths, "[Play] FAILED: ${t.javaClass.simpleName}: ${t.message}")
                        gameLog.error("Game launch failed", t)
                        // Re-throw so App.kt's try/catch flips launching=false in UI
                        throw t
                    }
                },
            )
        }
    }
}

private const val DEFAULT_BACKEND_URL = "https://df6afa7f399888.lhr.life"

/**
 * Compile-time switch: when true the launcher runs without a backend.
 * Used for portable distribution — login is skipped, a synthetic lifetime
 * account is injected, and all mods come from the embedded resources.
 */
private const val OFFLINE_MODE = false

private fun stageLabel(key: String): String = when (key) {
    "game.preparing" -> "Подготовка Minecraft..."
    "game.downloading" -> "Загрузка файлов..."
    "game.launching" -> "Запуск игры..."
    else -> key
}

/** Reads the launcher version from the JVM, falling back to a hard-coded build tag. */
private fun readClientVersion(): String =
    System.getProperty("nightshift.launcher.version", "")
        .ifBlank { System.getenv("NIGHTSHIFT_LAUNCHER_VERSION").orEmpty() }
        .ifBlank { "1.0.0" }

/**
 * Best-effort lookup of the running launcher's .exe path on disk.
 *
 * jpackage'd Compose Desktop apps have their .exe at the path returned by
 * [ProcessHandle.current().info().command()]; running from gradle / IntelliJ
 * we get a JDK java executable instead, which is fine: the self-update flow
 * skips itself when the resolved command isn't a Windows .exe.
 */
private fun launcherExePath(): java.nio.file.Path? {
    val cmd = ProcessHandle.current().info().command().orElse(null) ?: return null
    if (!cmd.endsWith(".exe", ignoreCase = true)) return null
    return runCatching { java.nio.file.Paths.get(cmd) }.getOrNull()
}

/**
 * Phase B self-update flow: opens a brand-styled progress window, downloads
 * the new launcher .exe, verifies SHA-256, drops update.bat next to the
 * binary and spawns it before exiting the JVM.
 *
 * On verification failure the window switches to a "Повторить" CTA. The user
 * can also close the window to skip the update — in that case we fall through
 * and the regular launcher boot proceeds with the current version.
 *
 * The caller MUST treat this as a potentially terminating call: if the
 * update succeeds, the function never returns (the JVM exits via
 * [exitProcess]). On user-skip / hard error it returns and Main continues.
 */
private fun runSelfUpdate(
    paths: LauncherPaths,
    module: LauncherUpdateModule,
    plan: LauncherUpdateModule.UpdatePlan.Available,
) {
    val log = LoggerFactory.getLogger("SelfUpdate")
    log.info(
        "Self-update window: targetVersion={} isRollback={}",
        plan.targetVersion, plan.isRollback,
    )

    val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    var skipped = false

    application {
        val windowState = rememberWindowState(width = 720.dp, height = 480.dp)
        var progress by remember { mutableStateOf(0f) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var attempt by remember { mutableStateOf(0) }
        var done by remember { mutableStateOf(false) }

        LaunchedEffect(attempt) {
            errorMessage = null
            progress = 0f
            try {
                val newExe = module.downloadAndVerify(plan) { downloaded, total ->
                    progress = if (total > 0) downloaded.toFloat() / total else progress.coerceAtMost(0.9f) + 0.01f
                }
                progress = 1f
                appendPlainLog(paths, "[SelfUpdate] downloaded ${plan.targetVersion} sha256=${plan.download.sha256}")
                module.spawnUpdaterStub(newExe)
                appendPlainLog(paths, "[SelfUpdate] stub spawned, exiting parent")
                done = true
                exitProcess(0)
            } catch (t: SecurityException) {
                log.warn("SHA-256 mismatch on attempt {}: {}", attempt, t.message)
                appendPlainLog(paths, "[SelfUpdate] sha mismatch, will offer retry")
                errorMessage = "Контрольная сумма не совпала. Повторите попытку."
            } catch (t: Throwable) {
                log.warn("Self-update failed on attempt {}: {}", attempt, t.message, t)
                appendPlainLog(paths, "[SelfUpdate] failed: ${t.javaClass.simpleName}: ${t.message}")
                errorMessage = "Ошибка обновления: ${t.message ?: t.javaClass.simpleName}"
            }
        }

        Window(
            onCloseRequest = {
                skipped = true
                exitApplication()
            },
            title = "NightShift Launcher",
            state = windowState,
            resizable = false,
        ) {
            `fun`.nightshift.launcher.client.ui.theme.NightShiftTheme {
                `fun`.nightshift.launcher.client.ui.theme.NightShiftBackdrop {
                    UpdateScreen(
                        targetVersion = plan.targetVersion,
                        isRollback = plan.isRollback,
                        progress01 = progress,
                        releaseNotes = plan.releaseNotes,
                        errorMessage = errorMessage,
                        onRetry = { attempt += 1 },
                        onMinimize = { windowState.isMinimized = true },
                        onClose = {
                            skipped = true
                            exitApplication()
                        },
                    )
                }
            }
        }
    }

    updateScope.cancel()
    if (skipped) {
        appendPlainLog(paths, "[SelfUpdate] user skipped update window; continuing with current version")
    }
}

private fun CoroutineScope.cancel() = (this.coroutineContext[Job])?.cancel()

/**
 * Catches every uncaught exception on every thread (including coroutines
 * launched on Dispatchers.IO/Default) and writes a crash report into
 * `%APPDATA%/NightShiftClient/logs/crash-<timestamp>.txt`.
 *
 * Without this handler an unexpected throwable from JNA / OSHI / Ktor /
 * Compose surfaces as a useless "Error: Unknown error" Swing dialog from
 * the jpackage launcher stub, which is what the user just saw on the
 * registration screen. With it, the failing stack trace is preserved on
 * disk and the launcher can keep running rather than being killed.
 */
private fun installGlobalCrashHandler(paths: LauncherPaths) {
    val logger = LoggerFactory.getLogger("CrashHandler")
    val handler = Thread.UncaughtExceptionHandler { thread, throwable ->
        logger.error("Uncaught exception on thread '{}'", thread.name, throwable)
        runCatching {
            val stamp = java.time.LocalDateTime.now().toString().replace(':', '-')
            val crashFile = paths.logs.resolve("crash-$stamp.txt")
            java.nio.file.Files.createDirectories(crashFile.parent)
            val sw = java.io.StringWriter().also { throwable.printStackTrace(java.io.PrintWriter(it)) }
            java.nio.file.Files.writeString(
                crashFile,
                "Thread: ${thread.name}\n${sw}",
            )
        }
        // Append to a plain-text running log too, so even if logback never
        // initialises we can still inspect the launcher's history.
        appendPlainLog(paths, "[CRASH on ${thread.name}] ${throwable.javaClass.simpleName}: ${throwable.message}")
    }
    Thread.setDefaultUncaughtExceptionHandler(handler)
    Thread.currentThread().uncaughtExceptionHandler = handler
}

/**
 * Plain-text fallback log writer. Logback occasionally fails to initialise
 * inside a jpackage'd Compose app (working dir read-only, classpath race,
 * etc.); having an unconditional `Files.writeString(..., APPEND)` path
 * guarantees we always have a forensic trail.
 */
internal fun appendPlainLog(paths: LauncherPaths, line: String) {
    runCatching {
        val target = paths.logs.resolve("launcher-plain.log")
        java.nio.file.Files.createDirectories(target.parent)
        val ts = java.time.LocalDateTime.now()
        java.nio.file.Files.writeString(
            target,
            "$ts $line${System.lineSeparator()}",
            java.nio.charset.StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND,
        )
    }
}

/**
 * Redirects System.out / System.err into the plain log so any println /
 * stack trace from libraries that bypass slf4j still ends up on disk.
 */
private fun redirectStdStreams(paths: LauncherPaths) {
    runCatching {
        val target = paths.logs.resolve("launcher-stdio.log")
        java.nio.file.Files.createDirectories(target.parent)
        val ps = java.io.PrintStream(
            java.nio.file.Files.newOutputStream(
                target,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND,
            ),
            true,
            "UTF-8",
        )
        System.setOut(ps)
        System.setErr(ps)
    }
}
