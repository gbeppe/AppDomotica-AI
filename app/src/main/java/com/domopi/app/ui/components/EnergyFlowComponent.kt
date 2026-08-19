package com.domopi.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domopi.app.ui.theme.ConsumptionYellow
import com.domopi.app.ui.theme.GridBlue
import com.domopi.app.ui.theme.SolarGreen

@Composable
fun EnergyFlowComponent(
    solarPower: Float,
    homeConsumption: Float,
    gridPower: Float, // positive = import, negative = export
    batteryPower: Float = 0f, // positive = discharging, negative = charging
    batterySoc: Float = 0f,
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

    Box(modifier = modifier.fillMaxWidth().height(260.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val solarPos = Offset(size.width / 2, 40.dp.toPx())
            val homePos = Offset(size.width / 2, size.height - 40.dp.toPx())
            val gridPos = Offset(60.dp.toPx(), size.height / 2)
            val batteryPos = Offset(size.width - 60.dp.toPx(), size.height / 2)

            // Draw Lines
            drawFlowLine(solarPos, center, SolarGreen, phase, solarPower > 10)
            drawFlowLine(center, homePos, ConsumptionYellow, phase, homeConsumption > 10)
            
            // Grid: center to grid is export (negative gridPower), grid to center is import (positive gridPower)
            drawFlowLine(gridPos, center, GridBlue, phase, Math.abs(gridPower) > 20, gridPower < 0)
            
            // Battery: battery to center is discharging (positive batteryPower), center to battery is charging (negative batteryPower)
            drawFlowLine(batteryPos, center, Color(0xFF00E676), phase, Math.abs(batteryPower) > 10, batteryPower < 0)
        }

        // Icons and Labels
        EnergyNode(pos = Alignment.TopCenter, icon = Icons.Default.WbSunny, label = "${solarPower.toInt()} W", color = SolarGreen)
        EnergyNode(pos = Alignment.BottomCenter, icon = Icons.Default.Home, label = "${homeConsumption.toInt()} W", color = ConsumptionYellow)
        EnergyNode(pos = Alignment.CenterStart, icon = Icons.Default.Notifications, label = "${gridPower.toInt()} W", color = GridBlue)
        
        // Battery Node
        val batteryColor = when {
            batterySoc > 50f -> Color(0xFF00E676) // Green
            batterySoc > 20f -> Color(0xFFFFAB40) // Orange
            else -> Color(0xFFFF5252) // Red
        }
        
        EnergyNode(
            pos = Alignment.CenterEnd, 
            icon = if (batteryPower < -10) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull, 
            label = "${batterySoc.toInt()}%", 
            subLabel = "${batteryPower.toInt()} W",
            color = batteryColor
        )
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
        color = color.copy(alpha = 0.2f),
        start = start,
        end = end,
        strokeWidth = 3.dp.toPx()
    )

    if (active) {
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 20f), phase * (if (reverse) 1f else -1f))
        drawLine(
            color = color,
            start = start,
            end = end,
            strokeWidth = 3.dp.toPx(),
            pathEffect = pathEffect
        )
    }
}

@Composable
fun BoxScope.EnergyNode(
    pos: Alignment,
    icon: ImageVector,
    label: String,
    subLabel: String? = null,
    color: Color
) {
    Column(
        modifier = Modifier.align(pos).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (subLabel != null) {
            Text(subLabel, color = color.copy(alpha = 0.7f), fontSize = 10.sp)
        }
    }
}
