package com.domopi.app.data

/** [value] is a Boolean, an Int (level, percent), a Double (temperature) or a String (mode, time). */
data class HeatingObservation(val value: Any, val receivedAtMs: Long,
    val retained: Boolean, val sourceTopic: String)

/**
 * Read-only heating plant for House AI: fireplace, puffer, floor pump, gas boiler, solar thermal.
 * Keys and value rules match the backend allowlist (`HEATING_METRICS`), e.g. "fireplace.level".
 * Invalid payloads are dropped, never defaulted, so the backend never rejects the request.
 */
data class HeatingSmartState(
    private val observations: Map<String, HeatingObservation> = emptyMap()
) {
    fun observe(relativeTopic: String, payload: String, receivedAtMs: Long,
        retained: Boolean, sourceTopic: String): HeatingSmartState {
        if (!relativeTopic.endsWith("/stat")) return this
        val (key, kind) = METRICS[relativeTopic.removeSuffix("/stat")] ?: return this
        if (receivedAtMs !in 0..253402214400000L || sourceTopic.endsWith("/cmd")) return this
        val previous = observations[key]
        if (previous != null && receivedAtMs < previous.receivedAtMs) return this
        val clean = payload.trim()
        val value: Any = when (kind) {
            Kind.BOOL -> when (clean.lowercase()) { "true" -> true; "false" -> false; else -> null }
            Kind.LEVEL -> clean.toIntOrNull()?.takeIf { it in 1..6 }
            Kind.PERCENT -> clean.toIntOrNull()?.takeIf { it in 0..100 }
            Kind.TEMPERATURE -> clean.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in -40.0..200.0 }
            Kind.MODE -> clean.takeIf { it in FIREPLACE_MODES }
            Kind.TIME -> clean.takeIf { TIME.matches(it) }
        } ?: return this
        return copy(observations = observations + (key to
            HeatingObservation(value, receivedAtMs, retained, sourceTopic)))
    }

    fun validObservations(): Map<String, HeatingObservation> = observations

    /** Current fireplace mode label, or null when not received. */
    fun fireplaceMode(): String? = observations["fireplace.mode"]?.value as? String

    enum class Kind { BOOL, LEVEL, PERCENT, TEMPERATURE, MODE, TIME }

    companion object {
        /** Relative stat topic (without "/stat") to backend key and value rule. */
        val METRICS: Map<String, Pair<String, Kind>> = mapOf(
            "fireplace/main/power" to ("fireplace.power" to Kind.BOOL),
            "fireplace/main/level" to ("fireplace.level" to Kind.LEVEL),
            "fireplace/main/mode" to ("fireplace.mode" to Kind.MODE),
            "fireplace/main/start_time" to ("fireplace.start_time" to Kind.TIME),
            "fireplace/main/stop_time" to ("fireplace.stop_time" to Kind.TIME),
            "fireplace/main/auto_power" to ("fireplace.auto_power" to Kind.BOOL),
            "heating/puffer/top_temperature" to ("puffer.top_temperature" to Kind.TEMPERATURE),
            "heating/puffer/bottom_temperature" to ("puffer.bottom_temperature" to Kind.TEMPERATURE),
            "energy/puffer_acs" to ("puffer.acs_temperature" to Kind.TEMPERATURE),
            "heating/floor_pump/enabled" to ("floor_pump.enabled" to Kind.BOOL),
            "heating/floor_pump/running" to ("floor_pump.running" to Kind.BOOL),
            "heating/gas_boiler/flame" to ("gas_boiler.flame" to Kind.BOOL),
            "heating/gas_boiler/modulation" to ("gas_boiler.modulation" to Kind.PERCENT),
            "heating/solar_thermal/collector_temperature" to ("solar_thermal.collector_temperature" to Kind.TEMPERATURE),
            "heating/solar_thermal/pump_speed" to ("solar_thermal.pump_speed" to Kind.PERCENT),
        )
        /** App labels of the fireplace modes (ROUTER-FIREPLACE-MODE.1/.2). */
        val FIREPLACE_MODES = setOf("Disattivato", "Riscaldamento", "Integrazione Caldaia", "Acqua Sanitaria", "Manuale")
        private val TIME = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")
        fun isTime(value: String): Boolean = TIME.matches(value)
    }
}
