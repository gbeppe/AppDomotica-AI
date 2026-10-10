package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridImportSummaryTest {
    private fun handle(manager: MqttManager, topic: String, payload: String) =
        MqttManager::class.java.getDeclaredMethod("handleIncomingMessage", String::class.java, String::class.java)
            .apply { isAccessible = true }.invoke(manager, topic, payload)

    @Test fun unknownUntilObtainedThenKnownEvenWhenZero() {
        val manager = MqttManager()
        assertEquals(0L, manager.energyData.value.gridImportAtMs)      // shown as "—", not 0,0 kWh
        manager.updateGridImportKwhSummary(kwhOggi = 0f, kwhIeri = 0f)
        assertTrue(manager.energyData.value.gridImportAtMs > 0)       // a real 0 kWh is now shown in green
    }

    @Test fun mqttSummaryMarksTheValueAsKnown() {
        val manager = MqttManager()
        handle(manager, "zara/interface/energy/grid/import_kwh_oggi/stat", "0.4")
        assertEquals(0.4f, manager.energyData.value.gridImportKwhOggi, 1e-6f)
        assertTrue(manager.energyData.value.gridImportAtMs > 0)
    }
}
