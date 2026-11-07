package com.recondrive.app.map

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for OsmMapUpdateManager data models, version tags, and state models.
 */
class OsmMapUpdateManagerTest {

    @Test
    fun testDefaultVersionConstants() {
        assertEquals("2026.10.15-uk-base", OsmMapUpdateManager.DEFAULT_OSM_VERSION)
        assertEquals("2026.11.07-uk-osm", OsmMapUpdateManager.REMOTE_LATEST_TAG)
    }

    @Test
    fun testUpdateAvailableFormatting() {
        val update = MapUpdateState.UpdateAvailable(
            currentVersion = "2026.10.15-uk-base",
            latestVersion = "2026.11.07-uk-osm",
            releaseDate = "November 2026",
            downloadSizeBytes = 1650L * 1024 * 1024,
            changeSummary = "Updated UK motorways (M1, M25, M6, M4)",
            downloadUrl = "https://download.geofabrik.de/europe/great-britain-latest.osm.pbf"
        )

        assertEquals("1650.0 MB", update.formattedSize)
        assertEquals("2026.11.07-uk-osm", update.latestVersion)
        assertEquals("November 2026", update.releaseDate)
    }

    @Test
    fun testMapUpdateStates() {
        val idle = MapUpdateState.Idle
        val checking = MapUpdateState.Checking
        val offline = MapUpdateState.Offline
        val skipped = MapUpdateState.Skipped

        assertTrue(idle is MapUpdateState)
        assertTrue(checking is MapUpdateState)
        assertTrue(offline is MapUpdateState)
        assertTrue(skipped is MapUpdateState)
    }
}
