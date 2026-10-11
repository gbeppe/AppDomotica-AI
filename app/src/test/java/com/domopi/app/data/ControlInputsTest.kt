package com.domopi.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Manual controls send one real command per decision, never one per drag step or keystroke. */
class ControlInputsTest {
    private fun source(name: String) = listOf(File("src/main/java/com/domopi/app/ui/screens/$name"),
        File("app/src/main/java/com/domopi/app/ui/screens/$name")).first { it.exists() }.readText()

    @Test fun completeTimesAreNormalized() {
        assertEquals("07:05", ControlInputs.time("7:05"))
        assertEquals("21:30", ControlInputs.time(" 21:30 "))
        assertEquals("00:00", ControlInputs.time("00:00"))
    }

    @Test fun partialOrInvalidTimesAreNeverSent() {
        listOf("2", "21", "21:", "21:3", "24:00", "21:60", "--:--", "", "2130", "21.30").forEach {
            assertNull(it, ControlInputs.time(it))
        }
    }

    @Test fun sliderPositionsAreRoundedAndBounded() {
        assertEquals(3, ControlInputs.step(2.9999f, 1..6))
        assertEquals(2, ControlInputs.step(2.4f, 1..6))
        assertEquals(6, ControlInputs.step(7f, 1..6))
        assertEquals(1, ControlInputs.step(0.2f, 1..4))
    }

    @Test fun fireplaceLevelAndTimesPublishOnlyOnDecision() {
        val hvac = source("HvacScreen.kt")
        assertTrue(hvac.contains("onValueChange = { pendingLevel = it }"))
        assertTrue(hvac.contains("else mqttManager.publish(\"zara/interface/fireplace/main/level/cmd\", level.toString())"))
        assertFalse(hvac.contains("onValueChange = { mqttManager.publish(\"zara/interface/fireplace"))
        assertTrue(hvac.contains("keyboardActions = KeyboardActions(onDone = { commit() })"))
        assertTrue(hvac.contains("if (valid != null && valid != current) onCommit(valid)"))
    }

    @Test fun nightVmcSpeedPublishesOnlyOnRelease() {
        val managed = source("AiManagedScreen.kt")
        assertTrue(managed.contains("onValueChange = { pendingVmc = it }"))
        assertFalse(managed.contains("onValueChange = { mqttManager.publish(\"zara/interface/ai/night_vmc_max_speed"))
    }
}
