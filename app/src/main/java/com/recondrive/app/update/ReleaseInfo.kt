package com.recondrive.app.update

/**
 * Metadata representing a GitHub Release fetched from GitHub API.
 */
data class ReleaseInfo(
    val tagName: String,
    val cleanVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val publishedAt: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val isNewerThanCurrent: Boolean
) {
    val formattedSize: String
        get() {
            if (apkSizeBytes <= 0) return "Unknown size"
            val mb = apkSizeBytes.toDouble() / (1024.0 * 1024.0)
            return "%.1f MB".format(mb)
        }
}

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class UpdateAvailable(val release: ReleaseInfo, val currentVersion: String) : UpdateState
    data class UpToDate(val currentVersion: String, val latestRelease: ReleaseInfo?) : UpdateState
    data class Downloading(
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val fileName: String,
        val release: ReleaseInfo? = null
    ) : UpdateState
    data class ReadyToInstall(val apkFile: java.io.File, val release: ReleaseInfo) : UpdateState
    data class Installing(
        val apkFile: java.io.File,
        val release: ReleaseInfo,
        val stage: String = "Committing PackageInstaller Session..."
    ) : UpdateState
    data class Error(val message: String, val lastAttemptTime: Long = System.currentTimeMillis()) : UpdateState
}
