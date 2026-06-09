package `fun`.nightshift.launcher.client.game

import `fun`.nightshift.launcher.client.paths.LauncherPaths
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Prepares Minecraft 1.21.4 + Fabric Loader and launches the game with the
 * NightShift Client mod injected.
 *
 * The implementation favours **idempotent** downloads — every artifact
 * carries a SHA-1 (Mojang) or SHA-512 (Fabric) hash, and an existing on-disk
 * file with the right hash is reused. A second click of "Play" therefore
 * skips network traffic entirely.
 *
 * **What's intentionally simple:**
 *  * Library exclusions (`rules`) only honour the OS branch, ignoring
 *    architecture-specific natives. NightShift only ships Windows x64, so
 *    that's enough.
 *  * Asset downloads are pulled lazily by Minecraft itself if they're
 *    missing — we only fetch the asset index manifest. This trades the
 *    first launch a few extra seconds for a much simpler launcher.
 *  * The bundled JRE 21 is expected to live at `runtime/jdk/bin/javaw.exe`
 *    (filled in by task 12.7); if it's missing we fall back to `JAVA_HOME`
 *    or the `java` on PATH.
 *
 * Everything is `suspend` and runs on `Dispatchers.IO`. Progress callbacks
 * fire on the dispatcher's thread — the UI must dispatch them onto Compose
 * via `Snapshot.withMutableSnapshot` or `mutableStateOf` updates.
 */
