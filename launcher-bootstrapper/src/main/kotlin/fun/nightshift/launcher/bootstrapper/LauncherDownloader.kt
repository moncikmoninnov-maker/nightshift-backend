package `fun`.nightshift.launcher.bootstrapper

import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class LauncherDownloader(
    private val backendUrl: String,
    private val installDir: Path,
) {
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    @Volatile
    var onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    @Volatile
    var onStatus: ((String) -> Unit)? = null
    @Volatile
    var onError: ((String) -> Unit)? = null

    fun run(): Path? {
        return try {
            onStatus?.invoke("Подключение к серверу...")
            val expectedSha256 = fetchChecksum()
            onStatus?.invoke("Скачивание лаунчера...")
            val zipPath = downloadLauncher()
            onStatus?.invoke("Проверка целостности...")
            verifySha256(zipPath, expectedSha256)
            onStatus?.invoke("Установка...")
            extractZip(zipPath)
            onStatus?.invoke("Запуск лаунчера...")
            zipPath.toFile().delete()
            findLauncherExe()
        } catch (t: Throwable) {
            onError?.invoke("Ошибка: ")
            null
        }
    }

    private fun fetchChecksum(): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("/launcher/checksum"))
            .timeout(java.time.Duration.ofSeconds(10)).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw RuntimeException("Сервер вернул ")
        }
        return response.body().trim()
    }

    private fun downloadLauncher(): Path {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("/launcher/download"))
            .timeout(java.time.Duration.ofMinutes(30)).GET().build()
        val tmpFile = installDir.resolve("launcher-download.tmp")
        Files.createDirectories(installDir)
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        try {
            if (response.statusCode() != 200) {
                throw RuntimeException("Сервер вернул ")
            }
            val total = try {
                response.headers().firstValue("Content-Length").get().toLong()
            } catch (_: Exception) { -1L }
            val body: InputStream = response.body()
            val buf = ByteArray(64 * 1024)
            var downloaded = 0L
            Files.newOutputStream(tmpFile).use { out ->
                while (true) {
                    val n = body.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    downloaded += n
                    if (total > 0) onProgress?.invoke(downloaded, total)
                }
            }
        } finally {
            response.body().close()
        }
        return tmpFile
    }

    private fun verifySha256(file: Path, expected: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        val fileBytes = Files.readAllBytes(file)
        val hash = digest.digest(fileBytes)
        val actual = buildString(hash.size * 2) {
            for (b in hash) append(b.toUByte().toString(16).padStart(2, '0'))
        }
        if (!actual.equals(expected, ignoreCase = true)) {
            throw SecurityException(
                "Контрольная сумма не совпадает. Ожидалось: , получено: "
            )
        }
    }

    private fun extractZip(zipPath: Path) {
        ZipInputStream(Files.newInputStream(zipPath)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val target = installDir.resolve(entry.name)
                    Files.createDirectories(target.parent)
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun findLauncherExe(): Path? {
        val exeName = "NightShift Launcher.exe"
        return Files.walk(installDir, 3)
            .filter { Files.isRegularFile(it) && it.fileName.toString().equals(exeName, ignoreCase = true) }
            .findFirst()
            .orElse(null)
    }
}