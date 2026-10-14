package com.domopi.app.data

import java.util.Locale

/**
 * Texts of the home summary and of the AI climate badge. Without the broker the app has no data:
 * it shows "—" instead of default values that look real (0,0°, "ATTIVATO").
 */
object DashboardText {
    const val UNKNOWN = "—"

    fun temperature(connected: Boolean, value: Float, locale: Locale): String =
        if (connected) "%.1f°C".format(locale, value) else UNKNOWN

    fun puffer(connected: Boolean, acs: Float, alto: Float, basso: Float, locale: Locale): String =
        if (connected) "${"%.1f".format(locale, acs)}° - ${"%.1f".format(locale, alto)}° - ${"%.1f".format(locale, basso)}°"
        else UNKNOWN

    fun pvForecast(connected: Boolean, kwh: Float, batteryPercent: Int, locale: Locale): String =
        if (connected) "${"%.1f".format(locale, kwh)} kWh (batt $batteryPercent%)" else UNKNOWN

    fun acBadge(connected: Boolean, currentState: String, systemEnabled: Boolean): String = when {
        !connected -> UNKNOWN
        currentState.isNotEmpty() -> currentState
        systemEnabled -> "ATTIVATO"
        else -> "DISATTIVATO"
    }
}
