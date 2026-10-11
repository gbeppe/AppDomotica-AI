package com.domopi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domopi.app.data.AppAlignmentStatus
import com.domopi.app.data.BuildAlignment
import com.domopi.app.network.HomeNetworkSettings
import com.domopi.app.ui.components.indicatorToneColor
import com.domopi.app.data.SettingsManager
import com.domopi.app.ui.theme.SolarGreen
import com.domopi.app.BuildConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    buildAlignment: AppAlignmentStatus = AppAlignmentStatus.CHECKING,
    onOpenBuildAlignment: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Rete di casa: salvata a parte, solo se dominio e subnet sono entrambi validi.
    var homeDomain by remember { mutableStateOf(HomeNetworkSettings.DEFAULT_DOMAIN) }
    var homeSubnet by remember { mutableStateOf(HomeNetworkSettings.DEFAULT_SUBNET) }
    var homeNetworkMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val home = settingsManager.homeNetwork.first()
        homeDomain = home.domain
        homeSubnet = home.subnet
    }
    val homeDomainError = if (HomeNetworkSettings.isValidDomain(homeDomain)) null else "Dominio non valido"
    val homeSubnetError = if (HomeNetworkSettings.isValidSubnet(homeSubnet)) null else "Subnet CIDR non valida (es. 192.168.1.0/24)"
    
    var domopiIp by remember { mutableStateOf("") }
    var domopiRemoteIp by remember { mutableStateOf("") }
    var domopiPort by remember { mutableStateOf("") }
    var domopiUser by remember { mutableStateOf("") }
    var domopiPass by remember { mutableStateOf("") }
    var digitalTwinPrefix by remember { mutableStateOf("zara/interface") }
    
    var emoncmsIp by remember { mutableStateOf("") }
    var emoncmsRemoteIp by remember { mutableStateOf("") }
    var emoncmsApiKey by remember { mutableStateOf("") }
    
    var tinycamIp by remember { mutableStateOf("") }
    var tinycamRemoteIp by remember { mutableStateOf("") }
    var tinycamPort by remember { mutableStateOf("") }
    var tinycamUser by remember { mutableStateOf("") }
    var tinycamPass by remember { mutableStateOf("") }
    var tinycamCameras by remember { mutableStateOf("") }
    var darkMode by remember { mutableStateOf(true) }
    var adminPin by remember { mutableStateOf("") }

    var initialValues by remember { mutableStateOf<Map<String, Any>?>(null) }
    
    val isDirty = initialValues != null && (
        domopiIp != initialValues!!["dpIp"] ||
        domopiRemoteIp != initialValues!!["dpRemoteIp"] ||
        domopiPort != initialValues!!["dpPort"] ||
        domopiUser != initialValues!!["dpUser"] ||
        domopiPass != initialValues!!["dpPass"] ||
        emoncmsIp != initialValues!!["ecIp"] ||
        emoncmsRemoteIp != initialValues!!["ecRemoteIp"] ||
        emoncmsApiKey != initialValues!!["ecKey"] ||
        tinycamIp != initialValues!!["tcIp"] ||
        tinycamRemoteIp != initialValues!!["tcRemoteIp"] ||
        tinycamPort != initialValues!!["tcPort"] ||
        tinycamUser != initialValues!!["tcUser"] ||
        tinycamPass != initialValues!!["tcPass"] ||
        tinycamCameras != initialValues!!["tcCams"] ||
        darkMode != initialValues!!["darkMode"] ||
        adminPin != initialValues!!["adminPin"]
    )

    LaunchedEffect(Unit) {
        val dpIp = settingsManager.domopiIp.first()
        val dpRemoteIp = settingsManager.domopiRemoteIp.first()
        val dpPort = settingsManager.domopiPort.first()
        val dpUser = settingsManager.domopiUser.first()
        val dpPass = settingsManager.domopiPass.first()
        
        val ecIp = settingsManager.emoncmsIp.first()
        val ecRemoteIp = settingsManager.emoncmsRemoteIp.first()
        val ecKey = settingsManager.emoncmsApiKey.first()
        
        val tcIp = settingsManager.tinycamLocalIp.first()
        val tcRemoteIp = settingsManager.tinycamRemoteIp.first()
        val tcPort = settingsManager.tinycamPort.first()
        val tcUser = settingsManager.tinycamUser.first()
        val tcPass = settingsManager.tinycamPass.first()
        val tcCams = settingsManager.tinycamCameras.first()
        val dm = settingsManager.darkMode.first() ?: true
        val pin = settingsManager.adminPin.first()
        
        domopiIp = dpIp
        domopiRemoteIp = dpRemoteIp
        domopiPort = dpPort
        domopiUser = dpUser
        domopiPass = dpPass
        
        emoncmsIp = ecIp
        emoncmsRemoteIp = ecRemoteIp
        emoncmsApiKey = ecKey
        
        tinycamIp = tcIp
        tinycamRemoteIp = tcRemoteIp
        tinycamPort = tcPort
        tinycamUser = tcUser
        tinycamPass = tcPass
        tinycamCameras = tcCams
        darkMode = dm
        adminPin = pin
        
        initialValues = mapOf(
            "dpIp" to dpIp, "dpRemoteIp" to dpRemoteIp, "dpPort" to dpPort, "dpUser" to dpUser, "dpPass" to dpPass,
            "ecIp" to ecIp, "ecRemoteIp" to ecRemoteIp, "ecKey" to ecKey,
            "tcIp" to tcIp, "tcRemoteIp" to tcRemoteIp, "tcPort" to tcPort, "tcUser" to tcUser, "tcPass" to tcPass, "tcCams" to tcCams,
            "darkMode" to dm, "adminPin" to pin
        )
    }

    fun saveAll() {
        scope.launch {
            settingsManager.saveDomoPiBroker(domopiIp, domopiRemoteIp, domopiPort, domopiUser, domopiPass)
            settingsManager.saveEmonCms(emoncmsIp, emoncmsRemoteIp, emoncmsApiKey)
            settingsManager.saveTinycam(tinycamIp, tinycamRemoteIp, tinycamPort, tinycamUser, tinycamPass, tinycamCameras)
            settingsManager.saveDarkMode(darkMode)
            settingsManager.saveAdminPin(adminPin)
            initialValues = mapOf(
                "dpIp" to domopiIp, "dpRemoteIp" to domopiRemoteIp, "dpPort" to domopiPort, "dpUser" to domopiUser, "dpPass" to domopiPass,
                "ecIp" to emoncmsIp, "ecRemoteIp" to emoncmsRemoteIp, "ecKey" to emoncmsApiKey,
                "tcIp" to tinycamIp, "tcRemoteIp" to tinycamRemoteIp, "tcPort" to tinycamPort, "tcUser" to tinycamUser, "tcPass" to tinycamPass, "tcCams" to tinycamCameras,
                "darkMode" to darkMode, "adminPin" to adminPin
            )
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Modifiche non salvate") },
            text = { Text("Hai apportato delle modifiche. Vuoi salvarle o scartarle?") },
            confirmButton = {
                TextButton(onClick = {
                    saveAll()
                    onBack()
                }) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { onBack() }) { Text("Scarta") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurazione") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isDirty) showDiscardDialog = true else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isDirty) {
                        TextButton(onClick = { saveAll() }) {
                            Text("SALVA", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Informazioni Build",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    BuildIdentityRows(
                        version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        gitSha = BuildConfig.GIT_SHA,
                        branch = BuildConfig.GIT_BRANCH,
                        dirty = BuildConfig.BUILD_DIRTY,
                        buildId = BuildConfig.BUILD_ID,
                        buildTimestamp = BuildConfig.BUILD_TIMESTAMP,
                    )
                    BuildInfoRow(
                        "Allineamento",
                        BuildAlignment.describe(buildAlignment),
                        indicatorToneColor(BuildAlignment.toneOf(buildAlignment))
                    )
                    TextButton(onClick = onOpenBuildAlignment) {
                        Text("Dettaglio build", style = MaterialTheme.typography.labelSmall)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Modalità Scura", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = darkMode,
                        onCheckedChange = { darkMode = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SolarGreen,
                            checkedTrackColor = SolarGreen.copy(alpha = 0.3f)
                        )
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                Text("Sicurezza", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = adminPin,
                    onValueChange = { if (it.length <= 4) adminPin = it },
                    label = { Text("PIN Modalità Esperto (4 cifre)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            
            item {
                Text("Broker Z-AI", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = domopiIp, onValueChange = { domopiIp = it }, label = { Text("IP Locale") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = domopiRemoteIp, onValueChange = { domopiRemoteIp = it }, label = { Text("IP Remoto / Tailscale") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = domopiPort, onValueChange = { domopiPort = it }, label = { Text("Porta") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = domopiUser, onValueChange = { domopiUser = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = domopiPass, onValueChange = { domopiPass = it }, label = { Text("Password (salvata cifrata)") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = digitalTwinPrefix, onValueChange = { digitalTwinPrefix = it }, label = { Text("Prefisso Topic Digital Twin") }, modifier = Modifier.fillMaxWidth())
                Text(
                    text = "⚠️ Modificare questo prefisso richiede il riavvio dell'applicazione per ri-sottoscrivere i topic con la nuova alberatura.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }
            
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Rete di casa", style = MaterialTheme.typography.titleMedium)
                Text(
                    "LOCALE: Wi-Fi senza VPN con questo dominio esatto. REMOTO: VPN con una route esatta verso questa subnet.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = homeDomain,
                    onValueChange = { homeDomain = it; homeNetworkMessage = null },
                    label = { Text("Dominio Wi-Fi di casa") },
                    isError = homeDomainError != null,
                    supportingText = homeDomainError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = homeSubnet,
                    onValueChange = { homeSubnet = it; homeNetworkMessage = null },
                    label = { Text("Subnet di casa (CIDR)") },
                    isError = homeSubnetError != null,
                    supportingText = homeSubnetError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        scope.launch {
                            homeNetworkMessage = if (settingsManager.saveHomeNetwork(homeDomain, homeSubnet))
                                "Rete di casa salvata" else "Valori non validi: non salvati"
                        }
                    }) { Text("Salva rete di casa") }
                    TextButton(onClick = {
                        homeDomain = HomeNetworkSettings.DEFAULT_DOMAIN
                        homeSubnet = HomeNetworkSettings.DEFAULT_SUBNET
                        scope.launch {
                            settingsManager.saveHomeNetwork(homeDomain, homeSubnet)
                            homeNetworkMessage = "Predefiniti ripristinati"
                        }
                    }) { Text("Ripristina predefiniti") }
                }
                homeNetworkMessage?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("EmonCMS", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = emoncmsIp, onValueChange = { emoncmsIp = it }, label = { Text("IP Locale") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = emoncmsRemoteIp, onValueChange = { emoncmsRemoteIp = it }, label = { Text("IP Remoto / Tailscale") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = emoncmsApiKey, onValueChange = { emoncmsApiKey = it.trim() },
                    label = { Text("Chiave API di lettura (salvata cifrata)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("TinyCam Pro", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = tinycamIp, onValueChange = { tinycamIp = it }, label = { Text("IP Locale") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tinycamRemoteIp, onValueChange = { tinycamRemoteIp = it }, label = { Text("IP Remoto / Cloud") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tinycamPort, onValueChange = { tinycamPort = it }, label = { Text("Porta") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tinycamUser, onValueChange = { tinycamUser = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tinycamPass, onValueChange = { tinycamPass = it }, label = { Text("Password (salvata cifrata)") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tinycamCameras, onValueChange = { tinycamCameras = it },
                    label = { Text("Telecamere (Nome=ID, separate da virgola; la prima va nella Home)") }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun BuildInfoRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = valueColor)
    }
}
