package com.domopi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.domopi.app.data.MqttManager
import com.domopi.app.ui.components.GlimmerGauge
import com.domopi.app.ui.theme.SolarGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientiScreen(mqttManager: MqttManager, onBack: () -> Unit) {
    val envState by mqttManager.environmentState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monitoraggio Ambienti") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                RoomSection("Soggiorno", envState.living)
            }
            item {
                HorizontalDivider()
            }
            item {
                RoomSection("Camera Matrimoniale", envState.bedroom)
            }
            item {
                HorizontalDivider()
            }
            item {
                OutdoorSection("Esterno", envState.outdoor)
            }
        }
    }
}

@Composable
fun RoomSection(name: String, data: com.domopi.app.data.SensorData) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(name, style = MaterialTheme.typography.headlineSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GlimmerGauge(
                value = data.temperature,
                min = 10f,
                max = 40f,
                label = "Temp",
                unit = "°C",
                color = SolarGreen
            )
            GlimmerGauge(
                value = data.humidity,
                min = 0f,
                max = 100f,
                label = "Umidità",
                unit = "%",
                color = MaterialTheme.colorScheme.secondary
            )
            GlimmerGauge(
                value = data.humidex,
                min = 20f,
                max = 50f,
                label = "Humidex",
                unit = "",
                color = Color(0xFFFF8000)
            )
        }
    }
}

@Composable
fun OutdoorSection(name: String, data: com.domopi.app.data.SensorData) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(name, style = MaterialTheme.typography.headlineSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GlimmerGauge(
                value = data.temperature,
                min = -10f,
                max = 45f,
                label = "Esterna",
                unit = "°C",
                color = Color(0xFF2979FF)
            )
            GlimmerGauge(
                value = data.humidity,
                min = 0f,
                max = 100f,
                label = "Umidità",
                unit = "%",
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}
