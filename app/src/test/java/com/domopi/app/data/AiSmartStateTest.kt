package com.domopi.app.data

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AiSmartStateTest {
    private fun sample(): AiSmartState {
        var state = AiSmartState()
        AiSmartState.lightTopics.forEachIndexed { i, topic ->
            if (i != 5) state = state.observe(topic, if (i == 4) "false" else "true", 1000, true)
        }
        return state.observe(AiSmartState.livingTopic, "24,2", 1100, true)
            .observe(AiSmartState.acsTopic, "41.1", 1200, false)
    }

    @Test fun mappedSnapshotDoesNotImplyPhysicalStateOrFreshness() {
        val text = sample().summary(true)
        assertTrue(text.contains("6 punti luce accesi, 1 spenti e 1 senza un dato valido, su 8"))
        assertTrue(text.contains("24,2 gradi"))
        assertTrue(text.contains("41,1 gradi"))
        assertTrue(text.contains("non conferma l'accensione fisica"))
        assertTrue(text.contains(AiSmartState.freshnessText))
    }

    @Test fun missingAndInvalidAreDistinctFromOffAndZero() {
        val initial = AiSmartState()
        assertTrue(initial.readings().all { it.quality == "missing" })
        assertTrue(initial.lightsText().contains("0 spenti e 8 senza un dato valido"))
        val state = initial.observe(AiSmartState.lightTopics[0], "broken", 1, false)
            .observe(AiSmartState.lightTopics[1], "off", 2, false)
            .observe(AiSmartState.livingTopic, "0", 3, false)
            .observe(AiSmartState.acsTopic, "NaN", 4, true)
        assertEquals("invalid", state.readings()[0].quality)
        assertEquals("Spento dichiarato", state.readings()[1].value)
        assertEquals(0.0, state.temperature(AiSmartState.livingTopic)!!, 0.0)
        assertNull(state.temperature(AiSmartState.acsTopic))
        assertEquals("invalid", state.readings().last().quality)
    }

    @Test fun invalidLatestValueReplacesOldValidValueAndOlderMessagesAreIgnored() {
        val state = sample().observe(AiSmartState.acsTopic, "Infinity", 1300, false)
        assertNull(state.temperature(AiSmartState.acsTopic))
        assertSame(state, state.observe(AiSmartState.acsTopic, "41.1", 1200, true))
        assertSame(state, state.observe(AiSmartState.acsTopic, "41.1", -1, true))
    }

    @Test fun commandsUnmappedLightsPumpsAndLegacyTopicsAreIgnored() {
        val state = sample()
        listOf("lights/living/power/cmd", "lights/prolunga/power/stat", "lights/cucina/power/stat",
            "pool/pump/power/stat", "pool/skimmer/power/stat", "emon/shellyACS/tempACS")
            .forEach { assertSame(state, state.observe(it, "true", 2000, true)) }
    }

    @Test fun repeatedMessagesDoNotDuplicateEntitiesOrCounts() {
        val state = sample()
        val repeated = state.observe(AiSmartState.lightTopics[0], "true", 2000, false)
        assertEquals(state.lightsText(), repeated.lightsText())
        assertEquals(10, repeated.readings().size)
    }

    @Test fun compoundQuestionsReturnBothTemperaturesWithLimits() {
        val text = sample().answer("Qual è la temperatura del living e dell’acqua sanitaria ACS?", true)
        assertTrue(text.contains("Temperatura living: 24,2"))
        assertTrue(text.contains("Acqua sanitaria ACS: 41,1"))
        assertTrue(text.contains(AiSmartState.freshnessText))
        assertTrue(sample().answer("Quante luci sono accese?", true).contains("6 punti luce accesi"))
        val all = sample().answer("Quante luci e quali temperature in soggiorno e ACS?", false)
        assertTrue(all.contains("Connessione assente"))
        assertTrue(all.contains("6 punti luce accesi"))
        assertTrue(all.contains("24,2"))
        assertTrue(all.contains("41,1"))
    }

    @Test fun unsupportedQuestionsAndCommandsDoNotInventAnswers() {
        val state = sample()
        assertTrue(state.answer("Apri il cancello e dimmi la temperatura living", true).contains("app classica"))
        assertTrue(state.answer("Quanto consuma il forno?", true).contains("non sono ancora disponibili"))
        assertTrue(AiSmartState().answer("Temperatura ACS?", true).contains("dato non disponibile"))
    }

    @Test fun provenanceKeepsActualPrefixRetainAndReceptionSeparateFromMeasurementAge() {
        val state = AiSmartState().observe(AiSmartState.livingTopic, "24.2", 0, true,
            "custom/house/env/living/temperature/stat")
        val reading = state.readings().first { it.entity.topic == AiSmartState.livingTopic }
        assertEquals("custom/house/env/living/temperature/stat", reading.observation!!.sourceTopic)
        assertTrue(reading.provenance().contains("01/01/1970 01:00:00"))
        assertTrue(reading.provenance().contains("Messaggio conservato dal broker"))
        assertTrue(reading.provenance().contains("Età della misura ignota"))
        assertTrue(sample().readings().last().provenance().contains("Messaggio non retained"))
    }

    @Test fun androidMappingMatchesBackendCatalogExactly() {
        val catalogFile = listOf(File("../backend/house_ai/current_catalog.json"),
            File("backend/house_ai/current_catalog.json")).first { it.isFile }
        val entities = Json.parseToJsonElement(catalogFile.readText()).jsonObject.getValue("entities").jsonArray
        val topics = entities.map { it.jsonObject.getValue("topic").jsonPrimitive.content.removePrefix("zara/interface/") }
        assertEquals(topics.toSet(), AiSmartState.topics.toSet())
        val lights = entities.filter { it.jsonObject.getValue("kind").jsonPrimitive.content == "light" }
        assertEquals(lights.map { it.jsonObject.getValue("topic").jsonPrimitive.content.removePrefix("zara/interface/") }.toSet(),
            AiSmartState.lightTopics.toSet())
    }
}
