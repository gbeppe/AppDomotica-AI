package com.domopi.app.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Opening summary of the AI Smart card (fase 3.5 A): temperatures and energy, short and
 * spoken-friendly like the assistant answers. Built on the phone from data already received,
 * no backend or model call. Missing data is left out, never shown as zero.
 */
object HouseSummary {
    /** Below this the grid or battery flow is reported as idle (meter noise). */
    const val IDLE_W = 50.0

    fun text(energy: EnergySmartState, environment: EnvironmentSmartState, connected: Boolean): String {
        val parts = mutableListOf<String>()
        if (!connected) parts += "Casa non collegata: sono gli ultimi dati ricevuti."
        parts += temperatures(environment)
        parts += energyText(energy)
        return parts.joinToString(" ")
    }

    fun temperatures(environment: EnvironmentSmartState): String {
        val values = environment.validObservations()
        val living = values["living.temperature"]?.value
        val outdoor = values["outdoor.temperature"]?.value
        return when {
            living != null && outdoor != null -> "Living ${degrees(living)}, fuori ${spoken(outdoor)}."
            living != null -> "Living ${degrees(living)}."
            outdoor != null -> "Fuori ${degrees(outdoor)}."
            else -> "Temperature non disponibili."
        }
    }

    /** Same sentence as the House AI energy overview (current_energy_overview, detail summary). */
    fun energyText(energy: EnergySmartState): String {
        val value = { metric: String -> energy.validObservations()[metric]?.value }
        val parts = mutableListOf<String>()
        value("solar_power_w")?.let { solar ->
            parts += if (solar < IDLE_W) "il fotovoltaico è fermo" else "il fotovoltaico produce ${power(solar)}"
        }
        val soc = value("battery_soc_percent")
        val battery = value("battery_power_w")
        if (soc != null || battery != null) {
            val flow = when {
                battery == null || abs(battery) < IDLE_W -> null
                battery < 0 -> "si sta caricando"
                else -> "si sta scaricando"
            }
            parts += if (soc == null) "la batteria ${flow ?: "è ferma"}"
            else "la batteria è al ${spoken(soc, 0)} per cento" + (flow?.let { " e $it" } ?: "")
        }
        value("home_consumption_w")?.let { parts += "la casa consuma ${power(it)}" }
        value("grid_power_w")?.let { grid ->
            parts += when {
                grid >= IDLE_W -> "stai importando ${power(grid)} dalla rete"
                grid <= -IDLE_W -> "stai immettendo ${power(grid)} in rete"
                else -> "non stai importando dalla rete"
            }
        }
        return if (parts.isEmpty()) "Dati energetici non disponibili."
        else parts.joinToString(", ").replaceFirstChar(Char::uppercase) + "."
    }

    private fun degrees(value: Double) = "${spoken(value)} gradi"

    /** 18 -> "18", 22.43 -> "22,4": no trailing ",0". */
    fun spoken(value: Double, digits: Int = 1): String {
        val factor = if (digits == 0) 1.0 else 10.0
        val rounded = (value * factor).roundToInt() / factor
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
        else String.format(Locale.ITALIAN, "%.${digits}f", rounded)
    }

    fun power(watts: Double): String {
        val w = abs(watts)
        return if (w < 1000) "${spoken(w, 0)} watt" else "${spoken(w / 1000)} kilowatt"
    }
}
