package com.domopi.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domopi.app.R
import com.domopi.app.data.EnergyData
import com.domopi.app.data.HvacState

@Composable
fun HvacFlowComponent(
    state: HvacState,
    energyData: EnergyData,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hvac_flow")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        val size = minOf(maxWidth, maxHeight)
        val radius = size * 0.35f
        val villaSize = size * 0.3f
        val nodeBoxSize = size * 0.18f

        // --- 1. DISEGNO FLUSSI ---
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasCenter = Offset(this.size.width / 2, this.size.height / 2)
            val canvasRadius = radius.toPx()

            fun getPos(angleDeg: Double): Offset {
                val angleRad = Math.toRadians(angleDeg)
                return Offset(
                    x = canvasCenter.x + (canvasRadius * Math.cos(angleRad)).toFloat(),
                    y = canvasCenter.y + (canvasRadius * Math.sin(angleRad)).toFloat()
                )
            }

            val posBoiler = getPos(-90.0)
            val posAC = getPos(-18.0)
            val posPalazzetti = getPos(54.0)
            val posVmc = getPos(126.0)
            val posSolar = getPos(198.0)

            drawHvacCurvePath(posBoiler, canvasCenter, if (state.boiler.active) Color.Red else Color.Gray, phase, state.boiler.active)
            
            val acActive = state.ac.active && state.ac.mode != "OFF"
            drawHvacCurvePath(posAC, canvasCenter, if (acActive) (if (state.ac.mode == "Heating") Color.Red else Color.Blue) else Color.Gray, phase, acActive)
            
            drawHvacCurvePath(posPalazzetti, canvasCenter, if (state.palazzetti.active) Color.Red else Color.Gray, phase, state.palazzetti.active)
            drawHvacCurvePath(posVmc, canvasCenter, Color(0xFF00E5FF), phase, true) // Sempre accesa
            drawHvacCurvePath(posSolar, canvasCenter, if (energyData.solarPumpSpeed > 0) Color(0xFFFFEA00) else Color.Gray, phase, energyData.solarPumpSpeed > 0)
        }

        // --- 2. VILLA REALE (CENTRO) ---
        ModernVillaGraphic(
            modifier = Modifier.size(villaSize),
            floorEnabled = state.floorHeating.enabled,
            pumpActive = state.floorHeating.pumpActive
        )

        // --- 3. DISPOSITIVI (NODI CIRCOLARI) ---
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            
            // Boiler (-90°)
            HvacCircularNode(angle = -90.0, dist = radius, nodeSize = nodeBoxSize, name = "Caldaia", status = if (state.boiler.active) "${state.boiler.modulation}%" else "OFF", color = if (state.boiler.active) Color.Red else Color.Gray, painter = painterResource(id = R.drawable.hvac_boiler))
            
            // AC (-18°)
            HvacCircularNode(angle = -18.0, dist = radius, nodeSize = nodeBoxSize, name = "AC", status = if (state.ac.active) "${state.ac.tempSet.toInt()}°" else "OFF", color = if (state.ac.active) (if (state.ac.mode == "Heating") Color.Red else Color.Cyan) else Color.Gray, painter = painterResource(id = R.drawable.hvac_ac))
            
            // Palazzetti (54°)
            HvacCircularNode(angle = 54.0, dist = radius, nodeSize = nodeBoxSize, name = "Focolare", status = if (state.palazzetti.active) "Liv: ${state.palazzetti.level}" else "OFF", color = if (state.palazzetti.active) Color.Red else Color.Gray, painter = painterResource(id = R.drawable.hvac_palazzetti))
            
            // VMC (126°)
            HvacCircularNode(angle = 126.0, dist = radius, nodeSize = nodeBoxSize, name = "VMC", status = "VEL: ${state.vmc.speed}", color = Color(0xFF00E5FF), painter = painterResource(id = R.drawable.hvac_vmc))
            
            // Solare (198°)
            HvacCircularNode(angle = 198.0, dist = radius, nodeSize = nodeBoxSize, name = "Solare", status = if (energyData.solarPumpSpeed > 0) "${energyData.solarPumpSpeed}%" else "OFF", color = if (energyData.solarPumpSpeed > 0) Color(0xFFFFEA00) else Color.Gray, painter = painterResource(id = R.drawable.hvac_solar_thermal))
        }
    }
}

@Composable
fun HvacCircularNode(angle: Double, dist: androidx.compose.ui.unit.Dp, nodeSize: androidx.compose.ui.unit.Dp, name: String, status: String, color: Color, painter: Painter) {
    val angleRad = Math.toRadians(angle)
    val offsetX = (dist.value * Math.cos(angleRad)).dp
    val offsetY = (dist.value * Math.sin(angleRad)).dp
    
    Column(
        modifier = Modifier.offset(x = offsetX, y = offsetY).width(nodeSize * 1.2f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(status, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Box(
            modifier = Modifier.size(nodeSize).background(color.copy(alpha = 0.05f), CircleShape).padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(painter = painter, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHvacCurvePath(
    start: Offset,
    end: Offset,
    color: Color,
    phase: Float,
    active: Boolean
) {
    val path = Path().apply {
        moveTo(start.x, start.y)
        val controlPoint = Offset((start.x + end.x) / 2, (start.y + end.y) / 2)
        quadraticBezierTo(controlPoint.x, controlPoint.y, end.x, end.y)
    }

    drawPath(
        path = path,
        color = color.copy(alpha = 0.1f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
    )

    if (active) {
        val pathMeasure = PathMeasure()
        pathMeasure.setPath(path, false)
        val length = pathMeasure.length
        
        val dotCount = 3
        for (i in 0 until dotCount) {
            val progress = (phase + i.toFloat() / dotCount) % 1f
            val pos = pathMeasure.getPosition(progress * length)
            
            drawCircle(color = color, radius = 4.dp.toPx(), center = pos)
            drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = pos)
        }
    }
}
