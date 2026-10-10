package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictiveReserveSmartStateTest {
    private val prefix = "zara/interface/"
    private fun PredictiveReserveSmartState.see(metric: String, payload: String, at: Long = 1000L) =
        observe("predictive_reserve/$metric/stat", payload, at, true, "${prefix}predictive_reserve/$metric/stat")

    @Test fun normalizesToTheBackendAllowlist() {
        val s = PredictiveReserveSmartState()
            .see("control_enabled", "true")
            .see("operating_mode", "active")
            .see("ready", "1")
        val values = s.validObservations().mapValues { it.value.value }
        assertEquals(mapOf("control_enabled" to "true", "operating_mode" to "ACTIVE", "ready" to "true"), values)
        assertEquals("zara/interface/predictive_reserve/ready/stat", s.validObservations().getValue("ready").sourceTopic)
    }

    @Test fun rejectsInvalidValuesCommandsAndOtherTopics() {
        val s = PredictiveReserveSmartState()
            .see("operating_mode", "MAYBE")
            .see("ready", "yes")
            .observe("predictive_reserve/control_enabled/cmd", "true", 1000L, false, "${prefix}predictive_reserve/control_enabled/cmd")
            .observe("stato_condizionatore/stato_attuale/stat", "ON", 1000L, true, "${prefix}stato_condizionatore/stato_attuale/stat")
        assertTrue(s.validObservations().isEmpty())
    }

    @Test fun olderMessagesNeverOverwriteNewerOnes() {
        val s = PredictiveReserveSmartState().see("operating_mode", "ACTIVE", at = 2000L).see("operating_mode", "SHADOW", at = 1000L)
        assertEquals("ACTIVE", s.validObservations().getValue("operating_mode").value)
    }
}
