package com.domopi.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.domopi.app.data.HouseAlerts
import kotlinx.coroutines.delay

/** Written alerts (fase 3.5 B) on top of any screen; tap to close, auto-hide after 8 s. */
@Composable
fun HouseAlertBanner(connected: Boolean, soc: Double?, modifier: Modifier = Modifier) {
    val alerts = remember { HouseAlerts() }
    val queue = remember { mutableStateListOf<String>() }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) { delay(5_000); tick++ }  // lets the 30 s disconnection grace expire
    }
    LaunchedEffect(connected, soc, tick) {
        queue += alerts.update(connected, soc, System.currentTimeMillis())
    }
    val current = queue.firstOrNull()
    LaunchedEffect(current) {
        if (current != null) { delay(8_000); queue.remove(current) }
    }
    AnimatedVisibility(visible = current != null, modifier = modifier,
        enter = slideInVertically { -it }, exit = slideOutVertically { -it }) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable { current?.let(queue::remove) }.testTag("house-alert")
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, contentDescription = null,
                    tint = MaterialTheme.colorScheme.inverseOnSurface)
                Spacer(Modifier.width(12.dp))
                Text(current.orEmpty(), color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
