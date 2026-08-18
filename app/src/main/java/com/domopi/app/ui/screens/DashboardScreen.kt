package com.domopi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.domopi.app.ui.components.EnergyFlowComponent
import com.domopi.app.ui.components.GlimmerGauge
import com.domopi.app.ui.theme.SolarGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
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
            // Sezione Energia
            Text("Energia & Flussi", style = MaterialTheme.typography.titleLarge)
            EnergyFlowComponent(
                solarPower = 2500f,
                homeConsumption = 1200f,
                gridPower = -1300f // Export
            )

            // Sezione Sensori
            Text("Ambiente", style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GlimmerGauge(
                    value = 22.5f,
                    min = 10f,
                    max = 40f,
                    label = "Soggiorno",
                    unit = "°C",
                    color = SolarGreen
                )
                GlimmerGauge(
                    value = 45f,
                    min = 0f,
                    max = 100f,
                    label = "Umidità",
                    unit = "%",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            
            // Pulsanti di navigazione rapida (Domini)
            Text("Domini", style = MaterialTheme.typography.titleLarge)
            DomainGrid()
        }
    }
}

@Composable
fun DomainGrid() {
    val domains = listOf("Luci", "Clima", "Sicurezza", "Irrigazione")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        domains.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { domain ->
                    Button(
                        onClick = { /* TODO */ },
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
