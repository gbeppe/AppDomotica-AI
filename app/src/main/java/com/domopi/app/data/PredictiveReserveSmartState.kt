package com.domopi.app.data

data class PredictiveReserveObservation(val value: String, val receivedAtMs: Long,
    val retained: Boolean, val sourceTopic: String)

/**
 * Read-only Predictive Reserve state for House AI (fase 3.1), from the PR-MODE.1 topics.
 * Values are normalized to the backend allowlist: "true"/"false" and "SHADOW"/"ACTIVE".
 */
data class PredictiveReserveSmartState(
    private val observations: Map<String, PredictiveReserveObservation> = emptyMap()
) {
    fun observe(relativeTopic: String, payload: String, receivedAtMs: Long,
        retained: Boolean, sourceTopic: String): PredictiveReserveSmartState {
        val metric = topics[relativeTopic] ?: return this
        if (receivedAtMs !in 0..253402214400000L || sourceTopic.endsWith("/cmd")) return this
        val previous = observations[metric]
        if (previous != null && receivedAtMs < previous.receivedAtMs) return this
        val clean = payload.trim()
        val value = when (metric) {
            "operating_mode" -> clean.uppercase().takeIf { it == "SHADOW" || it == "ACTIVE" }
            else -> when (clean.lowercase()) { "true", "1" -> "true"; "false", "0" -> "false"; else -> null }
        } ?: return this
        return copy(observations = observations + (metric to
            PredictiveReserveObservation(value, receivedAtMs, retained, sourceTopic)))
    }

    fun validObservations(): Map<String, PredictiveReserveObservation> = observations

    companion object {
        val topics = mapOf(
            "predictive_reserve/control_enabled/stat" to "control_enabled",
            "predictive_reserve/operating_mode/stat" to "operating_mode",
            "predictive_reserve/ready/stat" to "ready")
    }
}
