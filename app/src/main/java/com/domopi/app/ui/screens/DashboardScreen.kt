package com.domopi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.domopi.app.data.MqttManager
import com.domopi.app.ui.components.EnergyFlowComponent
import com.domopi.app.ui.components.GlimmerGauge
import com.domopi.app.ui.theme.SolarGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(mqttManager: MqttManager, onDomainClick: (String) -> Unit) {
    // Osserviamo i dati energetici live
    val energyData by mqttManager.energyData.collectAsState()
    // Osserviamo lo stato ambientale live
    val envState by mqttManager.environmentState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("DomoPi Dashboard") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Sezione Energia Live
            Text("Energia & Flussi Live", style = MaterialTheme.typography.titleLarge)
            EnergyFlowComponent(
                solarPower = energyData.solarPower,
                homeConsumption = energyData.homeConsumption,
                gridPower = energyData.gridPower,
                batteryPower = energyData.batteryPower,
                batterySoc = energyData.batterySoc
            )

            // Sezione Sensori Live
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Ambiente", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { onDomainClick("Ambienti") }) {
                    Text("Vedi Tutto")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GlimmerGauge(
                    value = envState.living.temperature,
                    min = 10f,
                    max = 40f,
                    label = "Soggiorno",
                    unit = "°C",
                    color = SolarGreen
                )
                GlimmerGauge(
                    value = envState.living.humidity,
                    min = 0f,
                    max = 100f,
                    label = "Umidità",
                    unit = "%",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            
            // Pulsanti di navigazione rapida (Domini)
            Text("Domini", style = MaterialTheme.typography.titleLarge)
            DomainGrid(onDomainClick)
        }
    }
}

@Composable
fun DomainGrid(onDomainClick: (String) -> Unit) {
    val domains = listOf("Luci", "Clima", "Ambienti", "Sicurezza", "Irrigazione")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        domains.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { domain ->
                    Button(
                        onClick = { onDomainClick(domain) },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(domain)
                    }
                }
            }
        }
    }
}
