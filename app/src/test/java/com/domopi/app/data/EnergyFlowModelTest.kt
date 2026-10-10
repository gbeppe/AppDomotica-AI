package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyFlowModelTest {
    private val now = 1_000_000_000L
    private fun fresh(solar: Float = 0f, home: Float = 0f, grid: Float = 0f, battery: Float = 0f, soc: Float = 50f, at: Long = now - 1000) =
        EnergyData(solarPower = solar, homeConsumption = home, gridPower = grid, batteryPower = battery, batterySoc = soc,
            solarAtMs = at, homeAtMs = at, gridAtMs = at, batteryAtMs = at, socAtMs = at)

    @Test fun todayScreenshotExportAndIdleBattery() {
        // 10/10/2026 14:06: FV 3.5 kW, casa 1.6 kW, rete -1.9 kW, Powerwall 0 W al 99%.
        val s = EnergyFlowModel.from(fresh(3500f, 1600f, -1900f, 0f, 99f), now, connected = true)
        assertTrue(s.solar.moving && s.solar.towardsHub)
        assertTrue(s.grid.moving); assertFalse(s.grid.towardsHub)          // immissione: dal quadro al traliccio
        assertFalse(s.battery.moving)                                        // 0 W: linea ferma
        assertTrue(s.home.moving); assertFalse(s.home.towardsHub)          // la casa esce sempre dal quadro
        assertEquals("immissione", EnergyFlowModel.gridNote(s.grid))
        assertEquals("99%", EnergyFlowModel.batteryNote(s.battery, s.socPercent))
    }

    @Test fun directionFollowsTheSign() {
        val charge = EnergyFlowModel.from(fresh(battery = -2500f, grid = 800f), now, true)
        assertFalse(charge.battery.towardsHub)
        assertTrue(charge.grid.towardsHub)
        assertEquals("prelievo", EnergyFlowModel.gridNote(charge.grid))
        assertEquals("50% · carica", EnergyFlowModel.batteryNote(charge.battery, charge.socPercent))
        val discharge = EnergyFlowModel.from(fresh(battery = 1800f), now, true)
        assertTrue(discharge.battery.towardsHub)
        assertEquals("50% · scarica", EnergyFlowModel.batteryNote(discharge.battery, discharge.socPercent))
    }

    @Test fun deadbandOf50WattsShowsZeroAndStillLine() {
        val s = EnergyFlowModel.from(fresh(solar = -3f, grid = 49f, battery = -50f), now, true)
        assertEquals(0f, s.solar.watts); assertFalse(s.solar.moving)
        assertEquals(0f, s.grid.watts); assertFalse(s.grid.moving)
        assertEquals(-50f, s.battery.watts); assertTrue(s.battery.moving)
    }

    @Test fun speedIsLogarithmicBetween50WAnd7kW() {
        assertEquals(2600, EnergyFlowModel.periodMs(50f))
        assertEquals(500, EnergyFlowModel.periodMs(7000f))
        assertEquals(500, EnergyFlowModel.periodMs(12000f))
        val mid = EnergyFlowModel.periodMs(591.6f)                          // geometric midpoint
        assertTrue(mid in 1540..1560)
        assertTrue(EnergyFlowModel.periodMs(3000f) < EnergyFlowModel.periodMs(300f))
    }

    @Test fun staleAfterThreeMinutesNeverZero() {
        val e = fresh(solar = 2000f, home = 900f).copy(solarAtMs = now - 180_001)
        val s = EnergyFlowModel.from(e, now, true)
        assertTrue(s.solar.stale); assertNull(s.solar.watts); assertFalse(s.solar.moving)
        assertEquals("—", EnergyFlowModel.format(s.solar.watts))
        assertFalse(s.home.stale)
        assertFalse(EnergyFlowModel.from(e.copy(solarAtMs = now - 180_000), now, true).solar.stale)
    }

    @Test fun neverReceivedOrDisconnectedIsStale() {
        val never = EnergyFlowModel.from(EnergyData(), now, true)
        assertTrue(never.allStale); assertNull(never.socPercent)
        val down = EnergyFlowModel.from(fresh(solar = 3000f), now, connected = false)
        assertTrue(down.allStale)
        assertEquals("non aggiornato", EnergyFlowModel.batteryNote(down.battery, down.socPercent))
    }

    @Test fun formatIsWattsBelow1000ThenKilowattsWithComma() {
        assertEquals("0 W", EnergyFlowModel.format(0f))
        assertEquals("999 W", EnergyFlowModel.format(-999f))
        assertEquals("1,0 kW", EnergyFlowModel.format(1000f))
        assertEquals("1,9 kW", EnergyFlowModel.format(-1900f))
        assertEquals("—", EnergyFlowModel.format(null))
    }
}
