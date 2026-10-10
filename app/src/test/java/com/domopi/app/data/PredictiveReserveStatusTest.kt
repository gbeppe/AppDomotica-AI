package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictiveReserveStatusTest {
    private fun effect(control: Boolean, mode: String?, ready: Boolean?) = PredictiveReserveStatus.effect(control, mode, ready)

    @Test fun offIsNeutralWhateverTheMode() {
        for (mode in listOf("SHADOW", "ACTIVE", null)) {
            assertEquals(PrSeverity.NEUTRAL, effect(false, mode, true).severity)
            assertEquals("PR OFF", effect(false, mode, true).badge)
        }
    }

    @Test fun onInShadowWarnsThatNightDryNeverStarts() {
        val e = effect(true, "SHADOW", true)
        assertEquals(PrSeverity.WARNING, e.severity)
        assertTrue(e.explanation.contains("non partirà"))
    }

    @Test fun onInActiveDependsOnReadiness() {
        assertEquals(PrSeverity.OK, effect(true, "ACTIVE", true).severity)
        assertTrue(effect(true, "ACTIVE", true).explanation.contains("eco26dry"))
        assertEquals(PrSeverity.WARNING, effect(true, "ACTIVE", false).severity)
        assertEquals(PrSeverity.UNKNOWN, effect(true, "ACTIVE", null).severity)
    }

    @Test fun unknownModeIsNeverPresentedAsSafe() {
        assertEquals(PrSeverity.UNKNOWN, effect(true, null, true).severity)
        assertTrue(PredictiveReserveStatus.details(null, null).contains("non ricevuta"))
    }

    @Test fun toggleTextDescribesTheEffectOfEnabling() {
        assertEquals(effect(true, "SHADOW", true).explanation, PredictiveReserveStatus.toggleExplanation(true, "SHADOW", true))
        assertTrue(PredictiveReserveStatus.toggleExplanation(false, "ACTIVE", true).contains("logica AI Climate normale"))
    }

    @Test fun mqttManagerReadsTheNewReadOnlyTopics() {
        val manager = MqttManager()
        val handle = MqttManager::class.java.getDeclaredMethod("handleIncomingMessage", String::class.java, String::class.java)
            .apply { isAccessible = true }
        assertNull(manager.aiSettings.value.predictiveReserveMode)
        handle.invoke(manager, "zara/interface/predictive_reserve/operating_mode/stat", "ACTIVE")
        handle.invoke(manager, "zara/interface/predictive_reserve/ready/stat", "true")
        assertEquals("ACTIVE", manager.aiSettings.value.predictiveReserveMode)
        assertEquals(true, manager.aiSettings.value.predictiveReserveReady)
        handle.invoke(manager, "zara/interface/predictive_reserve/operating_mode/stat", "bogus")
        handle.invoke(manager, "zara/interface/predictive_reserve/ready/stat", "false")
        assertNull(manager.aiSettings.value.predictiveReserveMode)
        assertEquals(false, manager.aiSettings.value.predictiveReserveReady)
        handle.invoke(manager, "zara/interface/predictive_reserve/control_enabled/stat", "true")
        assertTrue(manager.aiSettings.value.predictiveReserveEnabled)
    }
}
