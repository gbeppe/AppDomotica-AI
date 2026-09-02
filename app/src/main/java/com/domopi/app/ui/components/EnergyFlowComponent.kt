package com.domopi.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domopi.app.R

@Composable
fun EnergyFlowComponent(
    modifier: Modifier = Modifier,
    solarPower: Float,
    homeConsumption: Float,
    gridPower: Float, 
    batteryPower: Float = 0f, 
    batterySoc: Float = 0f,
) {
    val hasData = (solarPower != 0f) || (homeConsumption != 0f) || (gridPower != 0f) || (batteryPower != 0f)

    if (!hasData) {
        Box(
            modifier = modifier.fillMaxWidth().height(400.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.dp,
                    color = Color.Gray.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(16.dp))
                Text("In connessione...", color = Color.Gray, fontSize = 14.sp)
            }
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "flow")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(modifier = modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.TopCenter) {
        
        // HUB is the geometric center for alignments
        val hubY = 180.dp
        val sidePadding = 40.dp
        val iconSize = 48.dp
        
        // Offset to align ICON center with hubY
        // Header Text height (~20dp) + Spacer (8dp) + half icon (24dp) = 52dp
        val nodeYOffset = hubY - 52.dp

        Canvas(modifier = Modifier.fillMaxSize()) {
            val hubPx = Offset(size.width / 2, hubY.toPx())
            val iconSizePx = iconSize.toPx()
            val halfIconPx = iconSizePx / 2
            
            // Solar (Top) - Start from bottom border
            val solarPoint = Offset(size.width / 2, 70.dp.toPx() + halfIconPx)
            
            // Grid (Left) - Start from right border
            val gridPoint = Offset(sidePadding.toPx() + iconSizePx, hubY.toPx())
            
            // House (Right) - Start from left border
            val homePoint = Offset(size.width - sidePadding.toPx() - iconSizePx, hubY.toPx())
            
            // Battery (Bottom) - Start from top border
            val batteryPoint = Offset(size.width / 2, (hubY + 25.dp).toPx())

            // Paths
            drawEnergyPathTesla(solarPoint, hubPx, Color(0xFFFFEB3B), phase, active = solarPower > 15, reverse = false)
            drawEnergyPathTesla(gridPoint, hubPx, Color(0xFFD939F3), phase, active = abs(gridPower) > 20, reverse = gridPower < 0)
            drawEnergyPathTesla(hubPx, homePoint, Color(0xFF00E5FF), phase, active = homeConsumption > 15, reverse = false)
            drawEnergyPathTesla(batteryPoint, hubPx, Color(0xFF00FF00), phase, active = abs(batteryPower) > 15, reverse = batteryPower < 0)
        }

        // 1. Solar (Top Center)
        TeslaNodeMinimal(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            imageRes = R.drawable.solar_icon,
            powerValue = "${(solarPower/1000f).let { "%.1f".format(it) }} kW",
            color = Color(0xFFFFEB3B),
            tintColor = Color(0xFFFFEB3B)
        )

        // 2. Grid (Left - Centered vertically with House)
        TeslaNodeMinimal(
            modifier = Modifier.align(Alignment.TopStart).padding(start = sidePadding, top = nodeYOffset),
            imageRes = R.drawable.grid_icon,
            powerValue = "${(gridPower/1000f).let { "%.1f".format(kotlin.math.abs(it)) }} kW",
            color = Color(0xFFD939F3),
            tintColor = Color(0xFFD939F3)
        )

        // 3. House (Right - Centered vertically with Grid)
        TeslaNodeMinimal(
            modifier = Modifier.align(Alignment.TopEnd).padding(end = sidePadding, top = nodeYOffset),
            imageRes = R.drawable.ic_house_tesla,
            powerValue = "${(homeConsumption/1000f).let { "%.1f".format(it) }} kW",
            color = Color(0xFF00E5FF)
        )

        // 4. Battery (Bottom Center - Aligned and Minimal)
        BatteryNodeMinimal(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = hubY + 25.dp),
            soc = batterySoc
        )
    }
}

@Composable
fun BatteryNodeMinimal(
    modifier: Modifier = Modifier,
    soc: Float
) {
    val batteryHeight = 110.dp
    val batteryWidth = 65.dp
    
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Battery Image (Transparent background)
        Image(
            painter = painterResource(id = R.drawable.tesla_powerwall),
            contentDescription = null,
            modifier = Modifier.size(width = batteryWidth, height = batteryHeight),
            contentScale = ContentScale.Fit
        )

        // Aligned indicator on the right
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = batteryWidth + 30.dp) 
                .height(batteryHeight),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(1.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(soc / 100f)
                        .background(Color(0xFF00FF00))
                        .align(Alignment.BottomCenter)
                )
            }
            
            Spacer(Modifier.width(6.dp))
            
            Text(
                text = "${soc.toInt()}%", 
                color = Color.White, 
                fontSize = 11.sp, 
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

@Composable
fun TeslaNodeMinimal(
    modifier: Modifier = Modifier,
    imageRes: Int,
    powerValue: String,
    color: Color,
    tintColor: Color? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(powerValue, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = tintColor?.let { ColorFilter.tint(it) }
            )
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEnergyPathTesla(
    start: Offset,
    end: Offset,
    color: Color,
    phase: Float,
    active: Boolean,
    reverse: Boolean
) {
    drawLine(
        color = color.copy(alpha = 0.1f),
        start = start,
        end = end,
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )

    if (active) {
        val actualStart = if (reverse) end else start
        val actualEnd = if (reverse) start else end
        
        val dotCount = 3
        for (i in 0 until dotCount) {
            val progress = (phase + i.toFloat() / dotCount) % 1f
            val pos = Offset(
                x = actualStart.x + (actualEnd.x - actualStart.x) * progress,
                y = actualStart.y + (actualEnd.y - actualStart.y) * progress
            )
            
            drawCircle(color = color, radius = 3.5.dp.toPx(), center = pos, alpha = 0.85f)
            drawCircle(color = Color.White, radius = 1.2.dp.toPx(), center = pos)
        }
    }
}
