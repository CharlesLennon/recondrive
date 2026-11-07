package com.recondrive.app.service

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for 24/7 Exploration Tracking Service contract and actions.
 */
class ExplorationTrackingServiceTest {

    @Test
    fun testServiceActionConstants() {
        assertEquals("com.recondrive.app.service.ACTION_START", ExplorationTrackingService.ACTION_START)
        assertEquals("com.recondrive.app.service.ACTION_STOP", ExplorationTrackingService.ACTION_STOP)
        assertEquals("com.recondrive.app.service.ACTION_TOGGLE", ExplorationTrackingService.ACTION_TOGGLE)
    }

    @Test
    fun testNotificationChannelConfig() {
        assertEquals("recon_exploration_service", ExplorationTrackingService.CHANNEL_ID)
        assertEquals(9001, ExplorationTrackingService.NOTIFICATION_ID)
    }

    @Test
    fun testInitialServiceState() {
        assertFalse("Service should not report running before being started", ExplorationTrackingService.isRunning.value)
        assertNull("Current location should initially be null", ExplorationTrackingService.currentLocation.value)
    }
}
