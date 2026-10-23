package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantFollowUpTest {
    private fun answer(status: String, text: String = "Intendi il mese di calendario o gli ultimi 30 giorni?") =
        EnergyAssistantAnswer(text, status, "q", "2026-10-23T08:00:00+02:00", emptyList())

    @Test
    fun clarificationStartsAFollowUp() {
        val followUp = AssistantFollowUp.after("Prelievo nell'ultimo mese?", answer("clarification_required"), null, 1_000)
        assertEquals(AssistantFollowUp("Prelievo nell'ultimo mese?",
            "Intendi il mese di calendario o gli ultimi 30 giorni?", 1_000), followUp)
    }

    @Test
    fun otherAnswersEndIt() {
        listOf("complete", "partial", "insufficient_data").forEach {
            assertNull(AssistantFollowUp.after("Prelievo?", answer(it), null, 1_000))
        }
        assertNull(AssistantFollowUp.after("Prelievo?", answer("clarification_required", " "), null, 1_000))
    }

    @Test
    fun aSecondClarificationKeepsTheFirstQuestion() {
        val first = AssistantFollowUp("Prelievo nell'ultimo mese?", "Calendario o 30 giorni?", 0)
        val second = AssistantFollowUp.after("Il mese", answer("clarification_required", "Settembre o ottobre?"), first, 5_000)
        assertEquals("Prelievo nell'ultimo mese? Il mese", second?.question)
        assertEquals("Settembre o ottobre?", second?.clarification)
    }

    @Test
    fun valuesFitTheBackendLimitsAndTheWindow() {
        val followUp = AssistantFollowUp.after("a".repeat(800), answer("clarification_required", "b".repeat(800)), null, 0)!!
        assertEquals(500, followUp.question.length)
        assertEquals(500, followUp.clarification.length)
        assertTrue(followUp.validAt(AssistantFollowUp.WINDOW_MS))
        assertFalse(followUp.validAt(AssistantFollowUp.WINDOW_MS + 1))
        assertFalse(followUp.validAt(-1))
    }
}
