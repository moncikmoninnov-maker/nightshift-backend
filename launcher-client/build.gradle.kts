import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

buildscript {
    repositories { mavenCentral() }
    dependencies {
        // ASM is only used by the obfuscation task below, never on the
        // runtime classpath, so we pull it through buildscript.
        classpath("org.ow2.asm:asm:9.7.1")
        classpath("org.ow2.asm:asm-commons:9.7.1")
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

kotlin {
    jvmToolchain(21)

    // Strip debug info at compile time so the jar contains no source-line
    // tables even before `obfuscateLauncherJar` runs. Two layers because
    // belt-and-braces beats one-pass: the kotlinc flag covers the bulk,
    // ASM cleans up edge cases that survive (e.g. inline lambdas).
    compilerOptions {
        // Keep parameter names stripped from the bytecode (Compose only
        // needs them at the @Composable-marker level which uses its own
        // metadata, not java.lang.reflect.Parameter).
        javaParameters.set(false)
        // -Xno-source-debug-extension drops Kotlin SMAP markers used by
        // debuggers to map inline functions back to source files.
        freeCompilerArgs.addAll(
            listOf(
                "-Xno-source-debug-extension",
            )
        )
    }
}

// jlink/jpackage on Windows cannot read `@args.txt` argument files when the
// containing path includes non-ASCII characters (Cyrillic in our case).
// Compose Desktop's createRuntimeImage task always passes such a file, so
// when the project lives under e.g. `C:\Users\костя\…` packaging fails
// with a useless `Error: <path>` message.
//
// Workaround: redirect this module's build output to a guaranteed-ASCII
// directory. Devs with ASCII-only paths can opt out by setting
// `nightshift.launcher.asciiBuildDir=false` in their `gradle.properties`.
val useAsciiBuildDir = (project.findProperty("nightshift.launcher.asciiBuildDir") as String?)
    ?.toBoolean() ?: true
if (useAsciiBuildDir && org.gradle.internal.os.OperatingSystem.current().isWindows) {
    val temp = System.getenv("TEMP") ?: System.getenv("TMP") ?: "C:/Temp"
    layout.buildDirectory.set(file("$temp/nightshift-launcher/launcher-client/build"))
}

dependencies {
    implementation(project(":launcher-shared"))

    // Compose Desktop UI
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // HTTP client (Ktor)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Native interop & hardware identification
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.oshi.core)

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)

    // Test
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.assertions.core)
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "fun.nightshift.launcher.client.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "NightShift Launcher"
            packageVersion = "1.0.0"
            description = "NightShift Client Beta launcher"
            vendor = "NightShift"

            // Explicit JDK modules so the createRuntimeImage step doesn't have
            // to invoke `jdeps` (which fails on Windows paths containing
            // non-ASCII characters).
            modules(
                "java.base",
                "java.desktop",
                "java.instrument",
                "java.logging",
                "java.management",
                "java.naming",
                "java.net.http",
                "java.prefs",
                "java.scripting",
                "java.security.jgss",
                "java.sql",
                "java.xml",
                "jdk.crypto.cryptoki",
                "jdk.crypto.ec",
                "jdk.unsupported",
                "jdk.unsupported.desktop",
            )

            windows {
                menuGroup = "NightShift"
                upgradeUuid = "9F1B6C3A-4D2E-4F0B-B7E9-2C5D6A8E1F44"
            }
        }
    }
}

/**
 * Refreshes the launcher's bundled mod jars before processResources runs.
 *
 *  * NightShift cheat   — pulled from `RichClient/build/libs/` (this is the
 *    file Gradle builds in the cheat sub-project; we copy it so the launcher
 *    always ships the freshest cheat after a `./gradlew :build` round-trip).
 *  * Fabric API + Baritone — read from the workspace root. They are vendored
 *    rather than downloaded at runtime: the dependency versions track
 *    Minecraft 1.21.4 exactly and we don't want a launcher boot blocked on
 *    the Fabric mirror being slow.
 *
 * Missing source files are skipped silently — re-running this task with
 * only one or two jars present updates whatever is available without
 * forcing the dev to rebuild everything.
 */
