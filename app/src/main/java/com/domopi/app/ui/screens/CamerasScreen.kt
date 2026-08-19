package com.domopi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.domopi.app.data.SettingsManager
import com.domopi.app.ui.components.CameraStreamComponent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CamerasScreen(settingsManager: SettingsManager, onBack: () -> Unit) {
    val tcIp by settingsManager.tinycamLocalIp.collectAsState("192.168.1.20")
    val tcPort by settingsManager.tinycamPort.collectAsState("8083")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Telecamere") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Ingresso", style = MaterialTheme.typography.titleMedium)
                CameraStreamComponent(url = "http://$tcIp:$tcPort/axis-cgi/mjpg/video.cgi?camera=1")
            }
            item {
                Text("Soggiorno", style = MaterialTheme.typography.titleMedium)
                CameraStreamComponent(url = "http://$tcIp:$tcPort/axis-cgi/mjpg/video.cgi?camera=2")
            }
        }
    }
}
