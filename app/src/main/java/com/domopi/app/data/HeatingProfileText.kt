package com.domopi.app.data

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Italian texts of the heating profile card and sheets (kept apart from Compose to be testable). */
object HeatingProfileText {
    private val ROME = ZoneId.of("Europe/Rome")
    private val ROOMS = mapOf("thermostat_living" to "soggiorno", "thermostat_bath" to "bagno")

    fun deg(v: Double?) = if (v == null) "—" else String.format(Locale.ITALIAN, "%.1f °C", v)

    private fun day(offset: Int) = when (offset) { 0 -> ""; 1 -> "domani "; else -> "fra $offset giorni " }

    /** "Media esterna 3 giorni: 17,2 °C (Ridotto tra 10 e 16 °C)" or why it is not known yet. */
    fun reason(s: HeatingProfileState): String {
        val mean = s.outdoorMean72h
        if (!s.automatic) return "Scelto da te: resta così finché non torni ad Automatico."
        if (s.hoursOfData < 48 || mean == null)
            return "Raccolta dati esterni: ${s.hoursOfData} ore su 48. Fino ad allora resta ${HeatingProfileState.label(s.profile)}."
        val band = when (s.profile) {
            "pieno" -> "Pieno sotto 10 °C"; "spento" -> "Spento sopra 16 °C"; else -> "Ridotto tra 10 e 16 °C"
        }
        return "Media esterna 3 giorni: ${deg(mean)} ($band)"
    }

    /** "Prossimo: 17:00 · soggiorno → 18,0 °C · bagno → 17,5 °C" (earliest change first). */
    fun next(s: HeatingProfileState): String? {
        val items = s.plan.mapNotNull { (id, p) -> p.next?.let { Triple(id, it, it.dayOffset * 1440 + minutes(it.at)) } }
        if (items.isEmpty()) return null
        val first = items.minOf { it.third }
        val now = items.filter { it.third == first }
        val n = now.first().second
        return "Prossimo: ${day(n.dayOffset)}${n.at} · " +
            now.joinToString(" · ") { "${ROOMS[it.first] ?: it.first} → ${deg(it.second.value)}" }
    }

    /** "Timer: fuori fascia → minimo 17,5 °C · prossima fascia 17:00 → 18,0 °C". */
    fun timerLine(p: ThermostatPlan?): String? {
        p ?: return null
        val now = if (p.inside) "in fascia ${p.slot ?: ""} → massimo ${deg(p.value)}".replace("  ", " ")
            else "fuori fascia → minimo ${deg(p.value)}"
        val n = p.next?.let {
            " · ${if (it.inside) "prossima fascia" else "fine fascia"} ${day(it.dayOffset)}${it.at} → ${deg(it.value)}"
        } ?: ""
        return "Timer: $now$n"
    }

    /** "16/10 14:51 · soggiorno a 22,0 °C (era 18,0) · esterno 23,3 °C · Ridotto". */
    fun correction(c: HeatingCorrection): String {
        val whenText = try {
            OffsetDateTime.parse(c.at).atZoneSameInstant(ROME).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
        } catch (_: Exception) { c.at }
        val was = c.previous?.let { " (era ${deg(it).removeSuffix(" °C")})" } ?: ""
        val ext = c.outdoorMean72h?.let { " · esterno ${deg(it)}" } ?: ""
        val prof = c.profile?.let { " · ${HeatingProfileState.label(it)}" } ?: ""
        return "$whenText · ${ROOMS[c.thermostat] ?: c.thermostat} a ${deg(c.value)}$was$ext$prof"
    }

    /** What each choice does, shown under the options before Conferma. */
    fun choice(mode: String, s: HeatingProfileState): String = when (mode) {
        "auto" -> "Automatico: oggi ${HeatingProfileState.label(autoGuess(s))}. Sotto 10 °C passa a Pieno, sopra 16 °C a Spento."
        "spento" -> "Spento: termostati sempre al minimo, finché non cambi."
        "ridotto" -> "Ridotto: soggiorno e bagno al massimo dalle 17:00 alle 21:00, al minimo il resto del giorno."
        "pieno" -> "Pieno: soggiorno 12:00–20:00; bagno 17:00–22:00, 05:05–07:00 feriali, 07:30–09:30 sabato e domenica."
        else -> ""
    }

    private fun autoGuess(s: HeatingProfileState): String? {
        val m = s.outdoorMean72h ?: return s.profile
        if (s.hoursOfData < 48) return s.profile
        return if (m < HeatingProfileState.PIENO_BELOW) "pieno" else if (m > HeatingProfileState.SPENTO_ABOVE) "spento" else "ridotto"
    }

    private fun minutes(at: String) = at.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }
}
