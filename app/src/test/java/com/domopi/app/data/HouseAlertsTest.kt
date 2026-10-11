package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HouseAlertsTest {
    private val min = 60_000L

    @Test fun nothingForTheStateFoundAtOpening() {
        val a = HouseAlerts()
        assertEquals(emptyList<String>(), a.update(false, null, 0))          // app starting, not yet connected
        assertEquals(emptyList<String>(), a.update(false, null, 2 * min))     // never connected: no alert
        assertEquals(emptyList<String>(), a.update(true, 100.0, 3 * min))     // already full at opening
        assertEquals(emptyList<String>(), HouseAlerts().update(true, 10.0, 0)) // already low at opening
    }

    @Test fun disconnectionAfterGraceAndReconnection() {
        val a = HouseAlerts()
        a.update(true, 50.0, 0)
        assertEquals(emptyList<String>(), a.update(false, 50.0, 1_000))
        assertEquals(emptyList<String>(), a.update(false, 50.0, 25_000))      // short drop: still silent
        assertEquals(listOf(HouseAlerts.DISCONNECTED), a.update(false, 50.0, 31_000))
        assertEquals(emptyList<String>(), a.update(false, 50.0, 60_000))      // said once
        assertEquals(listOf(HouseAlerts.RECONNECTED), a.update(true, 50.0, 90_000))
    }

    @Test fun shortDropIsNeverAnnouncedNorItsReconnection() {
        val a = HouseAlerts()
        a.update(true, 50.0, 0)
        a.update(false, 50.0, 1_000)
        assertEquals(emptyList<String>(), a.update(true, 50.0, 20_000))
    }

    @Test fun batteryFullOnceWithHysteresisAndInterval() {
        val a = HouseAlerts()
        a.update(true, 90.0, 0)
        assertEquals(listOf(HouseAlerts.FULL), a.update(true, 100.0, min))
        assertEquals(emptyList<String>(), a.update(true, 98.0, 2 * min))     // not re-armed above 95
        assertEquals(emptyList<String>(), a.update(true, 100.0, 3 * min))
        a.update(true, 94.0, 4 * min)                                          // re-armed
        assertEquals(emptyList<String>(), a.update(true, 100.0, 5 * min))     // but within 30 minutes
        a.update(true, 94.0, 40 * min)
        assertEquals(listOf(HouseAlerts.FULL), a.update(true, 100.0, 41 * min))
    }

    @Test fun batteryLowOnceWithHysteresis() {
        val a = HouseAlerts()
        a.update(true, 30.0, 0)
        assertEquals(listOf(HouseAlerts.LOW), a.update(true, 19.0, min))
        assertEquals(emptyList<String>(), a.update(true, 22.0, 2 * min))
        assertEquals(emptyList<String>(), a.update(true, 18.0, 3 * min))     // not re-armed below 25
    }

    @Test fun batteryIgnoredWhileDisconnected() {
        val a = HouseAlerts()
        a.update(true, 90.0, 0)
        assertEquals(emptyList<String>(), a.update(false, 100.0, 1_000))
    }
}
