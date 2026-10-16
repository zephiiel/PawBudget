package com.zephiiel.pawbudget.nativeapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import java.util.zip.ZipFile

data class NativeUpdate(val version: String, val downloadUrl: String)

data class UpdateCheckResult(val message: String, val update: NativeUpdate? = null)

data class UpdateDownloadProgress(val downloadedBytes: Long, val totalBytes: Long, val percent: Int?)

private const val MaxApkBytes = 150L * 1024L * 1024L
private const val GitHubReleasesApi = "https://api.github.com/repos/zephiiel/PawBudget/releases/latest"

suspend fun checkForNativeUpdate(installedVersion: String): UpdateCheckResult =
    withContext(Dispatchers.IO) {
        val installed = parseVersion(installedVersion)
            ?: throw IOException("The installed version ($installedVersion) is not in a supported numeric format.")
        val connection = URL(GitHubReleasesApi).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "PawBudget-Native-Android")
        try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                return@withContext UpdateCheckResult("No public GitHub release was found.")
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub returned HTTP $responseCode.")
            }
            val release = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val tag = release.optString("tag_name")
            val releaseVersion = parseVersion(tag)
                ?: throw IOException("The latest release tag must use a numeric version such as v1.2.3.")
            if (compareVersions(releaseVersion, installed) <= 0) {
                return@withContext UpdateCheckResult("This app is up to date.")
            }
            val nativeApk = release.optJSONArray("assets")
                ?.let { assets -> (0 until assets.length()).mapNotNull(assets::optJSONObject) }
                ?.firstOrNull { asset ->
                    asset.optString("name").lowercase(Locale.ROOT).let {
                        it.startsWith("nativepaw-") && it.endsWith(".apk")
                    }
                }
            val downloadUrl = nativeApk?.optString("browser_download_url")
                ?.takeIf(String::isNotBlank)
                ?: return@withContext UpdateCheckResult(
                    "Version $tag is available, but its release has no NativePaw APK asset.",
                )
            if (!isPawBudgetReleaseAsset(downloadUrl)) {
                throw IOException("The update URL is not a PawBudget GitHub release asset.")
            }
            UpdateCheckResult("Version $tag is available.", NativeUpdate(tag, downloadUrl))
        } finally {
            connection.disconnect()
        }
    }

suspend fun downloadAndOpenNativeUpdate(
    context: Context,
    update: NativeUpdate,
    onProgress: (UpdateDownloadProgress) -> Unit,
) = withContext(Dispatchers.IO) {
    if (!isPawBudgetReleaseAsset(update.downloadUrl)) {
        throw IOException("The APK URL is not a PawBudget GitHub release asset.")
    }
    val apkFile = File(context.cacheDir, "updates/pawbudget-native-update.apk")
    try {
        downloadApk(update.downloadUrl, apkFile, onProgress)
        ZipFile(apkFile).use { archive ->
            if (archive.getEntry("AndroidManifest.xml") == null) {
                throw IOException("The downloaded file is not a valid APK archive.")
            }
        }
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val installerIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(installerIntent)
        } catch (error: ActivityNotFoundException) {
            throw IOException("Could not open the Android package installer.", error)
        } catch (error: SecurityException) {
            throw IOException("Could not open the Android package installer.", error)
        }
    } catch (error: Exception) {
        apkFile.delete()
        throw error
    }
}

private fun downloadApk(
    downloadUrl: String,
    apkFile: File,
    onProgress: (UpdateDownloadProgress) -> Unit,
) {
    val directory = apkFile.parentFile
    if (directory == null || (!directory.isDirectory && !directory.mkdirs())) {
        throw IOException("Could not create the APK cache directory.")
    }
    val connection = URL(downloadUrl).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    connection.instanceFollowRedirects = true
    try {
        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("GitHub returned HTTP $responseCode.")
        }
        if (connection.url.protocol != "https") {
            throw IOException("The APK download did not remain on a secure connection.")
        }
        val expectedBytes = connection.contentLengthLong
        if (expectedBytes > MaxApkBytes) {
            throw IOException("The APK exceeds the 150 MB download limit.")
        }
        connection.inputStream.use { input ->
            apkFile.outputStream().buffered().use { output ->
                val buffer = ByteArray(8192)
                var downloaded = 0L
                var lastReported = 0L
                onProgress(UpdateDownloadProgress(0L, expectedBytes, percent(expectedBytes, 0L)))
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    downloaded += count
                    if (downloaded > MaxApkBytes) {
                        throw IOException("The APK exceeds the 150 MB download limit.")
                    }
                    output.write(buffer, 0, count)
                    if (downloaded - lastReported >= 256 * 1024 || (expectedBytes > 0 && downloaded >= expectedBytes)) {
                        onProgress(UpdateDownloadProgress(downloaded, expectedBytes, percent(expectedBytes, downloaded)))
                        lastReported = downloaded
                    }
                }
                if (downloaded == 0L || (expectedBytes > 0 && downloaded != expectedBytes)) {
                    throw IOException("The APK download was incomplete.")
                }
                onProgress(UpdateDownloadProgress(downloaded, expectedBytes, percent(expectedBytes, downloaded)))
            }
        }
    } finally {
        connection.disconnect()
    }
}

private fun percent(totalBytes: Long, downloadedBytes: Long): Int? =
    if (totalBytes > 0L) ((downloadedBytes * 100L / totalBytes).coerceAtMost(100L)).toInt() else null

private fun isPawBudgetReleaseAsset(value: String): Boolean {
    val uri = runCatching { URI(value) }.getOrNull() ?: return false
    return uri.scheme == "https" &&
        uri.host == "github.com" &&
        (uri.port == -1 || uri.port == 443) &&
        uri.userInfo == null &&
        uri.path?.startsWith("/zephiiel/PawBudget/releases/download/") == true
}

private fun parseVersion(value: String): List<Int>? {
    val match = Regex("^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?$").matchEntire(value) ?: return null
    return match.groupValues.drop(1).map { component ->
        component.ifEmpty { "0" }.toIntOrNull() ?: return null
    }
}

internal fun compareVersions(first: List<Int>, second: List<Int>): Int {
    for (index in 0..2) {
        val difference = first.getOrElse(index) { 0 }.compareTo(second.getOrElse(index) { 0 })
        if (difference != 0) return difference
    }
    return 0
}
