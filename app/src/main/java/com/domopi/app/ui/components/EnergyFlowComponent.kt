package com.domopi.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domopi.app.R
import com.domopi.app.data.EnergyData
import com.domopi.app.data.EnergyFlowModel
import com.domopi.app.data.FlowLine
import com.domopi.app.ui.theme.SolarGreen
import kotlinx.coroutines.delay

// WallBox UI-HOME.1 palette (fase 3.4).
private val FlowSolar = Color(0xFFF5A623)
private val FlowBattery = Color(0xFF2DD4A7)
private val FlowGrid = Color(0xFF4C8DFF)
private val FlowHome = Color(0xFFCFD6E2)
private val FlowStale = Color(0xFF4A5568)
private val FlowInk = Color(0xFFE6EBF3)
private val FlowDim = Color(0xFF8B97AA)

// Diagram drawn in a 340 x 300 unit box scaled to the card width (cross layout, decision D1).
private const val BOX_W = 340f
private const val BOX_H = 300f
private val HUB = Offset(170f, 140f)
private val SOLAR_NODE = Offset(170f, 34f)
private val GRID_NODE = Offset(38f, 140f)
private val HOME_NODE = Offset(302f, 140f)
private val BATTERY_NODE = Offset(170f, 244f)
private const val BATTERY_W = 40f
private const val BATTERY_H = 60f
private const val PARTICLE_SPACING = 14f

/** Each line is drawn from its node edge to the hub. */
private val SOLAR_LINE = Offset(170f, 60f) to HUB
private val GRID_LINE = Offset(64f, 140f) to HUB
private val BATTERY_LINE = Offset(170f, BATTERY_NODE.y - BATTERY_H / 2 - 4f) to HUB
private val HOME_LINE = Offset(276f, 140f) to HUB

