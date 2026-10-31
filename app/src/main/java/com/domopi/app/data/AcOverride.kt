package com.domopi.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * Manual priority of the air conditioner (AI-CLIMATE-AC-OVERRIDE.1, phase T4 of COMANDI_A_TEMPO.md):
 * AI Climate keeps [mode] (off, heat, cool, dry) until [label], then switches it off and decides again.
 * [solarOnly] (AI-CLIMATE-AC-OVERRIDE.2): closed after 15 minutes of battery or grid draw.
 */
@Serializable
data class AcOverride(val mode: String, val setpoint: Int?, val label: String, val solarOnly: Boolean = false) {
    val text: String get() = when (mode) {
        "off" -> "spento"
        "heat" -> "caldo $setpoint °C"
        "cool" -> "raffrescamento $setpoint °C"
        else -> "deumidifica $setpoint °C"
    } + (if (solarOnly) " solo sole" else "") + " fino alle $label"

    companion object {
        const val TOPIC_CMD = "zara/interface/climate/ac/override/cmd"
        const val AUTO = "auto"
        /** App buttons: 2 hours, as the voice command without an end (user's decisions of 23/10/2026; max 6). */
        const val BUTTON_HOURS = 2L
        private val LABEL = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
        private val RANGES = mapOf("heat" to 16..22, "cool" to 22..28, "dry" to 23..25)
        private val json = Json { ignoreUnknownKeys = true }

        /** State of zara/interface/climate/ac/override/stat: the active priority (or null) and a closing reason. */
        fun parse(raw: String): Pair<AcOverride?, String?> = try {
            val o: JsonObject = json.parseToJsonElement(raw).jsonObject
            val active = o["active"]?.jsonPrimitive?.booleanOrNull == true
            val mode = o["mode"]?.jsonPrimitive?.contentOrNull
            val setpoint = o["setpoint"]?.jsonPrimitive?.intOrNull
            val label = o["label"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val closed = o["closed_reason"]?.jsonPrimitive?.contentOrNull?.take(200)
            val valid = active && LABEL.matches(label) &&
                (mode == "off" || (mode in RANGES && setpoint != null && setpoint in RANGES.getValue(mode!!)))
            (if (valid) AcOverride(mode!!, if (mode == "off") null else setpoint, label, o["solar_only"]?.jsonPrimitive?.booleanOrNull == true) else null) to (if (active) null else closed)
        } catch (_: Exception) { null to null }

        fun command(mode: String, setpoint: Int? = null, now: OffsetDateTime = OffsetDateTime.now(), solarOnly: Boolean = false): String {
            require(mode == "off" || (mode in RANGES && setpoint != null && setpoint in RANGES.getValue(mode)))
            val until = now.plusHours(BUTTON_HOURS).truncatedTo(ChronoUnit.SECONDS)
            return if (mode == "off") """{"mode":"off","until":"$until"}"""
            else """{"mode":"$mode","setpoint":$setpoint,"until":"$until"${if (solarOnly) ""","solar_only":true""" else ""}}"""
        }
    }
}
