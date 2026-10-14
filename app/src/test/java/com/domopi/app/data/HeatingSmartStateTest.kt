package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatingSmartStateTest {
    private val prefix = "zara/interface/"

    private fun HeatingSmartState.see(relative: String, payload: String, at: Long = 1000L, source: String = prefix + relative) =
        observe(relative, payload, at, true, source)

    @Test fun keepsTheLiveHeatingStates() {
        val s = HeatingSmartState()
            .see("fireplace/main/power/stat", "true")
            .see("fireplace/main/level/stat", "3")
            .see("fireplace/main/mode/stat", "Riscaldamento")
            .see("fireplace/main/start_time/stat", "16:30")
            .see("fireplace/main/auto_power/stat", "true")
            .see("heating/puffer/top_temperature/stat", "42.9")
            .see("energy/puffer_acs/stat", "47")
            .see("heating/floor_pump/enabled/stat", "false")
            .see("heating/gas_boiler/flame/stat", "false")
            .see("heating/solar_thermal/pump_speed/stat", "0")
        val o = s.validObservations().mapValues { it.value.value }
        assertEquals(mapOf("fireplace.power" to true, "fireplace.level" to 3, "fireplace.mode" to "Riscaldamento",
            "fireplace.start_time" to "16:30", "fireplace.auto_power" to true, "puffer.top_temperature" to 42.9,
            "puffer.acs_temperature" to 47.0, "floor_pump.enabled" to false, "gas_boiler.flame" to false,
            "solar_thermal.pump_speed" to 0), o)
        assertEquals("zara/interface/energy/puffer_acs/stat",
            s.validObservations().getValue("puffer.acs_temperature").sourceTopic)
    }

    @Test fun dropsWhatTheBackendWouldReject() {
        val s = HeatingSmartState()
            .see("fireplace/main/level/stat", "3", at = 2000L)
            .see("fireplace/main/level/stat", "5", at = 1000L)
            .see("fireplace/main/level/stat", "7")
            .see("fireplace/main/mode/stat", "inverno")
            .see("fireplace/main/stop_time/stat", "1730")
            .see("fireplace/main/power/stat", "1")
            .see("heating/gas_boiler/modulation/stat", "null")
            .see("heating/puffer/top_temperature/stat", "NaN")
            .see("fireplace/main/power/cmd", "true")
            .see("fireplace/main/power/stat", "true", source = "zara/interface/fireplace/main/power/cmd")
            .see("heating/heat_pump/running/stat", "true")
        assertEquals(mapOf("fireplace.level" to 3), s.validObservations().mapValues { it.value.value })
    }

    @Test fun keysAndModesMatchTheBackendAllowlist() {
        val backend = listOf(java.io.File("../backend/house_ai/energy_tools.py"),
            java.io.File("backend/house_ai/energy_tools.py")).first { it.exists() }.readText()
            .substringAfter("HEATING_METRICS = {").substringBefore("}")
        HeatingSmartState.METRICS.forEach { (topic, metric) ->
            assertTrue(topic, backend.contains("\"${metric.first}\": (\"$topic\","))
        }
        assertEquals(HeatingSmartState.METRICS.size, Regex("\": \\(\"").findAll(backend).count())
        // Integers stay integers, so the JSON body carries 4 and not 4.0.
        assertEquals(4, HeatingSmartState().see("fireplace/main/level/stat", "4")
            .validObservations().getValue("fireplace.level").value)
    }
}
