package com.recondrive.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.recondrive.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * ReconDrive Tactical In-App Update Engine
 * Connects to GitHub Releases API (CharlesLennon/recondrive), audits semantic versions,
 * streams APK binaries with real-time progress, and invokes Android Package Installer.
 */
class AppUpdateManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    val currentVersionName: String
        get() = try {
            BuildConfig.VERSION_NAME
        } catch (e: Throwable) {
            "1.0.0-tactical"
        }

    val currentVersionCode: Int
        get() = try {
            BuildConfig.VERSION_CODE
        } catch (e: Throwable) {
            1
        }

    /**
     * Query GitHub Releases API for the latest published release.
     */
    fun checkForUpdates(isManual: Boolean = false) {
        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading) {
            return
        }

        _updateState.value = UpdateState.Checking

        scope.launch {
            try {
                val releaseInfo = withContext(Dispatchers.IO) {
                    fetchLatestReleaseFromGitHub()
                }

                if (releaseInfo == null) {
                    _updateState.value = UpdateState.Error("No valid releases or APK assets found on GitHub.")
                    return@launch
                }

                if (releaseInfo.isNewerThanCurrent) {
                    Timber.i("Update Available: Remote ${releaseInfo.tagName} > Local $currentVersionName")
                    _updateState.value = UpdateState.UpdateAvailable(
                        release = releaseInfo,
                        currentVersion = currentVersionName
                    )
                } else {
                    Timber.i("ReconDrive is up to date ($currentVersionName)")
                    _updateState.value = UpdateState.UpToDate(
                        currentVersion = currentVersionName,
                        latestRelease = releaseInfo
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "GitHub Update Check Failed")
                _updateState.value = UpdateState.Error(
                    e.localizedMessage ?: "Failed to connect to GitHub Releases API."
                )
            }
        }
    }

    /**
     * Streams the APK binary from GitHub Release assets to local storage with progress reporting.
     */
    fun downloadAndInstall(release: ReleaseInfo, onAutoInstall: Boolean = true) {
        if (_updateState.value is UpdateState.Downloading) return

        scope.launch {
            try {
                val targetDir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir,
                    "updates"
                ).apply { mkdirs() }

                val targetFile = File(targetDir, release.apkFileName)

                withContext(Dispatchers.IO) {
                    downloadFileWithRedirects(
                        downloadUrl = release.apkDownloadUrl,
                        targetFile = targetFile,
                        expectedSize = release.apkSizeBytes
                    ) { progress, downloaded, total ->
                        _updateState.value = UpdateState.Downloading(
                            progressPercent = progress,
                            bytesDownloaded = downloaded,
                            totalBytes = total,
                            fileName = release.apkFileName
                        )
                    }
                }

                Timber.i("APK Download Completed: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                _updateState.value = UpdateState.ReadyToInstall(targetFile, release)

                if (onAutoInstall) {
                    installApk(targetFile)
                }
            } catch (e: Exception) {
                Timber.e(e, "APK Download failed")
                _updateState.value = UpdateState.Error(
                    "Download failed: ${e.localizedMessage ?: "Network error"}"
                )
            }
        }
    }

    /**
     * Triggers the Android system package installer via FileProvider.
     */
    fun installApk(apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                _updateState.value = UpdateState.Error("APK file not found or corrupted.")
                return
            }

            // Android 8.0+ Unknown sources permission check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Timber.w("REQUEST_INSTALL_PACKAGES not granted. Prompting user settings.")
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            Timber.i("Launching Android Package Installer for $apkUri")
            context.startActivity(installIntent)

        } catch (e: Exception) {
            Timber.e(e, "Failed to launch package installer")
            _updateState.value = UpdateState.Error("Installer launch failed: ${e.localizedMessage}")
        }
    }

    /**
     * Open the GitHub release web page in browser.
     */
    fun openReleaseWebPage(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open release page URL")
        }
    }

    /**
     * Reset updater state to Idle
     */
    fun resetState() {
        _updateState.value = UpdateState.Idle
    }

    // =========================================================================
    // INTERNAL NETWORK & PARSING IMPLEMENTATION
    // =========================================================================

    private fun fetchLatestReleaseFromGitHub(): ReleaseInfo? {
        val endpoint = URL("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("User-Agent", "ReconDrive-Updater/$currentVersionName")
        }

        try {
            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                Timber.e("GitHub API Error: HTTP $responseCode")
                return null
            }

            val rawJson = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(rawJson)

            val tagName = root.optString("tag_name", "")
            val name = root.optString("name", tagName)
            val body = root.optString("body", "No release notes provided.")
            val htmlUrl = root.optString("html_url", "https://github.com/$GITHUB_REPO/releases")
            val publishedAt = root.optString("published_at", "")

            val assets = root.optJSONArray("assets")
            var apkDownloadUrl = ""
            var apkFileName = ""
            var apkSizeBytes = 0L

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkDownloadUrl = asset.optString("browser_download_url", "")
                        apkFileName = assetName
                        apkSizeBytes = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            if (apkDownloadUrl.isEmpty()) {
                Timber.w("Latest release found ($tagName), but contains no .apk assets.")
                return null
            }

            val cleanRemote = cleanVersionString(tagName)
            val cleanLocal = cleanVersionString(currentVersionName)
            val isNewer = compareSemVer(cleanRemote, cleanLocal) > 0

            return ReleaseInfo(
                tagName = tagName,
                cleanVersion = cleanRemote,
                releaseTitle = name,
                releaseNotes = body,
                htmlUrl = htmlUrl,
                publishedAt = publishedAt,
                apkDownloadUrl = apkDownloadUrl,
                apkFileName = apkFileName,
                apkSizeBytes = apkSizeBytes,
                isNewerThanCurrent = isNewer
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadFileWithRedirects(
        downloadUrl: String,
        targetFile: File,
        expectedSize: Long,
        onProgress: (progress: Int, downloaded: Long, total: Long) -> Unit
    ) {
        var currentUrl = downloadUrl
        var redirects = 0
        val maxRedirects = 6

        while (redirects < maxRedirects) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "ReconDrive-Updater/$currentVersionName")
            }

            val code = conn.responseCode
            if (code in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, 307, 308)) {
                val newUrl = conn.getHeaderField("Location") ?: throw IllegalStateException("Redirect without Location")
                conn.disconnect()
                currentUrl = newUrl
                redirects++
                continue
            }

            if (code !in 200..299) {
                conn.disconnect()
                throw IllegalStateException("Failed to download asset: HTTP $code")
            }

            val totalBytes = if (conn.contentLengthLong > 0) conn.contentLengthLong else expectedSize
            var downloadedBytes = 0L

            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    var lastReportTime = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 100 || downloadedBytes == totalBytes) {
                            lastReportTime = now
                            val progress = if (totalBytes > 0) {
                                ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            onProgress(progress, downloadedBytes, totalBytes)
                        }
                    }
                    output.flush()
                }
            }
            conn.disconnect()
            return
        }
        throw IllegalStateException("Too many HTTP redirects ($redirects)")
    }

    companion object {
        const val GITHUB_REPO = "CharlesLennon/recondrive"

        /**
         * Cleans version strings like "v1.0.0-tactical" -> "1.0.0"
         */
        fun cleanVersionString(raw: String): String {
            return raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .split("-")[0]
                .trim()
        }

        /**
         * Compare two semver strings: e.g. "1.0.1" vs "1.0.0" -> 1
         * Returns > 0 if v1 > v2, < 0 if v1 < v2, 0 if equal.
         */
        fun compareSemVer(v1: String, v2: String): Int {
            val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
            val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(parts1.size, parts2.size)
            for (i in 0 until maxLen) {
                val num1 = parts1.getOrElse(i) { 0 }
                val num2 = parts2.getOrElse(i) { 0 }
                if (num1 != num2) {
                    return num1.compareTo(num2)
                }
            }
            return 0
        }
    }
}
