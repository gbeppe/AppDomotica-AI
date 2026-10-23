package com.domopi.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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

    private fun checkStatus(connection: HttpURLConnection) {
        val code = connection.responseCode
        if (code == 200) return
        // Small error bodies only: they carry the backend error code and the retry wait.
        val body = runCatching {
            connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val chunk = CharArray(4096)
                val count = reader.read(chunk)
                if (count > 0) String(chunk, 0, count) else null
            }
        }.getOrNull()
        throw IllegalStateException(HouseAiErrors.http(code, body))
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
                checkStatus(connection)
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

    /** Fase 3.5 C: today's totals and tomorrow's forecast; fixed backend tools, no model call. */
    suspend fun daySummary(baseUrl: String, token: String): EnergyAssistantAnswer = withContext(Dispatchers.IO) {
        val base = validatedBase(baseUrl)
        require(token.isNotBlank())
        val connection = URL("${base.toExternalForm()}/v1/summary/day").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 60000
            connection.setRequestProperty("Authorization", "Bearer $token")
            checkStatus(connection)
            EnergyAssistantAnswer.fromJson(JSONObject(readBounded(connection)))
        } finally {
            connection.disconnect()
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
            checkStatus(connection)
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
        reserve: PredictiveReserveSmartState = PredictiveReserveSmartState(),
        thermostats: ThermostatSmartState = ThermostatSmartState(),
        environment: EnvironmentSmartState = EnvironmentSmartState(),
        heating: HeatingSmartState = HeatingSmartState(),
        rules: RulesState = RulesState(),
        profile: HeatingProfileState = HeatingProfileState(),
        followUp: AssistantFollowUp? = null,
        systemHealth: Map<String, HostHealth> = emptyMap(),
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
        val reserveObservations = JSONObject()
        reserve.validObservations().forEach { (metric, observation) ->
            reserveObservations.put(metric, JSONObject().put("value", observation.value)
                .put("received_at_ms", observation.receivedAtMs).put("retained", observation.retained)
                .put("source_topic", observation.sourceTopic))
        }
        body.put("current_predictive_reserve", JSONObject()
            .put("schema", "house_ai.current_predictive_reserve_input.v1")
            .put("connected", connected).put("observations", reserveObservations))
        val thermostatObservations = JSONObject()
        thermostats.validObservations().forEach { (key, observation) ->
            thermostatObservations.put(key, JSONObject().put("value", observation.value)
                .put("received_at_ms", observation.receivedAtMs).put("retained", observation.retained)
                .put("source_topic", observation.sourceTopic))
        }
        body.put("current_thermostats", JSONObject()
            .put("schema", "house_ai.current_thermostats_input.v1")
            .put("connected", connected).put("observations", thermostatObservations))
        val environmentObservations = JSONObject()
        environment.validObservations().forEach { (key, observation) ->
            environmentObservations.put(key, JSONObject().put("value", observation.value)
                .put("received_at_ms", observation.receivedAtMs).put("retained", observation.retained)
                .put("source_topic", observation.sourceTopic))
        }
        body.put("current_environment", JSONObject()
            .put("schema", "house_ai.current_environment_input.v1")
            .put("connected", connected).put("observations", environmentObservations))
        val heatingObservations = JSONObject()
        heating.validObservations().forEach { (key, observation) ->
            heatingObservations.put(key, JSONObject().put("value", observation.value)
                .put("received_at_ms", observation.receivedAtMs).put("retained", observation.retained)
                .put("source_topic", observation.sourceTopic))
        }
        body.put("current_heating", JSONObject()
            .put("schema", "house_ai.current_heating_input.v1")
            .put("connected", connected).put("observations", heatingObservations))
        if (rules.known) {
            val list = org.json.JSONArray()
            rules.rules.forEach {
                list.put(JSONObject().put("id", it.id).put("text", it.text).put("status", it.status)
                    .put("action", JSONObject().put("tool", it.actionTool)))
            }
            body.put("current_rules", JSONObject().put("schema", "house_ai.current_rules_input.v1")
                .put("engine_mode", rules.engineMode).put("rules", list))
        }
        heatingProfileJson(profile)?.let { body.put("current_heating_profile", it) }
        systemHealthJson(systemHealth)?.let { body.put("current_system_health", it) }
        val lightObservations = JSONObject()
        lights.validLightObservations().forEach { (light, observation) ->
            val item = JSONObject().put("value", observation.payload.toBooleanStrict())
                .put("received_at_ms", observation.receivedAtMs).put("retained", observation.retained)
                .put("source_topic", observation.sourceTopic)
            lightObservations.put(light, item)
        }
        val lightCommands = JSONObject()
        lights.lightCommands.forEach { (light, command) ->
            lightCommands.put(light, JSONObject(command.toJson()))
        }
        body.put("current_lights", JSONObject().put("schema", "house_ai.current_lights_input.v1")
            .put("connected", connected).put("observations", lightObservations)
            .put("commands", lightCommands))
        context?.let { body.put("conversation_context", JSONObject()
            .put("domain", it.domain).put("focus", it.focus)) }
        followUp?.let { body.put("follow_up", JSONObject()
            .put("question", it.question).put("clarification", it.clarification)) }
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
            checkStatus(connection)
            EnergyAssistantAnswer.fromJson(JSONObject(readBounded(connection)))
        } finally {
            connection.disconnect()
        }
    }
}

/** Health summaries of .20 and .15 (phase S3) as house_ai.current_system_health_input.v1, or null if none yet. */
internal fun systemHealthJson(hosts: Map<String, HostHealth>): JSONObject? {
    if (hosts.isEmpty()) return null
    val out = JSONObject()
    hosts.filterKeys { it in SystemHealth.HOSTS }.forEach { (name, h) ->
        val checks = JSONArray()
        h.checks.take(100).forEach { c ->
            checks.put(JSONObject().put("id", c.id.take(120)).put("title", c.title.take(120)).put("ok", c.ok)
                .put("detail", c.detail.take(300)).put("advice", c.advice.take(300)))
        }
        out.put(name, JSONObject().put("checked_at_ms", h.checkedAtMs).put("ok", h.ok).put("checks", checks))
    }
    return JSONObject().put("schema", "house_ai.current_system_health_input.v1").put("hosts", out)
}

/** HEATING-PROFILE.2 state as house_ai.current_heating_profile_input.v1, or null while incomplete. */
internal fun heatingProfileJson(p: HeatingProfileState): JSONObject? {
    val mode = p.mode ?: return null
    val timer = p.timerMode?.takeIf { it == "shadow" || it == "active" } ?: return null
    val profile = p.profile ?: return null
    val thermostats = JSONObject()
    p.plan.filterKeys { it == "thermostat_living" || it == "thermostat_bath" }.forEach { (id, t) ->
        val next = t.next?.let {
            JSONObject().put("day_offset", it.dayOffset).put("at", it.at).put("inside", it.inside)
                .put("value", it.value ?: JSONObject.NULL)
        }
        thermostats.put(id, JSONObject().put("inside", t.inside).put("value", t.value ?: JSONObject.NULL)
            .put("slot", t.slot ?: JSONObject.NULL).put("min", t.min ?: JSONObject.NULL)
            .put("max", t.max ?: JSONObject.NULL).put("next", next ?: JSONObject.NULL))
    }
    return JSONObject().put("schema", "house_ai.current_heating_profile_input.v1").put("profile", profile)
        .put("mode", mode).put("outdoor_mean_72h", p.outdoorMean72h ?: JSONObject.NULL)
        .put("hours_of_data", p.hoursOfData).put("timer_mode", timer).put("thermostats", thermostats)
}
