package com.domopi.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domopi.app.ui.theme.ConsumptionYellow
import com.domopi.app.ui.theme.GridBlue
import com.domopi.app.ui.theme.SolarGreen

@Composable
fun EnergyFlowComponent(
    solarPower: Float, // Watts
    homeConsumption: Float, // Watts
    gridPower: Float, // Watts (positive = import, negative = export)
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "flow")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(modifier = modifier.fillMaxWidth().height(300.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val solarPos = Offset(size.width / 2, 50.dp.toPx())
            val homePos = Offset(size.width / 2, size.height - 50.dp.toPx())
            val gridPos = Offset(50.dp.toPx(), size.height / 2)

            // Draw Lines
            drawFlowLine(solarPos, center, SolarGreen, phase, solarPower > 0)
            drawFlowLine(center, homePos, ConsumptionYellow, phase, homeConsumption > 0)
            drawFlowLine(gridPos, center, GridBlue, phase, gridPower != 0f, gridPower < 0)
        }

        // Icons and Labels
        EnergyNode(solarPos = Alignment.TopCenter, icon = Icons.Default.WbSunny, label = "${solarPower.toInt()} W", color = SolarGreen)
        EnergyNode(solarPos = Alignment.BottomCenter, icon = Icons.Default.Home, label = "${homeConsumption.toInt()} W", color = ConsumptionYellow)
        EnergyNode(solarPos = Alignment.CenterStart, icon = Icons.Default.Notifications, label = "${gridPower.toInt()} W", color = GridBlue)
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlowLine(
    start: Offset,
    end: Offset,
    color: Color,
    phase: Float,
    active: Boolean,
    reverse: Boolean = false
) {
    drawLine(
        color = color.copy(alpha = 0.3f),
        start = start,
        end = end,
        strokeWidth = 4.dp.toPx()
    )

    if (active) {
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 20f), phase * (if (reverse) 1f else -1f))
        drawLine(
            color = color,
            start = start,
            end = end,
            strokeWidth = 4.dp.toPx(),
            pathEffect = pathEffect
        )
    }
}

@Composable
fun BoxScope.EnergyNode(
    solarPos: Alignment,
    icon: ImageVector,
    label: String,
    color: Color
) {
    Column(
        modifier = Modifier.align(solarPos).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
