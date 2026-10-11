package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EnvironmentSmartStateTest {
    private fun EnvironmentSmartState.see(relative: String, payload: String, at: Long = 1000L,
        source: String = "zara/interface/$relative") = observe(relative, payload, at, true, source)

    @Test fun keepsOnlyValidRoomSensors() {
        val s = EnvironmentSmartState()
            .see("env/living/temperature/stat", "22,4")
            .see("env/living/humidity/stat", "67")
            .see("env/outdoor/temperature/stat", "-3.5")
            .see("env/kids_room/humidex/stat", "26.1")
        assertEquals(mapOf("living.temperature" to 22.4, "living.humidity" to 67.0,
            "outdoor.temperature" to -3.5, "kids_room.humidex" to 26.1),
            s.validObservations().mapValues { it.value.value })
    }

    @Test fun rejectsInvalidUnknownCommandsAndOlder() {
        val s = EnvironmentSmartState()
            .see("env/living/temperature/stat", "22", at = 2000L)
            .see("env/living/temperature/stat", "21", at = 1000L)
            .see("env/living/humidity/stat", "140")
            .see("env/living/humidex/stat", "abc")
            .see("env/kitchen/temperature/stat", "20")
            .see("env/solar_altitude/stat", "10")
            .see("env/living/pressure/stat", "1013")
            .see("env/bedroom/temperature/stat", "20", source = "zara/interface/env/bedroom/temperature/cmd")
        assertEquals(mapOf("living.temperature" to 22.0), s.validObservations().mapValues { it.value.value })
    }
}
