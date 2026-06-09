package `fun`.nightshift.launcher.backend.routes

import `fun`.nightshift.launcher.backend.db.ActivationKeyRepository
import `fun`.nightshift.launcher.backend.db.dbQuery
import `fun`.nightshift.launcher.shared.dto.ApiResponse
import `fun`.nightshift.launcher.shared.dto.ModManifestEntry
import `fun`.nightshift.launcher.shared.dto.ModManifestResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.request.receiveStream
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Instant

private val log = LoggerFactory.getLogger("fun.nightshift.launcher.backend.routes.ModRoutes")

/**
 * `/mod/...` routes for over-the-air mod distribution.
 *
 * Files live in [modsDir] (default `<userHome>/.nightshift-backend/mods/`).
 * Each file is one Fabric mod jar served verbatim; the manifest is computed
 * on the fly by listing the directory and hashing each entry. We deliberately
 * avoid a separate metadata file so the operator can drop a new jar in and
 * the next manifest fetch picks it up — no DB migration, no JSON edit.
 *
 * **Routes:**
 *  * `GET  /mod/manifest`         — list all known mods with sha256.
 *  * `GET  /mod/download/{name}`  — stream raw jar bytes.
 *  * `POST /admin/mod/upload/{name}` — replace one mod (admin-token guarded).
 *
 * Admin upload requires header `X-Admin-Token` matching the `ADMIN_TOKEN`
 * env var. When `ADMIN_TOKEN` is empty (dev), the upload route is left
 * unprotected so iteration is fast.
 */
