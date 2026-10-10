package com.domopi.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import com.domopi.app.data.AppAlignmentStatus
import com.domopi.app.data.BuildAlignment
import com.domopi.app.data.IndicatorTone
import com.domopi.app.network.AccessPathState
import com.domopi.app.network.AccessPathStatus
import com.domopi.app.ui.theme.SolarGreen

private val IndicatorRed = Color(0xFFFF5252)

/** Shared top-bar flash: "flashing" always means "cannot be determined" (WallBox T-P1). */
@Composable
fun rememberFlashAlpha(flashing: Boolean): Float {
    if (!flashing) return 1.0f
    val infiniteTransition = rememberInfiniteTransition(label = "topBarFlash")
    val animAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    return animAlpha
}

fun accessPathColor(status: AccessPathStatus): Color = when (status) {
    AccessPathStatus.LOCALE, AccessPathStatus.REMOTO_CONNECTED -> SolarGreen
    AccessPathStatus.REMOTO_DISCONNECTED, AccessPathStatus.NON_IN_RETE, AccessPathStatus.UNKNOWN -> IndicatorRed
    AccessPathStatus.PENDING -> Color.Gray
}

fun indicatorToneColor(tone: IndicatorTone): Color = when (tone) {
    IndicatorTone.OK -> SolarGreen
    IndicatorTone.ERROR, IndicatorTone.UNVERIFIABLE -> IndicatorRed
    IndicatorTone.NEUTRAL -> Color.Gray
}

@Composable
fun AccessPathIndicator(state: AccessPathState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = accessPathColor(state.status).copy(alpha = rememberFlashAlpha(state.isFlashing))
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .sizeIn(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp)
            .semantics { contentDescription = "Modalità di connessione: ${state.label}" }
    ) {
        Text(
            text = state.label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun BuildAlignmentIcon(status: AppAlignmentStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tone = BuildAlignment.toneOf(status)
    val color = indicatorToneColor(tone).copy(alpha = rememberFlashAlpha(tone == IndicatorTone.UNVERIFIABLE))
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .sizeIn(minWidth = 40.dp, minHeight = 48.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Allineamento build app: ${BuildAlignment.describe(status)}" }
    ) {
        Icon(
            imageVector = Icons.Default.AccountTree,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
    }
}
