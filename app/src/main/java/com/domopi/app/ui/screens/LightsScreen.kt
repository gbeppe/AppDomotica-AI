package com.domopi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.domopi.app.data.MqttManager

data class LightDevice(val id: String, val name: String, val topic: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightsScreen(mqttManager: MqttManager, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler { onBack() }
    val knownDevices = remember {
        listOf(
            LightDevice("sala", "Soggiorno Main", "sala"),
            LightDevice("libreria", "Libreria", "libreria"),
            LightDevice("televisione", "Televisione", "televisione"),
            LightDevice("tavolinolettura", "Tavolino Lettura", "tavolinolettura"),
            LightDevice("lucecamera", "Luce Camera", "lucecamera"),
            LightDevice("lampadahifi", "Lampada HiFi", "lampadahifi"),
            LightDevice("lavanderia", "Lavanderia", "lavanderia"),
            LightDevice("portico", "Portico Ingresso", "portico"),
            LightDevice("cucina", "Cucina Piano", "cucina"),
            LightDevice("esterno", "Esterno", "esterno"),
            LightDevice("prolunga", "Prolunga / Allarme", "prolunga")
        )
    }

    val liveStates by mqttManager.lightStates.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Illuminazione") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sezione Luci
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Punti luce", style = MaterialTheme.typography.titleMedium)
            }
            
            items(knownDevices) { device ->
                val isOn = liveStates[device.topic] ?: false
                LightCard(
                    name = device.name,
                    isOn = isOn,
                    onToggle = { mqttManager.toggleLight(device.topic, isOn) }
                )
            }

            // Sezione Scenari
            item(span = { GridItemSpan(maxLineSpan) }) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Scenari", style = MaterialTheme.typography.titleMedium)
            }
            
            item {
                SceneButton("TV Mode", "tv") {
                    mqttManager.sendLightScene("tv")
                }
            }

            item {
                SceneButton("Sleep Mode", "sleep") {
                    mqttManager.sendLightScene("sleep")
                }
            }

            item {
                SceneButton("Tutte ON", "all_on") {
                    mqttManager.sendLightScene("all_on")
                }
            }

            item {
                SceneButton("Tutte OFF", "all_off") {
                    mqttManager.sendLightScene("all_off")
                }
            }
        }
    }
}

@Composable
fun SceneButton(label: String, type: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun LightCard(name: String, isOn: Boolean, onToggle: () -> Unit) {
    Card(
        onClick = onToggle,
        colors = CardDefaults.cardColors(
            containerColor = if (isOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth().height(100.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Lightbulb,
                contentDescription = null,
                tint = if (isOn) Color(0xFFFFD600) else Color.Gray,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(name, style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
