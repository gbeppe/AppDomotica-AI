package com.domopi.app.data

enum class SmartControlDomain { CLIMATE, PLANTS }
enum class SmartControlKind { SWITCH, STEPPER, SLIDER, CHOICE, TIME }

data class SmartControlSpec(
    val id: String,
    val domain: SmartControlDomain,
    val group: String,
    val label: String,
    val kind: SmartControlKind,
    val stateTopic: String,
    val commandTopic: String,
    val minimum: Float? = null,
    val maximum: Float? = null,
    val choices: List<String> = emptyList(),
)

data class SmartControlObservation(
    val value: String,
    val receivedAtMs: Long,
    val retained: Boolean,
    val sourceTopic: String,
)

/** Read-only preparation for future Smart controls. This type cannot publish commands. */
data class SmartControlState(
    private val observations: Map<String, SmartControlObservation> = emptyMap(),
) {
    fun observe(relativeTopic: String, payload: String, receivedAtMs: Long,
        retained: Boolean, sourceTopic: String): SmartControlState {
        val spec = SmartControlCatalog.byRelativeStateTopic[relativeTopic] ?: return this
        if (sourceTopic != spec.stateTopic || sourceTopic.endsWith("/cmd") ||
            receivedAtMs !in 0..253402214400000L) return this
        val clean = payload.trim().takeIf { it.isNotEmpty() && it.length <= 200 } ?: return this
        if (!validValue(spec, clean)) return this
        val previous = observations[spec.id]
        if (previous != null && receivedAtMs < previous.receivedAtMs) return this
        return copy(observations = observations + (spec.id to
            SmartControlObservation(clean, receivedAtMs, retained, sourceTopic)))
    }

    fun observation(id: String): SmartControlObservation? = observations[id]
    fun validObservations(): Map<String, SmartControlObservation> = observations

    private fun validValue(spec: SmartControlSpec, value: String): Boolean = when (spec.kind) {
        SmartControlKind.SWITCH -> value.lowercase() in setOf("true", "false", "on", "off", "1", "0")
        SmartControlKind.STEPPER, SmartControlKind.SLIDER -> value.replace(',', '.').toFloatOrNull()?.isFinite() == true
        SmartControlKind.CHOICE -> true // Preserve unexpected state; choices constrain only a future command.
        SmartControlKind.TIME -> Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(value)
    }
}

object SmartControlCatalog {
    private const val P = "zara/interface/"
    private fun spec(id: String, domain: SmartControlDomain, group: String, label: String,
        kind: SmartControlKind, path: String, minimum: Float? = null, maximum: Float? = null,
        choices: List<String> = emptyList()) = SmartControlSpec(id, domain, group, label, kind,
        "$P$path/stat", "$P$path/cmd", minimum, maximum, choices)

    val controls = listOf(
        spec("pr_enabled", SmartControlDomain.CLIMATE, "Automazione", "Predictive Reserve notturno", SmartControlKind.SWITCH, "predictive_reserve/control_enabled"),
        spec("ai_enabled", SmartControlDomain.CLIMATE, "Automazione", "AI clima abilitata", SmartControlKind.SWITCH, "ai/system_enabled"),
        spec("compressor_on_min", SmartControlDomain.CLIMATE, "Cicli compressore", "Minuti ON", SmartControlKind.STEPPER, "ai/compressor_on_min"),
        spec("compressor_off_min", SmartControlDomain.CLIMATE, "Cicli compressore", "Minuti OFF", SmartControlKind.STEPPER, "ai/compressor_off_min"),
        spec("night_humidex", SmartControlDomain.CLIMATE, "Comfort notturno", "Soglia Humidex notte", SmartControlKind.STEPPER, "ai/night_humidex_threshold"),
        spec("night_vmc_speed", SmartControlDomain.CLIMATE, "Comfort notturno", "Velocità massima VMC notte", SmartControlKind.SLIDER, "ai/night_vmc_max_speed", 1f, 4f),
        spec("deficit_tolerance", SmartControlDomain.CLIMATE, "Comfort notturno", "Tolleranza deficit (min)", SmartControlKind.STEPPER, "ai/deficit_tolerance_min"),
        spec("morning_ac", SmartControlDomain.CLIMATE, "Gestione mattutina", "Gestione AC mattutina", SmartControlKind.SWITCH, "ai/morning_ac_management"),
        spec("morning_humidex", SmartControlDomain.CLIMATE, "Gestione mattutina", "Soglia emergenza Humidex", SmartControlKind.STEPPER, "ai/morning_humidex_emergency"),
        spec("vmc_speed", SmartControlDomain.PLANTS, "VMC", "Velocità VMC", SmartControlKind.CHOICE, "ventilation/vmc/speed", choices = listOf("1", "2", "3", "4")),
        spec("floor_pump_enabled", SmartControlDomain.PLANTS, "Riscaldamento", "Pompa pavimento abilitata", SmartControlKind.SWITCH, "heating/floor_pump/enabled"),
        spec("living_target", SmartControlDomain.PLANTS, "Termostato soggiorno", "Temperatura obiettivo", SmartControlKind.SLIDER, "climate/thermostat_living/target_temperature", 16f, 22f),
        spec("living_min", SmartControlDomain.PLANTS, "Termostato soggiorno", "Temperatura minima", SmartControlKind.STEPPER, "climate/thermostat_living/min_temperature", 16f, 22f),
        spec("living_max", SmartControlDomain.PLANTS, "Termostato soggiorno", "Temperatura massima", SmartControlKind.STEPPER, "climate/thermostat_living/max_temperature", 16f, 22f),
        spec("bath_target", SmartControlDomain.PLANTS, "Termostato bagno", "Temperatura obiettivo", SmartControlKind.SLIDER, "climate/thermostat_bath/target_temperature", 16f, 22f),
        spec("bath_min", SmartControlDomain.PLANTS, "Termostato bagno", "Temperatura minima", SmartControlKind.STEPPER, "climate/thermostat_bath/min_temperature", 16f, 22f),
        spec("bath_max", SmartControlDomain.PLANTS, "Termostato bagno", "Temperatura massima", SmartControlKind.STEPPER, "climate/thermostat_bath/max_temperature", 16f, 22f),
        spec("fireplace_power", SmartControlDomain.PLANTS, "Termocamino", "Termocamino acceso", SmartControlKind.SWITCH, "fireplace/main/power"),
        spec("fireplace_mode", SmartControlDomain.PLANTS, "Termocamino", "Modalità", SmartControlKind.CHOICE, "fireplace/main/mode", choices = listOf("Disattivato", "Riscaldamento", "Integrazione Caldaia", "Acqua Sanitaria", "Manuale")),
        spec("fireplace_start", SmartControlDomain.PLANTS, "Termocamino", "Ora avvio", SmartControlKind.TIME, "fireplace/main/start_time"),
        spec("fireplace_stop", SmartControlDomain.PLANTS, "Termocamino", "Ora arresto", SmartControlKind.TIME, "fireplace/main/stop_time"),
        spec("fireplace_level", SmartControlDomain.PLANTS, "Termocamino", "Livello combustione", SmartControlKind.SLIDER, "fireplace/main/level", 1f, 6f),
        spec("fireplace_auto", SmartControlDomain.PLANTS, "Termocamino", "Gestione automatica", SmartControlKind.SWITCH, "fireplace/main/auto_power"),
    )

    val byRelativeStateTopic = controls.associateBy { it.stateTopic.removePrefix(P) }
}
