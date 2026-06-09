rootProject.name = "nightshift-launcher"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
    }
}

include(":launcher-shared")
include(":launcher-client")
include(":launcher-backend")
include(":launcher-mod-publisher")
include(":launcher-bootstrapper")
