plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Same ASCII-buildDir workaround as launcher-client: Gradle test workers
// build their classpath via @argfile, which hits an encoding bug when the
// project path contains Cyrillic characters (Gradle bug, see launcher-client).
val useAsciiBuildDir = (project.findProperty("nightshift.launcher.asciiBuildDir") as String?)
    ?.toBoolean() ?: true
if (useAsciiBuildDir && org.gradle.internal.os.OperatingSystem.current().isWindows) {
    val temp = System.getenv("TEMP") ?: System.getenv("TMP") ?: "C:/Temp"
    layout.buildDirectory.set(file("$temp/nightshift-launcher/launcher-shared/build"))
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.assertions.core)
}

tasks.test {
    useJUnitPlatform()
}
