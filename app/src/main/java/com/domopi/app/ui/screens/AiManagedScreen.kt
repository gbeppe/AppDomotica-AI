package com.domopi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.domopi.app.data.MqttManager
import com.domopi.app.ui.theme.SolarGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiManagedScreen(mqttManager: MqttManager, onBack: () -> Unit) {
    val aiData by mqttManager.aiManagedData.collectAsState()
    val aiSettings by mqttManager.aiSettings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Climate Control") },
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
            item {
                StatusCard(aiData)
            }

            item {
                ForecastCard(aiData)
            }

            item {
                ControlCard(
                    title = "Sistema Abilitato",
                    onEnabledChange = { enabled ->
                        mqttManager.publish("zara/interface/climate/ai_enabling/cmd", if (enabled) "true" else "false", retained = false)
                    },
                    isEnabled = aiSettings.systemEnabled
                )
            }

            item {
                ControlCard(
                    title = "Gestione Mattino (Solar Only)",
                    onEnabledChange = { enabled ->
                        mqttManager.publish("casa/clima/cmnd/grace_mode_solar", if (enabled) "true" else "false", retained = false)
                    },
                    isEnabled = aiSettings.graceModeSolar
                )
            }

            item {
                SettingsSection(
                    title = "Parametri AI",
                    settings = listOf(
                        SettingItem("Minuti ON", aiSettings.minOnTime, "min_run_time"),
                        SettingItem("Minuti OFF", aiSettings.minOffTime, "min_off_time"),
                        SettingItem("Tolleranza Deficit", aiSettings.deficitTolerance, "deficit_tolerance_time"),
                        SettingItem("Soglia Emergenza Humidex", aiSettings.emergencyHumidex, "emergency_humidex_away")
                    ),
                    onValueChange = { topic, value ->
                        mqttManager.publish("casa/clima/cmnd/$topic", value, retained = false)
                    }
                )
            }

            item {
                ThresholdSection(
                    title = "Soglie Comfort",
                    humidexTarget = aiSettings.targetHumidex,
                    vmcMaxNight = aiSettings.vmcMaxNight.toFloat(),
                    onHumidexChange = { mqttManager.publish("casa/clima/cmnd/target_humidex", it.toString(), retained = false) },
                    onVmcChange = { mqttManager.publish("casa/clima/cmnd/vmc_max_notte", it.toInt().toString(), retained = false) }
                )
            }

            item {
                SystemDetailsCard(aiData)
            }
        }
    }
}

@Composable
fun ForecastCard(data: com.domopi.app.data.AiManagedData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Previsioni & Backup", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem("Solare Domani", "%.1f kWh".format(java.util.Locale.US, data.logica_controllo.previsione_solare_domani_kwh))
                MetricItem("Ricarica Batt.", "${data.logica_controllo.previsione_ricarica_battery_percent}%")
                MetricItem("In Batteria", "%.1f kWh".format(java.util.Locale.US, data.logica_controllo.kwh_stimati_in_batteria))
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
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
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
fun ControlCard(title: String, isEnabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Switch(checked = isEnabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
fun SettingsSection(title: String, settings: List<SettingItem>, onValueChange: (String, String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            settings.forEach { item ->
                var textValue by remember(item.value) { mutableStateOf(item.value) }
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text(item.label) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { onValueChange(item.topic, textValue) }) {
                            Text("SET")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ThresholdSection(
    title: String,
    humidexTarget: Float,
    vmcMaxNight: Float,
    onHumidexChange: (Float) -> Unit,
    onVmcChange: (Float) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            
            Column {
                val formattedHumidex = "%.1f".format(java.util.Locale.US, humidexTarget)
                Text("Target Humidex: $formattedHumidex", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = humidexTarget,
                    onValueChange = onHumidexChange,
                    valueRange = 25f..35f,
                    steps = 20
                )
            }

            Column {
                Text("Max VMC Notte: ${vmcMaxNight.toInt()}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = vmcMaxNight,
                    onValueChange = onVmcChange,
                    valueRange = 1f..3f,
                    steps = 1
                )
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}

data class SettingItem(val label: String, val value: String, val topic: String)