fun Route.modRoutes(modsDir: Path) {
    Files.createDirectories(modsDir)

    route("/mod") {
        post("/validate") {
            val req = call.receive<ModValidateRequest>()
            if (req.keyValue.isBlank() || req.hwid.isBlank()) {
                return@post call.respondError(
                    HttpStatusCode.BadRequest, "validation_failed",
                    "keyValue and hwid are required",
                )
            }
            val now = Instant.now()
            val result = dbQuery {
                val key = ActivationKeyRepository.findByValue(req.keyValue.trim())
                    ?: return@dbQuery ModValidateResult.NotFound
                when (key.status) {
                    "unused" -> {
                        val expiresAt = when (val t = key.keyType.lowercase()) {
                            "day" -> now.plus(java.time.Duration.ofDays(1))
                            "week" -> now.plus(java.time.Duration.ofDays(7))
                            "month" -> now.plus(java.time.Duration.ofDays(30))
                            "lifetime" -> null
                            else -> null
                        }
                        ActivationKeyRepository.activate(
                            keyId = key.id,
                            accountId = null,
                            hwid = req.hwid,
                            activatedAt = now,
                            expiresAt = expiresAt,
                        )
                        ModValidateResult.Valid
                    }
                    "active" -> {
                        if (key.hwid != req.hwid) {
                            ModValidateResult.Invalid("Key is bound to a different HWID")
                        } else if (key.expiresAt != null && key.expiresAt.isBefore(now)) {
                            ActivationKeyRepository.markExpired(key.id)
                            ModValidateResult.Invalid("Key has expired")
                        } else {
                            ModValidateResult.Valid
                        }
                    }
                    "expired" -> ModValidateResult.Invalid("Key has expired")
                    else -> ModValidateResult.Invalid("Key is not active")
                }
            }
            when (result) {
                is ModValidateResult.NotFound ->
                    call.respondError(HttpStatusCode.NotFound, "key_not_found", "Key not found")
                is ModValidateResult.Invalid ->
                    call.respondError(HttpStatusCode.Forbidden, "key_invalid", result.reason)
                is ModValidateResult.Valid ->
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = mapOf("status" to "active")))
            }
        }

        get("/manifest") {
            val entries = listMods(modsDir).map { (fileName, bytes) ->
                ModManifestEntry(
                    fileName = fileName,
                    sha256 = sha256(bytes),
                    sizeBytes = bytes.size.toLong(),
                    version = parseVersionFromName(fileName),
                    downloadUrl = "/mod/download/$fileName",
                )
            }
            call.respond(
                HttpStatusCode.OK,
                ApiResponse(success = true, data = ModManifestResponse(entries)),
            )
        }

        get("/download/{name}") {
            val name = call.parameters["name"]
            if (name.isNullOrBlank() || name.contains("..") || name.contains("/") || name.contains("\\")) {
                return@get call.respondError(
                    HttpStatusCode.BadRequest, "validation_failed",
                    "Mod name must be a single path segment without separators",
                )
            }
            val target = modsDir.resolve(name)
            if (!Files.exists(target) || !Files.isRegularFile(target)) {
                return@get call.respondError(HttpStatusCode.NotFound, "mod_not_found", "Mod '$name' not found")
            }
            // Stream the file rather than slurping it into a ByteArray.
            //
            // We previously called Files.readAllBytes() which materialised
            // the whole jar in JVM heap before sending. With multiple mods
            // (and the cheat jar at ~25 MiB) that easily blew the default
            // 512 MiB heap and caused Netty to truncate the response in
            // mid-flight — the launcher then saw a SHA-256 mismatch and
            // fell back to embedded mods, which silently masked the bug.
            //
            // respondOutputStream streams the file in fixed-size chunks
            // through Netty's writer, keeping memory usage bounded
            // regardless of jar size.
            val size = Files.size(target)
            call.response.header(io.ktor.http.HttpHeaders.ContentLength, size.toString())
            call.respondOutputStream(ContentType.Application.OctetStream, HttpStatusCode.OK) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    Files.newInputStream(target).use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            this@respondOutputStream.write(buf, 0, n)
                        }
                    }
                }
            }
        }
    }

    // Admin routes — require X-Admin-Token when ADMIN_TOKEN is set.
    route("/admin/mod") {
        post("/upload/{name}") {
            val configured = System.getenv("ADMIN_TOKEN")?.takeIf { it.isNotBlank() }
            if (configured != null) {
                val provided = call.request.headers["X-Admin-Token"]
                if (provided != configured) {
                    return@post call.respondError(
                        HttpStatusCode.Unauthorized, "unauthorized",
                        "Missing or invalid X-Admin-Token header",
                    )
                }
            }

            val name = call.parameters["name"]
            if (name.isNullOrBlank() || name.contains("..") || name.contains("/") || name.contains("\\") ||
                !name.endsWith(".jar")) {
                return@post call.respondError(
                    HttpStatusCode.BadRequest, "validation_failed",
                    "Mod name must end with .jar and contain no path separators",
                )
            }

            val target = modsDir.resolve(name)
            val tmp = modsDir.resolve("$name.tmp")
            // Streaming the request body uses BlockingAdapter under the hood,
            // which parks the calling thread. Netty's event loop forbids that
            // (UnsupportedOperationException "Parking is prohibited"), so we
            // hop onto Dispatchers.IO for the actual byte pump.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                Files.newOutputStream(tmp).use { out ->
                    call.receiveStream().use { src ->
                        src.copyTo(out)
                    }
                }
                Files.move(
                    tmp,
                    target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
            }
            val size = Files.size(target)
            log.info("[admin] uploaded mod '{}' ({} KiB)", name, size / 1024)
            call.respond(
                HttpStatusCode.OK,
                ApiResponse(success = true, data = mapOf("fileName" to name, "sizeBytes" to size.toString())),
            )
        }
    }
}

private fun listMods(modsDir: Path): List<Pair<String, ByteArray>> {
    if (!Files.isDirectory(modsDir)) return emptyList()
    return Files.list(modsDir).use { stream ->
        stream
            .filter { Files.isRegularFile(it) }
            .filter { it.fileName.toString().endsWith(".jar") }
            .map { it.fileName.toString() to Files.readAllBytes(it) }
            .sorted { a, b -> a.first.compareTo(b.first) }
            .toList()
    }
}

private fun sha256(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return buildString(digest.size * 2) {
        for (b in digest) append(b.toUByte().toString(16).padStart(2, '0'))
    }
}

/**
 * Best-effort version extraction from the file name. Examples:
 * `NightShift Client Recode 2.7.jar` → `2.7`
 * `fabric-api-0.119.4-1.21.4.jar`  → `0.119.4-1.21.4`
 * `random.jar`                     → `unknown`
 */
private fun parseVersionFromName(name: String): String {
    val withoutExt = name.removeSuffix(".jar")
    val match = Regex("[-\\s](\\d.*)$").find(withoutExt)
    return match?.groupValues?.get(1) ?: "unknown"
}

@kotlinx.serialization.Serializable
private data class ModValidateRequest(
    val keyValue: String,
    val hwid: String,
)

private sealed class ModValidateResult {
    data object Valid : ModValidateResult()
    data object NotFound : ModValidateResult()
    data class Invalid(val reason: String) : ModValidateResult()
}
