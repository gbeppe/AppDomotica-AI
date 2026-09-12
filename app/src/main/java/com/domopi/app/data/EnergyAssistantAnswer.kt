package com.domopi.app.data

import org.json.JSONObject

/** Same answer string is displayed and spoken. Evidence is a separate inspectable detail. */
data class EnergyAssistantAnswer(
    val text: String,
    val status: String,
    val question: String,
    val generatedAt: String,
    val evidence: List<String>,
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
            return EnergyAssistantAnswer(text, status, body.optString("question"), body.optString("generated_at"), sources)
        }
    }
}
