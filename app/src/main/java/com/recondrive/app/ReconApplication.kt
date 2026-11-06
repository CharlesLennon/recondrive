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

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber logging
        Timber.plant(Timber.DebugTree())
        Timber.i("ReconDrive Tactical Engine Initialized")

        // Initialize Core Subsystems
        offlineTileRepository = OfflineTileRepository(this)
        fogGridManager = FogGridManager(this)
    }

    companion object {
        lateinit var instance: ReconApplication
            private set
    }

    init {
        instance = this
    }
}
