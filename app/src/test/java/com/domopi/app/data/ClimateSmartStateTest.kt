package com.domopi.app.data

import org.junit.Assert.*
import org.junit.Test

class ClimateSmartStateTest {
    @Test fun acceptsOnlyValidatedRegistryStateTopics() {
        var state = ClimateSmartState()
        state = state.observe("stato_condizionatore/stato_attuale/stat", " ACCESO ", 10, true,
            "zara/interface/stato_condizionatore/stato_attuale/stat")
        state = state.observe("stato_condizionatore/temperatura_impostata_c/stat", "25,5", 11, false,
            "zara/interface/stato_condizionatore/temperatura_impostata_c/stat")
        state = state.observe("stato_condizionatore/sconosciuto/stat", "inventato", 12, false,
            "zara/interface/stato_condizionatore/sconosciuto/stat")
        assertEquals("ACCESO", state.validObservations()["current_state"]?.value)
        assertEquals("25,5", state.validObservations()["temperature_set_c"]?.value)
        assertEquals(2, state.validObservations().size)
    }

    @Test fun rejectsCommandsInvalidSetpointAndOlderObservations() {
        var state = ClimateSmartState().observe("stato_condizionatore/modalita_aria/stat", "cool", 20, false,
            "zara/interface/stato_condizionatore/modalita_aria/stat")
        state = state.observe("stato_condizionatore/modalita_aria/stat", "heat", 19, false,
            "zara/interface/stato_condizionatore/modalita_aria/stat")
        state = state.observe("stato_condizionatore/temperatura_impostata_c/stat", "abc", 21, false,
            "zara/interface/stato_condizionatore/temperatura_impostata_c/stat")
        state = state.observe("stato_condizionatore/stato_attuale/stat", "on", 22, false,
            "zara/interface/stato_condizionatore/stato_attuale/cmd")
        assertEquals("cool", state.validObservations()["air_mode"]?.value)
        assertEquals(1, state.validObservations().size)
    }
}
