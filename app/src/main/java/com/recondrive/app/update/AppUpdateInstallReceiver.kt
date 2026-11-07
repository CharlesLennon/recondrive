package com.recondrive.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import timber.log.Timber

/**
 * BroadcastReceiver listening for PackageInstaller session results.
 * Handles user confirmation prompts (STATUS_PENDING_USER_ACTION)
 * and logs telemetry/incompatibility reports.
 */
class AppUpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Status code: $status"

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Timber.i("PackageInstaller requires user confirmation. Launching confirmation activity.")
                val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmationIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Timber.i("Package installation successfully initiated!")
            }
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                Timber.e("Package installation failed: INCOMPATIBLE. Prior build had differing certificate or ABI. $message")
                try {
                    com.recondrive.app.ReconApplication.instance.updateManager.reportInstallFailure(
                        "Signature mismatch (INSTALL_FAILED_UPDATE_INCOMPATIBLE). The installed app was built before the permanent signing key was added in v1.0.12. Please uninstall the old version once to install signed updates."
                    )
                } catch (t: Throwable) {
                    Timber.w(t, "Could not notify updateManager")
                }
            }
            else -> {
                Timber.e("Package installation session completed with status $status: $message")
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.recondrive.app.ACTION_INSTALL_STATUS"
    }
}
