package com.domopi.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.domopi.app.data.ConnectionMode
import com.domopi.app.data.DomoPiConnectivityManager
import com.domopi.app.data.MqttManager
import com.domopi.app.data.SettingsManager
import com.domopi.app.ui.components.EnergyFlowComponent
import com.domopi.app.ui.theme.SolarGreen

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(
    mqttManager: MqttManager,
    connectivityManager: DomoPiConnectivityManager,
    settingsManager: SettingsManager,
    onNavigate: (String) -> Unit
) {
    val isConnectedMap by mqttManager.isConnected.collectAsState()
    val isConnected = isConnectedMap.values.all { it }
    val connectionMode by connectivityManager.connectionMode.collectAsState()
    
    val aiSettings by mqttManager.aiSettings.collectAsState()
    val lightStates by mqttManager.lightStates.collectAsState()
    val envState by mqttManager.environmentState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DomoPi", fontWeight = FontWeight.Bold) },
                actions = {
                    Row(
                        modifier = Modifier
                            .clickable { onNavigate("diagnosis") }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (connectionMode == ConnectionMode.LOCAL) "LOCALE" else "REMOTO",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (connectionMode == ConnectionMode.LOCAL) SolarGreen else Color.Cyan
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(if (isConnected) Color.Green else Color.Red, CircleShape)
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                TextButton(
                    onClick = { onNavigate("configuration") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("CONFIGURAZIONE")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // AI Climate Toggle Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.clickable { onNavigate("clima") },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoMode, null, tint = if (aiSettings.systemEnabled) SolarGreen else Color.Gray)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Climatizzazione AI: ${if (aiSettings.systemEnabled) "AUTO" else "MANUALE"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = aiSettings.systemEnabled,
                        onCheckedChange = { 
                            mqttManager.publish("domopi", "casa/clima/cmnd/AI_climate_enabling", if (it) "true" else "false", retained = true)
                        },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }

            // Top 2/3: Horizontal Carousel
            Box(modifier = Modifier.weight(2f)) {
                val pagerState = rememberPagerState(pageCount = { 5 })
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    pageSpacing = 16.dp
                ) { page ->
                    DomainCard(page, mqttManager, settingsManager, onNavigate)
                }
            }

            // Bottom 1/3: Home State Summary
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("STATO CASA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    
                    val activeLights = lightStates.filter { it.value }.keys.size
                    SummaryRow(Icons.Default.Lightbulb, "Luci Accese", "$activeLights")
                    
                    SummaryRow(
                        Icons.Default.Thermostat, 
                        "Soggiorno", 
                        "%.1f°C".format(java.util.Locale.US, envState.living.temperature)
                    )
                    SummaryRow(
                        Icons.Default.Bed, 
                        "Camera", 
                        "%.1f°C".format(java.util.Locale.US, envState.bedroom.temperature)
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DomainCard(
    page: Int, 
    mqttManager: MqttManager, 
    settingsManager: SettingsManager,
    onNavigate: (String) -> Unit
) {
    val energyData by mqttManager.energyData.collectAsState()
    val aiData by mqttManager.aiManagedData.collectAsState()
    val lightStates by mqttManager.lightStates.collectAsState()
    
    val tcIp by settingsManager.tinycamLocalIp.collectAsState("192.168.1.20")
    val tcPort by settingsManager.tinycamPort.collectAsState("8083")
    
    val title = when(page) {
        0 -> "ENERGIA"
        1 -> "LUCI"
        2 -> "CLIMA"
        3 -> "AMBIENTI"
        4 -> "TELECAMERE"
        else -> ""
    }
    
    val target = when(page) {
        0 -> "energy_detail"
        1 -> "lights"
        2 -> "clima"
        3 -> "ambienti"
        4 -> "cameras"
        else -> "dashboard"
    }

    Card(
        onClick = { onNavigate(target) },
        modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (page != 0) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
            }
            
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when(page) {
                    0 -> EnergyFlowComponent(
                        solarPower = energyData.solarPower,
                        homeConsumption = energyData.homeConsumption,
                        gridPower = energyData.gridPower,
                        batteryPower = energyData.batteryPower,
                        batterySoc = energyData.batterySoc
                    )
                    1 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val onCount = lightStates.filter { it.value }.size
                        Icon(Icons.Default.Lightbulb, null, modifier = Modifier.size(64.dp), tint = if (onCount > 0) Color(0xFFFFD600) else Color.Gray)
                        Text("$onCount Luci Accese", style = MaterialTheme.typography.bodyLarge)
                    }
                    2 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(aiData.stato_condizionatore.stato_attuale, style = MaterialTheme.typography.headlineMedium)
                        Text("Set: ${aiData.stato_condizionatore.temperatura_impostata_c}°C", style = MaterialTheme.typography.bodyMedium)
                        Text(aiData.stato_condizionatore.modalita_aria, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    3 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.HomeWork, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text("Monitoraggio Stanze", style = MaterialTheme.typography.bodyMedium)
                    }
                    4 -> com.domopi.app.ui.components.CameraStreamComponent(
                        url = "http://$tcIp:$tcPort/axis-cgi/mjpg/video.cgi?camera=1",
                        modifier = Modifier.clip(MaterialTheme.shapes.medium)
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            Text("Tocca per dettagli", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}
