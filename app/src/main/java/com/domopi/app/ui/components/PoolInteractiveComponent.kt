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
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16 / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A1A)) // Sfondo scuro di base
    ) {
        val parentW = this.maxWidth
        val parentH = this.maxHeight

        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Base layer: L'immagine originale della piscina
            Image(
                painter = painterResource(id = R.drawable.pool_background),
                contentDescription = "Pool Layout",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            // 2. Overlay Interattivi: Copriamo o coloriamo le aree in base allo stato
            
            // LUCE ACQUA (Area grande centrale)
            PoolElementOverlay(
                id = "lucipiscina",
                isOn = lightStates["lucipiscina"] ?: false,
                xPerc = 0.22f, yPerc = 0.13f, wPerc = 0.56f, hPerc = 0.35f,
                parentW = parentW, parentH = parentH,
                onToggle = onToggle,
                shape = CircleShape,
                onColor = Color(0x4000E5FF), // Azzurro semitrasparente quando acceso
                offColor = Color(0xCC1A1A1A)  // Grigio scuro quasi coprente quando spento
            )

            // SKIMMER
            PoolElementOverlay(
                id = "skimmerpiscina",
                isOn = lightStates["skimmerpiscina"] ?: false,
                xPerc = 0.31f, yPerc = 0.35f, wPerc = 0.08f, hPerc = 0.12f,
                parentW = parentW, parentH = parentH,
                onToggle = onToggle,
                shape = RoundedCornerShape(4.dp),
                offColor = Color(0xEE1A1A1A) // "Nasconde" l'elemento se OFF
            )

            // POMPA
            PoolElementOverlay(
                id = "pompapiscina",
                isOn = lightStates["pompapiscina"] ?: false,
                xPerc = 0.73f, yPerc = 0.38f, wPerc = 0.12f, hPerc = 0.18f,
                parentW = parentW, parentH = parentH,
                onToggle = onToggle,
                shape = RoundedCornerShape(4.dp),
                offColor = Color(0xEE1A1A1A) // "Nasconde" la pompa se OFF
            )

            // LUCI PAVIMENTO (Pedana)
            PoolElementOverlay(
                id = "lucipedanapiscina",
                isOn = lightStates["lucipedanapiscina"] ?: false,
                xPerc = 0.15f, yPerc = 0.58f, wPerc = 0.7f, hPerc = 0.35f,
                parentW = parentW, parentH = parentH,
                onToggle = onToggle,
                shape = RoundedCornerShape(8.dp),
                onColor = Color(0x33FFEA00), // Leggero bagliore giallo se ON
                offColor = Color(0xDD1A1A1A)  // Coprente se OFF
            )
        }
    }
}

@Composable
fun PoolElementOverlay(
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
    onColor: Color = Color.Transparent,
    offColor: Color = Color.Transparent
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(width = parentW * wPerc, height = parentH * hPerc)
            .offset(x = parentW * xPerc, y = parentH * yPerc)
            .clip(shape)
            .background(if (isOn) onColor else offColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onToggle(id)
            }
    )
}
