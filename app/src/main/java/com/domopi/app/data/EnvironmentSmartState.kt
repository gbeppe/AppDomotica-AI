package com.domopi.app.data

data class EnvironmentObservation(val value: Double, val receivedAtMs: Long,
    val retained: Boolean, val sourceTopic: String)

/**
 * Room sensors for House AI: temperature, humidity and humidex per room. Keys match the
 * backend allowlist, e.g. "living.temperature". Invalid payloads are dropped, never defaulted.
 */
data class EnvironmentSmartState(
    private val observations: Map<String, EnvironmentObservation> = emptyMap()
) {
    fun observe(relativeTopic: String, payload: String, receivedAtMs: Long,
        retained: Boolean, sourceTopic: String): EnvironmentSmartState {
        val parts = relativeTopic.split("/")
        if (parts.size != 4 || parts[0] != "env" || parts[3] != "stat") return this
        val (room, metric) = parts[1] to parts[2]
        if (room !in ROOMS || metric !in METRICS) return this
        if (receivedAtMs !in 0..253402214400000L || sourceTopic.endsWith("/cmd")) return this
        val key = "$room.$metric"
        val previous = observations[key]
        if (previous != null && receivedAtMs < previous.receivedAtMs) return this
        val range = if (metric == "humidity") 0.0..100.0 else -40.0..70.0
        val value = payload.trim().replace(',', '.').toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in range } ?: return this
        return copy(observations = observations + (key to
            EnvironmentObservation(value, receivedAtMs, retained, sourceTopic)))
    }

    fun validObservations(): Map<String, EnvironmentObservation> = observations

    companion object {
        val ROOMS = setOf("living", "bedroom", "kids_room", "service_bathroom", "technical_room", "outdoor")
        val METRICS = setOf("temperature", "humidity", "humidex")
    }
}
