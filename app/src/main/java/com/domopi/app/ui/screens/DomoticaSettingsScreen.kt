package com.domopi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.domopi.app.data.MqttManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomoticaSettingsScreen(mqttManager: MqttManager, onBack: () -> Unit) {
    val settings by mqttManager.domoticaSettings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestione Casa") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Impostazioni Generali",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                SettingToggleCard(
                    title = "Modalità Vacanza",
                    description = "Ottimizza i consumi e simula presenza quando sei fuori",
                    icon = Icons.Default.FlightTakeoff,
                    checked = settings.holidayMode,
                    color = Color(0xFFE91E63),
                    onCheckedChange = { mqttManager.publish("zara/interface/settings/holiday_mode/cmd", it.toString()) }
                )
            }

            item {
                SettingToggleCard(
                    title = "Accensione Luci ECO",
                    description = "Limita la potenza e il numero di luci attive per risparmio",
                    icon = Icons.Default.Eco,
                    checked = settings.ecoLights,
                    color = Color(0xFF4CAF50),
                    onCheckedChange = { mqttManager.publish("zara/interface/settings/eco_lights/cmd", it.toString()) }
                )
            }

            item {
                SettingToggleCard(
                    title = "Luci Piscina AUTO",
                    description = "Gestione crepuscolare automatica delle luci piscina",
                    icon = Icons.Default.Pool,
                    checked = settings.poolLightsAuto,
                    color = Color(0xFF2196F3),
                    onCheckedChange = { mqttManager.publish("zara/interface/settings/pool_lights_auto/cmd", it.toString()) }
                )
            }

            item {
                SettingToggleCard(
                    title = "Sensore Portico",
                    description = "Abilita l'automazione luci basata sul sensore di movimento",
                    icon = Icons.Default.SensorWindow,
                    checked = settings.porchSensor,
                    color = Color(0xFFFF9800),
                    onCheckedChange = { mqttManager.publish("zara/interface/settings/porch_sensor/cmd", it.toString()) }
                )
            }

            item {
                SettingToggleCard(
                    title = "Climatizzazione Auto",
                    description = "Permette all'AI di gestire autonomamente gli split AC",
                    icon = Icons.Default.AcUnit,
                    checked = settings.acAuto,
                    color = Color(0xFF00E5FF),
                    onCheckedChange = { mqttManager.publish("zara/interface/settings/ac_auto/cmd", it.toString()) }
                )
            }
        }
    }
}

@Composable
fun SettingToggleCard(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    color: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) color.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.medium,
                color = if (checked) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = if (checked) color else Color.Gray)
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = color,
                    checkedTrackColor = color.copy(alpha = 0.3f)
                )
            )
        }
    }
}
