package com.domopi.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.domopi.app.R

@Composable
fun ModernVillaGraphic(
    modifier: Modifier = Modifier,
    floorEnabled: Boolean,
    pumpActive: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "villa_anim")
    
    // Animazione per il gradiente pulsante quando la pompa è attiva
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        // --- 1. FOTO REALE DELLA VILLA ---
        Image(
            painter = painterResource(id = R.drawable.hvac_villa),
            contentDescription = "Villa",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // --- 2. OVERLAY GRADIENTE (Moderno e Semplificato) ---
        if (floorEnabled || pumpActive) {
            val alphaValue = if (pumpActive) pulseAlpha else 0.4f
            
            // Usiamo un gradiente arancione che parte dal basso (massima opacità) e sfuma verso l'alto
            val orangeGradient = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFF9800).copy(alpha = alphaValue * 0.5f),
                    Color(0xFFFF9800).copy(alpha = alphaValue)
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f) // Copre la metà inferiore della foto
                    .background(orangeGradient)
            )
        }
    }
}