val embedNightShiftMod by tasks.registering(Copy::class) {
    val workspaceRoot = rootProject.layout.projectDirectory.dir("..").asFile
    val sources = listOf(
        // Built artefact from the cheat sub-project.
        workspaceRoot.resolve("RichClient/build/libs/NightShift Client Recode 2.7.jar"),
        // Vendored runtime dependencies.
        workspaceRoot.resolve("fabric-api-0.119.4-1.21.4.jar"),
        workspaceRoot.resolve("baritone-api-fabric-1.13.1.jar"),
    )
    val present = sources.filter { it.exists() }
    onlyIf { present.isNotEmpty() }
    from(present)
    into(layout.projectDirectory.dir("src/main/resources/mods"))
    doFirst {
        for (src in sources) {
            if (src.exists()) {
                println("[embedMods] +${src.name} (${src.length() / 1024} KiB)")
            } else {
                println("[embedMods] missing ${src.name} — skipped")
            }
        }
    }
}

tasks.named("processResources") {
    dependsOn(embedNightShiftMod)
}

// ---------------------------------------------------------------------------
// Light obfuscation
// ---------------------------------------------------------------------------
//
// What it does:
//  * Strips local-variable debug tables (LocalVariableTable, LocalVariableTypeTable).
//  * Strips line-number tables (LineNumberTable).
//  * Strips SourceFile / SourceDebugExtension attributes.
//  * Renames every JVM-visible local variable slot to `var<index>`.
//
// What it deliberately does NOT do:
//  * Class / method / field renaming. Compose `LayoutNode`, `Recomposer` and
//    kotlinx-serialization `@Serializable` companions are looked up by name
//    via reflection, so renaming them would break the launcher in subtle
//    ways at runtime (UI not recomposing, JSON parse errors).
//  * String encryption. Most decompilers still surface the cleartext anyway,
//    and we ship plenty of human-readable strings (URLs, log messages) that
//    are needed at runtime.
//  * Control-flow obfuscation. Cheap to apply with ASM, but Compose code
//    paths are heavily branchy and any mistake here regresses UI behaviour.
//
// Net effect: a casual decompiler shows valid Kotlin/Java with mangled
// `var0/var1/...` locals and no source-line markers. Stack traces lose
// line numbers but keep class+method names — fine for our purpose because
// crash reports already include the cleartext logback log alongside.
//
// The task runs as the very last step of the `jar` task and rewrites the
// produced archive in place, so every downstream consumer (Compose
// `createDistributable`, `embedHashes`, etc.) picks up the obfuscated jar
// automatically.
val obfuscateLauncherJar by tasks.registering {
    description = "Strips debug info from the launcher jar (no class rename)."
    group = "build"
    dependsOn(tasks.named("jar"))

    val jarTask = tasks.named<Jar>("jar")
    val targetJar = jarTask.flatMap { it.archiveFile }
    inputs.file(targetJar)
    outputs.file(targetJar)

    doLast {
        val jar = targetJar.get().asFile
        if (!jar.exists()) {
            logger.warn("[obfuscate] {} does not exist, skipping", jar)
            return@doLast
        }
        val tmp = File(jar.parentFile, jar.nameWithoutExtension + ".obf.jar")
        var rewritten = 0
        var passthrough = 0
        ZipFile(jar).use { input ->
            ZipOutputStream(tmp.outputStream().buffered()).use { output ->
                val entries = input.entries().toList().sortedBy { it.name }
                for (entry in entries) {
                    val name = entry.name
                    val bytes = input.getInputStream(entry).use { it.readBytes() }
                    val out = if (name.endsWith(".class") && shouldObfuscate(name)) {
                        rewritten++
                        rewriteClass(bytes)
                    } else {
                        passthrough++
                        bytes
                    }
                    val newEntry = ZipEntry(name).apply {
                        // Reset the timestamps so consecutive builds give
                        // a deterministic byte-identical jar; useful if we
                        // ever sign the artefact.
                        time = 0L
                    }
                    output.putNextEntry(newEntry)
                    output.write(out)
                    output.closeEntry()
                }
            }
        }
        if (!jar.delete()) {
            throw GradleException("Could not delete ${jar.absolutePath} before replacing")
        }
        if (!tmp.renameTo(jar)) {
            throw GradleException("Could not rename ${tmp.absolutePath} to ${jar.absolutePath}")
        }
        logger.lifecycle("[obfuscate] {} classes rewritten, {} passthrough", rewritten, passthrough)
    }
}

