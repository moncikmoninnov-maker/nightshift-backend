package `fun`.nightshift.launcher.publisher

import `fun`.nightshift.launcher.shared.dto.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * NightShift `Mod_Publish_CLI` — uploads a single mod-jar to the running
 * launcher backend via `POST /admin/mod/upload/{name}`.
 *
 * Usage:
 * ```
 * nightshift-publish --file <path> --name <fileName>
 *                    [--backend-url <url>] [--admin-token <token>]
 * ```
 *
 * Defaults:
 *  - `--backend-url` falls back to env `NIGHTSHIFT_BACKEND_URL` and finally
 *    `http://127.0.0.1:8080`.
 *  - `--admin-token` falls back to env `NIGHTSHIFT_ADMIN_TOKEN`.
 *
 * Exit codes (matches design.md):
 *  - 0  — success (HTTP 200)
 *  - 2  — HTTP 4xx/5xx; response body printed to stderr
 *  - 3  — network / timeout error
 *  - 4  — local file not readable
 *  - 64 — invalid arguments (usage error)
 *
 * The CLI never echoes `--admin-token` to stdout/stderr, even on error.
 */
fun main(args: Array<String>) {
    val parsed = parseArgs(args) ?: run {
        printUsage()
        exitProcess(EXIT_USAGE)
    }

    val file = parsed.filePath
    if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
        System.err.println("nightshift-publish: cannot read file: $file")
        exitProcess(EXIT_FILE_UNREADABLE)
    }

    val backendUrl = parsed.backendUrl.trimEnd('/')
    val nameSegment = URLEncoder
        .encode(parsed.modName, StandardCharsets.UTF_8)
        // Ktor's path interpreter treats `+` as space; `%20` is what we want
        // for spaces inside a single path segment.
        .replace("+", "%20")
    val targetUrl = "$backendUrl/admin/mod/upload/$nameSegment"

    runBlocking {
        HttpClient(CIO) {
            install(ContentNegotiation) { json(JSON) }
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 5 * 60_000  // big mod jars deserve patience
                socketTimeoutMillis = 5 * 60_000
            }
        }.use { client ->
            val bytes = try {
                Files.readAllBytes(file)
            } catch (t: IOException) {
                System.err.println("nightshift-publish: cannot read file: ${file}: ${t.message}")
                exitProcess(EXIT_FILE_UNREADABLE)
            }

            try {
                val response = client.post(targetUrl) {
                    parsed.adminToken?.let { header("X-Admin-Token", it) }
                    header("X-Client-Version", PUBLISHER_VERSION)
                    contentType(ContentType.Application.OctetStream)
                    setBody(bytes)
                }
                val status = response.status.value
                val text = response.bodyAsText()

                if (status == 200) {
                    val (returnedName, sizeBytes) = parseUploadResponse(text)
                    println("uploaded $returnedName sizeBytes=$sizeBytes")
                    exitProcess(EXIT_OK)
                } else {
                    System.err.println("nightshift-publish: HTTP $status: $text")
                    exitProcess(EXIT_HTTP_ERROR)
                }
            } catch (t: IOException) {
                System.err.println("nightshift-publish: network error: ${t.javaClass.simpleName}: ${t.message}")
                exitProcess(EXIT_NETWORK)
            } catch (t: io.ktor.client.plugins.HttpRequestTimeoutException) {
                System.err.println("nightshift-publish: timeout: ${t.message}")
                exitProcess(EXIT_NETWORK)
            }
        }
    }
}

private const val EXIT_OK = 0
private const val EXIT_HTTP_ERROR = 2
private const val EXIT_NETWORK = 3
private const val EXIT_FILE_UNREADABLE = 4
private const val EXIT_USAGE = 64

private const val PUBLISHER_VERSION = "1.0.0"

private val JSON = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private data class CliArgs(
    val filePath: Path,
    val modName: String,
    val backendUrl: String,
    val adminToken: String?,
)

/**
 * Hand-rolled argv parser. We have only four flags; pulling in kotlinx-cli
 * (and an extra ~600 KiB on the fat-jar) is overkill.
 *
 * Returns `null` if a required flag/argument is missing or malformed; the
 * caller renders the usage block and exits with [EXIT_USAGE].
 */
private fun parseArgs(args: Array<String>): CliArgs? {
    var filePath: String? = null
    var modName: String? = null
    var backendUrl: String? = null
    var adminToken: String? = null

    var i = 0
    while (i < args.size) {
        when (val flag = args[i]) {
            "--file" -> {
                filePath = args.getOrNull(i + 1) ?: return null
                i += 2
            }
            "--name" -> {
                modName = args.getOrNull(i + 1) ?: return null
                i += 2
            }
            "--backend-url" -> {
                backendUrl = args.getOrNull(i + 1) ?: return null
                i += 2
            }
            "--admin-token" -> {
                adminToken = args.getOrNull(i + 1) ?: return null
                i += 2
            }
            "-h", "--help" -> return null
            else -> {
                System.err.println("nightshift-publish: unknown argument '$flag'")
                return null
            }
        }
    }

    if (filePath == null || modName == null) return null
    if (!modName.endsWith(".jar")) {
        System.err.println("nightshift-publish: --name must end with .jar")
        return null
    }

    val resolvedBackendUrl = backendUrl
        ?: System.getenv("NIGHTSHIFT_BACKEND_URL")?.takeIf { it.isNotBlank() }
        ?: "http://127.0.0.1:8080"

    val resolvedAdminToken = adminToken
        ?: System.getenv("NIGHTSHIFT_ADMIN_TOKEN")?.takeIf { it.isNotBlank() }

    return CliArgs(
        filePath = Paths.get(filePath),
        modName = modName,
        backendUrl = resolvedBackendUrl,
        adminToken = resolvedAdminToken,
    )
}

private fun printUsage() {
    System.err.println(
        """
        |nightshift-publish — publish a mod-jar to the NightShift launcher backend.
        |
        |Usage:
        |  nightshift-publish --file <path> --name <fileName>
        |                     [--backend-url <url>] [--admin-token <token>]
        |
        |Required:
        |  --file         Path to the local mod jar.
        |  --name         Target file name on the server (must end with .jar).
        |
        |Optional:
        |  --backend-url  Backend base URL.
        |                 Default: NIGHTSHIFT_BACKEND_URL env or http://127.0.0.1:8080
        |  --admin-token  Value of the X-Admin-Token header.
        |                 Default: NIGHTSHIFT_ADMIN_TOKEN env (omitted if unset).
        |
        |Exit codes:
        |  0  success
        |  2  HTTP error from backend (body printed to stderr)
        |  3  network / timeout error
        |  4  local file not readable
        |  64 invalid arguments
        """.trimMargin()
    )
}

/**
 * Pulls `fileName` and `sizeBytes` out of the upload response without
 * leaking the full payload to stdout. The backend always wraps successful
 * responses in `ApiResponse<T>`; we deliberately avoid binding to a
 * specific generic type so a future backend extension that adds fields
 * doesn't break the CLI.
 */
private fun parseUploadResponse(body: String): Pair<String, String> {
    return try {
        val root = JSON.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonObject ?: return "?" to "?"
        val fileName = data["fileName"]?.jsonPrimitive?.content ?: "?"
        val sizeBytes = data["sizeBytes"]?.jsonPrimitive?.content ?: "?"
        fileName to sizeBytes
    } catch (_: Throwable) {
        "?" to "?"
    }
}
