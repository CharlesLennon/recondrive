package com.recondrive.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
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

    private val prefs = context.getSharedPreferences("recondrive_app_update_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    val skippedVersion: String?
        get() = prefs.getString(KEY_SKIPPED_APP_VERSION, null)

    /**
     * Checks whether device currently has active internet connectivity.
     */
    fun isNetworkConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Records that the user has chosen to skip this release.
     */
    fun skipUpdate(release: ReleaseInfo) {
        Timber.i("User skipped app update: ${release.tagName}")
        prefs.edit().putString(KEY_SKIPPED_APP_VERSION, release.tagName).apply()
        _updateState.value = UpdateState.Idle
    }

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
        if (!isNetworkConnected()) {
            Timber.i("App Updater: No network detected. Skipping automated update check.")
            _updateState.value = UpdateState.Idle
            return
        }

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

                val isSkipped = !isManual && releaseInfo.tagName == skippedVersion
                if (releaseInfo.isNewerThanCurrent && !isSkipped) {
                    Timber.i("Update Available: Remote ${releaseInfo.tagName} > Local $currentVersionName")
                    _updateState.value = UpdateState.UpdateAvailable(
                        release = releaseInfo,
                        currentVersion = currentVersionName
                    )
                } else if (releaseInfo.isNewerThanCurrent && isSkipped) {
                    Timber.i("Update ${releaseInfo.tagName} available but previously skipped by user.")
                    _updateState.value = UpdateState.Idle
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
                // Use internal cache directory to avoid SD card/scoped storage mounting or path-mapping issues
                val targetDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val targetFile = File(targetDir, release.apkFileName)

                // Delete any partial or obsolete build before downloading
                if (targetFile.exists()) {
                    targetFile.delete()
                }

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

                // Make file world-readable so system package installer process can read the raw bytes
                targetFile.setReadable(true, false)

                // Verify package archive integrity prior to triggering installation
                val packageArchiveInfo = context.packageManager.getPackageArchiveInfo(targetFile.absolutePath, 0)
                if (packageArchiveInfo == null) {
                    targetFile.delete()
                    throw IllegalStateException("Downloaded package is corrupted or incomplete. Please retry.")
                }

                Timber.i("APK Download & Integrity Verified: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
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
    /**
     * Triggers the Android system package installer.
     * Uses modern PackageInstaller.Session streaming API as primary engine,
     * with FileProvider ACTION_VIEW as fallback.
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

            // Ensure world-readable permissions on Linux filesystem level
            apkFile.setReadable(true, false)

            // Primary Engine: Modern Android PackageInstaller.Session (streams APK binary directly to installer)
            try {
                Timber.i("Launching PackageInstaller.Session for ${apkFile.name}")
                installViaPackageInstallerSession(apkFile)
                return
            } catch (e: Exception) {
                Timber.w(e, "PackageInstaller.Session failed, falling back to FileProvider ACTION_VIEW")
            }

            // Fallback Engine: FileProvider ACTION_VIEW Intent
            installViaFileProvider(apkFile)

        } catch (e: Exception) {
            Timber.e(e, "Failed to launch package installer")
            _updateState.value = UpdateState.Error("Installer launch failed: ${e.localizedMessage}")
        }
    }

    private fun installViaPackageInstallerSession(apkFile: File) {
        val packageInstaller = context.packageManager.packageInstaller
        val sessionParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = packageInstaller.createSession(sessionParams)
        val session = packageInstaller.openSession(sessionId)

        session.openWrite("package", 0, apkFile.length()).use { out ->
            apkFile.inputStream().use { input ->
                input.copyTo(out)
            }
            session.fsync(out)
        }

        val intent = Intent(context, AppUpdateInstallReceiver::class.java).apply {
            action = AppUpdateInstallReceiver.ACTION_INSTALL_STATUS
            `package` = context.packageName
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
        session.commit(pendingIntent.intentSender)
        session.close()
    }

    private fun installViaFileProvider(apkFile: File) {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val knownInstallers = listOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.apps.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller",
            "android"
        )
        for (pkg in knownInstallers) {
            try {
                context.grantUriPermission(pkg, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(
                installIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(installIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        for (info in resolveInfos) {
            try {
                context.grantUriPermission(info.activityInfo.packageName, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
        }

        Timber.i("Launching Android Package Installer via ACTION_VIEW for $apkUri")
        try {
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Timber.w(e, "Standard ACTION_VIEW package install failed, trying ACTION_INSTALL_PACKAGE fallback")
            val fallbackIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
            context.startActivity(fallbackIntent)
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
        private const val KEY_SKIPPED_APP_VERSION = "pref_skipped_app_version"

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