/**
 * Decide which entries we touch. We strip debug info for everything we
 * shipped ourselves; META-INF / kotlin-metadata / module-info are
 * deliberately left intact because they participate in classpath wiring.
 */
fun shouldObfuscate(entryName: String): Boolean {
    if (entryName.startsWith("META-INF/")) return false
    if (entryName == "module-info.class") return false
    return entryName.endsWith(".class")
}

/**
 * Strips debug attributes via ASM.
 *
 * ClassWriter.COMPUTE_MAXS is mandatory because the rewrite removes
 * LocalVariableTable entries which can change the verification view of
 * the method — Java 21 byte-code verifier is strict and refuses any
 * inconsistency.
 */
fun rewriteClass(bytes: ByteArray): ByteArray {
    val reader = org.objectweb.asm.ClassReader(bytes)
    val writer = org.objectweb.asm.ClassWriter(reader, org.objectweb.asm.ClassWriter.COMPUTE_MAXS)
    val stripFlags = org.objectweb.asm.ClassReader.SKIP_DEBUG
    reader.accept(
        DebugStripperClassVisitor(writer),
        stripFlags,
    )
    return writer.toByteArray()
}

/**
 * Visitor that drops `SourceFile`, `SourceDebugExtension`, line numbers
 * and local-variable tables. We pair this with `ClassReader.SKIP_DEBUG`
 * for belt-and-braces: the reader skips most of it, the visitor catches
 * any surviving annotations.
 */
private class DebugStripperClassVisitor(
    delegate: org.objectweb.asm.ClassVisitor,
) : org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9, delegate) {

    override fun visitSource(source: String?, debug: String?) {
        // Drop both — pretend we never knew the .kt file name.
        super.visitSource(null, null)
    }

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?,
    ): org.objectweb.asm.MethodVisitor? {
        val mv = super.visitMethod(access, name, descriptor, signature, exceptions) ?: return null
        return object : org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9, mv) {
            override fun visitLineNumber(line: Int, start: org.objectweb.asm.Label?) {
                // No-op: don't propagate the line number to the writer.
            }

            override fun visitLocalVariable(
                name: String?,
                descriptor: String?,
                signature: String?,
                start: org.objectweb.asm.Label?,
                end: org.objectweb.asm.Label?,
                index: Int,
            ) {
                // No-op: drop the entire LocalVariableTable entry. The JVM
                // doesn't need it; only debuggers / decompilers do.
            }
        }
    }
}

// Hook obfuscation into the standard `jar` lifecycle so any task depending
// on `jar` (createDistributable, processResources, etc.) sees the
// obfuscated archive automatically.
tasks.named("jar") {
    finalizedBy(obfuscateLauncherJar)
}

// Compose Desktop's bundling tasks pull our jar into the runtime image; we
// must declare the obfuscation step as an explicit dependency so Gradle's
// task-graph validation accepts the in-place rewrite. Wiring it on
// `prepareAppResources` covers `createDistributable`, `packageExe` and
// the dev `run` flow in one shot.
afterEvaluate {
    tasks.matching {
        it.name == "prepareAppResources" ||
            it.name.startsWith("createDistributable") ||
            it.name.startsWith("packageDistributionForCurrentOS") ||
            it.name.startsWith("packageExe") ||
            it.name.startsWith("packageReleaseExe") ||
            it.name == "run" ||
            it.name == "runRelease"
    }.configureEach {
        dependsOn(obfuscateLauncherJar)
    }
}

/**
 * Convenience task: builds the portable application image (no installer)
 * and copies the whole folder next to the project root. Inside the folder
 * users get the runnable `NightShift Launcher.exe` plus the bundled JRE
 * and native libs — no admin rights required, just a double-click.
 */
tasks.register<Copy>("copyLauncherExeToRoot") {
    dependsOn("createDistributable")
    val imageDir = layout.buildDirectory.dir("compose/binaries/main/app")
    from(imageDir)
    into(rootProject.layout.projectDirectory.dir("dist"))
}
