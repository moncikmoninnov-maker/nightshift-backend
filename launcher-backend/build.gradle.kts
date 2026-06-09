plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("fun.nightshift.launcher.backend.ApplicationKt")
}

dependencies {
    implementation(project(":launcher-shared"))

    // Ktor server stack
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.request.validation)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.config.yaml)

    // Persistence
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.hikari)
    implementation(libs.postgresql)
    implementation("com.h2database:h2:2.2.224")
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    // Password hashing
    implementation(libs.argon2)

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
}
