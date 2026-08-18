package com.domopi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage

@Composable
fun CameraStreamComponent(
    url: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16 / 9f)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Usiamo Coil per visualizzare lo stream MJPEG (se supportato) o i frame statici
        SubcomposeAsyncImage(
            model = url,
            contentDescription = "Camera Stream",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            },
            error = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Errore caricamento stream", color = Color.White)
                    Text(url, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        )
    }
}
