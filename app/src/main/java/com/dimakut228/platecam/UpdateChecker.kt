package com.dimakut228.platecam

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class LatestRelease(
    val build: Long,
    val apkUrl: String
)

/**
 * Проверка обновлений через GitHub Releases.
 *
 * Релиз публикуется в фиксированный тег `latest`, номер сборки (он же versionCode)
 * содержится в заголовке и описании релиза вида «PlateCam (build N)».
 * Сетевой код выполняется только здесь и только при проверке обновлений.
 */
object UpdateChecker {

    const val API_URL = "https://api.github.com/repos/Dimakut228/PlateCam/releases/latest"

    private val BUILD_PATTERN = Regex("build[^0-9]{0,10}(\\d+)", RegexOption.IGNORE_CASE)
    private val APK_URL_PATTERN = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]*PlateCam\\.apk)\"")

    /**
     * Чистая функция сравнения версий; покрывается unit-тестами.
     * [latest] больше [current] только если номер сборки выше.
     */
    fun isNewer(latest: Long, current: Long): Boolean = latest > current

    /**
     * Разбирает JSON релиза GitHub Releases API: номер сборки и ссылку на APK.
     * Возвращает null, если данные не распознаны.
     */
    fun parseRelease(json: String): LatestRelease? {
        val build = BUILD_PATTERN.find(json)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val apkUrl = APK_URL_PATTERN.find(json)?.groupValues?.get(1) ?: return null
        return LatestRelease(build, apkUrl)
    }

    /** Запрашивает описание последнего релиза. Работает в фоновом потоке. */
    fun fetchLatest(): LatestRelease? {
        val connection = URL(API_URL).openConnection() as? HttpURLConnection ?: return null
        return try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "PlateCam-Updater")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).readText()
            parseRelease(body)
        } catch (t: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Скачивает APK во внутреннее хранилище. Работает в фоновом потоке.
     * Возвращает файл или null при ошибке.
     */
    fun download(context: Context, release: LatestRelease): File? {
        val dir = File(context.filesDir, "updates")
        dir.mkdirs()
        val target = File(dir, "PlateCam-${release.build}.apk")
        val tmp = File(dir, "PlateCam-${release.build}.apk.tmp")
        val connection = URL(release.apkUrl).openConnection() as? HttpURLConnection ?: return null
        return try {
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "PlateCam-Updater")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (tmp.length() <= 0L) {
                tmp.delete()
                return null
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) return null
            target
        } catch (t: Throwable) {
            tmp.delete()
            null
        } finally {
            connection.disconnect()
        }
    }

    /** Лучший уже скачанный APK (по номеру сборки), если он новее установленного. */
    fun downloadedUpdate(context: Context, currentBuild: Long): File? {
        val dir = File(context.filesDir, "updates")
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("PlateCam-") && f.name.endsWith(".apk") }
            ?: return null
        val best = files.mapNotNull { file ->
            val build = file.name.removePrefix("PlateCam-").removeSuffix(".apk").toLongOrNull()
            when {
                build == null -> { file.delete(); null }
                build > currentBuild -> build to file
                else -> { file.delete(); null }
            }
        }.maxByOrNull { it.first } ?: return null
        return best.second
    }

    /** Intent установки APK через FileProvider. */
    fun installIntent(context: Context, apk: File): Intent? {
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk
            )
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (t: Throwable) {
            null
        }
    }
}