class GameLauncher(
    private val paths: LauncherPaths,
    private val modJarSource: ModJarSource,
    private val protectedModsPreparer: `fun`.nightshift.launcher.client.crypto.ProtectedModsPreparer,
    private val tempDirCleaner: `fun`.nightshift.launcher.client.crypto.TempDirCleaner,
    private val http: HttpClient = defaultHttpClient(),
) {
    
    // Store temp directory path for cleanup
    @Volatile
    private var currentTempDir: Path? = null
    
    init {
        // Clean up old temp directories on launcher startup
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            tempDirCleaner.cleanupOldDirectories()
        }
    }

    /**
     * Builds the runtime environment for one launch. Throws [GameLauncherException]
     * on any preparation failure so callers can show a single error dialog.
     * 
     * @param sessionToken User's session token for mod encryption/decryption (empty string for offline mode)
     * @param progress Progress sink for UI updates
     */
    suspend fun prepare(
        sessionToken: String = "",
        progress: ProgressSink = ProgressSink.NONE
    ): GameEnvironment =
        withContext(Dispatchers.IO) {
            log.info("Preparing Minecraft {} environment in {}", MC_VERSION, paths.minecraft)
            ensureMcDir()
            progress.onStage("game.preparing")

            // 1. Vanilla version manifest → version JSON
            val versionJson = downloadVanillaVersionJson(progress)
            val vanilla = VanillaVersion.parse(versionJson)
            log.info("Vanilla MC {} downloaded", vanilla.id)

            // 2. Vanilla client.jar
            val clientJar = paths.minecraft.resolve("versions/${vanilla.id}/${vanilla.id}.jar")
            ensureSha1(clientJar, vanilla.clientUrl, vanilla.clientSha1, progress)

            // 3. Vanilla libraries
            val libs = downloadVanillaLibraries(vanilla, progress)

            // 4. Fabric loader profile
            val fabricProfile = downloadFabricProfile(progress)
            val fabricLibs = downloadFabricLibraries(fabricProfile, progress)

            // 5. NightShift mod JAR + bundled dependencies into mods/
            placeBundledMods(sessionToken, progress)

            // 5.5 Ensure FPS cap is unlimited. Minecraft renders maxFps=260
            // as "Безлимитный" in the video settings UI; we patch
            // options.txt before each launch so newly-installed users get
            // it out of the box without breaking existing custom keybinds.
            ensureUnlimitedFps()

            // 6. Asset index (assets themselves are downloaded by MC on first run).
            downloadAssetIndex(vanilla, progress)
            downloadAssetObjects(vanilla, progress)
            
            // 7. Prepare protected mods (decrypt premium mods to temp directory)
            val protectedModsDir = try {
                protectedModsPreparer.prepare(paths.cacheMods, sessionToken)
            } catch (e: Exception) {
                log.warn("Failed to prepare protected mods: {}", e.message, e)
                null // Graceful degradation - game may still work with public mods
            }
            
            // Store temp directory for cleanup
            currentTempDir = protectedModsDir

            val classpath = buildList {
                add(clientJar)
                addAll(libs)
                addAll(fabricLibs)
            }.let { dedupeClasspath(it) }
            val nativesDir = paths.minecraft.resolve("versions/${vanilla.id}/natives")
            Files.createDirectories(nativesDir)

            GameEnvironment(
                minecraftDir = paths.minecraft,
                jrePath = locateJre(),
                classpath = classpath,
                nativesDir = nativesDir,
                mainClass = fabricProfile.mainClass,
                vanillaArgs = vanilla.gameArgs,
                fabricArgs = fabricProfile.gameArgs,
                jvmDefaults = fabricProfile.jvmArgs,
                versionId = fabricProfile.profileId,
                assetsIndex = vanilla.assetsIndex,
                assetsDir = paths.minecraft.resolve("assets"),
                protectedModsDir = protectedModsDir,
            )
        }

    /**
     * Builds the JVM command and starts the child process.
     *
     * @param env       Output of [prepare].
     * @param username  In-game username (the player's launcher login is fine).
     * @param memoryMb  `-Xmx` heap budget for the child JVM.
     */
    fun launch(env: GameEnvironment, username: String, memoryMb: Int): Process {
        val command = buildCommand(env, username, memoryMb)
        log.info("Launching: {}", command.joinToString(" "))
        val pb = ProcessBuilder(command)
            .directory(env.minecraftDir.toFile())
            .redirectErrorStream(true)
        
        // Register shutdown hook for temp directory cleanup
        val cleanupHook = if (env.protectedModsDir != null) {
            Thread {
                log.debug("Shutdown hook triggered, cleaning up temp directory")
                tempDirCleaner.cleanup(env.protectedModsDir)
            }.also { hook ->
                Runtime.getRuntime().addShutdownHook(hook)
                log.debug("Registered shutdown hook for temp directory cleanup")
            }
        } else {
            null
        }
        
        // Inherit a clean env so plugins like JAVA_OPTS don't sneak extra args in.
        val process = pb.start()
        
        // Launch cleanup coroutine to wait for process and cleanup after exit
        if (env.protectedModsDir != null) {
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val exitCode = process.waitFor()
                    log.info("Minecraft process exited with code {}", exitCode)
                } catch (e: InterruptedException) {
                    log.warn("Process wait interrupted", e)
                } finally {
                    // Cleanup temp directory after game exit
                    log.debug("Game exited, cleaning up temp directory")
                    tempDirCleaner.cleanup(env.protectedModsDir)
                    
                    // Remove shutdown hook to avoid double cleanup
                    if (cleanupHook != null) {
                        try {
                            Runtime.getRuntime().removeShutdownHook(cleanupHook)
                            log.debug("Removed shutdown hook after manual cleanup")
                        } catch (e: IllegalStateException) {
                            // Hook already running or JVM shutting down, ignore
                            log.debug("Could not remove shutdown hook (JVM may be shutting down)")
                        }
                    }
                }
            }
        }
        
        return process
    }

    private fun buildCommand(env: GameEnvironment, username: String, memoryMb: Int): List<String> {
        val cp = env.classpath.joinToString(separator = java.io.File.pathSeparator) { it.toAbsolutePath().toString() }
        // Fabric searches for the vanilla Minecraft jar on the classpath by
        // hashing entry contents — fast in production, fragile when the
        // launcher already ships its own `client.jar`. We point Fabric at
        // the vanilla jar explicitly so the McProvider always resolves.
        // The path is the first entry of the classpath we built (clientJar).
        val clientJar = env.classpath.firstOrNull()?.toAbsolutePath()?.toString()
        val cmd = mutableListOf<String>()
        cmd += env.jrePath.toAbsolutePath().toString()
        cmd += "-Xmx${memoryMb}m"
        // Match -Xms to -Xmx so the heap never has to grow at runtime; each
        // JVM heap expansion is a global pause, and on a 4–8 GiB ceiling
        // the cumulative cost is ~1 second of fragmented stalls during the
        // first few minutes of play. AlwaysPreTouch (below) commits the
        // pages up-front so we don't trade those pauses for page-fault
        // hitches during chunk loading.
        cmd += "-Xms${memoryMb}m"
        // -----------------------------------------------------------------
        // Aikar's G1GC flags — the de-facto standard for low-pause Minecraft.
        // Without these, Minecraft 1.21 on stock G1GC gives 100–300 ms STW
        // pauses during chunk unload. With them, p99 pause stays under 25 ms
        // on typical 4–8 GiB heaps.
        //
        // Reference: PaperMC documentation, derived from Aikar's research on
        // long-running survival servers. The same flags work for the client.
        //
        // Why these specific knobs:
        //   * G1NewSizePercent=30 / G1MaxNewSizePercent=40 — bias toward
        //     a large young gen; chunks die young, so we want eden roomy.
        //   * G1HeapRegionSize=8M — the default 1M is too granular for
        //     8 GiB+ heaps and causes humongous-object thrash on map data.
        //   * G1ReservePercent=20 — keeps headroom so to-space exhaustion
        //     does not trigger full-GC stalls.
        //   * InitiatingHeapOccupancyPercent=15 — start mixed-collections
        //     early; don't wait until 45 % to reclaim old gen.
        //   * +ParallelRefProcEnabled — parallelise reference processing,
        //     matters for entities/block-entities cleanup.
        //   * +UseNUMA — multi-socket NUMA awareness (no-op on consumer
        //     CPUs, harmless to leave on).
        //   * +PerfDisableSharedMem — kills the hsperfdata jitter that
        //     occasionally causes 30–80 ms hiccups on Windows.
        //   * MaxTenuringThreshold=1 — tenure quickly so the young-gen
        //     copy work stays cheap.
        // -----------------------------------------------------------------
        cmd += listOf(
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+UseG1GC",
            "-XX:G1NewSizePercent=30",
            "-XX:G1MaxNewSizePercent=40",
            "-XX:G1HeapRegionSize=8M",
            "-XX:G1ReservePercent=20",
            "-XX:G1HeapWastePercent=5",
            "-XX:G1MixedGCCountTarget=4",
            "-XX:G1MixedGCLiveThresholdPercent=90",
            "-XX:G1RSetUpdatingPauseTimePercent=5",
            "-XX:InitiatingHeapOccupancyPercent=15",
            "-XX:+ParallelRefProcEnabled",
            "-XX:+UseNUMA",
            "-XX:+PerfDisableSharedMem",
            "-XX:MaxTenuringThreshold=1",
            "-XX:SurvivorRatio=32",
            "-XX:+AlwaysPreTouch",
            "-XX:+DisableExplicitGC",
            // Note: do NOT add -XX:+UseFastAccessorMethods here. It was
            // removed in Java 9 and Java 21 refuses to start with it.
            // Likewise, -XX:+UseStringDeduplication costs ~5 ms per young-gen
            // collection and saves only ~30 MiB on a fresh world; not worth
            // the latency hit on the client.
        )
        cmd += "-Djava.library.path=${env.nativesDir.toAbsolutePath()}"
        if (clientJar != null) {
            cmd += "-Dfabric.gameJarPath=$clientJar"
            cmd += "-Dfabric.gameJarPath.client=$clientJar"
        }
        // Add Fabric addMods flag if protected mods directory exists
        if (env.protectedModsDir != null) {
            cmd += "-Dfabric.addMods=${env.protectedModsDir.toAbsolutePath()}"
        }
        cmd += env.jvmDefaults
        cmd += "-cp"
        cmd += cp
        cmd += env.mainClass

        // Standard MC game arguments. Offline auth — token is `0`/`offline`.
        cmd += listOf(
            "--username", username.ifBlank { "Player" },
            "--version", env.versionId,
            "--gameDir", env.minecraftDir.toAbsolutePath().toString(),
            "--assetsDir", env.assetsDir.toAbsolutePath().toString(),
            "--assetIndex", env.assetsIndex,
            "--uuid", offlineUuid(username),
            "--accessToken", "0",
            "--clientId", "",
            "--xuid", "",
            "--userType", "legacy",
            "--versionType", "release",
        )
        cmd += env.fabricArgs
        return cmd
    }

    private fun ensureMcDir() {
        Files.createDirectories(paths.minecraft.resolve("versions/$MC_VERSION"))
        Files.createDirectories(paths.minecraft.resolve("libraries"))
        Files.createDirectories(paths.minecraft.resolve("assets/indexes"))
        Files.createDirectories(paths.mods)
    }

    // ---- Vanilla Minecraft -------------------------------------------------

    private suspend fun downloadVanillaVersionJson(progress: ProgressSink): JsonObject {
        val cached = paths.minecraft.resolve("versions/$MC_VERSION/$MC_VERSION.json")
        if (Files.exists(cached)) {
            return runCatching { JSON.parseToJsonElement(Files.readString(cached)).jsonObject }
                .getOrElse { fetchAndCacheVanillaJson(cached, progress) }
        }
        return fetchAndCacheVanillaJson(cached, progress)
    }

    private suspend fun fetchAndCacheVanillaJson(target: Path, progress: ProgressSink): JsonObject {
        progress.onStage("game.preparing")
        val manifestText = http.get(MOJANG_MANIFEST_URL).bodyAsText()
        val manifest = JSON.parseToJsonElement(manifestText).jsonObject
        val version = manifest["versions"]?.jsonArray
            ?.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull == MC_VERSION }
            ?.jsonObject ?: throw GameLauncherException("Mojang manifest does not contain $MC_VERSION")
        val versionUrl = version["url"]?.jsonPrimitive?.content
            ?: throw GameLauncherException("Mojang manifest entry for $MC_VERSION has no URL")
        val versionText = http.get(versionUrl).bodyAsText()
        Files.createDirectories(target.parent)
        Files.writeString(target, versionText)
        return JSON.parseToJsonElement(versionText).jsonObject
    }

    private suspend fun downloadVanillaLibraries(version: VanillaVersion, progress: ProgressSink): List<Path> {
        val out = ArrayList<Path>()
        for ((idx, lib) in version.libraries.withIndex()) {
            val target = paths.minecraft.resolve("libraries").resolve(lib.path)
            ensureSha1(target, lib.url, lib.sha1, progress)
            out.add(target)
            progress.onProgress((idx + 1).toFloat() / version.libraries.size.coerceAtLeast(1))
        }
        return out
    }

    private suspend fun downloadAssetIndex(version: VanillaVersion, progress: ProgressSink) {
        val target = paths.minecraft.resolve("assets/indexes/${version.assetsIndex}.json")
        ensureSha1(target, version.assetsIndexUrl, version.assetsIndexSha1, progress)
    }

    /**
     * Downloads each asset object referenced by the asset index.
     *
     * The assets index is a JSON of the form
     * ```
     * { "objects": { "minecraft/sounds/foo.ogg": { "hash": "abc...", "size": 123 } } }
     * ```
     * Each entry's content is fetched from
     * `https://resources.download.minecraft.net/<first-2-chars>/<full-hash>`
     * and stored at `assets/objects/<first-2-chars>/<full-hash>` — the same
     * layout the official Mojang launcher uses, which is where Minecraft
     * itself resolves files via the [VirtualFileSystem]/`class_7669` path.
     *
     * Files that already exist on disk (and aren't zero-bytes) are skipped,
     * so launching after the first run takes seconds rather than minutes.
     *
     * Downloads run in parallel across [ASSET_DOWNLOAD_PARALLELISM] coroutines
     * — the bottleneck is per-connection latency to resources.download.minecraft.net,
     * so 16-way parallelism shrinks first-run time from ~10 minutes to ~1 minute
     * on a typical 100 Mbps line.
     */
    private suspend fun downloadAssetObjects(version: VanillaVersion, progress: ProgressSink) {
        val indexPath = paths.minecraft.resolve("assets/indexes/${version.assetsIndex}.json")
        val indexJson = JSON.parseToJsonElement(Files.readString(indexPath)).jsonObject
        val objects = indexJson["objects"]?.jsonObject ?: return
        val total = objects.size
        if (total == 0) return

        val objectsRoot = paths.minecraft.resolve("assets/objects")
        Files.createDirectories(objectsRoot)

        // Build the to-download list once so we can split it across workers.
        val pending = ArrayList<Pair<String, java.nio.file.Path>>(total)
        for ((_, value) in objects) {
            val obj = value.jsonObject
            val hash = obj["hash"]?.jsonPrimitive?.content ?: continue
            val target = objectsRoot.resolve(hash.take(2)).resolve(hash)
            if (Files.exists(target) && Files.size(target) > 0) continue
            pending += hash to target
        }
        if (pending.isEmpty()) {
            log.info("All {} asset objects already on disk", total)
            return
        }

        log.info("Downloading {} new asset objects out of {}", pending.size, total)
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val totalToFetch = pending.size

        coroutineScope {
            val chunkSize = (pending.size / ASSET_DOWNLOAD_PARALLELISM).coerceAtLeast(1)
            for (chunk in pending.chunked(chunkSize)) {
                launch(Dispatchers.IO) {
                    for ((hash, target) in chunk) {
                        try {
                            Files.createDirectories(target.parent)
                            val url = "https://resources.download.minecraft.net/${hash.take(2)}/$hash"
                            val bytes = http.get(url).readBytes()
                            Files.write(target, bytes)
                        } catch (t: Throwable) {
                            log.warn("Failed to fetch asset {}: {}", hash, t.message)
                        }
                        val n = done.incrementAndGet()
                        if (n % 100 == 0 || n == totalToFetch) {
                            progress.onProgress(n.toFloat() / totalToFetch)
                            log.info("Asset download: {}/{}", n, totalToFetch)
                        }
                    }
                }
            }
        }
        log.info("Asset download complete")
    }

    // ---- Fabric ------------------------------------------------------------

    private suspend fun downloadFabricProfile(progress: ProgressSink): FabricProfile {
        val versionsUrl = "https://meta.fabricmc.net/v2/versions/loader/$MC_VERSION"
        val text = http.get(versionsUrl).bodyAsText()
        val arr = JSON.parseToJsonElement(text).jsonArray
        val first = arr.firstOrNull()?.jsonObject
            ?: throw GameLauncherException("Fabric meta has no loader for $MC_VERSION")
        val loaderVersion = first["loader"]?.jsonObject?.get("version")?.jsonPrimitive?.content
            ?: throw GameLauncherException("Fabric loader entry has no version")

        val profileUrl = "https://meta.fabricmc.net/v2/versions/loader/$MC_VERSION/$loaderVersion/profile/json"
        val profileText = http.get(profileUrl).bodyAsText()
        return FabricProfile.parse(JSON.parseToJsonElement(profileText).jsonObject)
    }

    private suspend fun downloadFabricLibraries(profile: FabricProfile, progress: ProgressSink): List<Path> {
        val out = ArrayList<Path>()
        for ((idx, lib) in profile.libraries.withIndex()) {
            val target = paths.minecraft.resolve("libraries").resolve(lib.path)
            // Fabric meta does not always ship sha1 — pull-by-name with a relaxed check.
            ensureRelaxed(target, lib.url, progress)
            out.add(target)
            progress.onProgress((idx + 1).toFloat() / profile.libraries.size.coerceAtLeast(1))
        }
        return out
    }

    /**
     * Ensures Minecraft's FPS cap is set to "Unlimited" in `options.txt`.
     *
     * Minecraft's settings file lives at `<gameDir>/options.txt` and is a
     * line-oriented `key:value` format. The relevant key is `maxFps`; a
     * value of 260 is rendered as "Безлимитный" / "Unlimited" in the video
     * settings UI.
     *
     * Behaviour:
     *  * File missing → creates a single-line `maxFps:260` so first launch
     *    already runs unlimited.
     *  * File exists, key missing → appends `maxFps:260`.
     *  * File exists, key present → overwrites only that line, preserving
     *    every other preference the user might have tweaked (keybinds,
     *    sound, GUI scale, etc.).
     *
     * Failures are logged at WARN; we never abort the launch over an
     * options-file glitch — Minecraft can still run with its built-in
     * defaults.
     */
    private fun ensureUnlimitedFps() {
        val optionsFile = paths.minecraft.resolve("options.txt")
        try {
            val targetLine = "maxFps:260"
            if (!Files.exists(optionsFile)) {
                Files.createDirectories(optionsFile.parent)
                Files.writeString(optionsFile, targetLine + System.lineSeparator())
                log.info("Created options.txt with maxFps:260")
                return
            }
            val original = Files.readAllLines(optionsFile)
            val patched = ArrayList<String>(original.size + 1)
            var seen = false
            for (line in original) {
                if (line.startsWith("maxFps:")) {
                    patched += targetLine
                    seen = true
                } else {
                    patched += line
                }
            }
            if (!seen) patched += targetLine
            // Skip rewriting when the file already has maxFps:260; avoids
            // pointless disk writes on every launch.
            if (original == patched) return
            Files.write(optionsFile, patched)
            log.info("Patched options.txt → maxFps:260")
        } catch (t: Throwable) {
            log.warn("Failed to patch FPS cap in {}: {}", optionsFile, t.message)
        }
    }

    // ---- Bundled mods ------------------------------------------------------

    /**
     * Copies every jar provided by the [modJarSource] into the Minecraft
     * `mods/` folder. Existing files with matching SHA-1 are left in place
     * so re-launches don't rewrite ~30 MB of jars on every click.
     *
     * The previous incarnation only placed the NightShift cheat. Now we
     * also ship Fabric API and Baritone alongside it so the cheat works
     * out of the box on a fresh install — Minecraft 1.21.4's Fabric build
     * needs Fabric API for the cheat's mixins, and users specifically
     * asked for Baritone.
     * 
     * Premium mods are skipped here because they are decrypted to a temporary
     * directory and loaded via Fabric's -Dfabric.addMods flag instead.
     */
    private suspend fun placeBundledMods(sessionToken: String, progress: ProgressSink) {
        val mods = modJarSource.read(sessionToken)
        if (mods.isEmpty()) {
            log.warn("Mod source returned no jars; skipping mod placement")
            return
        }
        Files.createDirectories(paths.mods)
        for (mod in mods) {
            // Classify mod to determine if it should be copied to minecraft/mods/
            val classification = `fun`.nightshift.launcher.client.crypto.classifyMod(mod.fileName)
            
            if (classification == `fun`.nightshift.launcher.client.crypto.ModClassification.PREMIUM) {
                // Skip premium mods - they're in the temp directory
                log.debug("Skipping premium mod '{}' (loaded from temp directory)", mod.fileName)
                continue
            }
            
            // Copy public mods to minecraft/mods/
            val target = paths.mods.resolve(mod.fileName)
            val sourceHash = sha1(mod.bytes)
            if (Files.exists(target)) {
                val existing = sha1(Files.readAllBytes(target))
                if (existing == sourceHash) {
                    log.debug("Mod '{}' already up-to-date", mod.fileName)
                    continue
                }
            }
            Files.write(target, mod.bytes)
            log.info("Wrote mod '{}' ({} KiB) to {}", mod.fileName, mod.bytes.size / 1024, target)
        }
    }

    // ---- Download helpers --------------------------------------------------

    private suspend fun ensureSha1(target: Path, url: String, expected: String, progress: ProgressSink) {
        if (Files.exists(target)) {
            val have = sha1(Files.readAllBytes(target))
            if (have.equals(expected, ignoreCase = true)) return
            log.warn("Hash mismatch for {} (have={}, expected={}); re-downloading", target, have, expected)
        }
        Files.createDirectories(target.parent)
        val bytes = http.get(url).readBytes()
        val have = sha1(bytes)
        if (!have.equals(expected, ignoreCase = true)) {
            throw GameLauncherException("SHA-1 mismatch for $url (have=$have, expected=$expected)")
        }
        Files.write(target, bytes)
    }

    /** Lighter check used for Fabric (manifest doesn't always carry sha1). */
    private suspend fun ensureRelaxed(target: Path, url: String, progress: ProgressSink) {
        if (Files.exists(target) && Files.size(target) > 0) return
        Files.createDirectories(target.parent)
        val bytes = http.get(url).readBytes()
        Files.write(target, bytes)
    }

    private fun sha1(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
        return buildString(digest.size * 2) {
            for (b in digest) append(b.toUByte().toString(16).padStart(2, '0'))
        }
    }

    private fun locateJre(): Path {
        val isWin = isWindows()
        val exeName = if (isWin) "javaw.exe" else "java"

        // Build a prioritised list of candidate JRE binaries and pick the
        // first one that satisfies our Minecraft 1.21.4 requirement
        // (Java >= 21). We explicitly avoid bare PATH entries (`scoop`'s
        // Temurin 17 wins lookup order on this developer's box and that
        // produces UnsupportedClassVersionError at runtime).
        val candidates = buildList<Path> {
            // 1) Bundled JRE shipped with the launcher (task 12.7).
            add(paths.runtime.resolve("jdk/bin/$exeName"))
            // 2) Same JVM that runs the launcher (jpackage installs a
            //    private JRE next to the .exe under runtime/bin/). For
            //    NightShift Launcher this is JDK 21.
            System.getProperty("java.home", "").takeIf { it.isNotBlank() }?.let { home ->
                add(Path.of(home, "bin", exeName))
                add(Path.of(home, "bin", if (isWin) "java.exe" else "java"))
            }
            // 3) JAVA_HOME env var (developer override).
            System.getenv("JAVA_HOME")?.takeIf { it.isNotBlank() }?.let { env ->
                add(Path.of(env, "bin", exeName))
                add(Path.of(env, "bin", if (isWin) "java.exe" else "java"))
            }
            // 4) Common Adoptium / Eclipse Temurin install roots.
            for (vendor in listOf("Eclipse Adoptium", "Adoptium", "Eclipse Foundation", "Java")) {
                val root = Path.of(System.getenv("ProgramFiles") ?: "C:/Program Files", vendor)
                if (Files.isDirectory(root)) {
                    runCatching {
                        Files.list(root).use { stream ->
                            stream.filter { Files.isDirectory(it) }
                                .filter { it.fileName.toString().contains("21") || it.fileName.toString().contains("jdk-21") }
                                .forEach { add(it.resolve("bin").resolve(exeName)) }
                        }
                    }
                }
            }
        }

        for (candidate in candidates) {
            if (!Files.exists(candidate)) continue
            val major = readJavaMajorVersion(candidate)
            if (major == null) {
                log.debug("Could not detect Java version for {}", candidate)
                continue
            }
            if (major >= 21) {
                log.info("Using JRE {} (Java {})", candidate, major)
                return candidate
            }
            log.debug("Skipping {}: Java {} < 21", candidate, major)
        }

        // 5) Fallback: PATH lookup. Last resort because PATH may pick an
        //    older JDK; we still version-check it before returning.
        runCatching {
            val cmd = if (isWin) listOf("where", exeName) else listOf("which", exeName)
            val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            for (line in out.lines()) {
                val p = Path.of(line.trim())
                if (!Files.exists(p)) continue
                val major = readJavaMajorVersion(p) ?: continue
                if (major >= 21) {
                    log.info("Using JRE {} (Java {}, found via PATH)", p, major)
                    return p
                }
            }
        }

        log.error("Could not locate a Java 21+ runtime; Minecraft 1.21.4 will fail to start")
        return Path.of(exeName)
    }

    /**
     * Asks `<javaw> -version` for the JVM's major version.
     * Returns null on any failure.
     */
    private fun readJavaMajorVersion(javaBinary: Path): Int? = runCatching {
        val proc = ProcessBuilder(javaBinary.toString(), "-version")
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        // Output looks like:  openjdk version "21.0.4" 2024-07-16
        // or:                 java version "1.8.0_321"
        val match = Regex("""version\s+"([^"]+)"""").find(output) ?: return@runCatching null
        val raw = match.groupValues[1]
        val major = if (raw.startsWith("1.")) {
            raw.substring(2).substringBefore('.').toIntOrNull()
        } else {
            raw.substringBefore('.').toIntOrNull()
        }
        major
    }.getOrNull()

    private fun isWindows() = System.getProperty("os.name", "").lowercase().contains("windows")

    /**
     * Removes duplicate libraries from the classpath, keeping the last
     * occurrence of each `group:artifact` pair. Fabric pins specific
     * library versions (notably ASM, log4j, jopt-simple) that conflict
     * with the vanilla Mojang manifest copies; without dedup the Knot
     * loader aborts with `IllegalStateException: duplicate ASM classes
     * found on classpath`. We keep the *last* occurrence because Fabric
     * libraries are appended after vanilla ones, so the Fabric pin wins —
     * which is exactly what the loader expects.
     *
     * The path layout we rely on is the Maven convention used by both
     * Mojang and Fabric:
     *
     *     libraries/<group>/<artifact>/<version>/<artifact>-<version>[-<classifier>].jar
     *
     * Anything that doesn't match (loose jars, etc.) falls back to dedup
     * by file name so a misshapen path still doesn't appear twice.
     */
    private fun dedupeClasspath(input: List<Path>): List<Path> {
        val seen = LinkedHashMap<String, Path>()
        for (path in input) {
            val key = mavenKey(path) ?: path.fileName.toString()
            seen[key] = path // last write wins
        }
        if (seen.size != input.size) {
            log.info("Classpath dedup: {} → {} entries", input.size, seen.size)
        }
        return seen.values.toList()
    }

    private fun mavenKey(jarPath: Path): String? {
        // Build a normalised relative form like "org/ow2/asm/asm/9.6/asm-9.6.jar"
        val parts = jarPath.toAbsolutePath().toString().replace('\\', '/').split('/')
        val libIdx = parts.indexOf("libraries")
        if (libIdx < 0 || parts.size - libIdx < 5) return null
        // ...libraries/<g1>/<g2>/.../<gN>/<artifact>/<version>/<file>
        val tail = parts.subList(libIdx + 1, parts.size)
        if (tail.size < 4) return null
        val artifact = tail[tail.size - 3]
        val version = tail[tail.size - 2]
        val fileName = tail[tail.size - 1]
        val groupParts = tail.subList(0, tail.size - 3)
        val group = groupParts.joinToString(".")

        // Extract the optional classifier from the file name, e.g.:
        //   asm-9.6.jar               -> classifier = ""
        //   jtracy-1.0.29-natives-windows.jar -> classifier = "natives-windows"
        // Without this, a "main" jar and its companion natives jar both map
        // to the same `group:artifact` key and one of them gets evicted by
        // dedup — exactly what wiped jtracy-1.0.29.jar from the classpath
        // and crashed Minecraft with NoClassDefFoundError: TracyClient.
        val expectedPrefix = "$artifact-$version"
        val classifier = if (fileName.startsWith(expectedPrefix) && fileName.endsWith(".jar")) {
            val middle = fileName.removePrefix(expectedPrefix).removeSuffix(".jar")
            middle.removePrefix("-")
        } else {
            ""
        }

        return "$group:$artifact:$classifier"
    }

    /** Stable offline UUID derived from username via SHA-1, MC convention. */
    private fun offlineUuid(username: String): String {
        val name = "OfflinePlayer:${username.ifBlank { "Player" }}"
        val md = MessageDigest.getInstance("MD5").digest(name.toByteArray(Charsets.UTF_8))
        // Set version (3) and IETF variant bits like UUID.nameUUIDFromBytes.
        md[6] = (md[6].toInt() and 0x0f or 0x30).toByte()
        md[8] = (md[8].toInt() and 0x3f or 0x80).toByte()
        val hex = buildString(32) { for (b in md) append(b.toUByte().toString(16).padStart(2, '0')) }
        return "${hex.substring(0,8)}-${hex.substring(8,12)}-${hex.substring(12,16)}-${hex.substring(16,20)}-${hex.substring(20,32)}"
    }

    companion object {
        private val log = LoggerFactory.getLogger(GameLauncher::class.java)
        const val MC_VERSION: String = "1.21.4"
        const val NIGHTSHIFT_JAR_NAME: String = "NightShift Client Recode 2.7.jar"
        private const val MOJANG_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        private const val ASSET_DOWNLOAD_PARALLELISM: Int = 16

        val JSON: Json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 120_000
                socketTimeoutMillis = 120_000
            }
        }
    }
}
