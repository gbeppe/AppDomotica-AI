package com.domopi.app.ui

import com.domopi.app.ui.components.PoolGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tap points measured on pool_background.jpg, normalized 0..1 (original 2752x1536). */
class PoolGeometryTest {
    private fun at(x: Float, y: Float) = PoolGeometry.hit(x, y)

    @Test fun eachElementIsHitWhereItIsDrawn() {
        assertEquals(PoolGeometry.WATER_ID, at(0.50f, 0.20f))     // middle of the water
        assertEquals(PoolGeometry.SKIMMER_ID, at(0.36f, 0.60f))   // skimmer body
        assertEquals(PoolGeometry.SKIMMER_ID, at(0.30f, 0.50f))   // left end of the orange rim
        assertEquals(PoolGeometry.SKIMMER_ID, at(0.36f, 0.75f))   // skimmer feet
        assertEquals(PoolGeometry.PUMP_ID, at(0.60f, 0.68f))      // filter tank
        assertEquals(PoolGeometry.PUMP_ID, at(0.60f, 0.43f))      // valve on top
        assertEquals(PoolGeometry.PUMP_ID, at(0.72f, 0.62f))      // motor on the right
        assertEquals(PoolGeometry.DECK_ID, at(0.47f, 0.85f))      // walkway between the two
        assertEquals(PoolGeometry.DECK_ID, at(0.10f, 0.38f))      // left side of the ring
        assertEquals(PoolGeometry.DECK_ID, at(0.218f, 0.831f))    // a spotlight
    }

    @Test fun grassIsNotAnElement() {
        assertNull(at(0.03f, 0.90f))
        assertNull(at(0.95f, 0.05f))
        assertNull(at(0.90f, 0.85f))
    }

    /** Skimmer and pump must be big enough for a finger on a phone (card about 360 dp wide). */
    @Test fun skimmerAndPumpAreFingerSized() {
        for (p in listOf(PoolGeometry.skimmer, PoolGeometry.pump)) {
            val width = p.points.maxOf { it.first } - p.points.minOf { it.first }
            assertTrue("width $width", width * 360f >= 48f)
        }
    }
}
