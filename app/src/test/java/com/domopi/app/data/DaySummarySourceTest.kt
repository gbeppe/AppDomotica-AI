package com.domopi.app.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fase 3.5 C: "Com'è andata oggi" uses the fixed backend endpoint and is never read aloud by itself. */
class DaySummarySourceTest {
    private fun source(path: String) = listOf(File("src/main/java/com/domopi/app/$path"),
        File("app/src/main/java/com/domopi/app/$path")).first { it.exists() }.readText()

    @Test fun repositoryCallsTheDaySummaryEndpoint() {
        val repository = source("data/HouseAiRepository.kt")
        assertTrue(repository.contains("/v1/summary/day"))
        assertTrue(repository.contains("EnergyAssistantAnswer.fromJson(JSONObject(readBounded(connection)))"))
    }

    @Test fun buttonShowsTheSummaryWithoutSpeaking() {
        val screen = source("ui/screens/AiSmartScreen.kt")
        assertTrue(screen.contains("Text(\"Com'è andata oggi\")"))
        assertTrue(screen.contains("onDaySummary = { daySummary() }"))
        val body = screen.substringAfter("fun daySummary() {").substringBefore("// A spoken question")
        assertFalse(body.contains("speak("))
    }
}
