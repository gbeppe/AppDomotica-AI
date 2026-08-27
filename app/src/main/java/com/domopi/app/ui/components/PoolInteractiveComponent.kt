package com.domopi.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.domopi.app.R

@Composable
fun PoolInteractiveComponent(
    lightStates: Map<String, Boolean>,
    onToggle: (String) -> Unit
) {
    val grayscaleFilter = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16 / 9f)
            .clip(RoundedCornerShape(12.dp))
    ) {
        val scope = this
        val parentW = scope.maxWidth
        val parentH = scope.maxHeight

        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Base layer: Full normal color image
            Image(
                painter = painterResource(id = R.drawable.pool_background),
                contentDescription = "Pool Layout",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            // 2. Interactive Layers: Grayscale slices on top when OFF
            
            // Water (Internal Lights)
            PoolElementOverlayFinal(
                id = "lucipiscina",
                isOn = lightStates["lucipiscina"] ?: false,
                xPerc = 0.22f, yPerc = 0.13f, wPerc = 0.56f, hPerc = 0.35f,
                parentW = parentW, parentH = parentH,
                onToggle = onToggle,
                shape = CircleShape,
                grayscaleFilter = grayscaleFilter
            )

            // Skimmer
            PoolElementOverlayFinal(
                id = "skimmerpiscina",
                isOn = lightStates["skimmerpiscina"] ?: false,
                xPerc = 0.31f, yPerc = 0.35f, wPerc = 0.08f, hPerc = 0.12f,
                parentW = parentW, parentH = parentH,
                onToggle = onToggle,
                shape = RoundedCornerShape(4.dp),
                grayscaleFilter = grayscaleFilter
            )

            // Pump
            PoolElementOverlayFinal(
                id = "pompapiscina",
                isOn = lightStates["pompapiscina"] ?: false,
                xPerc = 0.73f, yPerc = 0.38f, wPerc = 0.12f, hPerc = 0.18f,
                parentW = parentW, parentH = parentH,
                onToggle = onToggle,
                shape = RoundedCornerShape(4.dp),
                grayscaleFilter = grayscaleFilter
            )

            // Deck Lights
            PoolElementOverlayFinal(
                id = "lucipedanapiscina",
                isOn = lightStates["lucipedanapiscina"] ?: false,
                xPerc = 0.15f, yPerc = 0.58f, wPerc = 0.7f, hPerc = 0.35f,
                parentW = parentW, parentH = parentH,
                onToggle = onToggle,
                shape = RoundedCornerShape(8.dp),
                grayscaleFilter = grayscaleFilter
            )
        }
    }
}

@Composable
fun PoolElementOverlayFinal(
    id: String,
    isOn: Boolean,
    xPerc: Float,
    yPerc: Float,
    wPerc: Float,
    hPerc: Float,
    parentW: Dp,
    parentH: Dp,
    onToggle: (String) -> Unit,
    shape: Shape,
    grayscaleFilter: ColorFilter
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(width = parentW * wPerc, height = parentH * hPerc)
            .offset(x = parentW * xPerc, y = parentH * yPerc)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onToggle(id)
            }
    ) {
        if (!isOn) {
            Image(
                painter = painterResource(id = R.drawable.pool_background),
                contentDescription = null,
                modifier = Modifier
                    .size(width = parentW, height = parentH)
                    .offset(x = -(parentW * xPerc), y = -(parentH * yPerc)),
                contentScale = ContentScale.FillBounds,
                colorFilter = grayscaleFilter
            )
            // Add a very slight dark overlay to emphasize it's off
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.1f)))
        }
    }
}
