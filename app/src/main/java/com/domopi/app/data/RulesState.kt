package com.domopi.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Rules published by RULES-ENGINE on .20 (zara/interface/rules/list/stat), sent to AI Smart. */
data class RulesState(val engineMode: String? = null, val rules: List<RuleSummary> = emptyList()) {
    val known: Boolean get() = engineMode != null

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val ID = Regex("[a-z0-9-]{3,40}")
        private val STATUSES = setOf("test", "active", "paused", "expired")

        fun parse(raw: String): RulesState? = try {
            val o = json.parseToJsonElement(raw) as JsonObject
            val mode = o["engine_mode"]?.jsonPrimitive?.content?.takeIf { it == "shadow" || it == "active" } ?: return null
            val rules = o["rules"]?.jsonArray?.mapNotNull { e ->
                val r = e as? JsonObject ?: return@mapNotNull null
                val id = r["id"]?.jsonPrimitive?.content?.takeIf { ID.matches(it) } ?: return@mapNotNull null
                val text = r["text"]?.jsonPrimitive?.content?.takeIf { it.length <= 300 } ?: return@mapNotNull null
                val status = r["status"]?.jsonPrimitive?.content?.takeIf { it in STATUSES } ?: return@mapNotNull null
                val tool = (r["action"] as? JsonObject)?.get("tool")?.jsonPrimitive?.content ?: return@mapNotNull null
                RuleSummary(id, text, status, tool)
            }.orEmpty()
            RulesState(mode, rules.take(20))
        } catch (_: Exception) { null }
    }
}

data class RuleSummary(val id: String, val text: String, val status: String, val actionTool: String)

/** A rule change confirmed by the user: published on zara/interface/rules/<verb>/cmd. */
data class RuleAction(val verb: String, val payload: String)
