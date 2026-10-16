package com.domopi.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Heating profile and thermostat timers published by HEATING-PROFILE on .20
 * (zara/interface/heating/{profile,timer/plan,profile/learning}/stat). One profile (the season)
 * for the house, slots per room; inside a slot the thermostat gets its max, outside its min.
 */
data class HeatingProfileState(
    val profile: String? = null,          // spento | ridotto | pieno
    val mode: String? = null,             // auto | spento | ridotto | pieno
    val outdoorMean72h: Double? = null,
    val hoursOfData: Int = 0,
    val timerMode: String? = null,        // shadow | active
    val plan: Map<String, ThermostatPlan> = emptyMap(),
    val corrections: List<HeatingCorrection> = emptyList(),
    val learningActive: Boolean = false,
) {
    val known: Boolean get() = profile != null
    val automatic: Boolean get() = mode == "auto"
    val shadow: Boolean get() = timerMode != "active"

    fun withProfile(raw: String) = parse(raw)?.let { o ->
        copy(profile = o.str("profile")?.takeIf { it in PROFILES }, mode = o.str("mode")?.takeIf { it in MODES },
            outdoorMean72h = o.num("outdoor_mean_72h"), hoursOfData = o.int("hours_of_data") ?: 0,
            timerMode = o.str("timer_mode"))
    } ?: this

    fun withPlan(raw: String) = parse(raw)?.let { o ->
        val thermostats = (o["thermostats"] as? JsonObject) ?: return@let null
        copy(plan = thermostats.mapNotNull { (id, v) ->
            val p = v as? JsonObject ?: return@mapNotNull null
            val next = (p["next"] as? JsonObject)?.let { n ->
                val at = n.str("at") ?: return@let null
                NextChange(n.int("day_offset") ?: 0, at, n.bool("inside") ?: false, n.num("value"))
            }
            id to ThermostatPlan(p.bool("inside") ?: false, p.num("value"), p.str("slot"), p.num("min"), p.num("max"), next)
        }.toMap())
    } ?: this

    fun withLearning(raw: String) = parse(raw)?.let { o ->
        val list = (o["corrections"] ?: return@let null).jsonArray.mapNotNull { e ->
            val c = e as? JsonObject ?: return@mapNotNull null
            HeatingCorrection(c.str("at") ?: return@mapNotNull null, c.str("thermostat") ?: return@mapNotNull null,
                c.num("value") ?: return@mapNotNull null, c.num("previous"), c.num("outdoor_mean_72h"), c.str("profile"))
        }
        copy(corrections = list.take(10), learningActive = o.bool("learning_active") ?: false)
    } ?: this

    companion object {
        val PROFILES = listOf("spento", "ridotto", "pieno")
        val MODES = listOf("auto") + PROFILES
        const val PIENO_BELOW = 10.0
        const val SPENTO_ABOVE = 16.0

        fun label(profile: String?) = when (profile) {
            "spento" -> "Spento"; "ridotto" -> "Ridotto"; "pieno" -> "Pieno"; "auto" -> "Automatico"; else -> "—"
        }

        private val json = Json { ignoreUnknownKeys = true }
        private fun parse(raw: String): JsonObject? = try { json.parseToJsonElement(raw) as? JsonObject } catch (_: Exception) { null }
        private fun JsonObject.prim(k: String) = this[k]?.takeIf { it !is JsonNull && it !is JsonObject }?.let {
            try { it.jsonPrimitive } catch (_: Exception) { null }
        }
        private fun JsonObject.str(k: String) = prim(k)?.takeIf { it.isString }?.content
        private fun JsonObject.num(k: String) = prim(k)?.doubleOrNull
        private fun JsonObject.int(k: String) = prim(k)?.intOrNull
        private fun JsonObject.bool(k: String) = prim(k)?.booleanOrNull
    }
}

data class ThermostatPlan(
    val inside: Boolean,
    val value: Double?,
    val slot: String?,
    val min: Double?,
    val max: Double?,
    val next: NextChange?,
)

/** Next slot boundary: dayOffset 0 = today, 1 = tomorrow. */
data class NextChange(val dayOffset: Int, val at: String, val inside: Boolean, val value: Double?)

data class HeatingCorrection(
    val at: String,
    val thermostat: String,
    val value: Double,
    val previous: Double?,
    val outdoorMean72h: Double?,
    val profile: String?,
)
