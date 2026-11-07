package com.recondrive.app

import android.app.Application
import com.recondrive.app.fog.FogGridManager
import com.recondrive.app.map.OfflineTileRepository
import timber.log.Timber

/**
 * ReconDrive Application Root
 * Initializes tactical navigation engines, offline tile cache, and fog persistence.
 */
class ReconApplication : Application() {

    lateinit var fogGridManager: FogGridManager
        private set

    lateinit var offlineTileRepository: OfflineTileRepository
        private set

    lateinit var fogPreferences: com.recondrive.app.fog.FogPreferences
        private set

    lateinit var skinManager: com.recondrive.app.skin.SkinManager
        private set

    lateinit var updateManager: com.recondrive.app.update.AppUpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber logging
        Timber.plant(Timber.DebugTree())
        Timber.i("ReconDrive Tactical Engine Initialized")

        // Initialize MapLibre Native SDK
        try {
            org.maplibre.android.MapLibre.getInstance(this)
            Timber.i("MapLibre Native SDK initialized successfully")
        } catch (e: Throwable) {
            Timber.w(e, "MapLibre native init note (headless or non-standard surface)")
        }

        // Initialize Core Subsystems
        updateManager = com.recondrive.app.update.AppUpdateManager(this)
        fogPreferences = com.recondrive.app.fog.FogPreferences(this)
        skinManager = com.recondrive.app.skin.SkinManager(this)
        offlineTileRepository = OfflineTileRepository(this)
        fogGridManager = FogGridManager(this, fogPreferences)
    }

    companion object {
        lateinit var instance: ReconApplication
            private set
    }

    init {
        instance = this
    }
}
