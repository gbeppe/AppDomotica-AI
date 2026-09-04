package com.domopi.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domopi.app.data.AiManagedData
import com.domopi.app.data.AcReasonCategory
import com.domopi.app.data.AcReasonMapper
import com.domopi.app.data.EnvironmentState
import com.domopi.app.data.HvacState
import com.domopi.app.data.LogicaControllo
import com.domopi.app.data.MetricheElettriche
import com.domopi.app.data.MqttManager
import com.domopi.app.data.StatoCondizionatore
import com.domopi.app.ui.components.NumericStepper
import com.domopi.app.ui.theme.SolarGreen
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiManagedScreen(
    mqttManager: MqttManager,
    settingsManager: com.domopi.app.data.SettingsManager,
    onBack: () -> Unit
) {
    val aiData by mqttManager.aiManagedData.collectAsState()
    val aiSettings by mqttManager.aiSettings.collectAsState()
    val envState by mqttManager.environmentState.collectAsState()
    val hvacState by mqttManager.hvacState.collectAsState()
    val isAdminMode by settingsManager.isAdminMode.collectAsState(initial = false)

    androidx.activity.compose.BackHandler { onBack() }

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
            // Allarme Sistema (se presente)
            if (aiSettings.alarm != null) {
                item {
                    val alarm = aiSettings.alarm!!
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, null, tint = Color.Red)
                                Spacer(Modifier.width(8.dp))
                                Text("ANOMALIA RILEVATA", color = Color.Red, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Stato: ${alarm.stato}", style = MaterialTheme.typography.bodyMedium)
                            Text(alarm.motivo, style = MaterialTheme.typography.bodySmall)
                            
                            if (alarm.elementiMancanti.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Elementi coinvolti:", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    alarm.elementiMancanti.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 1. Sistema Abilitato (In alto)
            item {
                ControlCard(
                    title = "AI abilitato",
                    icon = Icons.Default.AutoAwesome,
                    isEnabled = aiSettings.systemEnabled,
                    onEnabledChange = { enabled ->
                        mqttManager.publish("zara/interface/ai/system_enabled/cmd", if (enabled) "true" else "false")
                    }
                )
            }

            // 2. Stato Operativo
            item {
                StatusCard(aiData, envState, hvacState)
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
                                onCheckedChange = { mqttManager.publish("zara/interface/ai/morning_ac_management/cmd", if (it) "true" else "false") },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = SolarGreen,
                                    checkedTrackColor = SolarGreen.copy(alpha = 0.3f)
                                )
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
fun ControlCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    val activeColor = SolarGreen
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) activeColor.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.medium,
                color = if (isEnabled) activeColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = if (isEnabled) activeColor else Color.Gray)
                }
            }

            Spacer(Modifier.width(16.dp))

            Text(
                title, 
                style = MaterialTheme.typography.bodyLarge, 
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            
            Switch(
                checked = isEnabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = activeColor,
                    checkedTrackColor = activeColor.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@Composable
fun StatusCard(
    data: com.domopi.app.data.AiManagedData,
    envState: com.domopi.app.data.EnvironmentState,
    hvacState: com.domopi.app.data.HvacState
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Stato Operativo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Stato Operativo Sistema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                val statusText = data.statoCondizionatore.statoAttuale.ifEmpty { "OFF" }
                val statusColor = when (statusText.uppercase()) {
                    "COOLING_ON", "RAFFRESCAMENTO" -> Color(0xFF2196F3)
                    "NIGHT_DRY", "DEUMIDIFICAZIONE" -> Color(0xFF009688)
                    "STANDBY_INVERTER" -> Color(0xFFFFB300)
                    "HEAT_DIURNO", "HEAT_SICUREZZA_NOTTE", "RISCALDAMENTO" -> Color(0xFFFF5722)
                    else -> Color.Gray
                }
                
                Surface(
                    color = statusColor.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = statusColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Sezione Motivo di Logica (motivoAc) + Descrizione Estesa + Valori Live
            val reasonInfo = AcReasonMapper.getAcReasonInfo(data.statoCondizionatore.motivoLogica, data)
            val categoryColor = when (reasonInfo.category) {
                AcReasonCategory.CRITICAL -> Color.Red
                AcReasonCategory.WARNING -> Color(0xFFFF9800)
                AcReasonCategory.NORMAL -> SolarGreen
                AcReasonCategory.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = categoryColor.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val icon = when (reasonInfo.category) {
                            AcReasonCategory.CRITICAL -> Icons.Default.Error
                            AcReasonCategory.WARNING -> Icons.Default.Warning
                            AcReasonCategory.NORMAL -> Icons.Default.CheckCircle
                            AcReasonCategory.UNKNOWN -> Icons.Default.Info
                        }
                        Icon(icon, contentDescription = null, tint = categoryColor, modifier = Modifier.size(18.dp))
                        Text(
                            text = reasonInfo.code,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = categoryColor
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Text(
                        text = reasonInfo.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (reasonInfo.metrics.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            reasonInfo.metrics.forEach { metric ->
                                Surface(
                                    color = categoryColor.copy(alpha = 0.12f),
                                    shape = MaterialTheme.shapes.extraSmall,
                                    border = BorderStroke(0.5.dp, categoryColor.copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${metric.label}: ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = metric.value,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = categoryColor,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            
            val locale = LocalConfiguration.current.locales[0]
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem("Temp. Int.", "%.1f°C".format(locale, envState.living.temperature))
                MetricItem("Humidex", "%.1f".format(locale, envState.living.humidex))
                MetricItem("Setpoint", "%.1f°C".format(locale, data.statoCondizionatore.temperaturaImpostataC))
                MetricItem("VMC", hvacState.vmc.speed.toString())
            }
        }
    }
}

@Composable
fun SystemDetailsCard(data: AiManagedData) {
    val locale = LocalConfiguration.current.locales[0]
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Dettagli Logica", style = MaterialTheme.typography.titleMedium)
            DetailRow("Stagione Attiva", data.logicaControllo.stagioneAttuale)
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
            
            DetailRow("SOC Minimo Applicato", "%.1f%%".format(locale, data.logicaControllo.socMinimoApplied))
            DetailRow("Soglia Humidex Reale", "%.1f".format(locale, data.logicaControllo.sogliaAttivazioneApplicata))
            DetailRow("Timer Anticiclo", "${data.logicaControllo.tempoMancanteAnticicloMinuti} min")
            DetailRow("Batteria Stimata", "%.1f kWh".format(locale, data.logicaControllo.kwhStimatiInBatteria))
            DetailRow("Previsione Ricarica", "${data.logicaControllo.previsioneRicaricaBatteryPercent}%")
            
            DetailRow("Prev. Solare Domani", "%.1f kWh".format(locale, data.logicaControllo.previsioneSolareDomaniKwh))
            DetailRow("Data Prev. Solare", data.logicaControllo.previsioneSolareData)
            
            DetailRow("Cuscinetto Sicurezza", "%.1f kWh".format(locale, data.logicaControllo.cuscinettoSicurezzaKwh))
            DetailRow("Cuscinetto Richiesto", "%.1f kWh".format(locale, data.logicaControllo.cuscinettoRichiestoKwh))
            
            DetailRow("Portata VMC Stimata", "${data.logicaControllo.vmcPortataStimataM3h} m³/h")
            DetailRow("Stanza Rilevamento VMC", data.logicaControllo.stanzaRilevamentoVmc.ifEmpty { "N/D" })
            
            DetailRow("Blocco Emergenza", if (data.logicaControllo.bloccoEmergenzaAttivo) "ATTIVO" else "Disattivo")
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
