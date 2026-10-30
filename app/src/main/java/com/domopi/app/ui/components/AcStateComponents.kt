package com.domopi.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.domopi.app.data.AcState
import kotlinx.coroutines.delay
import java.time.OffsetDateTime
import kotlin.math.cos
import kotlin.math.sin

/*
 * Air conditioner state (AI-CLIMATE-AC-STATE.1, design agreed with the user on 30/10/2026, mockup
 * "Condizionatore: tre stati"): while a safeguard holds a change back, three icons current → safeguard
 * → requested with only the current one blinking; otherwise one icon with the power confirmation.
 * Line icons in the style of the Hitachi remote (sun, hexagon snowflake, drop with air, A in a circle).
 */

private val HEAT = Color(0xFFFF6A3D)
private val COOL = Color(0xFF3FA2FF)
private val DRY = Color(0xFF21B3A3)
private val AUTO = Color(0xFF9C7CF4)
private val GUARD = Color(0xFFFFB300)
private val OFF = Color(0xFF8E8B98)
val AC_ALARM = Color(0xFFFF5A5F)

fun acModeColor(mode: String): Color = when (mode) {
    "heat" -> HEAT
    "cool", "standby" -> COOL
    "dry" -> DRY
    "auto" -> AUTO
    "guard" -> GUARD
    else -> OFF
}

/** Line icon drawn on a 24×24 grid: heat, cool, standby, dry, auto, off, guard. */
@Composable
fun AcModeIcon(kind: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 1.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun polar(r: Float, deg: Float) = Math.toRadians(deg.toDouble()).let { p(12f + r * cos(it).toFloat(), 12f + r * sin(it).toFloat()) }
        fun line(a: Offset, b: Offset) = drawLine(color, a, b, stroke.width, StrokeCap.Round)
        fun path(build: Path.() -> Unit) = drawPath(Path().apply(build), color, style = stroke)
        when (kind) {
            "heat" -> {
                drawCircle(color, 4.6f * u, p(12f, 12f), style = stroke)
                for (a in 0 until 360 step 45) line(polar(7.2f, a.toFloat()), polar(if (a % 90 == 0) 10.8f else 9.6f, a.toFloat()))
            }
            "cool", "standby" -> {
                val angles = listOf(-90f, -30f, 30f, 90f, 150f, 210f)
                path { angles.forEachIndexed { i, a -> polar(5.2f, a).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }; close() }
                angles.forEach { a ->
                    line(polar(5.2f, a), polar(10.8f, a))
                    val c = polar(8.4f, a)
                    val d = Math.toRadians((a + 90).toDouble()).let { Offset(cos(it).toFloat(), sin(it).toFloat()) * (2.2f * u) }
                    line(c - d, c + d)
                }
            }
            "dry" -> {
                path {
                    moveTo(8.6f * u, 3.4f * u); lineTo(3.8f * u, 12.1f * u)
                    arcTo(Rect(p(8.6f, 14.57f), 5.4f * u), 207.2f, -234.4f, false); close()
                }
                path { moveTo(6.8f * u, 10.6f * u); cubicTo(9.4f * u, 7.2f * u, 12.4f * u, 10.8f * u, 17.8f * u, 6f * u) }
                path { moveTo(15.2f * u, 5.6f * u); lineTo(18.1f * u, 5.8f * u); lineTo(17.9f * u, 8.7f * u) }
                path { moveTo(9f * u, 17.2f * u); cubicTo(11.6f * u, 13.8f * u, 14.6f * u, 17.4f * u, 20f * u, 12.6f * u) }
                path { moveTo(17.4f * u, 12.2f * u); lineTo(20.3f * u, 12.4f * u); lineTo(20.1f * u, 15.3f * u) }
            }
            "auto" -> {
                val r = Rect(p(12f, 12f), 8.8f * u)
                path { arcTo(r, -77f, 154f, true) }
                path { arcTo(r, 103f, 154f, true) }
                drawCircle(color, 1.3f * u, p(12f, 3.2f)); drawCircle(color, 1.3f * u, p(12f, 20.8f))
                path { moveTo(8.6f * u, 16.6f * u); lineTo(12f * u, 7.4f * u); lineTo(15.4f * u, 16.6f * u) }
                line(p(9.8f, 13.4f), p(14.2f, 13.4f))
            }
            "guard" -> {
                line(p(7f, 3f), p(17f, 3f)); line(p(7f, 21f), p(17f, 21f))
                path { moveTo(8f * u, 3f * u); cubicTo(8f * u, 7.5f * u, 11.8f * u, 9f * u, 11.8f * u, 12f * u); cubicTo(11.8f * u, 15f * u, 8f * u, 16.5f * u, 8f * u, 21f * u) }
                path { moveTo(16f * u, 3f * u); cubicTo(16f * u, 7.5f * u, 12.2f * u, 9f * u, 12.2f * u, 12f * u); cubicTo(12.2f * u, 15f * u, 16f * u, 16.5f * u, 16f * u, 21f * u) }
            }
            else -> {   // off: power symbol
                line(p(12f, 3.5f), p(12f, 11.5f))
                path { arcTo(Rect(p(12f, 12f), 7.6f * u), -50f, 280f, true) }
            }
        }
    }
}

