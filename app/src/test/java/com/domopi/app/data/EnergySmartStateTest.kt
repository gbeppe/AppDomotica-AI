package com.domopi.app.data

import org.junit.Assert.*
import org.junit.Test

class EnergySmartStateTest {
    @Test fun summaryExplainsSignedFlowAndInvalidDataWithoutLosingSpeechText() {
        val state = EnergySmartState().observe("energy/grid/power_raw/stat", "-500", 100, true, "zara/interface/energy/grid/power_raw/stat")
            .observe("energy/battery/power_raw/stat", "100", 100, true, "zara/interface/energy/battery/power_raw/stat")
            .observe("energy/solar/power/stat", "-10", 100, true, "zara/interface/energy/solar/power/stat")
        val text = state.summary(false)
        assertTrue(text.contains("Immissione rete: 500,0 W"))
        assertTrue(text.contains("Scarica batteria: 100,0 W"))
        assertTrue(text.contains("Fotovoltaico: dato non valido"))
        assertTrue(text.contains("disconnesso"))
        val chunks = speechChunks(text.repeat(30), 50)
        assertEquals(text.repeat(30), chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 50 })
    }

    @Test fun missingZeroInvalidAndOutOfOrderRemainDistinct() {
        val empty = EnergySmartState()
        assertTrue(empty.validObservations().isEmpty())
        val zero = empty.observe("energy/solar/power/stat", "0", 100, true,
            "zara/interface/energy/solar/power/stat")
        assertEquals(0.0, zero.reading("solar_power_w")!!.value!!, 0.0)
        val invalid = zero.observe("energy/solar/power/stat", "NaN", 200, false,
            "zara/interface/energy/solar/power/stat")
        assertEquals("invalid", invalid.reading("solar_power_w")!!.quality)
        assertFalse(invalid.validObservations().containsKey("solar_power_w"))
        assertSame(invalid, invalid.observe("energy/solar/power/stat", "10", 199, false,
            "zara/interface/energy/solar/power/stat"))
    }

    @Test fun onlyPublicMappedStateTopicsAreAccepted() {
        var state = EnergySmartState()
        EnergySmartState.topics.forEach { (topic, metric) ->
            state = state.observe(topic, "42.5", 100, false, "custom/prefix/$topic")
            assertEquals(42.5, state.reading(metric)!!.value!!, 0.0)
        }
        val unchanged = state
        for (topic in listOf("energy/grid/power_raw/cmd", "TeslaPowerwall/SOE", "energy/unknown/stat"))
            assertSame(unchanged, unchanged.observe(topic, "50", 200, false, topic))
    }

    @Test fun socRangeAndSourceMetadataArePreserved() {
        val valid = EnergySmartState().observe("energy/battery/soc/stat", "81,2", 123, true,
            "zara/interface/energy/battery/soc/stat")
        val observation = valid.reading("battery_soc_percent")!!
        assertEquals(81.2, observation.value!!, 0.0)
        assertEquals(123, observation.receivedAtMs)
        assertTrue(observation.retained)
        val invalid = valid.observe("energy/battery/soc/stat", "101", 124, false,
            "zara/interface/energy/battery/soc/stat")
        assertNull(invalid.reading("battery_soc_percent")!!.value)
    }
}
