package com.recondrive.app.map

import org.junit.Assert.*
import org.junit.Test

class UkOsmDataEngineTest {

    @Test
    fun testUkBoundsContainsMajorCities() {
        // London
        assertTrue(UkOsmDataEngine.UK_FULL_BOUNDS.contains(51.5074, -0.1278))
        // Edinburgh
        assertTrue(UkOsmDataEngine.UK_FULL_BOUNDS.contains(55.9533, -3.1883))
        // Cardiff
        assertTrue(UkOsmDataEngine.UK_FULL_BOUNDS.contains(51.4816, -3.1791))
        // Belfast
        assertTrue(UkOsmDataEngine.UK_FULL_BOUNDS.contains(54.5973, -5.9301))
    }

    @Test
    fun testMajorRoadsCountAndGeometries() {
        val roads = UkOsmDataEngine.MAJOR_ROADS
        assertTrue(roads.size >= 10)
        
        val m1 = roads.firstOrNull { it.ref == "M1" }
        assertNotNull(m1)
        assertEquals(OsmRoadType.MOTORWAY, m1!!.type)
        assertTrue(m1.points.size >= 8)

        val m25 = roads.firstOrNull { it.ref == "M25" }
        assertNotNull(m25)
        assertTrue(m25.points.size >= 10)
    }

    @Test
    fun testDistanceCalculation() {
        // Distance between London and Birmingham (~160 km)
        val dist = UkOsmDataEngine.calculateDistanceKm(51.5074, -0.1278, 52.4862, -1.8904)
        assertTrue(dist in 150.0..180.0)
    }
}
