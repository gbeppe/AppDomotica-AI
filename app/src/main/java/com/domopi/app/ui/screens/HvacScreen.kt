package com.domopi.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.domopi.app.data.MqttManager
import com.domopi.app.ui.components.PufferTankComponent
import com.domopi.app.ui.theme.SolarGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HvacScreen(mqttManager: MqttManager, onBack: () -> Unit) {
    val hvacState by mqttManager.hvacState.collectAsState()
    val energyData by mqttManager.energyData.collectAsState()

    androidx.activity.compose.BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestione Impianti HVAC") },
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
            // 1. VMC (Unica scheda mantenuta come da istruzioni)
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WindPower, null, tint = Color(0xFF00E5FF))
                            Spacer(Modifier.width(8.dp))
                            Text("VMC (Ventilazione Meccanica)", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(16.dp))
                        
                        Text(
                            text = "Velocità Attuale: ${hvacState.vmc.speed}", 
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF00E5FF)
                        )
                        Spacer(Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            (1..4).forEach { speed ->
                                FilterChip(
                                    selected = hvacState.vmc.speed == speed,
                                    onClick = { mqttManager.publish("zara/interface/ventilation/vmc/speed/cmd", speed.toString()) },
                                    label = { Text(speed.toString()) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                        selectedLabelColor = Color(0xFF00E5FF)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 2. Temperature Puffer (Riserva Energia)
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Waves, null, tint = Color(0xFF2979FF))
                            Spacer(Modifier.width(8.dp))
                            Text("Riserva Energia (Puffer)", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(24.dp))
                        
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            PufferTankComponent(
                                acsTemp = energyData.pufferAcs,
                                altoTemp = energyData.pufferAlto,
                                bassoTemp = energyData.pufferBasso
                            )
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Logica colori: Azzurro (<=30°), Arancio (31-40°), Rosso (>40°)", 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }

            // 3. Solare Termico
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WbSunny, null, tint = Color(0xFFFFEA00))
                            Spacer(Modifier.width(8.dp))
                            Text("Solare Termico", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Temperatura Collettore", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${"%.1f".format(java.util.Locale.US, energyData.solarCollectorTemp)}°C", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                            
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Velocità Pompa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (energyData.solarPumpSpeed > 0) {
                                        val rotateTransition = rememberInfiniteTransition(label = "pump_rotation")
                                        val angle by rotateTransition.animateFloat(
                                            initialValue = 0f, targetValue = 360f,
                                            animationSpec = infiniteRepeatable(tween(1000 / (energyData.solarPumpSpeed.coerceAtLeast(1)), easing = LinearEasing))
                                        )
                                        Icon(
                                            Icons.Default.Autorenew, 
                                            null, 
                                            modifier = Modifier.size(20.dp).graphicsLayer(rotationZ = angle),
                                            tint = SolarGreen
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text("${energyData.solarPumpSpeed}%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        
                        if (energyData.solarPumpSpeed > 0) {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { energyData.solarPumpSpeed / 100f },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = SolarGreen,
                                trackColor = SolarGreen.copy(alpha = 0.1f),
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                }
            }

            // 4. Caldaia a Gas
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.HotTub, null, tint = Color.Red)
                                Spacer(Modifier.width(8.dp))
                                Text("Caldaia a Gas", style = MaterialTheme.typography.titleMedium)
                            }
                            
                            Surface(
                                color = if (hvacState.boiler.active) Color.Red.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f),
                                shape = CircleShape,
                                border = BorderStroke(1.dp, if (hvacState.boiler.active) Color.Red else Color.Gray)
                            ) {
                                Text(
                                    text = if (hvacState.boiler.active) "FIAMMA ON" else "SPENTA",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hvacState.boiler.active) Color.Red else Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        if (hvacState.boiler.active) {
                            Spacer(Modifier.height(20.dp))
                            Text("Modulazione Fiamma", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LinearProgressIndicator(
                                    progress = { hvacState.boiler.modulation / 100f },
                                    modifier = Modifier.weight(1f).height(8.dp),
                                    color = Color.Red,
                                    trackColor = Color.Red.copy(alpha = 0.1f),
                                    strokeCap = StrokeCap.Round
                                )
                                Spacer(Modifier.width(16.dp))
                                Text("${hvacState.boiler.modulation}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 4. Riscaldamento a Pavimento
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Layers, null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(8.dp))
                                Text("Pompa Pavimento", style = MaterialTheme.typography.titleMedium)
                            }
                            
                            Switch(
                                checked = hvacState.floorHeating.enabled,
                                onCheckedChange = { mqttManager.publish("zara/interface/heating/floor_pump/enabled/cmd", if (it) "true" else "false") }
                            )
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (hvacState.floorHeating.pumpActive) Color.Green.copy(alpha = 0.1f) 
                                    else Color.Gray.copy(alpha = 0.1f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp)
                        ) {
                            val rotateTransition = rememberInfiniteTransition(label = "pump_anim")
                            val angle by rotateTransition.animateFloat(
                                initialValue = 0f, targetValue = 360f,
                                animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing))
                            )
                            
                            Icon(
                                Icons.Default.Autorenew, 
                                null, 
                                modifier = Modifier
                                    .size(24.dp)
                                    .then(if (hvacState.floorHeating.pumpActive) Modifier.graphicsLayer(rotationZ = angle) else Modifier),
                                tint = if (hvacState.floorHeating.pumpActive) Color.Green else Color.Gray
                            )
                            
                            Spacer(Modifier.width(12.dp))
                            
                            Column {
                                Text(
                                    text = if (hvacState.floorHeating.pumpActive) "POMPA IN FUNZIONE" else "POMPA FERMA",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (hvacState.floorHeating.pumpActive) Color.Green else Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (hvacState.floorHeating.enabled) "Sistema abilitato dal controllo AI" else "Sistema disabilitato",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
            // 5. Termocamino Palazzetti
            item {
                FireplaceCard(hvacState.palazzetti, mqttManager)
            }

            // 6. Termostati Ambienti
            item {
                Text(
                    "Termostati", 
                    style = MaterialTheme.typography.titleLarge, 
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            item {
                ThermostatCard(
                    title = "Soggiorno",
                    state = hvacState.thermostatLiving,
                    deviceId = "thermostat_living",
                    mqttManager = mqttManager
                )
            }
            
            item {
                ThermostatCard(
                    title = "Bagno Servizio",
                    state = hvacState.thermostatBath,
                    deviceId = "thermostat_bath",
                    mqttManager = mqttManager
                )
            }
        }
    }
}

@Composable
fun ThermostatCard(
    title: String,
    state: com.domopi.app.data.ThermostatStatus,
    deviceId: String,
    mqttManager: MqttManager
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                // Indicatore Power (Stato)
                Icon(
                    Icons.Default.PowerSettingsNew,
                    null,
                    tint = if (state.power) Color.Red else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Temperatura Attuale (Gauge stilizzato)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ATTUALE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        text = "${"%.1f".format(java.util.Locale.US, state.currentTemp)}°",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = if (state.power) Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Slider Target
                Column(modifier = Modifier.weight(1f).padding(start = 24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text("SETPOINT", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(
                            "${"%.1f".format(java.util.Locale.US, state.targetTemp)}°C",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = state.targetTemp,
                        onValueChange = { mqttManager.publish("zara/interface/climate/$deviceId/target_temperature/cmd", "%.1f".format(java.util.Locale.US, it)) },
                        valueRange = state.minTemp..state.maxTemp,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )

            // Limiti (Min/Max)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LimitAdjustment(
                    label = "Min",
                    value = state.minTemp,
                    onValueChange = { mqttManager.publish("zara/interface/climate/$deviceId/min_temperature/cmd", "%.1f".format(java.util.Locale.US, it)) }
                )
                LimitAdjustment(
                    label = "Max",
                    value = state.maxTemp,
                    onValueChange = { mqttManager.publish("zara/interface/climate/$deviceId/max_temperature/cmd", "%.1f".format(java.util.Locale.US, it)) }
                )
            }
        }
    }
}

@Composable
fun LimitAdjustment(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onValueChange(value - 0.5f) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp))
            }
            Text(
                text = "${"%.1f".format(java.util.Locale.US, value)}°",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { onValueChange(value + 0.5f) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FireplaceCard(state: com.domopi.app.data.PalazzettiStatus, mqttManager: MqttManager) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fireplace, null, tint = Color(0xFFFF5722))
                    Spacer(Modifier.width(12.dp))
                    Text("Termocamino Palazzetti", style = MaterialTheme.typography.titleMedium)
                }

                FilledIconToggleButton(
                    checked = state.active,
                    onCheckedChange = { mqttManager.publish("zara/interface/fireplace/main/power/cmd", it.toString()) },
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        checkedContainerColor = Color.Red.copy(alpha = 0.2f),
                        checkedContentColor = Color.Red
                    )
                ) {
                    Icon(Icons.Default.PowerSettingsNew, null)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Modalità Operativa (Menu moderno)
            var expanded by remember { mutableStateOf(false) }
            val modes = listOf("Disattivato", "Riscaldamento", "Integrazione Caldaia", "Acqua Sanitaria", "Manuale")
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = state.mode,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Modalità") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    modes.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode) },
                            onClick = {
                                mqttManager.publish("zara/interface/fireplace/main/mode/cmd", mode)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Orari Avvio/Spegnimento
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.startTime,
                    onValueChange = { mqttManager.publish("zara/interface/fireplace/main/start_time/cmd", it) },
                    label = { Text("Avvio") },
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = Color.Green) },
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = state.stopTime,
                    onValueChange = { mqttManager.publish("zara/interface/fireplace/main/stop_time/cmd", it) },
                    label = { Text("Fine") },
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Default.Stop, null, tint = Color.Red) },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            // Sezione Potenza
            Text(
                "Livello Potenza Combustione", 
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = state.level.toFloat(),
                    onValueChange = { mqttManager.publish("zara/interface/fireplace/main/level/cmd", it.toInt().toString()) },
                    valueRange = 1f..6f,
                    steps = 4,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF9800),
                        activeTrackColor = Color(0xFFFF9800)
                    )
                )
                Text(
                    text = state.level.toString(),
                    modifier = Modifier.padding(start = 16.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF9800)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Controllo Automatico
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Gestione Automatica", style = MaterialTheme.typography.bodyMedium)
                        Text("Regola la potenza in base al carico", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = state.autoPower,
                        onCheckedChange = { mqttManager.publish("zara/interface/fireplace/main/auto_power/cmd", it.toString()) }
                    )
                }
            }
        }
    }
}

