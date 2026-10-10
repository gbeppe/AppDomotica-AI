package com.domopi.app.data

import org.json.JSONObject

/** Same answer string is displayed and spoken. Evidence is a separate inspectable detail. */
data class EnergyAssistantAnswer(
    val text: String,
    val status: String,
    val question: String,
    val generatedAt: String,
    val evidence: List<String>,
    val context: AssistantContext? = null,
    val lightCommands: List<LightCommand> = emptyList(),
    val thermostatCommands: List<ThermostatCommand> = emptyList(),
) {
    val statusLabel: String get() = when (status) {
        "complete" -> "Periodo coperto"
        "partial" -> "Dati con limiti"
        "insufficient_data" -> "Dati insufficienti"
        "clarification_required" -> "Serve un chiarimento"
        "not_configured" -> "Assistente da configurare"
        else -> "Risposta"
    }

    companion object {
        fun fromJson(body: JSONObject): EnergyAssistantAnswer {
            require(body.getString("schema") == "house_ai.assistant_answer.v1")
            val text = body.getString("answer")
            require(text.isNotBlank() && text.length <= 50_000)
            val status = body.getString("status")
            require(status in setOf("complete", "partial", "insufficient_data", "clarification_required", "not_configured"))
            val sources = mutableListOf<String>()
            val lightCommands = mutableListOf<LightCommand>()
            val thermostatCommands = mutableListOf<ThermostatCommand>()
            val results = body.optJSONArray("results")
            require((results?.length() ?: 0) <= 6)
            for (i in 0 until (results?.length() ?: 0)) {
                val item = results!!.getJSONObject(i)
                val result = item.getJSONObject("result")
                val detail = mutableListOf("Riferimento: ${item.getString("id")}")
                when (result.getString("schema")) {
                    "house_ai.energy_history.v1" -> {
                        val period = result.getJSONObject("period")
                        val source = result.getJSONObject("source")
                        detail += "EmonCMS · feed ${source.getInt("feed_id")} · campionamento ${source.getInt("interval_seconds")} s"
                        detail += "Periodo: ${period.getString("start")} → ${period.getString("end_exclusive")} escluso · Europe/Rome"
                        detail += "Copertura: ${String.format(java.util.Locale.ITALIAN, "%.1f", result.getDouble("coverage_ratio") * 100)}%"
                    }
                    "house_ai.current_energy_result.v1" -> {
                        detail += "Digital Twin · ${if (result.optBoolean("connected")) "collegato alla richiesta" else "disconnesso alla richiesta"}"
                        val observation = result.optJSONObject("observation")
                        if (observation == null) detail += "Dato non disponibile"
                        else {
                            detail += observation.getString("source_topic")
                            detail += "Ricezione: ${java.time.Instant.ofEpochMilli(observation.getLong("received_at_ms"))}"
                            detail += "Retained: ${if (observation.getBoolean("retained")) "sì" else "no"}"
                        }
                    }
                    "house_ai.current_air_conditioner.v1" -> {
                        detail += "Digital Twin · stato condizionatore dal registry"
                        detail += "Motivazione registrata da Node-RED; non deduzione causale"
                        val observations = result.getJSONObject("observations")
                        val topics = observations.keys().asSequence().map {
                            observations.getJSONObject(it).getString("source_topic")
                        }.toList().sorted()
                        detail += topics
                    }
                    "house_ai.current_lights.v1" -> {
                        detail += "Digital Twin · stati dichiarati dei punti luce dal registry"
                        val observations = result.getJSONObject("observations")
                        val topics = observations.keys().asSequence().map {
                            observations.getJSONObject(it).getString("source_topic")
                        }.toList().sorted()
                        detail += topics
                        val commands = result.optJSONObject("commands")
                        require((commands?.length() ?: 0) <= AiSmartState.lightEntities.size)
                        commands?.keys()?.asSequence()?.sorted()?.forEach { light ->
                            val command = commands.getJSONObject(light)
                            detail += "Node-RED · ${command.getString("entity_id")} · ${command.getString("stage")}"
                            detail += "Comando: ${command.getString("command_id")}"
                            detail += "Policy: ${command.getString("actor_policy")} · origine: ${command.getString("origin")}"
                            detail += command.getString("source_topic")
                            detail += "Evento: ${java.time.Instant.ofEpochMilli(command.getLong("observed_at_ms"))}"
                            detail += "Ricezione Android: ${java.time.Instant.ofEpochMilli(command.getLong("received_at_ms"))}"
                            detail += "Retained: ${command.getBoolean("retained")} · nessuna conferma fisica"
                        }
                    }
                    "house_ai.light_command.v1" -> {
                        val light = result.getString("light")
                        val state = result.getString("state")
                        require(light in AiSmartState.lightIdToTopic && state in setOf("on", "off"))
                        lightCommands += LightCommand(light, state == "on")
                        detail += "Comando luci validato · $light · $state"
                    }
                    "house_ai.current_thermostats.v1" -> {
                        detail += "Digital Twin · termostati riscaldamento · " +
                            if (result.optBoolean("connected")) "collegato alla richiesta" else "disconnesso alla richiesta"
                        val observations = result.getJSONObject("observations")
                        detail += observations.keys().asSequence().sorted().map {
                            val observation = observations.getJSONObject(it)
                            "${observation.getString("source_topic")} = ${observation.get("value")}"
                        }.toList()
                    }
                    "house_ai.thermostat_command.v1" -> {
                        val thermostat = result.getString("thermostat")
                        val target = result.getString("target_c")
                        require(thermostat in ThermostatSmartState.THERMOSTATS && target in ThermostatSmartState.TARGETS)
                        thermostatCommands += ThermostatCommand(thermostat, target)
                        detail += "Comando setpoint validato · $thermostat · $target °C"
                    }
                    "house_ai.backend_log_evidence.v1" -> {
                        detail += "Node-RED · ${result.getString("file")} · ${result.getString("day")}"
                        detail += "Tipo: ${result.getString("evidence_type")} · record trovati: ${result.getInt("matched_records")}"
                        val records = result.optJSONArray("records")
                        if (records != null && records.length() > 0) {
                            val lines = (0 until records.length()).map { records.getJSONObject(it).getJSONObject("source").getInt("line") }
                            detail += "Righe restituite: ${lines.joinToString(", ")}"
                        }
                        detail += "Righe invalide: ${result.getInt("invalid_lines")} · troncamento: ${result.getBoolean("truncated")}"
                    }
                    "house_ai.energy_daily_extreme.v1" -> {
                        val period = result.getJSONObject("period")
                        val source = result.getJSONObject("source")
                        detail += "EmonCMS · feed ${source.getInt("feed_id")} · campionamento ${source.getInt("interval_seconds")} s"
                        detail += "Periodo: ${period.getString("start")} → ${period.getString("end_exclusive")} escluso · Europe/Rome"
                        detail += "Giorni completi confrontati: ${result.getInt("eligible_days")} · " +
                            "esclusi: ${result.getJSONArray("excluded_days").length()}"
                    }
                    "house_ai.current_predictive_reserve.v1" -> {
                        detail += "Digital Twin · stato Predictive Reserve (PR-MODE.1) · " +
                            if (result.optBoolean("connected")) "collegato alla richiesta" else "disconnesso alla richiesta"
                        val observations = result.getJSONObject("observations")
                        detail += observations.keys().asSequence().sorted().map {
                            val observation = observations.getJSONObject(it)
                            "${observation.getString("source_topic")} = ${observation.getString("value")}"
                        }.toList()
                    }
                    "house_ai.energy_comparison.v1" -> detail += "Confronto: ${result.getString("left_id")} meno ${result.getString("right_id")}"
                    "house_ai.source_error.v1" -> detail += "Sorgente non disponibile"
                    else -> error("Schema evidenza non riconosciuto")
                }
                val limits = result.optJSONArray("limitations")
                for (j in 0 until (limits?.length() ?: 0)) detail += limits!!.getString(j)
                sources += detail.joinToString("\n")
            }
            val limits = body.optJSONArray("limitations")
            for (i in 0 until (limits?.length() ?: 0)) sources += limits!!.getString(i)
            val context = body.optJSONObject("conversation_context")?.let {
                val parsed = AssistantContext(it.getString("domain"), it.getString("focus"))
                require(parsed == AssistantContext("climate", "air_conditioner"))
                parsed
            }
            return EnergyAssistantAnswer(text, status, body.optString("question"), body.optString("generated_at"), sources, context, lightCommands,
                thermostatCommands)
        }
    }
}

data class AssistantContext(val domain: String, val focus: String)
data class LightCommand(val lightId: String, val state: Boolean)
data class ThermostatCommand(val thermostat: String, val targetC: String)
