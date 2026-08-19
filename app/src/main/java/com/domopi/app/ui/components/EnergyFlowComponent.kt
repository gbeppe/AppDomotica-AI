package com.domopi.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domopi.app.R

@Composable
fun EnergyFlowComponent(
    solarPower: Float,
    homeConsumption: Float,
    gridPower: Float, 
    batteryPower: Float = 0f, 
    batterySoc: Float = 0f,
    modifier: Modifier = Modifier
) {
    // Check if we have received any meaningful data yet
    val hasData = solarPower != 0f || homeConsumption != 0f || gridPower != 0f || batteryPower != 0f

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
                Text(
                    "In connessione...", 
                    color = Color.Gray, 
                    fontSize = 14.sp,
                    style = MaterialTheme.typography.bodyMedium
                )
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

    Box(modifier = modifier.fillMaxWidth().height(460.dp), contentAlignment = Alignment.TopCenter) {
        
        val hubY = 160.dp
        val sidePadding = 40.dp
        val iconSize = 48.dp
        val nodeYOffset = hubY - 52.dp

        Canvas(modifier = Modifier.fillMaxSize()) {
            val hubPx = Offset(size.width / 2, hubY.toPx())
            
            val solarTopPx = Offset(size.width / 2, 70.dp.toPx())
            val gridLeftPx = Offset(sidePadding.toPx() + (iconSize.toPx() / 2), hubY.toPx())
            val homeRightPx = Offset(size.width - sidePadding.toPx() - (iconSize.toPx() / 2), hubY.toPx())
            val batteryTopPx = Offset(size.width / 2, hubY.toPx() + 100.dp.toPx())

            drawEnergyPathTesla(solarTopPx, hubPx, Color(0xFFFFEA00), phase, solarPower > 15, false)
            drawEnergyPathTesla(gridLeftPx, hubPx, Color.White, phase, Math.abs(gridPower) > 20, gridPower < 0)
            drawEnergyPathTesla(hubPx, homeRightPx, Color(0xFF00E5FF), phase, homeConsumption > 15, false)
            drawEnergyPathTesla(batteryTopPx, hubPx, Color(0xFF00FF00), phase, Math.abs(batteryPower) > 15, batteryPower < 0)
        }

        TeslaNodeSimpleV4(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            imageRes = R.drawable.solar_icon,
            powerValue = "${(solarPower/1000f).let { "%.1f".format(it) }} kW",
            color = Color(0xFFFFEA00)
        )

        TeslaNodeSimpleV4(
            modifier = Modifier.align(Alignment.TopStart).padding(start = sidePadding, top = nodeYOffset),
            imageRes = R.drawable.grid_icon,
            powerValue = "${(gridPower/1000f).let { "%.1f".format(kotlin.math.abs(it)) }} kW",
            color = Color.White
        )

        TeslaNodeSimpleV4(
            modifier = Modifier.align(Alignment.TopEnd).padding(end = sidePadding, top = nodeYOffset),
            imageRes = R.drawable.house_icon,
            powerValue = "${(homeConsumption/1000f).let { "%.1f".format(it) }} kW",
            color = Color(0xFF00E5FF)
        )

        BatteryNodeTeslaFinalV2(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = hubY + 70.dp),
            soc = batterySoc
        )
    }
}

@Composable
fun BatteryNodeTeslaFinalV2(
    modifier: Modifier = Modifier,
    soc: Float
) {
    val batteryHeight = 100.dp
    val batteryWidth = 65.dp
    
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Image(
            painter = painterResource(id = R.drawable.tesla_powerwall),
            contentDescription = null,
            modifier = Modifier
                .size(width = batteryWidth, height = batteryHeight)
                .background(Color.White, RoundedCornerShape(2.dp))
                .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
            contentScale = ContentScale.FillBounds
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = batteryWidth + 24.dp) 
                .height(batteryHeight),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
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
            
            Spacer(Modifier.width(4.dp))
            
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
fun TeslaNodeSimpleV4(
    modifier: Modifier = Modifier,
    imageRes: Int,
    powerValue: String,
    color: Color
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
                contentScale = ContentScale.Fit
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
