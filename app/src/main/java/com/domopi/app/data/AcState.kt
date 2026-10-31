package com.domopi.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * State of the air conditioner published by AI Climate (AI-CLIMATE-AC-STATE.1 on .20, retained
 * zara/interface/climate/ac/state/stat, domopi.ac_state.v1). It is driven by infrared only and has no
 * display: this is what it is doing ([mode], [setpoint]), the power reading that confirms it
 * ([confirm]), the requested state held back by a compressor timer ([target], [guard]) and the alarms.
 * The topic carries codes only; the Italian texts are here (user's decisions of 30/10/2026).
 */
@Serializable
data class AcState(
    val aiEnabled: Boolean,
    val mode: String,
    val setpoint: Double?,
    val by: String,
    val confirm: String,
    val powerW: Int,
    val target: Target?,
    val guard: Guard?,
    val alarms: List<String>,
    val reason: String,
    val surplusW: Int?,
    val soc: Int?,
    val temp: Double?,
    val humidex: Double?,
    val vmc: Int?,
) {
    @Serializable
    data class Target(val mode: String, val setpoint: Double?, val by: String, val until: String?)

    @Serializable
    data class Guard(val type: String, val until: String, val minutes: Int)

    /** Three icons (current → safeguard → requested) while a safeguard holds a change back, else one. */
    val inTransition: Boolean get() = target != null && guard != null

    val currentLabel: String get() = label(mode, setpoint)

    val confirmText: String get() = when (confirm) {
        "off" -> "✓ spento"
        "stopping" -> "◌ in spegnimento ($powerW W)"
        "running" -> "✓ in funzione · $powerW W"
        "no_power" -> "⚠ comandato acceso ma consuma $powerW W"
        else -> "◌ in avvio ($powerW W, solo ventola)"
    }

    val byText: String get() = when (by) {
        "user" -> "comando tuo"
        "watchdog" -> "riacceso dal vigilante"
        else -> "deciso da AI Climate"
    }

    val guardText: String get() = when (guard?.type) {
        "min_run" -> "compressore: tempo minimo acceso (${guard.minutes} min)"
        "min_off" -> "compressore: pausa minima (${guard.minutes} min)"
        else -> ""
    }

    val targetText: String get() = target?.let {
        if (it.by == "user") "richiesto da te" + (it.until?.let { u -> " fino alle ${hhmm(u)}" } ?: "")
        else "richiesto da AI Climate"
    } ?: ""

    val alarmTexts: List<String> get() = alarms.mapNotNull { ALARMS[it] } +
        (if (!aiEnabled) listOf("AI Climate disabilitato: fermo l'automatico, i comandi a tempo funzionano") else emptyList())

    /** Time left on the safeguard, "m:ss"; "a momenti" once it is over (the next cycle of the .20 acts). */
    fun countdown(now: OffsetDateTime = OffsetDateTime.now()): String = guard?.let {
        val left = try { Duration.between(now, OffsetDateTime.parse(it.until)).seconds } catch (_: Exception) { return "" }
        if (left <= 0) "a momenti" else "%d:%02d".format(left / 60, left % 60)
    } ?: ""

    /** One line for AI Managed Control, under the buttons of "Condizionatore a tempo". */
    fun line(now: OffsetDateTime = OffsetDateTime.now()): String =
        if (inTransition) "$currentLabel → ⏳ ${countdown(now)} → ${label(target!!.mode, target.setpoint)} · $targetText"
        else "$currentLabel · $confirmText"

    companion object {
        const val TOPIC = "zara/interface/climate/ac/state/stat"
        private val json = Json { ignoreUnknownKeys = true }
        private val MODES = setOf("off", "heat", "cool", "dry", "standby", "auto")
        private val ALARMS = mapOf(
            "ON_NO_POWER" to "Comandato acceso ma non consuma: forse spento dal telecomando",
            "WATCHDOG_RESTART" to "Il vigilante ha rimandato il comando (spento dal telecomando?)",
            "BATTERY_CLOSED_MANUAL" to "Comando manuale chiuso dalla protezione della batteria",
            "SOLAR_ONLY_CLOSED" to "Comando «solo sole» chiuso: prelevava da batteria o rete",
            "WATCHDOG_SUSPENDED" to "AI Climate sospeso dal vigilante: il condizionatore non risponde ai comandi",
        )

        fun label(mode: String, setpoint: Double?): String {
            val t = setpoint?.let { " ${"%.0f".format(it)}°" } ?: ""
            return when (mode) {
                "off" -> "Spento"
                "heat" -> "Caldo$t"
                "cool" -> "Fresco$t"
                "dry" -> "Deumidifica$t"
                "auto" -> "Auto$t"
                else -> "Standby$t"
            }
        }

        private fun hhmm(iso: String): String = try {
            OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        } catch (_: Exception) { "" }

        private fun JsonObject.str(k: String) = (this[k] as? JsonElement)?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
        private fun JsonObject.num(k: String) = (this[k] as? JsonElement)?.takeIf { it !is JsonNull }?.jsonPrimitive?.doubleOrNull
        private fun JsonObject.obj(k: String) = (this[k] as? JsonObject)

        fun parse(raw: String): AcState? = try {
            val o = json.parseToJsonElement(raw).jsonObject
            require(o.str("schema") == "domopi.ac_state.v1")
            val cur = o.obj("current")!!
            // IR commands with "auto" in their name set the AUTO mode of the unit (user, 30/10/2026); none in use yet
            val mode = if (cur.str("code")?.contains("auto", ignoreCase = true) == true) "auto"
                else cur.str("mode")!!.also { require(it in MODES) }
            val conf = o.obj("confirm")
            val ctx = o.obj("context")
            val target = o.obj("target")?.let { t ->
                AcState.Target(t.str("mode")!!.also { require(it in MODES) }, t.num("setpoint"), t.str("by") ?: "ai", t.str("until"))
            }
            val guard = o.obj("guard")?.let { g -> AcState.Guard(g.str("type")!!, g.str("until")!!, g.num("minutes")?.toInt() ?: 0) }
            AcState(
                aiEnabled = o["ai_enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                mode = mode, setpoint = if (mode == "off") null else cur.num("setpoint"), by = cur.str("by") ?: "ai",
                confirm = conf?.str("status") ?: "off", powerW = conf?.num("power_w")?.toInt() ?: 0,
                target = target, guard = guard,
                alarms = (o["alarms"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                reason = o.str("reason").orEmpty().take(200),
                surplusW = ctx?.num("surplus_w")?.toInt(), soc = ctx?.num("soc")?.toInt(), temp = ctx?.num("temp"),
                humidex = ctx?.num("humidex"), vmc = ctx?.get("vmc")?.jsonPrimitive?.intOrNull,
            )
        } catch (_: Exception) { null }
    }
}
