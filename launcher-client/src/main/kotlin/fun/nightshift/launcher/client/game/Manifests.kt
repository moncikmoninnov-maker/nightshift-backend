package `fun`.nightshift.launcher.client.game

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parsed shape of `versions/{id}.json` from the Mojang version manifest.
 * Only the fields used by [GameLauncher] are retained.
 */
internal data class VanillaVersion(
    val id: String,
    val clientUrl: String,
    val clientSha1: String,
    val libraries: List<MojangLibrary>,
    val gameArgs: List<String>,
    val assetsIndex: String,
    val assetsIndexUrl: String,
    val assetsIndexSha1: String,
) {
    companion object {
        fun parse(o: JsonObject): VanillaVersion {
            val id = o["id"]?.jsonPrimitive?.content ?: error("vanilla json missing 'id'")
            val downloads = o["downloads"]?.jsonObject ?: error("vanilla json missing 'downloads'")
            val client = downloads["client"]?.jsonObject ?: error("vanilla json missing 'client'")
            val clientUrl = client["url"]?.jsonPrimitive?.content ?: error("client.url missing")
            val clientSha1 = client["sha1"]?.jsonPrimitive?.content ?: error("client.sha1 missing")

            val libs = (o["libraries"] as? JsonArray)?.mapNotNull { MojangLibrary.parse(it.jsonObject) }
                ?: emptyList()

            val gameArgs = parseGameArgs(o["arguments"])
            val assetIndex = o["assetIndex"]?.jsonObject ?: error("assetIndex missing")
            val assets = assetIndex["id"]?.jsonPrimitive?.content ?: error("assetIndex.id missing")
            val assetsUrl = assetIndex["url"]?.jsonPrimitive?.content ?: error("assetIndex.url missing")
            val assetsSha = assetIndex["sha1"]?.jsonPrimitive?.content ?: error("assetIndex.sha1 missing")
            return VanillaVersion(id, clientUrl, clientSha1, libs, gameArgs, assets, assetsUrl, assetsSha)
        }

        /**
         * `arguments.game` may be a list of either plain strings or objects
         * with `rules` + `value`. We keep only the unconditional plain-string
         * tokens since the launcher already supplies the canonical MC args
         * from its own template.
         */
        private fun parseGameArgs(arguments: JsonElement?): List<String> {
            val o = (arguments as? JsonObject) ?: return emptyList()
            val arr = (o["game"] as? JsonArray) ?: return emptyList()
            return arr.mapNotNull { el ->
                if (el is kotlinx.serialization.json.JsonPrimitive) el.content else null
            }
        }
    }
}

/** Single library entry from the Mojang manifest. */
internal data class MojangLibrary(
    /** Maven-style relative path inside `libraries/`. */
    val path: String,
    val url: String,
    val sha1: String,
) {
    companion object {
        fun parse(o: JsonObject): MojangLibrary? {
            // Skip libs disallowed by OS rules (we only support Windows).
            if (!rulesAllow(o["rules"])) return null
            val downloads = o["downloads"]?.jsonObject ?: return null
            val artifact = downloads["artifact"]?.jsonObject ?: return null
            val path = artifact["path"]?.jsonPrimitive?.content ?: return null
            val url = artifact["url"]?.jsonPrimitive?.content ?: return null
            val sha = artifact["sha1"]?.jsonPrimitive?.content ?: return null
            return MojangLibrary(path, url, sha)
        }

        private fun rulesAllow(rules: JsonElement?): Boolean {
            val arr = (rules as? JsonArray) ?: return true
            // Default action is "allow"; rules narrow it.
            var allowed = true
            for (r in arr) {
                val ro = r.jsonObject
                val action = ro["action"]?.jsonPrimitive?.content
                val osName = ro["os"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                val match = osName == null || osName == "windows"
                when (action) {
                    "allow" -> if (!match) allowed = false
                    "disallow" -> if (match) allowed = false
                }
            }
            return allowed
        }
    }
}

/** Parsed shape of the Fabric `profile/json` document. */
internal data class FabricProfile(
    val profileId: String,
    val mainClass: String,
    val libraries: List<FabricLibrary>,
    val gameArgs: List<String>,
    val jvmArgs: List<String>,
) {
    companion object {
        fun parse(o: JsonObject): FabricProfile {
            val id = o["id"]?.jsonPrimitive?.content ?: error("Fabric profile missing 'id'")
            val mainClass = o["mainClass"]?.jsonPrimitive?.content
                ?: error("Fabric profile missing 'mainClass'")

            val libs = ((o["libraries"] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { el ->
                val lo = (el as? JsonObject) ?: return@mapNotNull null
                val name = lo["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val url = lo["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                FabricLibrary.fromMavenName(name, url)
            }

            val args = (o["arguments"] as? JsonObject)
            val gameArgs = ((args?.get("game") as? JsonArray) ?: JsonArray(emptyList()))
                .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            val jvmArgs = ((args?.get("jvm") as? JsonArray) ?: JsonArray(emptyList()))
                .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            return FabricProfile(id, mainClass, libs, gameArgs, jvmArgs)
        }
    }
}

internal data class FabricLibrary(
    /** Relative Maven path under `libraries/`. */
    val path: String,
    val url: String,
) {
    companion object {
        /**
         * Translates Maven coords (`group:artifact:version`) into a relative
         * jar path under `libraries/` matching Mojang/Fabric conventions.
         */
        fun fromMavenName(coords: String, repoBaseUrl: String): FabricLibrary {
            val parts = coords.split(':')
            require(parts.size >= 3) { "Invalid maven coords: $coords" }
            val (group, artifact, version) = Triple(parts[0], parts[1], parts[2])
            val groupPath = group.replace('.', '/')
            val classifier = if (parts.size >= 4) "-${parts[3]}" else ""
            val fileName = "$artifact-$version$classifier.jar"
            val relPath = "$groupPath/$artifact/$version/$fileName"
            // Some Fabric manifests give a *directory* URL ending with `/`, others
            // give a fully-formed file URL. Normalise to the file URL.
            val url = if (repoBaseUrl.endsWith(".jar")) repoBaseUrl
                      else repoBaseUrl.trimEnd('/') + "/$relPath"
            return FabricLibrary(relPath, url)
        }
    }
}
