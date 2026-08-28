package com.domopi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.domopi.app.data.MqttManager
import com.domopi.app.ui.components.PoolInteractiveComponent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoolScreen(mqttManager: MqttManager, onBack: () -> Unit) {
    val lightStates by mqttManager.lightStates.collectAsState()

    androidx.activity.compose.BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestione Piscina") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text("Dashboard Interattiva", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                PoolInteractiveComponent(
                    lightStates = lightStates,
                    onToggle = { id -> 
                        val currentState = lightStates[id] ?: false
                        mqttManager.toggleLight(id, currentState)
                    }
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Stato Dispositivi", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        
                        PoolControlRow("Pompa Filtro", lightStates["pompapiscina"] ?: false) {
                            mqttManager.toggleLight("pompapiscina", lightStates["pompapiscina"] ?: false)
                        }
                        PoolControlRow("Skimmer", lightStates["skimmerpiscina"] ?: false) {
                            mqttManager.toggleLight("skimmerpiscina", lightStates["skimmerpiscina"] ?: false)
                        }
                        PoolControlRow("Luce Interna", lightStates["lucipiscina"] ?: false) {
                            mqttManager.toggleLight("lucipiscina", lightStates["lucipiscina"] ?: false)
                        }
                        PoolControlRow("Luce Pedana", lightStates["lucipedanapiscina"] ?: false) {
                            mqttManager.toggleLight("lucipedanapiscina", lightStates["lucipedanapiscina"] ?: false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PoolControlRow(label: String, isOn: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = isOn, onCheckedChange = { onToggle(isOn) })
    }
}
