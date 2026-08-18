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

data class LogEntry(val timestamp: String, val type: String, val message: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicalMenuScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Menu Tecnico & Diagnostica") }) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Service Status
            Card {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Stato Servizi", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    ServiceStatusRow("Node-RED", true, "192.168.1.20:1880")
                    ServiceStatusRow("MQTT Broker", true, "192.168.1.20:1883")
                    ServiceStatusRow("Tinycam Pro", false, "Offline")
                }
            }

            // Log Viewer
            Text("Live Logs", style = MaterialTheme.typography.titleMedium)
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
            LogEntry("15:30:01", "MQTT IN", "emon/AC/em: 450W"),
            LogEntry("15:30:05", "MQTT OUT", "cmnd/light/1: ON"),
            LogEntry("15:30:10", "ERROR", "Timeout connecting to 192.168.1.20")
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
