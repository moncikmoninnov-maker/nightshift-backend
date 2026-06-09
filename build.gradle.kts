// Root build script for the NightShift Launcher multi-module project.
// Module-specific configuration lives in each module's build.gradle.kts.

plugins {
    // Applied false here so they can be configured per-module without forcing the
    // version onto every subproject classpath.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose) apply false
}

allprojects {
    group = "fun.nightshift.launcher"
    version = "1.0.0"
}
