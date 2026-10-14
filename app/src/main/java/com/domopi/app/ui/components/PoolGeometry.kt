package com.domopi.app.ui.components

/**
 * Regions of pool_background.jpg (2752x1536 original, shipped at 2048x1143) in normalized image
 * coordinates (0..1), measured on the picture. Skimmer and pump follow the object outline, so the
 * whole object lights up and can be tapped. Pure Kotlin so hit-testing is unit tested.
 */
object PoolGeometry {
    data class Ellipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float) {
        fun contains(x: Float, y: Float): Boolean {
            val dx = (x - cx) / rx
            val dy = (y - cy) / ry
            return dx * dx + dy * dy <= 1f
        }
    }

    /** Closed outline, points in drawing order. */
    data class Polygon(val points: List<Pair<Float, Float>>) {
        fun contains(x: Float, y: Float): Boolean {
            var inside = false
            var j = points.lastIndex
            for (i in points.indices) {
                val (xi, yi) = points[i]
                val (xj, yj) = points[j]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
            return inside
        }
    }

    const val ASPECT = 2752f / 1536f
    const val WATER_ID = "lucipiscina"
    const val SKIMMER_ID = "skimmerpiscina"
    const val PUMP_ID = "pompapiscina"
    const val DECK_ID = "lucipedanapiscina"

    val water = Ellipse(0.502f, 0.198f, 0.304f, 0.132f)
    val skimmer = Polygon(listOf(
        0.287f to 0.5f, 0.292f to 0.47f, 0.33f to 0.452f, 0.4f to 0.452f, 0.432f to 0.47f, 0.437f to 0.5f,
        0.43f to 0.535f, 0.425f to 0.545f, 0.418f to 0.66f, 0.41f to 0.705f, 0.395f to 0.71f,
        0.392f to 0.775f, 0.335f to 0.775f, 0.333f to 0.71f, 0.315f to 0.7f, 0.305f to 0.6f, 0.3f to 0.545f,
        0.29f to 0.53f
    ))
    val pump = Polygon(listOf(
        0.575f to 0.42f, 0.615f to 0.405f, 0.64f to 0.43f, 0.675f to 0.438f, 0.69f to 0.47f, 0.69f to 0.55f,
        0.7f to 0.57f, 0.73f to 0.585f, 0.742f to 0.63f, 0.735f to 0.66f, 0.715f to 0.67f, 0.725f to 0.72f,
        0.7f to 0.74f, 0.66f to 0.755f, 0.645f to 0.79f, 0.6f to 0.805f, 0.56f to 0.8f, 0.545f to 0.77f,
        0.54f to 0.66f, 0.548f to 0.58f, 0.56f to 0.545f, 0.555f to 0.51f, 0.553f to 0.47f, 0.575f to 0.455f
    ))
    val deckRing = Ellipse(0.499f, 0.351f, 0.452f, 0.255f)
    /** Walkway towards the viewer: top-left, top-right, bottom-right, bottom-left. */
    val walkway = Polygon(listOf(0.247f to 0.56f, 0.73f to 0.57f, 0.798f to 0.935f, 0.169f to 0.918f))
    /** Recessed deck spotlights. */
    val spots = listOf(
        0.08f to 0.297f, 0.914f to 0.301f, 0.131f to 0.451f, 0.865f to 0.453f, 0.282f to 0.579f,
        0.218f to 0.831f, 0.745f to 0.84f
    )

    /** Device toggled by a tap at normalized (x, y); the objects on the deck win over the deck. */
    fun hit(x: Float, y: Float): String? = when {
        pump.contains(x, y) -> PUMP_ID
        skimmer.contains(x, y) -> SKIMMER_ID
        water.contains(x, y) -> WATER_ID
        deckRing.contains(x, y) || walkway.contains(x, y) -> DECK_ID
        else -> null
    }
}
