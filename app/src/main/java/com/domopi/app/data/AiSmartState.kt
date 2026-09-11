package com.domopi.app.data

import java.text.Normalizer
import java.util.Locale

data class SmartObservation(val payload: String, val receivedAtMs: Long, val retained: Boolean)

/** Only the validated read-only subset. Missing and invalid values never become OFF/zero. */
data class AiSmartState(val observations: Map<String, SmartObservation> = emptyMap()) {
    fun observe(topic: String, payload: String, receivedAtMs: Long, retained: Boolean): AiSmartState {
        if (topic !in topics) return this
        if ((observations[topic]?.receivedAtMs ?: Long.MIN_VALUE) > receivedAtMs) return this
        return copy(observations = observations + (topic to SmartObservation(payload, receivedAtMs, retained)))
    }

    private fun light(topic: String): Boolean? = when (observations[topic]?.payload?.trim()?.lowercase(Locale.ROOT)) {
        "true", "on", "1" -> true
        "false", "off", "0" -> false
        else -> null
    }

    fun temperature(topic: String): Double? = observations[topic]?.payload?.trim()
        ?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() }

    fun lightsText(): String {
        val states = lightTopics.map { light(it) }
        val on = states.count { it == true }
        val off = states.count { it == false }
        return "Negli ultimi stati ricevuti: $on punti luce accesi, $off spenti e ${8-on-off} senza un dato valido, su 8 punti mappati. " +
            "Il conteggio non copre tutte le luci della casa e non conferma l'accensione fisica."
    }

    fun temperatureText(topic: String, name: String): String = temperature(topic)?.let {
        "$name: ${String.format(Locale.ITALIAN, "%.1f", it)} gradi."
    } ?: "$name: dato non disponibile."

    fun summary(connected: Boolean): String = listOf(
        if (connected) "Ultimi dati disponibili della casa." else "Connessione alla casa assente. Mostro solo gli eventuali ultimi dati ricevuti.",
        lightsText(), temperatureText(livingTopic, "Temperatura living"),
        temperatureText(acsTopic, "Acqua sanitaria ACS"), freshnessText
    ).joinToString(" ")

    fun answer(question: String, connected: Boolean): String {
        val q = Normalizer.normalize(question.lowercase(Locale.ITALIAN), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
        if (Regex("\\b(accendi|spegni|apri|chiudi|imposta|attiva|disattiva|regola|aumenta|abbassa)\\b").containsMatchIn(q))
            return "AI smart per ora risponde alle domande. Per comandare i dispositivi passa all'app classica."
        if (Regex("\\b(casa|riepilogo|sintesi)\\b").containsMatchIn(q) &&
            !Regex("\\b(luci|living|acs|sanitaria)\\b").containsMatchIn(q)) return summary(connected)
        val parts = mutableListOf<String>()
        if (Regex("\\b(luci|luce|lampade)\\b").containsMatchIn(q)) parts += lightsText()
        if (Regex("\\b(living|soggiorno|sala)\\b").containsMatchIn(q)) parts += temperatureText(livingTopic, "Temperatura living")
        if (Regex("\\b(acs|sanitaria)\\b").containsMatchIn(q)) parts += temperatureText(acsTopic, "Acqua sanitaria ACS")
        if (parts.isEmpty()) return "Puoi chiedermi un riepilogo della casa, quante luci risultano accese o le temperature di living e acqua sanitaria ACS, anche insieme. Le altre richieste non sono ancora disponibili."
        return (listOf(if (connected) "Dai dati disponibili:" else "Connessione assente: questi sono gli ultimi dati ricevuti.") +
            parts + freshnessText + "Per ora posso consultare solo luci mappate, living e ACS.").joinToString(" ")
    }

    companion object {
        val lightTopics = listOf("lights/living/power/stat", "lights/libreria/power/stat", "lights/tv/power/stat",
            "lights/reading/power/stat", "lights/bedroom/power/stat", "lights/hifi/power/stat",
            "pool/water/power/stat", "pool/deck/power/stat")
        const val livingTopic = "env/living/temperature/stat"
        const val acsTopic = "energy/puffer_acs/stat"
        val topics = lightTopics + livingTopic + acsTopic
        const val freshnessText = "L'ora delle misure non è disponibile: non posso confermare quanto siano aggiornate."
    }
}
