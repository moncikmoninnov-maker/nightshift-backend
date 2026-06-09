plugins {
    kotlin("jvm")
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("fun.nightshift.launcher.bootstrapper.BootstrapperMainKt")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjdk-release=21")
    }
}

// Use installDist from the application plugin to create a distribution
// with all dependencies (Kotlin stdlib) bundled in lib/.
// jpackage then picks up the jar + lib/*.jar via --input.
tasks.named("installDist") {
    dependsOn(tasks.named("compileKotlin"), tasks.named("processResources"))
}

tasks.register<Copy>("copyBootstrapperToDist") {
    dependsOn("jar")
    from(layout.buildDirectory.dir("libs"))
    into(rootProject.layout.projectDirectory.dir("dist"))
    include("*.jar")
}

tasks.register("createMinimalRuntime") {
    description = "Builds a minimal JRE image for the bootstrapper"
    group = "distribution"
    dependsOn("jar")
    val jlink = file("${System.getProperty("java.home")}/bin/jlink")
    val modulePath = file("${System.getProperty("java.home")}/jmods")
    val outputDir = layout.buildDirectory.dir("minimal-runtime").get().asFile
    val jarFile = layout.buildDirectory.dir("libs").get().file("launcher-bootstrapper-${version}.jar")
    inputs.file(jarFile)
    outputs.dir(outputDir)
    doLast {
        outputDir.mkdirs()
        exec {
            commandLine(
                jlink.absolutePath,
                "--module-path", modulePath.absolutePath,
                "--add-modules", "java.base,java.desktop,java.net.http,jdk.crypto.cryptoki,jdk.unsupported",
                "--output", outputDir.absolutePath,
                "--strip-debug",
                "--no-header-files",
                "--no-man-pages",
                "--compress", "2",
            )
            isIgnoreExitValue = false
        }
    }
}
