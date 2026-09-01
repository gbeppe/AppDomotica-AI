package com.domopi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domopi.app.data.DomoPiConnectivityManager
import com.domopi.app.data.SettingsManager
import kotlinx.coroutines.delay

data class LogEntry(val timestamp: String, val type: String, val message: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicalMenuScreen(
    settingsManager: SettingsManager, 
    connectivityManager: DomoPiConnectivityManager
) {
    val nrLocalIp by settingsManager.nodeRedLocalIp.collectAsState("")
    val nrPort by settingsManager.nodeRedPort.collectAsState("1880")
    
    val mqttLocalIp by settingsManager.mqttLocalIp.collectAsState("")
    val mqttPort by settingsManager.mqttPort.collectAsState("1883")
    
    val tcLocalIp by settingsManager.tinycamLocalIp.collectAsState("")
    val tcPort by settingsManager.tinycamPort.collectAsState("8083")

    var nrOnline by remember { mutableStateOf(false) }
    var mqttOnline by remember { mutableStateOf(false) }
    var tcOnline by remember { mutableStateOf(false) }

    LaunchedEffect(nrLocalIp, nrPort, mqttLocalIp, mqttPort, tcLocalIp, tcPort) {
        while(true) {
            nrOnline = connectivityManager.checkServiceReachable(nrLocalIp, nrPort.toIntOrNull() ?: 1880)
            mqttOnline = connectivityManager.checkServiceReachable(mqttLocalIp, mqttPort.toIntOrNull() ?: 1883)
            tcOnline = connectivityManager.checkServiceReachable(tcLocalIp, tcPort.toIntOrNull() ?: 8083)
            delay(5000)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Menu Tecnico & Diagnostica") }) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Stato Servizi (Locale)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    ServiceStatusRow("Node-RED", nrOnline, "$nrLocalIp:$nrPort")
                    ServiceStatusRow("MQTT Broker", mqttOnline, "$mqttLocalIp:$mqttPort")
                    ServiceStatusRow("Tinycam Pro", tcOnline, "$tcLocalIp:$tcPort")
                }
            }

            Text("Live Logs (Mock)", style = MaterialTheme.typography.titleMedium)
            LogViewer()
        }
    }
}

@Composable
fun ServiceStatusRow(name: String, isOnline: Boolean, details: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(if (isOnline) Color.Green else Color.Red, MaterialTheme.shapes.extraSmall)
            )
            Spacer(Modifier.width(8.dp))
            Text(name)
        }
        Text(details, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LogViewer() {
    val logs = remember {
        mutableStateListOf(
            LogEntry("15:30:01", "MQTT IN", "zara/interface/env/living/temperature: 24.5"),
            LogEntry("15:30:05", "MQTT OUT", "zara/interface/lights/living/power/cmd: true"),
            LogEntry("15:30:10", "ERROR", "Connection lost")
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black,
        shape = MaterialTheme.shapes.medium
    ) {
        LazyColumn(modifier = Modifier.padding(8.dp)) {
            items(logs) { log ->
                Text(
                    text = "[${log.timestamp}] ${log.type}: ${log.message}",
                    color = when(log.type) {
                        "ERROR" -> Color.Red
                        "MQTT OUT" -> Color.Cyan
                        else -> Color.Green
                    },
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}
