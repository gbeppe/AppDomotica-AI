package com.domopi.app.data

import org.junit.Assert.*
import org.junit.Test

class SmartControlStateTest {
    @Test fun catalogMirrorsAllDashboardClimateAndPlantControls() {
        val controls = SmartControlCatalog.controls
        assertEquals(23, controls.size)
        assertEquals(23, controls.map { it.id }.distinct().size)
        assertEquals(23, controls.map { it.stateTopic }.distinct().size)
        assertEquals(23, controls.map { it.commandTopic }.distinct().size)
        assertTrue(controls.all { it.stateTopic.endsWith("/stat") })
        assertTrue(controls.all { it.commandTopic.endsWith("/cmd") })
        assertEquals(9, controls.count { it.domain == SmartControlDomain.CLIMATE })
        assertEquals(14, controls.count { it.domain == SmartControlDomain.PLANTS })
    }

    @Test fun observesStateOnlyWithProvenanceAndKeepsUnknownChoiceReadable() {
        var state = SmartControlState().observe("fireplace/main/mode/stat", "Modalità futura", 20, true,
            "zara/interface/fireplace/main/mode/stat")
        state = state.observe("fireplace/main/mode/stat", "Manuale", 19, false,
            "zara/interface/fireplace/main/mode/stat")
        state = state.observe("fireplace/main/mode/cmd", "Manuale", 21, false,
            "zara/interface/fireplace/main/mode/cmd")
        assertEquals("Modalità futura", state.observation("fireplace_mode")?.value)
        assertTrue(state.observation("fireplace_mode")?.retained == true)
        assertEquals(1, state.validObservations().size)
    }

    @Test fun rejectsInvalidTypedStateWithoutInventingDefaults() {
        var state = SmartControlState()
        state = state.observe("ai/system_enabled/stat", "forse", 1, false,
            "zara/interface/ai/system_enabled/stat")
        state = state.observe("fireplace/main/start_time/stat", "25:80", 2, false,
            "zara/interface/fireplace/main/start_time/stat")
        state = state.observe("climate/thermostat_living/target_temperature/stat", "NaN", 3, false,
            "zara/interface/climate/thermostat_living/target_temperature/stat")
        assertTrue(state.validObservations().isEmpty())
    }
}