@Composable
fun EnergyFlowComponent(
    energyData: EnergyData,
    connected: Boolean,
    modifier: Modifier = Modifier,
    /** null = not yet obtained (EmonCMS unreachable, no MQTT summary): shown as "—", never as 0. */
    gridImportKwhOggi: Float? = null,
    gridImportKwhIeri: Float? = null,
) {
    // Freshness is re-evaluated every 5 s even when no message arrives.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(5_000)
        }
    }
    val state = EnergyFlowModel.from(energyData, nowMs, connected)

    // Decision D4: respect Android's "remove animations" (animator duration scale 0).
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val lines = listOf(state.solar, state.grid, state.battery, state.home)
    val phases = remember { mutableStateListOf(0f, 0f, 0f, 0f) }
    val currentLines by rememberUpdatedState(lines)
    if (animationsEnabled) {
        // Runs only while the card is composed and frames are produced (foreground).
        LaunchedEffect(Unit) {
            var last = 0L
            while (true) {
                withFrameMillis { frame ->
                    val dt = if (last == 0L) 0L else frame - last
                    last = frame
                    currentLines.forEachIndexed { i, line ->
                        if (line.moving) phases[i] = (phases[i] + dt.toFloat() / line.periodMs) % 1f
                    }
                }
            }
        }
    }

    val measurer = rememberTextMeasurer()
    val powerwall = painterResource(id = R.drawable.tesla_powerwall)
    val labelStyle = TextStyle(color = FlowDim, fontSize = 11.sp)
    val valueStyle = TextStyle(color = FlowInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val bigStyle = TextStyle(color = FlowInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    val noteStyle = TextStyle(color = FlowDim, fontSize = 10.5.sp)

    val description = "Fotovoltaico ${EnergyFlowModel.format(state.solar.watts)}, " +
        "rete ${EnergyFlowModel.format(state.grid.watts)} ${EnergyFlowModel.gridNote(state.grid)}, " +
        "Powerwall ${EnergyFlowModel.format(state.battery.watts)} ${EnergyFlowModel.batteryNote(state.battery, state.socPercent)}, " +
        "casa ${EnergyFlowModel.format(state.home.watts)}"

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(BOX_W / BOX_H)
                .semantics { contentDescription = description }
        ) {
            val u = size.width / BOX_W
            fun p(o: Offset) = Offset(o.x * u, o.y * u)

            val specs = listOf(
                Triple(SOLAR_LINE, state.solar, FlowSolar),
                Triple(GRID_LINE, state.grid, FlowGrid),
                Triple(BATTERY_LINE, state.battery, FlowBattery),
                Triple(HOME_LINE, state.home, FlowHome),
            )
            specs.forEachIndexed { i, (seg, line, color) ->
                drawFlowLine(p(seg.first), p(seg.second), line, color, phases[i], animationsEnabled, u)
            }

            // Hub: the house switchboard every line passes through.
            drawCircle(Brush.radialGradient(listOf(FlowHome.copy(alpha = 0.5f), Color.Transparent),
                center = p(HUB), radius = 22f * u), radius = 22f * u, center = p(HUB))
            drawCircle(FlowHome, radius = 7f * u, center = p(HUB))

            drawSun(p(SOLAR_NODE), u)
            drawPylon(p(GRID_NODE), u)
            drawHouse(p(HOME_NODE), u)

            // Battery: the app's Tesla Powerwall image, kept as requested.
            val imgTopLeft = p(Offset(BATTERY_NODE.x - BATTERY_W / 2, BATTERY_NODE.y - BATTERY_H / 2))
            translate(imgTopLeft.x, imgTopLeft.y) {
                with(powerwall) { draw(Size(BATTERY_W * u, BATTERY_H * u)) }
            }
            val barTop = p(Offset(BATTERY_NODE.x + BATTERY_W / 2 + 6f, BATTERY_NODE.y - 26f))
            val barSize = Size(5f * u, 52f * u)
            drawRect(Color.White.copy(alpha = 0.15f), topLeft = barTop, size = barSize)
            state.socPercent?.let { soc ->
                val h = barSize.height * (soc / 100f).coerceIn(0.04f, 1f)
                drawRect(FlowBattery, topLeft = Offset(barTop.x, barTop.y + barSize.height - h), size = Size(barSize.width, h))
            }

            fun text(t: String, style: TextStyle, at: Offset) = drawText(measurer, t, topLeft = p(at), style = style)
            // Solar: to the right of the icon.
            text("Fotovoltaico", labelStyle, Offset(SOLAR_NODE.x + 28f, SOLAR_NODE.y - 22f))
            text(EnergyFlowModel.format(state.solar.watts), valueStyle.copy(color = colorFor(state.solar, FlowSolar)),
                Offset(SOLAR_NODE.x + 28f, SOLAR_NODE.y - 8f))
            if (state.solar.stale) text("non aggiornato", noteStyle, Offset(SOLAR_NODE.x + 28f, SOLAR_NODE.y + 12f))
            // Grid: label above, value and direction below.
            text("Rete", labelStyle, Offset(GRID_NODE.x - 20f, GRID_NODE.y - 42f))
            text(EnergyFlowModel.format(state.grid.watts), valueStyle.copy(color = colorFor(state.grid, FlowGrid)),
                Offset(GRID_NODE.x - 20f, GRID_NODE.y + 24f))
            text(EnergyFlowModel.gridNote(state.grid), noteStyle, Offset(GRID_NODE.x - 20f, GRID_NODE.y + 44f))
            // Home: big value, decision D3.
            text("Casa", labelStyle, Offset(HOME_NODE.x - 14f, HOME_NODE.y - 42f))
            text(EnergyFlowModel.format(state.home.watts), bigStyle.copy(color = if (state.home.stale) FlowDim else FlowInk),
                Offset(HOME_NODE.x - 56f, HOME_NODE.y + 22f))
            if (state.home.stale) text("non aggiornato", noteStyle, Offset(HOME_NODE.x - 40f, HOME_NODE.y + 52f))
            // Battery: values beside the SOC bar, name below the image.
            text(EnergyFlowModel.format(state.battery.watts), valueStyle.copy(color = colorFor(state.battery, FlowBattery)),
                Offset(BATTERY_NODE.x + BATTERY_W / 2 + 16f, BATTERY_NODE.y - 18f))
            text(EnergyFlowModel.batteryNote(state.battery, state.socPercent), noteStyle,
                Offset(BATTERY_NODE.x + BATTERY_W / 2 + 16f, BATTERY_NODE.y + 2f))
            text("Powerwall", labelStyle, Offset(BATTERY_NODE.x - 24f, BATTERY_NODE.y + BATTERY_H / 2 + 2f))
        }

        when {
            !connected -> QualityText("Broker MQTT non raggiungibile · dati non aggiornati", Color(0xFFFF5C5C))
            lines.any { it.stale } -> QualityText("Alcuni valori non aggiornati da oltre 3 minuti", Color(0xFFF2B84B))
        }

        GridImportSummary(gridImportKwhIeri, gridImportKwhOggi)
    }
}

