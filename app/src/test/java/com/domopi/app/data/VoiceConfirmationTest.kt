package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceConfirmationTest {
    @Test fun onlyTheSingleWordsConfirmoOrConferma() {
        assertEquals(true, VoiceConfirmation.classify("confermo"))
        assertEquals(true, VoiceConfirmation.classify(" Conferma. "))
        listOf("sì", "ok", "va bene", "sì confermo", "non confermo", "confermo lo spegnimento", "", null).forEach {
            assertEquals(it, null, VoiceConfirmation.classify(it))
        }
    }

    @Test fun cancelWords() {
        listOf("no", "Annulla", "annullo!").forEach { assertEquals(it, false, VoiceConfirmation.classify(it)) }
    }

    @Test fun validForSixtySeconds() {
        assertFalse(VoiceConfirmation.expired(1_000, 61_000))
        assertTrue(VoiceConfirmation.expired(1_000, 61_001))
        assertTrue(VoiceConfirmation.expired(2_000, 1_000))
    }
}
