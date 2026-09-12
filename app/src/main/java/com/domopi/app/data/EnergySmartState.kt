package com.domopi.app.data

data class EnergySmartObservation(
    val value: Double?,
    val receivedAtMs: Long,
    val retained: Boolean,
    val sourceTopic: String,
    val quality: String,
)

/** Current energy observations from the public Digital Twin; never publishes. */
data class EnergySmartState(
    private val observations: Map<String, EnergySmartObservation> = emptyMap(),
) {
    fun observe(
        relativeTopic: String,
        payload: String,
        receivedAtMs: Long,
        retained: Boolean,
        sourceTopic: String,
    ): EnergySmartState {
        val metric = topics[relativeTopic] ?: return this
        if (receivedAtMs !in 0..253402214400000L || sourceTopic.endsWith("/cmd")) return this
        val previous = observations[metric]
        if (previous != null && receivedAtMs < previous.receivedAtMs) return this
        val parsed = payload.trim().replace(',', '.').toDoubleOrNull()
            ?.takeIf { it.isFinite() && (metric != "battery_soc_percent" || it in 0.0..100.0) &&
                (metric !in setOf("solar_power_w", "home_consumption_w") || it >= 0) }
        val observation = EnergySmartObservation(
            value = parsed,
            receivedAtMs = receivedAtMs,
            retained = retained,
            sourceTopic = sourceTopic,
            quality = if (parsed == null) "invalid" else "valid",
        )
        return copy(observations = observations + (metric to observation))
    }

    fun validObservations(): Map<String, EnergySmartObservation> =
        observations.filterValues { it.quality == "valid" && it.value != null }

    fun reading(metric: String): EnergySmartObservation? = observations[metric]

    fun summary(connected: Boolean): String =
        (if (connected) "Ultime letture energetiche ricevute." else "Digital Twin disconnesso: eventuali ultime letture ricevute.") +
            "\n" + topics.values.joinToString("\n") { metric -> readingText(metric) } +
            "\nEtà delle misure sorgente ignota; retained non conferma freschezza o stato fisico."

    fun readingText(metric: String): String {
        val reading = observations[metric]
        val value = reading?.value
        val label = labels.getValue(metric)
        if (value == null) return "$label: ${if (reading == null) "dato non ricevuto" else "dato non valido"}."
        val direction = when (metric) {
            "grid_power_w" -> if (value > 0) "Prelievo rete" else if (value < 0) "Immissione rete" else "Scambio netto rete"
            "battery_power_w" -> if (value > 0) "Scarica batteria" else if (value < 0) "Carica batteria" else "Scambio netto batteria"
            else -> label
        }
        val unit = if (metric == "battery_soc_percent") "%" else "W"
        return "$direction: ${String.format(java.util.Locale.ITALIAN, "%.1f", kotlin.math.abs(value))} $unit."
    }

    companion object {
        val labels = mapOf(
            "solar_power_w" to "Fotovoltaico", "home_consumption_w" to "Consumo casa",
            "grid_power_w" to "Rete", "battery_power_w" to "Batteria", "battery_soc_percent" to "Livello Powerwall",
        )
        val topics = mapOf(
            "energy/solar/power/stat" to "solar_power_w",
            "energy/home/consumption/stat" to "home_consumption_w",
            "energy/grid/power_raw/stat" to "grid_power_w",
            "energy/battery/power_raw/stat" to "battery_power_w",
            "energy/battery/soc/stat" to "battery_soc_percent",
        )
    }
}
