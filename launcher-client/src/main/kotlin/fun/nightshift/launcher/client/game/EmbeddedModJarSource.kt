package `fun`.nightshift.launcher.client.game

import org.slf4j.LoggerFactory

/**
 * [ModJarSource] backed by classpath resources under `/mods/`.
 *
 * The packaging pipeline copies every jar from
 * `launcher-client/src/main/resources/mods/` into the launcher fat-jar
 * (see the `embedNightShiftMod` Gradle task), so this source picks up
 * the NightShift cheat plus any mandatory dependencies that ship with
 * the launcher: today that is `fabric-api` and `baritone-api-fabric`.
 *
 * Listing classpath resources directly is awkward in JPMS, so the manifest
 * is hard-coded — adding a new bundled mod is a one-liner here. Returning
 * a missing jar surfaces a WARN; the launcher then continues with the rest
 * (e.g. Minecraft still starts even if the cheat jar got moved).
 */
class EmbeddedModJarSource(
    private val resourceNames: List<String> = DEFAULT_RESOURCES,
) : ModJarSource {

    override fun read(sessionToken: String): List<ModJar> {
        val out = ArrayList<ModJar>(resourceNames.size)
        for (name in resourceNames) {
            val resourcePath = "/mods/$name"
            val stream = javaClass.getResourceAsStream(resourcePath)
            if (stream == null) {
                log.warn("Mod jar not found at resource '{}'", resourcePath)
                continue
            }
            val bytes = stream.use { it.readAllBytes() }
            out += ModJar(fileName = name, bytes = bytes)
            log.info("Loaded embedded mod '{}' ({} KiB)", name, bytes.size / 1024)
        }
        return out
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmbeddedModJarSource::class.java)

        /**
         * Manifest of jars bundled with the launcher. Order matters only
         * for log readability — Fabric loads everything in `mods/` lazily
         * regardless of placement order.
         */
        val DEFAULT_RESOURCES: List<String> = listOf(
            // Fabric API — required by basically every Fabric mod, including
            // Baritone and most QoL mods we may add later.
            "fabric-api-0.119.4-1.21.4.jar",
            // Baritone — pathing/automation mod requested by users.
            "baritone-api-fabric-1.13.1.jar",
            // The cheat itself. Last so it appears at the top of the
            // Fabric mods list ordered by load time.
            "NightShift Client Recode 2.7.jar",
        )
    }
}
