package com.domopi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domopi.app.data.MqttManager
import com.domopi.app.ui.components.NumericStepper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiManagedScreen(mqttManager: MqttManager, onBack: () -> Unit) {
    val aiData by mqttManager.aiManagedData.collectAsState()
    val aiSettings by mqttManager.aiSettings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Managed Control") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // operativo
            item {
                StatusCard(aiData)
            }

            // 1. Sistema Abilitato
            item {
                ControlCard(
                    title = "Sistema AI Abilitato",
                    subtitle = "Attiva/Disattiva logica climatica intelligente",
                    isEnabled = aiSettings.systemEnabled,
                    onEnabledChange = { enabled ->
                        mqttManager.publish("zara/interface/ai/system_enabled/cmd", if (enabled) "true" else "false")
                    }
                )
            }

            // 2 & 3. Tempi Compressore
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Gestione Cicli Compressore", style = MaterialTheme.typography.titleMedium)
                        
                        NumericStepper(
                            label = "Minuti ON (min)",
                            value = aiSettings.compressorOnMin,
                            onValueChange = { mqttManager.publish("zara/interface/ai/compressor_on_min/cmd", it.toString()) }
                        )
                        
                        NumericStepper(
                            label = "Minuti OFF (min)",
                            value = aiSettings.compressorOffMin,
                            onValueChange = { mqttManager.publish("zara/interface/ai/compressor_off_min/cmd", it.toString()) }
                        )
                    }
                }
            }

            // 4 & 5. Parametri Notturni
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Comfort Notturno", style = MaterialTheme.typography.titleMedium)
                        
                        NumericStepper(
                            label = "Soglia Humidex Notte",
                            value = aiSettings.nightHumidexThreshold,
                            onValueChange = { mqttManager.publish("zara/interface/ai/night_humidex_threshold/cmd", it.toString()) }
                        )
                        
                        Column {
                            Text("Velocità Max VMC Notte: ${aiSettings.nightVmcMaxSpeed}", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = aiSettings.nightVmcMaxSpeed.toFloat(),
                                onValueChange = { mqttManager.publish("zara/interface/ai/night_vmc_max_speed/cmd", it.toInt().toString()) },
                                valueRange = 1f..4f,
                                steps = 2
                            )
                        }
                    }
                }
            }

            // 6. Tolleranza Deficit
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    NumericStepper(
                        label = "Tolleranza Deficit (min)",
                        value = aiSettings.deficitToleranceMin,
                        onValueChange = { mqttManager.publish("zara/interface/ai/deficit_tolerance_min/cmd", it.toString()) },
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // 7 & 8. Gestione Mattino
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Gestione Mattutina (08:00 - 12:00)", style = MaterialTheme.typography.titleMedium)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Abilita Gestione AC", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = aiSettings.morningAcManagement,
                                onCheckedChange = { mqttManager.publish("zara/interface/ai/morning_ac_management/cmd", if (it) "true" else "false") }
                            )
                        }
                        
                        NumericStepper(
                            label = "Soglia Emergenza Humidex",
                            value = aiSettings.morningHumidexEmergency,
                            onValueChange = { mqttManager.publish("zara/interface/ai/morning_humidex_emergency/cmd", it.toString()) }
                        )
                    }
                }
            }

            item {
                SystemDetailsCard(aiData)
            }
        }
    }
}

@Composable
fun ControlCard(title: String, subtitle: String, isEnabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = isEnabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
fun StatusCard(data: com.domopi.app.data.AiManagedData) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Stato Operativo", style = MaterialTheme.typography.titleMedium)
                val color = when (data.stato_condizionatore.modalita_aria) {
                    "Raffrescamento" -> Color(0xFF2196F3)
                    "Riscaldamento" -> Color(0xFFFF5722)
                    "Deumidificazione" -> Color(0xFF009688)
                    else -> Color.Gray
                }
                Surface(
                    color = color.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = data.stato_condizionatore.stato_attuale,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = color,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = data.stato_condizionatore.motivo_logica,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem("Temp. Interna", "%.1f°C".format(java.util.Locale.US, data.metriche_ambientali.temperatura_c))
                MetricItem("Humidex", "%.1f".format(java.util.Locale.US, data.metriche_ambientali.humidex))
                MetricItem("VMC Speed", "${data.stato_vmc.velocita_attuale}")
            }
        }
    }
}

@Composable
fun SystemDetailsCard(data: com.domopi.app.data.AiManagedData) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Dettagli Logica", style = MaterialTheme.typography.titleMedium)
            DetailRow("Stagione Attiva", data.stagione_attiva)
            DetailRow("SOC Minimo Applicato", "%.1f%%".format(java.util.Locale.US, data.logica_controllo.soc_minimo_applied))
            DetailRow("Soglia Humidex Reale", "%.1f".format(java.util.Locale.US, data.logica_controllo.soglia_attivazione_applicata))
            DetailRow("Timer Anticiclo", "${data.logica_controllo.tempo_mancante_anticiclo_minuti} min")
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
