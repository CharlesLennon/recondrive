package com.recondrive.app.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Android Auto Main Service Entry Point
 * Registered in AndroidManifest.xml with category androidx.car.app.category.NAVIGATION
 */
class ReconCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // Permits connections from Android Auto Desktop Head Unit (DHU) and certified automotive hosts
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return ReconCarSession()
    }
}