private fun colorFor(line: FlowLine, color: Color) = if (line.stale || line.watts == 0f) FlowDim else color

@Composable
private fun QualityText(text: String, color: Color) {
    Text(text, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
}

/** Green at 0 kWh, flashing red when energy was imported, grey "—" when unknown. */
@Composable
private fun GridImportSummary(ieri: Float?, oggi: Float?) {
    val locale = LocalConfiguration.current.locales[0]
    val blinkAlpha by rememberInfiniteTransition(label = "import").animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "blink_red"
    )
    fun color(kwh: Float?) = when {
        kwh == null -> Color.Gray
        kwh >= 0.05f -> Color.Red.copy(alpha = blinkAlpha)
        else -> SolarGreen
    }
    fun text(kwh: Float?) = kwh?.let { "%.1f kWh".format(locale, it) } ?: "— kWh"
    val ieriColor = color(ieri)
    val oggiColor = color(oggi)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Importato ieri : ", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text(text(ieri), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold, color = ieriColor)
        }
        Text("•", color = Color.Gray.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("oggi : ", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text(text(oggi), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold, color = oggiColor)
        }
    }
}

/**
 * Base line plus particles. Stale: grey dashed and still. Below the deadband: faint and
 * still. Animations off: solid line, no particles.
 */
private fun DrawScope.drawFlowLine(
    node: Offset, hub: Offset, line: FlowLine, color: Color, phase: Float, animate: Boolean, u: Float
) {
    if (line.stale) {
        drawLine(FlowStale.copy(alpha = 0.8f), node, hub, strokeWidth = 3f * u, cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * u, 6f * u)))
        return
    }
    drawLine(color.copy(alpha = 0.22f), node, hub, strokeWidth = 3f * u, cap = StrokeCap.Round)
    if (!line.moving) return
    if (!animate) {
        drawLine(color.copy(alpha = 0.55f), node, hub, strokeWidth = 5.5f * u, cap = StrokeCap.Round)
        return
    }
    val length = (hub - node).getDistance()
    if (length <= 0f) return
    val dir = (hub - node) / length
    val spacing = PARTICLE_SPACING * u
    // Distance from the node grows when energy flows into the hub, shrinks otherwise.
    val offset = (if (line.towardsHub) phase else 1f - phase) * spacing
    var d = offset
    while (d <= length) {
        drawCircle(color, radius = 2.75f * u, center = node + dir * d)
        d += spacing
    }
}

private fun DrawScope.icon(center: Offset, u: Float, block: DrawScope.() -> Unit) =
    withTransform({ translate(center.x, center.y); scale(u, u, pivot = Offset.Zero) }) { block() }

private fun DrawScope.drawSun(center: Offset, u: Float) = icon(center, u) {
    drawCircle(FlowSolar, radius = 9f, center = Offset.Zero)
    val rays = listOf(0f to 1f, 0f to -1f, 1f to 0f, -1f to 0f, 0.71f to 0.71f, -0.71f to -0.71f, 0.71f to -0.71f, -0.71f to 0.71f)
    for ((x, y) in rays) {
        drawLine(FlowSolar, Offset(x * 15f, y * 15f), Offset(x * 19f, y * 19f), strokeWidth = 2.4f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawPylon(center: Offset, u: Float) = icon(center, u) {
    val path = Path().apply {
        moveTo(-11f, 18f); lineTo(0f, -18f); lineTo(11f, 18f)
        moveTo(-7f, 4f); lineTo(7f, 4f)
        moveTo(-9f, 11f); lineTo(9f, 11f)
        moveTo(-13f, -9f); lineTo(13f, -9f)
    }
    drawPath(path, FlowGrid, style = Stroke(width = 2.2f, join = StrokeJoin.Round, cap = StrokeCap.Round))
}

private fun DrawScope.drawHouse(center: Offset, u: Float) = icon(center, u) {
    val path = Path().apply {
        moveTo(-14f, 2f); lineTo(0f, -12f); lineTo(14f, 2f)
        moveTo(-10f, -2f); lineTo(-10f, 14f); lineTo(10f, 14f); lineTo(10f, -2f)
    }
    drawPath(path, FlowHome, style = Stroke(width = 2.2f, join = StrokeJoin.Round, cap = StrokeCap.Round))
}
