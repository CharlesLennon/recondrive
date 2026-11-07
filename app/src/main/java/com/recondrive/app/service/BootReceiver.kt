package com.recondrive.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.recondrive.app.ReconApplication
import timber.log.Timber

/**
 * Boot & Package Update Receiver
 * Restarts tactical exploration tracking on device startup if 24/7 background tracking is enabled.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Timber.i("BootReceiver received action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val isEnabled = try {
                ReconApplication.instance.fogPreferences.isBackgroundTrackingEnabled.value
            } catch (e: Exception) {
                true // default to true
            }

            if (isEnabled) {
                Timber.i("Auto-starting 24/7 background exploration radar on boot")
                ExplorationTrackingService.start(context)
            }
        }
    }
}
