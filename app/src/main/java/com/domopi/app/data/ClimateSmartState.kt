package com.domopi.app.data

data class ClimateSmartObservation(val value: String, val receivedAtMs: Long,
    val retained: Boolean, val sourceTopic: String)

/** Read-only observations from the validated climate registry subset. */
data class ClimateSmartState(private val observations: Map<String, ClimateSmartObservation> = emptyMap()) {
    fun observe(relativeTopic: String, payload: String, receivedAtMs: Long,
        retained: Boolean, sourceTopic: String): ClimateSmartState {
        val metric = topics[relativeTopic] ?: return this
        if (receivedAtMs !in 0..253402214400000L || sourceTopic.endsWith("/cmd")) return this
        val previous = observations[metric]
        if (previous != null && receivedAtMs < previous.receivedAtMs) return this
        val clean = payload.trim().takeIf { it.isNotEmpty() && it.length <= 500 } ?: return this
        if (metric == "temperature_set_c" && clean.replace(',', '.').toDoubleOrNull()?.isFinite() != true) return this
        return copy(observations = observations + (metric to
            ClimateSmartObservation(clean, receivedAtMs, retained, sourceTopic)))
    }

    fun validObservations(): Map<String, ClimateSmartObservation> = observations

    fun declaredActiveText(): String? {
        val state = observations["current_state"]?.value?.trim() ?: return null
        val normalized = state.uppercase()
        return if (normalized == "ON" || normalized == "ACCESO" || normalized.endsWith("_ON"))
            "Condizionatore: $state (stato dichiarato)." else null
    }

    fun declaredActiveLabel(): String? = declaredActiveText()?.removeSuffix(".")

    companion object {
        val topics = mapOf(
            "stato_condizionatore/stato_attuale/stat" to "current_state",
            "stato_condizionatore/modalita_aria/stat" to "air_mode",
            "stato_condizionatore/temperatura_impostata_c/stat" to "temperature_set_c",
            "stato_condizionatore/motivo_logica/stat" to "recorded_reason")
    }
}
