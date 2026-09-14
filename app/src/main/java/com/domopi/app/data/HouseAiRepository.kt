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
        val loopback = base.host in setOf("127.0.0.1", "localhost", "::1", "[::1]")
        require((base.protocol == "https" || base.protocol == "http" && loopback) &&
            base.host.isNotBlank() && base.userInfo == null &&
            base.query == null && base.ref == null)
        return base
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://domopi.tailf30ba8.ts.net"
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

    suspend fun stackHealth(baseUrl: String, token: String, digitalTwinConnected: Boolean): StackHealthState =
        withContext(Dispatchers.IO) {
            val base = validatedBase(baseUrl)
            require(token.isNotBlank())
            val started = System.currentTimeMillis()
            val connection = URL("${base.toExternalForm()}/v1/health/details")
                .openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10000
                connection.readTimeout = 30000
                connection.setRequestProperty("Authorization", "Bearer $token")
                checkStatus(connection.responseCode)
                val body = JSONObject(readBounded(connection))
                check(body.optString("schema") == "house_ai.stack_health.v1")
                val checkedAt = body.getLong("checked_at_ms")
                val states = mutableMapOf<StackNode, StackNodeState>()
                states[StackNode.PRIVATE_ROUTE] = StackNodeState(StackNode.PRIVATE_ROUTE,
                    NodeStatus.ONLINE, System.currentTimeMillis() - started,
                    "HTTPS e certificato verificati", checkedAt)
                states[StackNode.DIGITAL_TWIN] = StackNodeState(StackNode.DIGITAL_TWIN,
                    if (digitalTwinConnected) NodeStatus.ONLINE else NodeStatus.DEGRADED,
                    detail = if (digitalTwinConnected) "Broker MQTT collegato" else "Broker MQTT non collegato",
                    lastCheckedMs = checkedAt)
                val mapping = mapOf("backend" to StackNode.BACKEND, "gateway" to StackNode.GATEWAY,
                    "groq" to StackNode.GROQ, "emoncms" to StackNode.EMONCMS, "logs" to StackNode.LOGS)
                val components = body.getJSONArray("components")
                repeat(components.length()) { index ->
                    val item = components.getJSONObject(index)
                    val node = mapping[item.getString("id")] ?: error("Componente sconosciuto")
                    val status = when (item.getString("status")) {
                        "online" -> NodeStatus.ONLINE
                        "offline" -> NodeStatus.OFFLINE
                        "degraded" -> NodeStatus.DEGRADED
                        "unknown" -> NodeStatus.UNKNOWN
                        else -> error("Stato componente sconosciuto")
                    }
                    states[node] = StackNodeState(node, status,
                        item.optLong("latency_ms").takeIf { item.has("latency_ms") },
                        item.optString("detail"),
                        item.optLong("last_checked_ms").takeIf { item.has("last_checked_ms") && !item.isNull("last_checked_ms") })
                }
                check(states.keys.containsAll(StackNode.entries))
                val blockers = setOf(StackNode.PRIVATE_ROUTE, StackNode.BACKEND, StackNode.GATEWAY)
                val overall = when {
                    blockers.any { states[it]?.status == NodeStatus.OFFLINE } -> StackOverallStatus.OFFLINE
                    states.values.all { it.status == NodeStatus.ONLINE } -> StackOverallStatus.ONLINE
                    else -> StackOverallStatus.DEGRADED
                }
                StackHealthState(overall, states)
            } finally { connection.disconnect() }
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
        climate: ClimateSmartState = ClimateSmartState(),
        lights: AiSmartState = AiSmartState(),
        context: AssistantContext? = null,
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
        val climateObservations = JSONObject()
        climate.validObservations().forEach { (metric, observation) ->
            climateObservations.put(metric, JSONObject().put("value", observation.value)
                .put("received_at_ms", observation.receivedAtMs).put("retained", observation.retained)
                .put("source_topic", observation.sourceTopic))
        }
        body.put("current_climate", JSONObject().put("schema", "house_ai.current_climate_input.v1")
            .put("connected", connected).put("observations", climateObservations))
        val lightObservations = JSONObject()
        lights.validLightObservations().forEach { (light, observation) ->
            lightObservations.put(light, JSONObject().put("value", observation.payload.toBooleanStrict())
                .put("received_at_ms", observation.receivedAtMs).put("retained", observation.retained)
                .put("source_topic", observation.sourceTopic))
        }
        body.put("current_lights", JSONObject().put("schema", "house_ai.current_lights_input.v1")
            .put("connected", connected).put("observations", lightObservations))
        context?.let { body.put("conversation_context", JSONObject()
            .put("domain", it.domain).put("focus", it.focus)) }
        val requestBody = body.toString()
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
            connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            checkStatus(connection.responseCode)
            EnergyAssistantAnswer.fromJson(JSONObject(readBounded(connection)))
        } finally {
            connection.disconnect()
        }
    }
}
