package com.domopi.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.domopi.app.data.ControlInputs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domopi.app.data.AcOverride
import com.domopi.app.data.AiManagedData
import com.domopi.app.data.AcReasonCategory
import com.domopi.app.data.AcReasonMapper
import com.domopi.app.data.EnvironmentState
import com.domopi.app.data.HvacState
import com.domopi.app.data.LogicaControllo
import com.domopi.app.data.MetricheElettriche
import com.domopi.app.data.MqttManager
import com.domopi.app.data.PredictiveReserveStatus
import com.domopi.app.data.PrSeverity
import com.domopi.app.ui.components.prSeverityColor
import com.domopi.app.data.SettingsManager
import com.domopi.app.data.StatoCondizionatore
import com.domopi.app.ui.components.NumericStepper
import com.domopi.app.ui.components.formatLocalizedDate
import com.domopi.app.ui.theme.SolarGreen
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiManagedScreen(
    mqttManager: MqttManager,
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    val aiData by mqttManager.aiManagedData.collectAsState()
    val aiSettings by mqttManager.aiSettings.collectAsState()
    val envState by mqttManager.environmentState.collectAsState()
    val hvacState by mqttManager.hvacState.collectAsState()
    val isAdminMode by settingsManager.isAdminMode.collectAsState(initial = false)

    var showPrDialog by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    if (showPrDialog) {
        val willEnable = !aiSettings.predictiveReserveEnabled
        AlertDialog(
            onDismissRequest = { showPrDialog = false },
            icon = { Icon(Icons.Default.NightsStay, null, tint = if (willEnable) SolarGreen else Color.Gray) },
            title = { Text("Conferma Modifica PR") },
            text = {
                Text(
                    (if (willEnable)
                        "Sei sicuro di voler abilitare il controllo Predictive Reserve notturno?\n\n"
                    else
                        "Sei sicuro di voler disabilitare il controllo Predictive Reserve notturno?\n\n") +
                        PredictiveReserveStatus.toggleExplanation(willEnable, aiSettings.predictiveReserveMode, aiSettings.predictiveReserveReady) +
                        "\n\n" + PredictiveReserveStatus.details(aiSettings.predictiveReserveMode, aiSettings.predictiveReserveReady)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mqttManager.publish("zara/interface/predictive_reserve/control_enabled/cmd", if (willEnable) "true" else "false")
                        showPrDialog = false
                    }
                ) {
                    Text("CONFERMA", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrDialog = false }) {
                    Text("ANNULLA")
                }
            }
        )
    }

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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Allarme Sistema (se presente)
            if (aiSettings.alarm != null) {
                item {
                    val alarm = aiSettings.alarm!!
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
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

            // 0 & 1. Abilitazioni Sistema (I due switch ricompattati)
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        // Switch 1: AI abilitato
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = MaterialTheme.shapes.medium,
                                color = if (aiSettings.systemEnabled) SolarGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (aiSettings.systemEnabled) SolarGreen else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Text(
                                "AI abilitato",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )

                            Switch(
                                checked = aiSettings.systemEnabled,
                                onCheckedChange = { enabled ->
                                    mqttManager.publish("zara/interface/ai/system_enabled/cmd", if (enabled) "true" else "false")
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = SolarGreen,
                                    checkedTrackColor = SolarGreen.copy(alpha = 0.3f)
                                )
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), thickness = 0.5.dp)

                        // Switch 2: Predictive Reserve
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = MaterialTheme.shapes.medium,
                                color = if (aiSettings.predictiveReserveEnabled) SolarGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.NightsStay,
                                        contentDescription = null,
                                        tint = if (aiSettings.predictiveReserveEnabled) SolarGreen else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            val prEffect = PredictiveReserveStatus.effect(
                                aiSettings.predictiveReserveEnabled, aiSettings.predictiveReserveMode, aiSettings.predictiveReserveReady)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Predictive Reserve",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    prEffect.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = prSeverityColor(prEffect.severity)
                                )
                            }

                            Switch(
                                checked = aiSettings.predictiveReserveEnabled,
                                onCheckedChange = { showPrDialog = true },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = SolarGreen,
                                    checkedTrackColor = SolarGreen.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }
            }

            // 2. Stato Operativo
            item {
                StatusCard(aiData, envState, hvacState)
            }

            item { AcOverrideCard(hvacState.ac.override, hvacState.ac.overrideClosed, hvacState.ac.state) { mqttManager.publish(AcOverride.TOPIC_CMD, it) } }

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
                            label = "Humidex impostato",
                            value = aiSettings.nightHumidexThreshold,
                            onValueChange = { mqttManager.publish("zara/interface/ai/night_humidex_threshold/cmd", it.toString()) }
                        )

                        NumericStepper(
                            label = "Isteresi comfort Humidex",
                            value = aiData.logicaControllo.ampiezzaIsteresiHumidex,
                            onValueChange = { mqttManager.publish("zara/interface/ai/humidex_comfort_hysteresis/cmd", it.toString()) }
                        )

                        NumericStepper(
                            label = "Margine di attivazione NIGHT_DRY",
                            value = aiData.logicaControllo.nightDryHumidexMargin,
                            onValueChange = { mqttManager.publish("zara/interface/ai/night_dry_humidex_margin/cmd", it.toString()) }
                        )
                        
                        Column {
                            // Dragging moves only the local value; one command on release, if it changed.
                            var pendingVmc by remember { mutableStateOf<Float?>(null) }
                            LaunchedEffect(aiSettings.nightVmcMaxSpeed) { pendingVmc = null }
                            val shownVmc = pendingVmc?.let { ControlInputs.step(it, 1..4) } ?: aiSettings.nightVmcMaxSpeed
                            Text("Velocità Max VMC Notte: $shownVmc", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = pendingVmc ?: aiSettings.nightVmcMaxSpeed.toFloat(),
                                onValueChange = { pendingVmc = it },
                                onValueChangeFinished = {
                                    val speed = pendingVmc?.let { ControlInputs.step(it, 1..4) }
                                    if (speed == null || speed == aiSettings.nightVmcMaxSpeed) pendingVmc = null
                                    else mqttManager.publish("zara/interface/ai/night_vmc_max_speed/cmd", speed.toString())
                                },
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
                            label = "Humidex emergenza",
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
    icon: ImageVector,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusCard(
    data: AiManagedData,
    envState: EnvironmentState,
    hvacState: HvacState
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
                        text = hvacState.ac.state?.currentLabel ?: statusText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = statusColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Sezione Motivo di Logica (motivoAc) + Descrizione Estesa + Quando Compare + Effetto + Valori Live
            val reasonInfo = AcReasonMapper.getAcReasonInfo(data.statoCondizionatore.motivoLogica, data)
            val categoryColor = when (reasonInfo.category) {
                AcReasonCategory.CRITICAL -> Color.Red
                AcReasonCategory.WARNING -> Color(0xFFFF9800)
                AcReasonCategory.NORMAL -> SolarGreen
                AcReasonCategory.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            // AI-CLIMATE-AC-STATE.1: the logic codes only on request (the state is in the line of
            // "Condizionatore a tempo" and in the HVAC screen; user, 30/10/2026)
            var technical by remember { mutableStateOf(false) }
            TextButton(onClick = { technical = !technical }, contentPadding = PaddingValues(0.dp)) {
                Text(if (technical) "Nascondi dettagli tecnici" else "Dettagli tecnici: ${reasonInfo.userTitle.ifEmpty { reasonInfo.code }}",
                    style = MaterialTheme.typography.labelMedium)
            }
            if (technical) {
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
                            Icon(icon, contentDescription = null, tint = categoryColor, modifier = Modifier.size(20.dp))
                            Text(
                                text = reasonInfo.userTitle.ifEmpty { reasonInfo.code },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = categoryColor
                            )
                        }

                        if (reasonInfo.userExplanation.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = reasonInfo.userExplanation,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        reasonInfo.remainingTimerMinutes?.let { remMin ->
                            if (remMin > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = categoryColor.copy(alpha = 0.15f),
                                    shape = MaterialTheme.shapes.extraSmall,
                                    border = BorderStroke(0.5.dp, categoryColor.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "⏱️ Tempo residuo: $remMin min",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = categoryColor
                                        )
                                    }
                                }
                            }
                        }

                        if (reasonInfo.description.isNotEmpty() && reasonInfo.description != reasonInfo.userExplanation) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = reasonInfo.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (reasonInfo.quandoCompare.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "📌 Quando compare: ${reasonInfo.quandoCompare}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (reasonInfo.effetto.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⚙️ Effetto: ${reasonInfo.effetto}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (reasonInfo.soglieCostanti.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "📐 ${reasonInfo.soglieCostanti}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = categoryColor
                            )
                        }

                        if (reasonInfo.metrics.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
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

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Codice logica: ${reasonInfo.code}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
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
            hvacState.ac.state?.let { st ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricItem("Surplus FV", st.surplusW?.let { "$it W" } ?: "—")
                    MetricItem("Batteria", st.soc?.let { "$it %" } ?: "—")
                    MetricItem("Consumo AC", "${st.powerW} W")
                }
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
            DetailRow(
                "Humidex Comfort",
                "%.1f (%.1f - %.1f)".format(
                    locale,
                    data.logicaControllo.sogliaSpegnimentoComfort,
                    data.logicaControllo.sogliaAttivazioneApplicata,
                    data.logicaControllo.ampiezzaIsteresiHumidex
                )
            )
            DetailRow("Timer Anticiclo", "${data.logicaControllo.tempoMancanteAnticicloMinuti} min")
            DetailRow("Batteria Stimata", "%.1f kWh".format(locale, data.logicaControllo.kwhStimatiInBatteria))
            DetailRow("Previsione Ricarica", "${data.logicaControllo.previsioneRicaricaBatteryPercent}%")
            
            DetailRow("Prev. Solare Domani", "%.1f kWh".format(locale, data.logicaControllo.previsioneSolareDomaniKwh))
            DetailRow("Data Prev. Solare", formatLocalizedDate(data.logicaControllo.previsioneSolareData, locale))
            
            DetailRow("Cuscinetto Sicurezza", "%.1f kWh".format(locale, data.logicaControllo.cuscinettoSicurezzaKwh))
            DetailRow("Cuscinetto Richiesto", "%.1f kWh".format(locale, data.logicaControllo.cuscinettoRichiestoKwh))
            
            DetailRow("Portata VMC Stimata", "${data.logicaControllo.vmcPortataStimataM3h} m³/h")
            DetailRow("Stanza Rilevamento VMC", data.logicaControllo.stanzaRilevamentoVmc.ifEmpty { "N/D" })
            
            DetailRow("Blocco Emergenza", if (data.logicaControllo.bloccoEmergenzaAttivo) "ATTIVO" else "Disattivo")

            data.logicaControllo.diagnosticaAc.timerTipo?.let { tipo ->
                DetailRow("Tipo Timer", tipo)
            }
            data.logicaControllo.diagnosticaAc.timerResiduoMinuti?.let { rem ->
                if (rem > 0) DetailRow("Timer Residuo", "$rem min")
            }
            data.logicaControllo.diagnosticaAc.pausaNotturnaResiduoMinuti?.let { rem ->
                if (rem > 0) DetailRow("Pausa Notturna Residua", "$rem min")
            }
            data.logicaControllo.diagnosticaAc.deficitResiduoMinuti?.let { rem ->
                if (rem > 0) DetailRow("Deficit Residuo", "$rem min")
            }
            data.logicaControllo.diagnosticaAc.causaTransizione?.let { causa ->
                DetailRow("Causa Transizione", causa)
            }
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
