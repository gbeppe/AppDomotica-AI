package com.domopi.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Separate read-only service: never publishes MQTT commands. */
class HouseAiRepository {
    private fun validatedBase(baseUrl: String): URL {
        val base = URL(baseUrl.trim().trimEnd('/'))
        require(base.protocol in listOf("http", "https") && base.host.isNotBlank() && base.userInfo == null &&
            base.query == null && base.ref == null)
        return base
    }

    private fun readBounded(connection: HttpURLConnection): String =
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val result = StringBuilder()
            val chunk = CharArray(8192)
            while (true) {
                val count = reader.read(chunk)
                if (count < 0) break
                check(result.length + count <= 2_000_000) { "Risposta troppo grande" }
                result.append(chunk, 0, count)
            }
            result.toString()
        }

    private fun checkStatus(code: Int) {
        check(code == 200) {
            when (code) {
                401 -> "Token backend non valido."
                400 -> "Domanda o dati inviati non validi."
                502 -> "Pianificatore o sorgente non disponibili. Riprova più tardi."
                else -> "Servizio non disponibile (HTTP $code)."
            }
        }
    }

    suspend fun report(baseUrl: String, token: String, day: String): JSONObject = withContext(Dispatchers.IO) {
        java.time.LocalDate.parse(day)
        val base = validatedBase(baseUrl)
        require(token.isNotBlank())
        val connection = URL("${base.toExternalForm()}/v1/report?day=$day").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 120000
            connection.setRequestProperty("Authorization", "Bearer $token")
            checkStatus(connection.responseCode)
            val text = readBounded(connection)
            JSONObject(text).also { check(it.optString("schema") == "house_ai.daily_report.v1") }
        } finally {
            connection.disconnect()
        }
    }

    // Node-RED log evidence is resolved by the backend using its configured log root.
    // Android sends questions, never Raspberry filesystem paths or SSH credentials.
    suspend fun assistant(
        baseUrl: String,
        token: String,
        question: String,
        energy: EnergySmartState,
        connected: Boolean,
    ): EnergyAssistantAnswer = withContext(Dispatchers.IO) {
        val base = validatedBase(baseUrl)
        require(token.isNotBlank() && question.isNotBlank() && question.length <= 1000)
        val observations = JSONObject()
        energy.validObservations().forEach { (metric, observation) ->
            observations.put(metric, JSONObject()
                .put("value", observation.value)
                .put("received_at_ms", observation.receivedAtMs)
                .put("retained", observation.retained)
                .put("source_topic", observation.sourceTopic))
        }
        val body = JSONObject()
            .put("question", question)
            .put("current_energy", JSONObject()
                .put("schema", "house_ai.current_energy_input.v1")
                .put("connected", connected)
                .put("observations", observations))
            .toString()
        val connection = URL("${base.toExternalForm()}/v1/assistant/query")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 120000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            checkStatus(connection.responseCode)
            EnergyAssistantAnswer.fromJson(JSONObject(readBounded(connection)))
        } finally {
            connection.disconnect()
        }
    }
}
