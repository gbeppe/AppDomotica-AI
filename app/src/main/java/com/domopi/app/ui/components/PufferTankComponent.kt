package com.domopi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PufferTankComponent(
    acsTemp: Float,
    altoTemp: Float,
    bassoTemp: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Contenitore Tank
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF252525))
                .border(2.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
        ) {
            // Layer Superiore: ACS
            PufferLayer(
                label = "ACS",
                temp = acsTemp,
                modifier = Modifier.weight(1f)
            )
            
            // Layer Centrale: ALTO
            PufferLayer(
                label = "ALTO",
                temp = altoTemp,
                modifier = Modifier.weight(1f),
                hasBorder = true
            )
            
            // Layer Inferiore: BASSO
            PufferLayer(
                label = "BASSO",
                temp = bassoTemp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun PufferLayer(
    label: String,
    temp: Float,
    modifier: Modifier = Modifier,
    hasBorder: Boolean = false
) {
    val color = when {
        temp <= 30f -> Color(0xFF00E5FF) // Azzurro
        temp <= 40f -> Color(0xFFFF9800) // Arancione
        else -> Color(0xFFF44336)        // Rosso
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(color.copy(alpha = 0.8f))
            .then(
                if (hasBorder) Modifier.border(width = 1.dp, color = Color.Black.copy(alpha = 0.2f)) 
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${"%.1f".format(java.util.Locale.US, temp)}°C",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