/** Seconds ticking only while a safeguard countdown is shown. */
@Composable
private fun rememberNow(active: Boolean): OffsetDateTime {
    val now by produceState(OffsetDateTime.now(), active) {
        while (active) { value = OffsetDateTime.now(); delay(1000) }
    }
    return now
}

@Composable
private fun blinkAlpha(on: Boolean, low: Float, millis: Int): Float {
    if (!on) return 1f
    val t = rememberInfiniteTransition(label = "ac")
    val a by t.animateFloat(1f, low, infiniteRepeatable(tween(millis), RepeatMode.Reverse), label = "ac-alpha")
    return a
}

@Composable
private fun AcStep(kind: String, color: Color, title: String, sub: String, iconSize: Dp, alpha: Float, modifier: Modifier = Modifier) {
    Column(modifier.alpha(alpha), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(shape = CircleShape, color = Color.Transparent, border = BorderStroke(2.dp, color), modifier = Modifier.size(iconSize)) {
            Box(contentAlignment = Alignment.Center) { AcModeIcon(kind, color, Modifier.size(iconSize * 0.62f)) }
        }
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Icons only: three steps during a safeguard (the safeguard blinks), else the current state alone. */
@Composable
fun AcStateFlow(state: AcState) {
    val now = rememberNow(state.inTransition)
    if (state.inTransition) {
        val blink = blinkAlpha(true, 0.25f, 550)
        val target = state.target!!
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
            AcStep(state.mode, acModeColor(state.mode), state.currentLabel, "adesso · ${state.powerW} W", 56.dp, 0.32f, Modifier.weight(1f))
            Text("→", Modifier.padding(top = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            AcStep("guard", GUARD, state.countdown(now), state.guardText, 56.dp, blink, Modifier.weight(1f))
            Text("→", Modifier.padding(top = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            AcStep(target.mode, acModeColor(target.mode), AcState.label(target.mode, target.setpoint), state.targetText, 56.dp, 0.32f, Modifier.weight(1f))
        }
    } else {
        val settling = state.confirm in setOf("starting", "stopping", "no_power")
        val pulse = blinkAlpha(settling, 0.55f, 800)
        AcStep(state.mode, acModeColor(state.mode), state.currentLabel, state.confirmText + "\n" + state.byText, 84.dp, pulse,
            Modifier.fillMaxWidth())
    }
}

/** Card for the HVAC screen: icons, alarms, the house values and the technical details on request. */
@Composable
fun AcStateCard(state: AcState?) {
    var details by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Condizionatore", style = MaterialTheme.typography.titleMedium)
            if (state == null) {
                Text("In attesa dello stato da AI Climate (.20)…", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            AcStateFlow(state)
            AcAlarms(state)
            AcMetrics(state)
            TextButton(onClick = { details = !details }, contentPadding = PaddingValues(0.dp)) {
                Text(if (details) "Nascondi dettagli tecnici" else "Dettagli tecnici", style = MaterialTheme.typography.labelMedium)
            }
            if (details) Text("${state.reason} · comando ${state.mode}${state.setpoint?.let { " ${"%.0f".format(it)}" } ?: ""} · ${state.powerW} W",
                style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AcAlarms(state: AcState) {
    state.alarmTexts.forEach {
        Surface(color = AC_ALARM.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, AC_ALARM.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()) {
            Text(it, Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = AC_ALARM, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Surplus, battery, temperature, humidex, setpoint and ventilation of the summary. */
@Composable
fun AcMetrics(state: AcState) {
    fun v(x: Number?, unit: String, fmt: String = "%.0f") = x?.let { fmt.format(it.toDouble()) + unit } ?: "—"
    val cells = listOf(
        "Surplus FV" to v(state.surplusW, " W"), "Batteria" to v(state.soc, " %"), "Temp. int." to v(state.temp, " °C", "%.1f"),
        "Humidex" to v(state.humidex, "", "%.1f"), "Setpoint" to v(state.setpoint, " °C"), "VMC" to (state.vmc?.toString() ?: "—"),
    )
    HorizontalDivider()
    cells.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth()) {
            row.forEach { (label, value) ->
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** The compact line for AI Managed Control, under the buttons of "Condizionatore a tempo". */
@Composable
fun AcStateLine(state: AcState?) {
    if (state == null) return
    val now = rememberNow(state.inTransition)
    val alarm = state.confirm == "no_power"
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val kind = if (state.inTransition) "guard" else state.mode
            AcModeIcon(kind, acModeColor(kind), Modifier.size(20.dp).alpha(blinkAlpha(state.inTransition, 0.25f, 550)))
            Text(state.line(now), style = MaterialTheme.typography.bodyMedium, color = if (alarm) AC_ALARM else Color.Unspecified)
        }
    }
    state.alarmTexts.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AC_ALARM) }
}
