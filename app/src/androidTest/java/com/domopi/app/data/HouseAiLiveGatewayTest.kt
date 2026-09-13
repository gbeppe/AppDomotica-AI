package com.domopi.app.data

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit opt-in: real model and read-only sources through a temporary SSH/ADB tunnel. */
class HouseAiLiveGatewayTest {
    @Test fun realGatewayAndThreeEvidenceSources() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("houseAiLive") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "live-gateway.json")
        val config = JSONObject(file.readText())
        try {
            var energy = EnergySmartState()
            val observations = config.getJSONObject("observations")
            for (metric in observations.keys()) {
                val observation = observations.getJSONObject(metric)
                val topic = observation.getString("source_topic")
                energy = energy.observe(topic.removePrefix("zara/interface/"),
                    observation.getDouble("value").toString(), observation.getLong("received_at_ms"),
                    observation.getBoolean("retained"), topic)
            }
            val question = "Indicami la produzione fotovoltaica corrente, i kWh prelevati dalla rete il 6 settembre 2026 e la produzione prevista nel log OpenMeteo per il 12 settembre 2026."
            val result = runBlocking {
                HouseAiRepository().assistant(config.getString("url"), config.getString("token"),
                    question, energy, false)
            }
            assertEquals(question, result.question)
            assertTrue(result.status in setOf("complete", "partial"))
            assertTrue(result.evidence.any { it.contains("Digital Twin") })
            assertTrue(result.evidence.any { it.contains("EmonCMS · feed 305") })
            assertTrue(result.evidence.any { it.contains("zara_previsione_kwh_openmeteo.log") })
            assertTrue(result.text.contains("2026-09-06"))
            assertTrue(result.text.contains("2026-09-12"))
            assertTrue(result.text.isNotBlank())
        } finally { file.delete() }
    }
}
