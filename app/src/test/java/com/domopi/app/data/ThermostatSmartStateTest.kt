package com.domopi.app.data

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermostatSmartStateTest {
    private val prefix = "zara/interface/"

    private fun ThermostatSmartState.see(relative: String, payload: String, at: Long = 1000L, source: String = prefix + relative) =
        observe(relative, payload, at, true, source)

    @Test fun keepsOnlyValidThermostatStates() {
        val s = ThermostatSmartState()
            .see("climate/thermostat_living/current_temperature/stat", "22.4")
            .see("climate/thermostat_living/target_temperature/stat", "18,5")
            .see("climate/thermostat_bath/power/stat", "false")
            .see("climate/thermostat_bath/min_temperature/stat", "17")
        val o = s.validObservations()
        assertEquals(4, o.size)
        assertEquals(22.4, o.getValue("thermostat_living.current_temperature").value)
        assertEquals(18.5, o.getValue("thermostat_living.target_temperature").value)
        assertEquals(false, o.getValue("thermostat_bath.power").value)
        assertEquals("zara/interface/climate/thermostat_bath/min_temperature/stat",
            o.getValue("thermostat_bath.min_temperature").sourceTopic)
    }

    @Test fun rejectsInvalidCommandsUnknownAndOlder() {
        val s = ThermostatSmartState()
            .see("climate/thermostat_living/target_temperature/stat", "20", at = 2000L)
            .see("climate/thermostat_living/target_temperature/stat", "19", at = 1000L)
            .see("climate/thermostat_living/power/stat", "100")
            .see("climate/thermostat_living/current_temperature/stat", "abc")
            .see("climate/thermostat_bath/current_temperature/stat", "400")
            .see("climate/thermostat_kitchen/power/stat", "true")
            .see("climate/control/humidex_reference/stat", "25.6")
            .see("climate/thermostat_bath/target_temperature/cmd", "21")
            .see("climate/thermostat_bath/target_temperature/stat", "21",
                source = "zara/interface/climate/thermostat_bath/target_temperature/cmd")
        assertEquals(mapOf("thermostat_living.target_temperature" to 20.0),
            s.validObservations().mapValues { it.value.value })
    }

    @Test fun targetsAreTheClosedSliderRange() {
        assertEquals(13, ThermostatSmartState.TARGETS.size)
        assertEquals("16.0", ThermostatSmartState.TARGETS.first())
        assertEquals("19.5", ThermostatSmartState.TARGETS[7])
        assertEquals("22.0", ThermostatSmartState.TARGETS.last())
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun commandPublishesOnlyAllowlistedTargets() {
        val manager = MqttManager()
        val queue = MqttManager::class.java.getDeclaredField("messageQueue")
            .apply { isAccessible = true }.get(manager) as ConcurrentLinkedQueue<MqttQueuedMessage>
        assertTrue(manager.setSmartThermostatTarget("thermostat_bath", "19.5"))
        assertFalse(manager.setSmartThermostatTarget("thermostat_bath", "22.5"))
        assertFalse(manager.setSmartThermostatTarget("thermostat_bath", "19.3"))
        assertFalse(manager.setSmartThermostatTarget("thermostat_kitchen", "20.0"))
        assertEquals(listOf(MqttQueuedMessage("zara/interface/climate/thermostat_bath/target_temperature/cmd", "19.5", false)),
            queue.toList())
    }

    @Test fun setpointSliderPublishesOnlyOnRelease() {
        val source = listOf(File("src/main/java/com/domopi/app/ui/screens/HvacScreen.kt"),
            File("app/src/main/java/com/domopi/app/ui/screens/HvacScreen.kt")).first { it.exists() }.readText()
        val slider = source.substringAfter("value = shownTarget.coerceIn(16f, 22f),").substringBefore("valueRange")
        assertTrue(slider.contains("onValueChange = { pendingTarget = it }"))
        assertTrue(slider.substringAfter("onValueChangeFinished").contains("target_temperature/cmd"))
        assertFalse(slider.substringBefore("onValueChangeFinished").contains("publish"))
    }
}
