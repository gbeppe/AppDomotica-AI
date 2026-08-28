package com.domopi.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.sp
import com.domopi.app.data.ConnectionMode
import com.domopi.app.data.DomoPiConnectivityManager
import com.domopi.app.data.MqttManager
import com.domopi.app.data.SettingsManager
import com.domopi.app.ui.components.EnergyFlowComponent
import com.domopi.app.ui.components.PoolInteractiveComponent
import com.domopi.app.ui.theme.SolarGreen

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(
    mqttManager: MqttManager,
    connectivityManager: DomoPiConnectivityManager,
    settingsManager: SettingsManager,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onNavigate: (String) -> Unit
) {
    val isConnected by mqttManager.isConnected.collectAsState()
    val connectionMode by connectivityManager.connectionMode.collectAsState()
    
    val aiSettings by mqttManager.aiSettings.collectAsState()
    val lightStates by mqttManager.lightStates.collectAsState()
    val envState by mqttManager.environmentState.collectAsState()

    // DEBUG: Log all true states
    LaunchedEffect(lightStates) {
        val allOn = lightStates.filter { it.value }.keys
        android.util.Log.d("LIGHT_DEBUG", "All ON keys in map: $allOn")
    }

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
                        modifier = Modifier
                            .clickable { onNavigate("clima") }
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoMode, 
                                null, 
                                tint = if (aiSettings.systemEnabled) SolarGreen else Color.Gray
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Climatizzazione AI",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        Surface(
                            color = if (aiSettings.systemEnabled) SolarGreen.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, if (aiSettings.systemEnabled) SolarGreen else Color.Gray)
                        ) {
                            Text(
                                text = if (aiSettings.systemEnabled) "ATTIVATO" else "DISATTIVATO",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (aiSettings.systemEnabled) SolarGreen else Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Top 2/3: Horizontal Carousel (Circular/Infinite)
            Box(modifier = Modifier.weight(2f)) {
                val actualPageCount = 9
                val pagerState = rememberPagerState(
                    initialPage = initialPage,
                    pageCount = { 10000 * actualPageCount }
                )

                // Sincronizza la pagina corrente con l'esterno
                LaunchedEffect(pagerState.currentPage) {
                    onPageChanged(pagerState.currentPage)
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    pageSpacing = 16.dp
                ) { page ->
                    val actualPage = page % actualPageCount
                    // Passiamo l'informazione se la pagina è quella corrente per attivare lo stream solo quando serve
                    val isVisible = pagerState.currentPage == page
                    DomainCard(actualPage, mqttManager, settingsManager, connectivityManager, isVisible, onNavigate)
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
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    item {
                        Text("STATO CASA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                    }
                    
                    // Strictly count only the lights from the known room IDs
                    val roomLightIds = listOf("sala", "libreria", "cucina", "televisione", "tavolinolettura", "lampadahifi", "lucecamera", "prolunga")
                    val activeLights = lightStates.filter { it.key in roomLightIds && it.value }
                    if (activeLights.isNotEmpty()) {
                        item { 
                            SummaryRow(
                                Icons.Default.Lightbulb, 
                                "Luci Accese (${activeLights.size})", 
                                activeLights.keys.joinToString(", ") { id ->
                                    when(id) {
                                        "sala" -> "Soggiorno"
                                        "lampadahifi" -> "HiFi"
                                        "tavolinolettura" -> "Tavolino"
                                        "lucecamera" -> "Camera"
                                        else -> id.replaceFirstChar { it.uppercase() }
                                    }
                                }
                            ) 
                        }
                    }

                    // Active Pool Devices
                    val poolDevices = mapOf(
                        "pompapiscina" to "Pompa Filtro",
                        "skimmerpiscina" to "Skimmer",
                        "lucipiscina" to "Luce Piscina",
                        "lucipedanapiscina" to "Luce Pedana"
                    )
                    poolDevices.forEach { (id, name) ->
                        if (lightStates[id] == true) {
                            item { SummaryRow(Icons.Default.Pool, "$name Attivo", "ON") }
                        }
                    }
                    
                    item {
                        SummaryRow(
                            Icons.Default.Thermostat, 
                            "Soggiorno", 
                            "%.1f°C".format(java.util.Locale.US, envState.living.temperature)
                        )
                    }
                    item {
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
    connectivityManager: DomoPiConnectivityManager,
    isVisible: Boolean,
    onNavigate: (String) -> Unit
) {
    val energyData by mqttManager.energyData.collectAsState()
    val aiData by mqttManager.aiManagedData.collectAsState()
    val lightStates by mqttManager.lightStates.collectAsState()
    val hvacState by mqttManager.hvacState.collectAsState()
    
    val tcLocalIp by settingsManager.tinycamLocalIp.collectAsState("192.168.1.20")
    val tcRemoteIp by settingsManager.tinycamRemoteIp.collectAsState("100.x.x.x")
    val tcPort by settingsManager.tinycamPort.collectAsState("8083")
    val tcUser by settingsManager.tinycamUser.collectAsState("admin")
    val tcPass by settingsManager.tinycamPass.collectAsState("password")
    
    val connectionMode by connectivityManager.connectionMode.collectAsState()
    val tcIp = if (connectionMode == ConnectionMode.LOCAL) tcLocalIp else tcRemoteIp
    
    val title = when(page) {
        0 -> "ENERGIA"
        1 -> "LUCI"
        2 -> "CLIMA"
        3 -> "PISCINA"
        4 -> "AMBIENTI"
        5 -> "TELECAMERE"
        6 -> "IMPIANTI"
        else -> ""
    }
    
    val target = when(page) {
        0 -> "energy_detail"
        1 -> "lights"
        2 -> "clima"
        3 -> "pool"
        4 -> "ambienti"
        5 -> "cameras"
        6 -> "hvac"
        7 -> "domotica_settings"
        8 -> "garage"
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
                val title = when(page) {
                    0 -> "ENERGIA"
                    1 -> "LUCI"
                    2 -> "CLIMA"
                    3 -> "PISCINA"
                    4 -> "AMBIENTI"
                    5 -> "TELECAMERE"
                    6 -> "IMPIANTI"
                    7 -> "GESTIONE CASA"
                    8 -> "GARAGE"
                    else -> ""
                }
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
                        val roomLightIds = listOf("sala", "libreria", "cucina", "televisione", "tavolinolettura", "lampadahifi", "lucecamera", "prolunga")
                        val onCount = lightStates.filter { it.key in roomLightIds && it.value }.size
                        Icon(Icons.Default.Lightbulb, null, modifier = Modifier.size(64.dp), tint = if (onCount > 0) Color(0xFFFFD600) else Color.Gray)
                        Text("$onCount Luci Accese", style = MaterialTheme.typography.bodyLarge)
                    }
                    2 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(aiData.stato_condizionatore.stato_attuale, style = MaterialTheme.typography.headlineMedium)
                        Text("Set: ${aiData.stato_condizionatore.temperatura_impostata_c}°C", style = MaterialTheme.typography.bodyMedium)
                        Text(aiData.stato_condizionatore.modalita_aria, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    3 -> PoolInteractiveComponent(
                        lightStates = lightStates,
                        onToggle = { id -> 
                            mqttManager.toggleLight(id, lightStates[id] ?: false)
                        }
                    )
                    4 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.HomeWork, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text("Monitoraggio Stanze", style = MaterialTheme.typography.bodyMedium)
                    }
                    5 -> if (isVisible) {
                        com.domopi.app.ui.components.CameraStreamComponent(
                            url = "http://${tcIp}:${tcPort}/axis-cgi/mjpg/video.cgi?cameraId=936942165",
                            user = tcUser,
                            pass = tcPass,
                            modifier = Modifier.clip(MaterialTheme.shapes.medium)
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.DarkGray).clip(MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Videocam, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        }
                    }
                    6 -> com.domopi.app.ui.components.HvacFlowComponent(
                        state = hvacState,
                        energyData = energyData
                    )
                    7 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val domoticaSettings by mqttManager.domoticaSettings.collectAsState()
                        Icon(
                            Icons.Default.HomeRepairService, 
                            null, 
                            modifier = Modifier.size(64.dp), 
                            tint = if (domoticaSettings.holidayMode) Color(0xFFE91E63) else MaterialTheme.colorScheme.primary
                        )
                        Text("Gestione Casa", style = MaterialTheme.typography.bodyLarge)
                        if (domoticaSettings.holidayMode) {
                            Text("MODALITÀ VACANZA ATTIVA", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE91E63), fontWeight = FontWeight.Bold)
                        }
                    }
                    8 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Garage, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Controllo Garage", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            Text("Tocca per dettagli", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}
