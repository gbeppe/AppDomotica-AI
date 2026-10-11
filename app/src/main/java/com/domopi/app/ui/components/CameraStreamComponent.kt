package com.domopi.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.domopi.app.data.CameraException
import com.domopi.app.data.CameraFailure
import com.domopi.app.data.TinyCamClient
import com.domopi.app.data.message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Native TinyCam viewer (no WebView: the system WebView does not render an MJPEG stream opened
 * as a page). [live] = continuous MJPEG; otherwise a snapshot every [refreshMs] (dashboard card).
 * Runs only while composed, so the carousel loads the camera only when its card is visible.
 */
@Composable
fun CameraStreamComponent(
    client: TinyCamClient,
    cameraId: String,
    modifier: Modifier = Modifier,
    live: Boolean = true,
    refreshMs: Long = 1500,
) {
    var frame by remember(cameraId) { mutableStateOf<ImageBitmap?>(null) }
    var failure by remember(cameraId) { mutableStateOf<CameraFailure?>(null) }

    LaunchedEffect(client, cameraId, live) {
        while (isActive) {
            val result = withContext(Dispatchers.IO) {
                if (live) client.stream(cameraId) { bytes ->
                    decode(bytes)?.let { frame = it; failure = null }
                    isActive
                } else client.snapshot(cameraId).map { bytes -> decode(bytes)?.let { frame = it; failure = null }; Unit }
            }
            result.exceptionOrNull()?.let { failure = (it as? CameraException)?.failure ?: CameraFailure.Unreachable }
            // Wrong credentials or camera never fix themselves quickly: retry slowly.
            delay(when (failure) {
                null -> if (live) 1000 else refreshMs
                CameraFailure.Unauthorized, CameraFailure.NotFound, CameraFailure.NotConfigured -> 15_000
                else -> 5_000
            })
        }
    }

    Box(modifier.fillMaxWidth().aspectRatio(16 / 9f).background(Color.Black).testTag("camera-view"),
        contentAlignment = Alignment.Center) {
        frame?.let { Image(it, contentDescription = "Telecamera", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        val current = failure
        when {
            current != null -> Text(current.message(), color = Color.White, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.6f)).padding(12.dp).testTag("camera-error"))
            frame == null -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        }
    }
}

private fun decode(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
