package com.domopi.app.data

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardTextTest {
    private val it = Locale.ITALY

    @Test fun withoutBrokerEveryValueIsUnknown() {
        assertEquals("—", DashboardText.temperature(false, 0f, it))
        assertEquals("—", DashboardText.puffer(false, 0f, 0f, 0f, it))
        assertEquals("—", DashboardText.pvForecast(false, 0f, 0, it))
        assertEquals("—", DashboardText.acBadge(false, "", systemEnabled = true))
        assertEquals("—", DashboardText.acBadge(false, "COOLING_ON", systemEnabled = true))
    }

    @Test fun connectedValuesKeepTheirFormat() {
        assertEquals("20,7°C", DashboardText.temperature(true, 20.7f, it))
        assertEquals("35,0° - 40,8° - 32,3°", DashboardText.puffer(true, 35f, 40.8f, 32.3f, it))
        assertEquals("12,4 kWh (batt 80%)", DashboardText.pvForecast(true, 12.4f, 80, it))
        assertEquals("COOLING_ON", DashboardText.acBadge(true, "COOLING_ON", systemEnabled = false))
        assertEquals("ATTIVATO", DashboardText.acBadge(true, "", systemEnabled = true))
        assertEquals("DISATTIVATO", DashboardText.acBadge(true, "", systemEnabled = false))
    }

    @Test fun dashboardUsesTheseTexts() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val dashboard = File(root, "com/domopi/app/ui/screens/MainDashboard.kt").readText()
        for (call in listOf("DashboardText.acBadge(isConnected", "DashboardText.puffer(isConnected",
                "DashboardText.temperature(isConnected, envState.living", "DashboardText.temperature(isConnected, envState.bedroom",
                "DashboardText.pvForecast(isConnected"))
            assertTrue(call, dashboard.contains(call))
    }
}
