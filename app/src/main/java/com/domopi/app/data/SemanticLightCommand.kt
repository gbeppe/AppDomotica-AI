package com.domopi.app.data

import kotlinx.serialization.json.*

/** Node-RED command evidence, independent of reported state and Android command submission. */
data class SemanticLightCommand(
    val lightId: String, val entityId: String, val commandId: String,
    val actor: String, val origin: String, val attribution: String,
    val requestedState: Boolean, val stage: String, val observedAtMs: Long,
    val receivedAtMs: Long, val retained: Boolean, val sourceTopic: String
) {
    fun supersedes(previous: SemanticLightCommand): Boolean =
        if (commandId == previous.commandId) {
            requestedState == previous.requestedState && actor == previous.actor &&
                origin == previous.origin && rank(stage) > rank(previous.stage)
        } else observedAtMs > previous.observedAtMs

    fun toJson(): String = buildJsonObject {
        put("schema", "domopi.semantic_command.v1")
        put("entity_id", entityId); put("command_id", commandId)
        put("actor", actor); put("actor_policy", "dashboard_only_v2")
        put("origin", origin); put("attribution", attribution)
        put("requested_state", requestedState); put("stage", stage)
        put("observed_at_ms", observedAtMs); put("received_at_ms", receivedAtMs)
        put("retained", retained); put("source_topic", sourceTopic)
        put("physical_confirmation", false); put("execution_confirmed", false)
        put("publisher_authenticated", false)
    }.toString()

    companion object {
        private val stages = mapOf("routed_commands" to "routed",
            "dispatched_commands" to "dispatched", "rm_receipts" to "send_invoked",
            "publisher_inputs" to "publisher_input")
        private fun rank(stage: String) = when (stage) {
            "routed" -> 0
            "dispatched" -> 1
            else -> 2
        }
        fun parse(topic: String, payload: String, receivedAtMs: Long, retained: Boolean,
                  sourceTopic: String): SemanticLightCommand? = runCatching {
            require(payload.length <= 16384 && receivedAtMs in 0..253402214400000L)
            val parts = topic.split('/')
            require(parts.size == 5 && parts[0] == "semantic" && parts[1] == "v1" && parts[4] == "stat")
            val expectedStage = stages[parts[2]] ?: return null
            val entity = parts[3]
            val light = AiSmartState.lightEntities.firstOrNull {
                it.topic.removeSuffix("/stat").replace('/', '.') == entity
            } ?: return null
            val obj = Json.parseToJsonElement(payload).jsonObject
            fun string(key: String): String {
                val value = obj[key] as? JsonPrimitive ?: error(key)
                require(value.isString && value.content.length in 1..256)
                return value.content
            }
            fun boolean(key: String): Boolean {
                val value = obj[key] as? JsonPrimitive ?: error(key)
                require(!value.isString)
                return value.booleanOrNull ?: error(key)
            }
            require(string("schema") == "domopi.semantic_command.v1")
            require(string("entity_id") == entity && string("stage") == expectedStage)
            require(string("actor_policy") == "dashboard_only_v2")
            val actor = string("actor")
            val origin = string("origin")
            require(actor in setOf("utente", "automazione"))
            require((actor == "utente") == (origin in setOf("app_interface", "nodered_dashboard")))
            val timestamp = obj["observed_at_ms"] as? JsonPrimitive ?: error("timestamp")
            require(!timestamp.isString)
            val observed = timestamp.longOrNull ?: error("timestamp")
            require(observed in 0..253402214400000L && observed <= receivedAtMs + 60000)
            require(!boolean("physical_confirmation") && !boolean("execution_confirmed") &&
                !boolean("publisher_authenticated"))
            require(sourceTopic == "zara/interface/$topic")
            SemanticLightCommand(light.id, entity, string("command_id"), actor, origin,
                string("attribution"), boolean("requested_state"), expectedStage,
                observed, receivedAtMs, retained, sourceTopic)
        }.getOrNull()
    }
}
