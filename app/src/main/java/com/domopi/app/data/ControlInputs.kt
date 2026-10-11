package com.domopi.app.data

import kotlin.math.roundToInt

/** Pure helpers for manual controls that publish real MQTT commands. */
object ControlInputs {
    private val TIME = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$")

    /** "7:05" or "07:05" -> "07:05"; anything incomplete or out of range -> null (never sent). */
    fun time(text: String): String? = TIME.matchEntire(text.trim())?.let { match ->
        "%02d:%s".format(match.groupValues[1].toInt(), match.groupValues[2])
    }

    /** Slider positions are floats: round (2.9999 is 3), never truncate. */
    fun step(value: Float, range: IntRange): Int = value.roundToInt().coerceIn(range)
}
