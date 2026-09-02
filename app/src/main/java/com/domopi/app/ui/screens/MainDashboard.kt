package com.domopi.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domopi.app.data.AcReasonCategory
import com.domopi.app.data.AcReasonMapper
import com.domopi.app.data.ConnectionMode
import com.domopi.app.data.ZaiConnectivityManager
import com.domopi.app.data.GithubStatus
import com.domopi.app.data.GithubVersionChecker
import com.domopi.app.data.MqttManager
import com.domopi.app.data.SettingsManager
import com.domopi.app.ui.components.EnergyFlowComponent
import com.domopi.app.ui.components.PoolInteractiveComponent
import com.domopi.app.ui.theme.SolarGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(
    mqttManager: MqttManager,
    connectivityManager: ZaiConnectivityManager,
    settingsManager: SettingsManager,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onNavigate: (String) -> Unit
) {
    val isConnected by mqttManager.isConnected.collectAsState()
    val connectionMode by connectivityManager.connectionMode.collectAsState()
    val isAdminMode by settingsManager.isAdminMode.collectAsState(initial = false)
    val adminPin by settingsManager.adminPin.collectAsState(initial = "1234")
    val githubStatus by GithubVersionChecker.status.collectAsState()
    val githubIconStyle by settingsManager.githubIconStyle.collectAsState(initial = "code")

    LaunchedEffect(Unit) {
        GithubVersionChecker.checkVersion()
    }
    
    var showPinDialog by remember { mutableStateOf(false) }
    var showAlarmDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val aiData by mqttManager.aiManagedData.collectAsState()
    val aiSettings by mqttManager.aiSettings.collectAsState()
    val lightStates by mqttManager.lightStates.collectAsState()
    val envState by mqttManager.environmentState.collectAsState()
    val hvacState by mqttManager.hvacState.collectAsState()
    val energyData by mqttManager.energyData.collectAsState()

    if (showPinDialog) {
        AdminPinDialog(
            correctPin = adminPin,
            onConfirm = {
                scope.launch { settingsManager.saveAdminMode(true) }
                showPinDialog = false
            },
            onDismiss = { showPinDialog = false }
        )
    }

    if (showAlarmDialog && aiSettings.alarm != null) {
        val alarm = aiSettings.alarm!!
        AlertDialog(
            onDismissRequest = { showAlarmDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = Color.Red) },
            title = { Text("Anomalia Sistema AI", color = Color.Red, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Stato: ${alarm.stato}", fontWeight = FontWeight.Bold)
                    Text(alarm.motivo)
                    if (alarm.elementiMancanti.isNotEmpty()) {
                        Text("Elementi mancanti:", style = MaterialTheme.typography.labelMedium)
                        alarm.elementiMancanti.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    val date = java.time.Instant.ofEpochMilli(alarm.timestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                    Text("Rilevato alle: $date", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAlarmDialog = false }) {
                    Text("OK, HO CAPITO")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onNavigate("configuration") }
                    ) {
                        Text("Z-AI", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        
                        // Icona GitHub Allineamento
                        val (githubColor, githubTooltip) = when (val s = githubStatus) {
                            is GithubStatus.UpToDate -> SolarGreen to "Allineato a GitHub (${s.latestHash})"
                            is GithubStatus.Behind -> Color.Red to "Indietro di ${s.behindBy} commit da GitHub"
                            is GithubStatus.LocalDev -> Color(0xFFFF9800) to "Build Locale / Dev"
                            is GithubStatus.Checking -> Color.Gray to "Verifica GitHub in corso..."
                            is GithubStatus.Error -> Color.Gray to "GitHub: ${s.message}"
                        }
                        
                        val githubIcon = when (githubIconStyle) {
                            "code" -> Icons.Default.Code
                            "sync" -> Icons.Default.CloudSync
                            "commit" -> Icons.Default.Commit
                            else -> Icons.Default.AccountTree
                        }

                        IconButton(
                            onClick = { onNavigate("configuration") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = githubIcon,
                                contentDescription = githubTooltip,
                                tint = githubColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(Modifier.width(4.dp))
                        
                        IconButton(
                            onClick = { 
                                if (isAdminMode) {
                                    scope.launch { settingsManager.saveAdminMode(false) }
                                } else {
                                    showPinDialog = true
                                }
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = {
                                            scope.launch {
                                                settingsManager.saveAdminPin("1234")
                                                settingsManager.saveAdminMode(false)
                                            }
                                        }
                                    )
                                }
                        ) {
                            Icon(
                                imageVector = if (isAdminMode) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = "Modo Admin",
                                tint = if (isAdminMode) SolarGreen else Color.Red,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
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
                            .clickable { if (isAdminMode) onNavigate("clima") }
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
                            
                            // Indicatore Allarme
                            if (aiSettings.alarm != null) {
                                Spacer(Modifier.width(12.dp))
                                val infiniteTransition = rememberInfiniteTransition(label = "alarm_blink")
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0.2f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(500, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "alpha"
                                )
                                IconButton(
                                    onClick = { showAlarmDialog = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = "Dettagli Allarme",
                                        tint = Color.Red.copy(alpha = alpha)
                                    )
                                }
                            }
                        }
                        
                        Surface(
                            color = if (aiSettings.systemEnabled) SolarGreen.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, if (aiSettings.systemEnabled) SolarGreen else Color.Gray)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                if (!isAdminMode) {
                                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(14.dp), tint = Color.Red.copy(alpha = 0.8f))
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    text = if (aiSettings.systemEnabled) "ATTIVATO" else "DISATTIVATO",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (aiSettings.systemEnabled) SolarGreen else Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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
                    DomainCard(actualPage, mqttManager, settingsManager, connectivityManager, isVisible, isAdminMode, onNavigate)
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("STATO CASA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            if (!isAdminMode) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Default.Lock, null, modifier = Modifier.size(12.dp), tint = Color.Red.copy(alpha = 0.7f))
                                Text("SOLA LETTURA", style = MaterialTheme.typography.labelSmall, color = Color.Red.copy(alpha = 0.7f), fontSize = 8.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Stato e Logica AC
                    val acStatus = aiData.statoCondizionatore.statoAttuale.ifEmpty { "OFF" }
                    val acStatusColor = when (acStatus.uppercase()) {
                        "COOLING_ON", "RAFFRESCAMENTO" -> Color(0xFF2196F3)
                        "NIGHT_DRY", "DEUMIDIFICAZIONE" -> Color(0xFF009688)
                        "STANDBY_INVERTER" -> Color(0xFFFFB300)
                        "HEAT_DIURNO", "HEAT_SICUREZZA_NOTTE", "RISCALDAMENTO" -> Color(0xFFFF5722)
                        else -> Color.Gray
                    }
                    item {
                        SummaryRow(
                            icon = Icons.Default.AcUnit,
                            label = "Stato Clima",
                            value = acStatus,
                            valueColor = acStatusColor
                        )
                    }

                    if (aiData.statoCondizionatore.motivoLogica.isNotEmpty()) {
                        val reasonInfo = AcReasonMapper.getAcReasonInfo(aiData.statoCondizionatore.motivoLogica)
                        val categoryColor = when (reasonInfo.category) {
                            AcReasonCategory.CRITICAL -> Color.Red
                            AcReasonCategory.WARNING -> Color(0xFFFF9800)
                            AcReasonCategory.NORMAL -> SolarGreen
                            AcReasonCategory.UNKNOWN -> Color.Gray
                        }

                        item {
                            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                SummaryRow(
                                    icon = when (reasonInfo.category) {
                                        AcReasonCategory.CRITICAL -> Icons.Default.Error
                                        AcReasonCategory.WARNING -> Icons.Default.Warning
                                        AcReasonCategory.NORMAL -> Icons.Default.CheckCircle
                                        AcReasonCategory.UNKNOWN -> Icons.Default.Info
                                    },
                                    label = "Motivo Logica",
                                    value = reasonInfo.code,
                                    valueColor = categoryColor,
                                    iconTint = categoryColor
                                )
                                Text(
                                    text = reasonInfo.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 4.dp)
                                )
                            }
                        }
                    }
                    
                    // 1. Luci
                    val roomLightIds = listOf("sala", "libreria", "cucina", "televisione", "tavolinolettura", "lampadahifi", "lucecamera", "prolunga")
                    val activeLights = lightStates.filter { it.key in roomLightIds && it.value }
                    if (activeLights.isNotEmpty()) {
                        item { 
                            SummaryRow(
                                Icons.Default.Lightbulb, 
                                "Luci", 
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

                    // 2. Piscina
                    val activePool = mutableListOf<String>()
                    if (lightStates["pompapiscina"] == true) activePool.add("Pompa")
                    if (lightStates["skimmerpiscina"] == true) activePool.add("Skimmer")
                    if (lightStates["lucipiscina"] == true) activePool.add("Luci")
                    if (lightStates["lucipedanapiscina"] == true) activePool.add("Pedana")
                    if (activePool.isNotEmpty()) {
                        item {
                            SummaryRow(Icons.Default.Pool, "Piscina", activePool.joinToString(", "))
                        }
                    }

                    // 3. Impianti HVAC
                    val activeHvac = mutableListOf<String>()
                    if (hvacState.boiler.active && hvacState.boiler.modulation > 0) activeHvac.add("Caldaia (${hvacState.boiler.modulation}%)")
                    if (hvacState.palazzetti.active) activeHvac.add("Focolare (L:${hvacState.palazzetti.level})")
                    if (hvacState.ac.active) activeHvac.add("AC (${hvacState.ac.mode})")
                    if (hvacState.floorHeating.pumpActive) activeHvac.add("Pavimento")
                    if (energyData.solarPumpSpeed > 0) activeHvac.add("Solare (${energyData.solarPumpSpeed}%)")
                    if (hvacState.vmc.speed > 1) activeHvac.add("VMC (V:${hvacState.vmc.speed})")
                    if (activeHvac.isNotEmpty()) {
                        item {
                            SummaryRow(Icons.Default.SettingsSuggest, "Impianti", activeHvac.joinToString(", "))
                        }
                    }

                    // 4. Puffer
                    item {
                        val locale = LocalConfiguration.current.locales[0]
                        SummaryRow(
                            Icons.Default.Waves, 
                            "Puffer", 
                            "${"%.1f".format(locale, energyData.pufferAcs)}° - ${"%.1f".format(locale, energyData.pufferAlto)}° - ${"%.1f".format(locale, energyData.pufferBasso)}°"
                        )
                    }
                    
                    item {
                        val locale = LocalConfiguration.current.locales[0]
                        SummaryRow(
                            Icons.Default.Thermostat, 
                            "Soggiorno", 
                            "%.1f°C".format(locale, envState.living.temperature)
                        )
                    }
                    item {
                        val locale = LocalConfiguration.current.locales[0]
                        SummaryRow(
                            Icons.Default.Bed, 
                            "Camera", 
                            "%.1f°C".format(locale, envState.bedroom.temperature)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = iconTint)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
fun DomainCard(
    page: Int, 
    mqttManager: MqttManager, 
    settingsManager: SettingsManager,
    connectivityManager: ZaiConnectivityManager,
    isVisible: Boolean,
    isAdminMode: Boolean,
    onNavigate: (String) -> Unit
) {
    val energyData by mqttManager.energyData.collectAsState()
    val aiData by mqttManager.aiManagedData.collectAsState()
    val lightStates by mqttManager.lightStates.collectAsState()
    val hvacState by mqttManager.hvacState.collectAsState()
    val envState by mqttManager.environmentState.collectAsState()
    val domoticaSettings by mqttManager.domoticaSettings.collectAsState()
    
    val tcLocalIp by settingsManager.tinycamLocalIp.collectAsState("")
    val tcRemoteIp by settingsManager.tinycamRemoteIp.collectAsState("")
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
        3 -> "hvac"
        4 -> "pool"
        5 -> "ambienti"
        6 -> "cameras"
        7 -> "domotica_settings"
        8 -> "garage"
        else -> "dashboard"
    }

    Card(
        onClick = { if (isAdminMode) onNavigate(target) },
        modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        enabled = isAdminMode || target == "energy_detail" // Possiamo lasciare energia sempre apribile o bloccare tutto
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
                    3 -> "IMPIANTI"
                    4 -> "PISCINA"
                    5 -> "AMBIENTI"
                    6 -> "TELECAMERE"
                    7 -> "SETTAGGI CASA"
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
                    2 -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(aiData.statoCondizionatore.statoAttuale, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Set: ${aiData.statoCondizionatore.temperaturaImpostataC}°C", style = MaterialTheme.typography.bodyMedium)
                        Text(aiData.statoCondizionatore.modalitaAria, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        
                        Spacer(Modifier.height(12.dp))
                        
                        // Griglia Dettagli Logica (Tutti i 12 parametri)
                        val logica = aiData.logicaControllo
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                CompactDetail("SOC", "${logica.socMinimoApplied.toInt()}%")
                                CompactDetail("Humidex", "%.1f".format(logica.sogliaAttivazioneApplicata))
                                CompactDetail("Timer", "${logica.tempoMancanteAnticicloMinuti}m")
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                CompactDetail("Batteria", "%.1f".format(logica.kwhStimatiInBatteria))
                                CompactDetail("Carica", "${logica.previsioneRicaricaBatteryPercent}%")
                                CompactDetail("Solare", "%.1f".format(logica.previsioneSolareDomaniKwh))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                CompactDetail("Data Sol.", logica.previsioneSolareData.ifEmpty { "N/D" })
                                CompactDetail("Cusc. Sic.", "%.1f".format(logica.cuscinettoSicurezzaKwh))
                                CompactDetail("Cusc. Ric.", "%.1f".format(logica.cuscinettoRichiestoKwh))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                CompactDetail("VMC", "${logica.vmcPortataStimataM3h}")
                                CompactDetail("Stanza", logica.stanzaRilevamentoVmc.ifEmpty { "N/D" })
                                CompactDetail("Blocco", if (logica.bloccoEmergenzaAttivo) "ON" else "OFF")
                            }
                        }
                    }
                    3 -> com.domopi.app.ui.components.HvacFlowComponent(
                        state = hvacState,
                        energyData = energyData
                    )
                    4 -> PoolInteractiveComponent(
                        lightStates = lightStates,
                        onToggle = { id -> 
                            if (isAdminMode) mqttManager.toggleLight(id, lightStates[id] ?: false)
                        }
                    )
                    5 -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            ThermometerItem("LIVING", envState.living.temperature, color = SolarGreen)
                            ThermometerItem("CAMERA", envState.bedroom.temperature, color = Color(0xFF2196F3))
                            ThermometerItem("ESTERNO", envState.outdoor.temperature, color = Color(0xFFFF9800))
                        }
                    }
                    6 -> if (isVisible) {
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
                    7 -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    ) {
                        val activeItems = mutableListOf<Pair<ImageVector, String>>()
                        if (domoticaSettings.holidayMode) activeItems.add(Icons.Default.FlightTakeoff to "VACANZA")
                        if (domoticaSettings.ecoLights) activeItems.add(Icons.Default.Eco to "LUCI ECO")
                        if (domoticaSettings.poolLightsAuto) activeItems.add(Icons.Default.Pool to "PISCINA AUTO")
                        if (domoticaSettings.porchSensor) activeItems.add(Icons.Default.SensorWindow to "SENSORE PORTICO")
                        if (domoticaSettings.acAuto) activeItems.add(Icons.Default.AcUnit to "CLIMA AUTO")

                        if (activeItems.isEmpty()) {
                            Icon(Icons.Default.HomeRepairService, null, modifier = Modifier.size(56.dp), tint = Color.Gray)
                            Text("Tutto Standard", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                        } else {
                            activeItems.forEach { (icon, text) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Icon(icon, null, modifier = Modifier.size(24.dp), tint = SolarGreen)
                                    Spacer(Modifier.width(16.dp))
                                    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                    8 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Garage, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Controllo Garage", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            if (isAdminMode) {
                Text("Tocca per dettagli", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(10.dp), tint = Color.Red.copy(alpha = 0.6f))
                    Spacer(Modifier.width(4.dp))
                    Text("Sola Lettura", style = MaterialTheme.typography.labelSmall, color = Color.Red.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
fun AdminPinDialog(
    correctPin: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modalità Esperto", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Inserisci il PIN per abilitare le modifiche", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = { 
                        if (it.length <= 4) {
                            enteredPin = it
                            isError = false
                        }
                    },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = isError,
                    supportingText = { if (isError) Text("PIN Errato", color = MaterialTheme.colorScheme.error) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (enteredPin == correctPin) {
                        onConfirm()
                    } else {
                        isError = true
                    }
                }
            ) {
                Text("SBLOCCA")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ANNULLA")
            }
        }
    )
}

@Composable
fun ThermometerItem(label: String, temp: Float, color: Color) {
    val min = -10f
    val max = 45f
    val progress = ((temp - min) / (max - min)).coerceIn(0f, 1f)
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .height(100.dp)
                .width(24.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Sfondo traccia
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.1f))
            )
            // Livello temperatura
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(progress)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp)
        Text("${"%.1f".format(temp)}°", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    }
}

@Composable
fun CompactDetail(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(90.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 11.sp)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

