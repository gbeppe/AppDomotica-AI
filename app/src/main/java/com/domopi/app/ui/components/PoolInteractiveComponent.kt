package com.domopi.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.domopi.app.R
import com.domopi.app.ui.components.PoolGeometry as G

/**
 * Pool picture: the whole image in grey tones, every device that is on drawn in its original
 * colours (water light, skimmer, pump, deck with spotlights). Tap an element to toggle it.
 */
@Composable
fun PoolInteractiveComponent(
    lightStates: Map<String, Boolean>,
    onToggle: (String) -> Unit,
) {
    val image = ImageBitmap.imageResource(R.drawable.pool_background)
    val grey = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply {
            setToSaturation(0f)
            timesAssign(ColorMatrix().apply { setToScale(0.8f, 0.8f, 0.8f, 1f) })
        })
    }
    val currentToggle = rememberUpdatedState(onToggle)
    val on = { id: String -> lightStates[id] == true }
    val description = listOf(G.WATER_ID to "luce acqua", G.SKIMMER_ID to "skimmer", G.PUMP_ID to "pompa filtro",
        G.DECK_ID to "luci pedana").joinToString(", ") { (id, label) -> "$label ${if (on(id)) "accesa" else "spenta"}" }
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(G.ASPECT)
            .clip(RoundedCornerShape(12.dp))
            .testTag("pool-image")
            .semantics { contentDescription = "Piscina: $description. Tocca un elemento per cambiarlo." }
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    G.hit(tap.x / size.width, tap.y / size.height)?.let { currentToggle.value(it) }
                }
            }
    ) {
        val dst = IntSize(size.width.toInt(), size.height.toInt())
        drawImage(image, dstSize = dst, colorFilter = grey)
        val paths = regionPaths(size)
        for (id in listOf(G.DECK_ID, G.WATER_ID, G.SKIMMER_ID, G.PUMP_ID)) {
            if (on(id)) clipPath(paths.getValue(id)) { drawImage(image, dstSize = dst) }
        }
        if (on(G.WATER_ID)) clipPath(paths.getValue(G.WATER_ID)) {
            drawRect(Color(0x3800E5FF))  // the pool light tints the water
        }
        if (on(G.DECK_ID)) drawSpotGlow()
    }
}

private fun DrawScope.regionPaths(size: Size): Map<String, Path> {
    fun oval(e: G.Ellipse) = Path().apply {
        addOval(Rect((e.cx - e.rx) * size.width, (e.cy - e.ry) * size.height,
            (e.cx + e.rx) * size.width, (e.cy + e.ry) * size.height))
    }
    val water = oval(G.water)
    val skimmer = oval(G.skimmer)
    val pump = oval(G.pump)
    val walkway = Path().apply {
        G.walkway.forEachIndexed { i, (x, y) ->
            if (i == 0) moveTo(x * size.width, y * size.height) else lineTo(x * size.width, y * size.height)
        }
        close()
    }
    // Deck = ring + walkway, minus the elements drawn on their own.
    var deck = Path.combine(PathOperation.Union, oval(G.deckRing), walkway)
    for (hole in listOf(water, skimmer, pump)) deck = Path.combine(PathOperation.Difference, deck, hole)
    return mapOf(G.WATER_ID to water, G.SKIMMER_ID to skimmer, G.PUMP_ID to pump, G.DECK_ID to deck)
}

private fun DrawScope.drawSpotGlow() {
    val radius = size.width * 0.035f
    for ((x, y) in G.spots) {
        val center = Offset(x * size.width, y * size.height)
        drawCircle(Brush.radialGradient(listOf(Color(0xCCFFEA78), Color.Transparent), center, radius),
            radius, center)
    }
}
