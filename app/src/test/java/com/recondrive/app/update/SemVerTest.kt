package com.recondrive.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {

    @Test
    fun testCleanVersionString() {
        assertEquals("1.0.0", AppUpdateManager.cleanVersionString("v1.0.0"))
        assertEquals("1.0.0", AppUpdateManager.cleanVersionString("V1.0.0"))
        assertEquals("1.0.0", AppUpdateManager.cleanVersionString("1.0.0-tactical"))
        assertEquals("1.0.1", AppUpdateManager.cleanVersionString("v1.0.1-rc1"))
    }

    @Test
    fun testCompareSemVer() {
        assertTrue(AppUpdateManager.compareSemVer("1.0.1", "1.0.0") > 0)
        assertTrue(AppUpdateManager.compareSemVer("1.1.0", "1.0.9") > 0)
        assertTrue(AppUpdateManager.compareSemVer("2.0.0", "1.9.9") > 0)
        assertTrue(AppUpdateManager.compareSemVer("1.0.0", "1.0.0") == 0)
        assertTrue(AppUpdateManager.compareSemVer("1.0.0", "1.0.1") < 0)
    }
}
