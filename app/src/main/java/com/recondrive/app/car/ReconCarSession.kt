package com.recondrive.app.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import timber.log.Timber

/**
 * Android Auto Car Session
 * Manages the lifecycle of screens displayed on the vehicle head unit.
 */
class ReconCarSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        Timber.i("Android Auto Session Created • Launching ReconCarNavigationScreen")
        return ReconCarNavigationScreen(carContext)
    }
}
