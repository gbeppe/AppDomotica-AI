package com.domopi.app.data

import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * Manual priority of the VMC speed (AI-CLIMATE-VMC-OVERRIDE.1, phase T2 of COMANDI_A_TEMPO.md):
 * AI Climate keeps the chosen speed until [label], then takes over again.
 */
@Serializable
data class VmcOverride(val speed: Int, val label: String) {
    companion object {
        const val TOPIC_CMD = "zara/interface/ventilation/vmc/override/cmd"
        const val AUTO = "auto"
        /** App buttons: 2 hours of manual priority (user's decision of 18/10/2026). */
        const val BUTTON_HOURS = 2L
        private val LABEL = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")

        /** zara/interface/ventilation/vmc/override/stat; null when not active or invalid. */
        fun parse(raw: String): VmcOverride? = try {
            val o = JSONObject(raw)
            val speed = o.optInt("speed", 0)
            val label = o.optString("label")
            if (o.optBoolean("active") && speed in 1..4 && LABEL.matches(label)) VmcOverride(speed, label) else null
        } catch (_: Exception) { null }

        fun command(speed: Int, now: OffsetDateTime = OffsetDateTime.now()): String {
            require(speed in 1..4)
            val until = now.plusHours(BUTTON_HOURS).truncatedTo(ChronoUnit.SECONDS)
            return """{"speed":$speed,"until":"$until"}"""
        }
    }
}
