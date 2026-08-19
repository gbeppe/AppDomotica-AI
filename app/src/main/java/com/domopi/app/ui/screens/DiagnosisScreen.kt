package com.domopi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domopi.app.data.MqttManager
import com.domopi.app.data.SettingsManager
import com.domopi.app.data.DomoPiConnectivityManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosisScreen(
    mqttManager: MqttManager,
    settingsManager: SettingsManager,
    connectivityManager: DomoPiConnectivityManager,
    onBack: () -> Unit
) {
    val isConnectedMap by mqttManager.isConnected.collectAsState()
    val trafficLog by mqttManager.trafficLog.collectAsState()
    
    val tcIp by settingsManager.tinycamLocalIp.collectAsState("192.168.1.20")
    val tcPort by settingsManager.tinycamPort.collectAsState("8083")
    var tcOnline by remember { mutableStateOf(false) }

    LaunchedEffect(tcIp, tcPort) {
        tcOnline = connectivityManager.checkServiceReachable(tcIp, tcPort.toIntOrNull() ?: 8083)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnosi & Traffico") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Connettività", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    BrokerStatusRow("DomoPi MQTT", isConnectedMap["domopi"] ?: false)
                    BrokerStatusRow("EmonPi MQTT", isConnectedMap["emonpi"] ?: false)
                    BrokerStatusRow("TinyCam Pro Server", tcOnline)
                }
            }

            Text("Traffico MQTT (Ultimi 50 messaggi)", style = MaterialTheme.typography.titleMedium)
            
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                color = Color.Black,
                shape = MaterialTheme.shapes.medium
            ) {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    items(trafficLog) { log ->
                        val color = if (log.contains("OUT:")) Color.Cyan else Color.Green
                        Text(
                            text = log,
                            color = color,
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BrokerStatusRow(name: String, isConnected: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(if (isConnected) Color.Green else Color.Red, MaterialTheme.shapes.extraSmall)
        )
        Spacer(Modifier.width(8.dp))
        Text(name)
        Spacer(Modifier.weight(1f))
        Text(if (isConnected) "Connesso" else "Disconnesso", style = MaterialTheme.typography.labelSmall)
    }
}
