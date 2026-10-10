package com.domopi.app.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Home energy-flow diagram rules, ported from WallBox UI-HOME.1 (fase 3.4):
 * every line goes through the house hub; direction from the sign; particle speed
 * logarithmic from 50 W to 7 kW; below the deadband the line is still; a value not
 * received for [STALE_AFTER_MS] (or MQTT disconnected) is shown as "—", never as 0.
 */
data class FlowLine(
    /** Watts as shown (deadband applied, sign kept); null when stale. */
    val watts: Float?,
    val stale: Boolean,
    /** Particles move on this line. */
    val moving: Boolean,
    /** Particles travel from the node towards the hub (false = from the hub to the node). */
    val towardsHub: Boolean,
    /** Time for a particle to cover one spacing, shorter when more power flows. */
    val periodMs: Int,
)

data class EnergyFlowState(
    val solar: FlowLine,
    val grid: FlowLine,
    val battery: FlowLine,
    val home: FlowLine,
    val socPercent: Float?,
    val allStale: Boolean,
)

object EnergyFlowModel {
    const val DEADBAND_W = 50f
    const val SATURATION_W = 7000f
    const val STALE_AFTER_MS = 180_000L
    const val SLOWEST_PERIOD_MS = 2600
    const val FASTEST_PERIOD_MS = 500

    fun periodMs(watts: Float): Int {
        val a = abs(watts).coerceIn(DEADBAND_W, SATURATION_W)
        val t = (ln(a) - ln(DEADBAND_W)) / (ln(SATURATION_W) - ln(DEADBAND_W))
        return (SLOWEST_PERIOD_MS - (SLOWEST_PERIOD_MS - FASTEST_PERIOD_MS) * t).roundToInt()
    }

    private fun isStale(receivedAtMs: Long, nowMs: Long, connected: Boolean) =
        !connected || receivedAtMs <= 0L || nowMs - receivedAtMs > STALE_AFTER_MS

    private fun line(watts: Float, receivedAtMs: Long, nowMs: Long, connected: Boolean, towardsHub: Boolean): FlowLine {
        if (isStale(receivedAtMs, nowMs, connected)) return FlowLine(null, true, false, towardsHub, SLOWEST_PERIOD_MS)
        val shown = if (abs(watts) < DEADBAND_W) 0f else watts
        return FlowLine(shown, false, shown != 0f, towardsHub, periodMs(shown))
    }

    fun from(e: EnergyData, nowMs: Long, connected: Boolean): EnergyFlowState {
        val solar = line(maxOf(0f, e.solarPower), e.solarAtMs, nowMs, connected, towardsHub = true)
        val grid = line(e.gridPower, e.gridAtMs, nowMs, connected, towardsHub = e.gridPower > 0f)
        val battery = line(e.batteryPower, e.batteryAtMs, nowMs, connected, towardsHub = e.batteryPower > 0f)
        val home = line(maxOf(0f, e.homeConsumption), e.homeAtMs, nowMs, connected, towardsHub = false)
        val soc = if (isStale(e.socAtMs, nowMs, connected)) null else e.batterySoc
        return EnergyFlowState(solar, grid, battery, home, soc,
            allStale = listOf(solar, grid, battery, home).all { it.stale })
    }

    /** W as integers below 1000, then kW with one decimal (Italian comma); "—" when stale. */
    fun format(watts: Float?, locale: Locale = Locale.ITALY): String {
        if (watts == null) return "—"
        val a = abs(watts)
        return if (a < 1000f) "${a.roundToInt()} W" else String.format(locale, "%.1f kW", a / 1000f)
    }

    fun gridNote(line: FlowLine): String = when {
        line.stale -> "non aggiornato"
        (line.watts ?: 0f) > 0f -> "prelievo"
        (line.watts ?: 0f) < 0f -> "immissione"
        else -> ""
    }

    fun batteryNote(line: FlowLine, soc: Float?): String {
        if (line.stale) return "non aggiornato"
        val socText = soc?.let { "${it.roundToInt()}%" } ?: "SOC —"
        return socText + when {
            (line.watts ?: 0f) > 0f -> " · scarica"
            (line.watts ?: 0f) < 0f -> " · carica"
            else -> ""
        }
    }
}
