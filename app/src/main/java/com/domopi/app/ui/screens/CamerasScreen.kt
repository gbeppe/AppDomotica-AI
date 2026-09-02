package com.domopi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.domopi.app.data.ConnectionMode
import com.domopi.app.data.ZaiConnectivityManager
import com.domopi.app.data.SettingsManager
import com.domopi.app.ui.components.CameraStreamComponent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CamerasScreen(
    settingsManager: SettingsManager,
    connectivityManager: ZaiConnectivityManager,
    onBack: () -> Unit,
) {
    val connectionMode by connectivityManager.connectionMode.collectAsState()
    
    val tcLocalIp by settingsManager.tinycamLocalIp.collectAsState("")
    val tcRemoteIp by settingsManager.tinycamRemoteIp.collectAsState("")
    val tcPort by settingsManager.tinycamPort.collectAsState("8083")
    val tcUser by settingsManager.tinycamUser.collectAsState("guest")
    val tcPass by settingsManager.tinycamPass.collectAsState("password")

    val tcIp = if (connectionMode == ConnectionMode.LOCAL) tcLocalIp else tcRemoteIp
    val baseUrl = "http://$tcIp:$tcPort/axis-cgi/mjpg/video.cgi"

    // Stato per la telecamera selezionata (0 = Ingresso, 1 = Soggiorno)
    var selectedCamIndex by remember { mutableIntStateOf(0) }
    
    val cams = listOf(
        "Ingresso" to "936942165",
        "Soggiorno" to "1708386743"
    )
    
    val currentCamName = cams[selectedCamIndex].first
    val currentCamId = cams[selectedCamIndex].second

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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Selettore Telecamera (Titolo + Frecce)
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (selectedCamIndex > 0) selectedCamIndex-- else selectedCamIndex = cams.size - 1 }
                ) {
                    Icon(Icons.Default.ChevronLeft, "Precedente", modifier = Modifier.size(32.dp))
                }
                
                Text(
                    text = currentCamName.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                
                IconButton(
                    onClick = { if (selectedCamIndex < (cams.size - 1)) selectedCamIndex++ else selectedCamIndex = 0 }
                ) {
                    Icon(Icons.Default.ChevronRight, "Successiva", modifier = Modifier.size(32.dp))
                }
            }

            // Riquadro Video Singolo
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                // Passiamo l'URL specifico della camera selezionata.
                // Compose ricaricherà il componente solo per questa camera.
                key(currentCamId) {
                    CameraStreamComponent(
                        url = "$baseUrl?cameraId=$currentCamId",
                        user = tcUser,
                        pass = tcPass,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                "Usa le frecce per cambiare visuale.\nLo stream viene attivato solo per la camera visibile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
