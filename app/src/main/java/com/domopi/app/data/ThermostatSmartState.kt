package com.domopi.app.data

import java.util.Locale

/** [value] is a Double for temperatures and a Boolean for heating power. */
data class ThermostatObservation(val value: Any, val receivedAtMs: Long,
    val retained: Boolean, val sourceTopic: String)

/**
 * Heating thermostats for House AI (fase 3.2). Keys match the backend allowlist,
 * e.g. "thermostat_living.target_temperature". Invalid payloads are dropped, never defaulted.
 */
data class ThermostatSmartState(
    private val observations: Map<String, ThermostatObservation> = emptyMap()
) {
    fun observe(relativeTopic: String, payload: String, receivedAtMs: Long,
        retained: Boolean, sourceTopic: String): ThermostatSmartState {
        val parts = relativeTopic.split("/")
        if (parts.size != 4 || parts[0] != "climate" || parts[3] != "stat") return this
        val (thermostat, metric) = parts[1] to parts[2]
        if (thermostat !in THERMOSTATS || metric !in METRICS) return this
        if (receivedAtMs !in 0..253402214400000L || sourceTopic.endsWith("/cmd")) return this
        val key = "$thermostat.$metric"
        val previous = observations[key]
        if (previous != null && receivedAtMs < previous.receivedAtMs) return this
        val clean = payload.trim()
        val value: Any = if (metric == "power") {
            when (clean.lowercase()) { "true" -> true; "false" -> false; else -> null }
        } else {
            clean.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in -30.0..60.0 }
        } ?: return this
        return copy(observations = observations + (key to
            ThermostatObservation(value, receivedAtMs, retained, sourceTopic)))
    }

    fun validObservations(): Map<String, ThermostatObservation> = observations

    companion object {
        val THERMOSTATS = setOf("thermostat_living", "thermostat_bath")
        val METRICS = setOf("current_temperature", "target_temperature", "power",
            "min_temperature", "max_temperature")
        /** Same closed set as the backend and the HVAC slider: 16.0–22.0 °C in 0.5 steps. */
        val TARGETS: List<String> = (32..44).map { String.format(Locale.US, "%.1f", it / 2.0) }
    }
}
