package com.domopi.app.ui.components

/**
 * Regions of pool_background.jpg (2726x1536) in normalized image coordinates (0..1),
 * measured on the picture. Pure Kotlin so hit-testing is unit tested.
 */
object PoolGeometry {
    data class Ellipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float) {
        fun contains(x: Float, y: Float): Boolean {
            val dx = (x - cx) / rx
            val dy = (y - cy) / ry
            return dx * dx + dy * dy <= 1f
        }
    }

    const val ASPECT = 2726f / 1536f
    const val WATER_ID = "lucipiscina"
    const val SKIMMER_ID = "skimmerpiscina"
    const val PUMP_ID = "pompapiscina"
    const val DECK_ID = "lucipedanapiscina"

    val water = Ellipse(0.5015f, 0.2094f, 0.300f, 0.124f)
    val skimmer = Ellipse(0.3165f, 0.356f, 0.032f, 0.064f)
    val pump = Ellipse(0.7835f, 0.4135f, 0.0665f, 0.094f)
    val deckRing = Ellipse(0.4993f, 0.3815f, 0.4518f, 0.2306f)
    /** Walkway towards the viewer: top-left, top-right, bottom-right, bottom-left. */
    val walkway = listOf(0.244f to 0.568f, 0.731f to 0.568f, 0.7985f to 0.927f, 0.169f to 0.927f)
    /** Recessed deck spotlights. */
    val spots = listOf(155f to 335f, 265f to 508f, 1830f to 340f, 1730f to 512f,
        565f to 652f, 1388f to 655f, 430f to 935f, 1490f to 940f).map { (x, y) -> x / 2000f to y / 1127f }

    private fun inWalkway(x: Float, y: Float): Boolean {
        var inside = false
        var j = walkway.lastIndex
        for (i in walkway.indices) {
            val (xi, yi) = walkway[i]
            val (xj, yj) = walkway[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    /** Device toggled by a tap at normalized (x, y); the smaller elements win over the deck. */
    fun hit(x: Float, y: Float): String? = when {
        pump.contains(x, y) -> PUMP_ID
        skimmer.contains(x, y) -> SKIMMER_ID
        water.contains(x, y) -> WATER_ID
        deckRing.contains(x, y) || inWalkway(x, y) -> DECK_ID
        else -> null
    }
}
