package com.recondrive.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.recondrive.app.ReconApplication
import com.recondrive.app.fog.ExplorationStats
import com.recondrive.app.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * 24/7 Tactical Exploration Background Service
 * Maintains persistent GPS surveillance to unmask OpenStreetMap territory and fog of war
 * even when ReconDrive is backgrounded, minimized, or the screen is turned off.
 */
class ExplorationTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var statsJob: Job? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var wakeLock: PowerManager.WakeLock? = null

    private var lastNotificationUpdateTime = 0L

    override fun onCreate() {
        super.onCreate()
        Timber.i("ExplorationTrackingService creating...")

        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ReconDrive:ExplorationTrackingWakelock"
        )?.apply {
            setReferenceCounted(false)
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    handleLocation(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Timber.d("ExplorationTrackingService onStartCommand with action: $action")

        when (action) {
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                if (_isRunning.value) {
                    stopTracking()
                    return START_NOT_STICKY
                } else {
                    startTracking()
                }
            }
            ACTION_START -> {
                startTracking()
            }
        }

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            Timber.w("Cannot start ExplorationTrackingService: missing location permissions")
            stopSelf()
            return
        }

        try {
            val stats = ReconApplication.instance.fogGridManager.stats.value
            val initialNotification = buildNotification(stats, _currentLocation.value)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }

            _isRunning.value = true

            // Acquire safe partial wake lock
            try {
                wakeLock?.acquire(10 * 60 * 1000L) // 10 minute safety timeout, refreshed on fixes
            } catch (e: Exception) {
                Timber.w(e, "WakeLock acquire note")
            }

            // High accuracy tactical GPS request
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(1500L)
                .setMinUpdateDistanceMeters(3.0f)
                .setWaitForAccurateLocation(false)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            // Collect stats updates to refresh notification text dynamically
            statsJob?.cancel()
            statsJob = serviceScope.launch {
                ReconApplication.instance.fogGridManager.stats.collect { updatedStats ->
                    maybeUpdateNotification(updatedStats, _currentLocation.value)
                }
            }

            Timber.i("Tactical radar 24/7 background exploration active")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start exploration foreground service")
            stopSelf()
        }
    }

    private fun handleLocation(location: Location) {
        _currentLocation.value = location

        // Feed directly to Fog of War spatial persistence engine
        ReconApplication.instance.fogGridManager.processLocation(location)

        // Refresh wake lock to ensure CPU remains alive while moving
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Timber.w(e, "WakeLock re-acquire note")
        }

        val stats = ReconApplication.instance.fogGridManager.stats.value
        maybeUpdateNotification(stats, location)
    }

    private fun maybeUpdateNotification(stats: ExplorationStats, location: Location?) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdateTime < 3500L) {
            return // Throttle notification redraws to conserve battery
        }
        lastNotificationUpdateTime = now

        val notification = buildNotification(stats, location)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun stopTracking() {
        Timber.i("Stopping ExplorationTrackingService")
        statsJob?.cancel()
        statsJob = null

        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Timber.w(e, "removeLocationUpdates note")
        }

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Timber.w(e, "wakeLock release note")
        }

        _isRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopTracking()
        Timber.i("ExplorationTrackingService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tactical Radar & Fog Exploration",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active background GPS telemetry uncovering Fog of War and tracking territory"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(stats: ExplorationStats, location: Location?): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ExplorationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val speedKmh = ((location?.speed ?: 0f) * 3.6f).toInt()
        val areaText = "%.2f km²".format(stats.totalAreaKm2)
        val streakText = "%.1f km".format(stats.currentUnchartedStreakKm)

        val bodyText = if (stats.totalAreaKm2 > 0.0) {
            "Explored: $areaText • Streak: $streakText • Speed: ${speedKmh} km/h"
        } else {
            "Radar Active • Uncovering UK territory as you drive"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛰️ ReconDrive Tactical Radar Active")
            .setContentText(bodyText)
            .setSubText("24/7 BACKGROUND SURVEILLANCE")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "PAUSE RADAR", stopPendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "recon_exploration_service"
        const val NOTIFICATION_ID = 9001

        const val ACTION_START = "com.recondrive.app.service.ACTION_START"
        const val ACTION_STOP = "com.recondrive.app.service.ACTION_STOP"
        const val ACTION_TOGGLE = "com.recondrive.app.service.ACTION_TOGGLE"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _currentLocation = MutableStateFlow<Location?>(null)
        val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, ExplorationTrackingService::class.java).apply {
                action = ACTION_START
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to startForegroundService")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ExplorationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop ExplorationTrackingService")
            }
        }

        fun toggle(context: Context) {
            if (_isRunning.value) {
                stop(context)
            } else {
                start(context)
            }
        }
    }
}
