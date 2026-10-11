package com.domopi.app.ui

import com.domopi.app.ui.components.PoolGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tap points measured on pool_background.jpg (display size 2000x1127). */
class PoolGeometryTest {
    private fun at(x: Float, y: Float) = PoolGeometry.hit(x / 2000f, y / 1127f)

    @Test fun eachElementIsHitWhereItIsDrawn() {
        assertEquals(PoolGeometry.WATER_ID, at(1000f, 240f))     // middle of the water
        assertEquals(PoolGeometry.SKIMMER_ID, at(632f, 400f))    // red skimmer basket
        assertEquals(PoolGeometry.PUMP_ID, at(1540f, 470f))      // filter pump body
        assertEquals(PoolGeometry.DECK_ID, at(970f, 850f))       // walkway
        assertEquals(PoolGeometry.DECK_ID, at(180f, 420f))       // left side of the ring
        assertEquals(PoolGeometry.DECK_ID, at(430f, 935f))       // a spotlight
    }

    @Test fun grassIsNotAnElement() {
        assertNull(at(60f, 1000f))
        assertNull(at(1900f, 60f))
        assertNull(at(1800f, 1000f))
    }
}
