package com.domopi.app.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.domopi.app.data.SettingsManager
import com.domopi.app.ui.theme.SolarGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settingsManager: SettingsManager) {
    val scope = rememberCoroutineScope()
    
    val nrLocalIp by settingsManager.nodeRedLocalIp.collectAsState(initial = "")
    val nrRemoteIp by settingsManager.nodeRedRemoteIp.collectAsState(initial = "")
    val nrPort by settingsManager.nodeRedPort.collectAsState(initial = "1880")
    
    val mqttLocalIp by settingsManager.mqttLocalIp.collectAsState(initial = "")
    val mqttRemoteIp by settingsManager.mqttRemoteIp.collectAsState(initial = "")
    val mqttPort by settingsManager.mqttPort.collectAsState(initial = "1883")
    
    val tcLocalIp by settingsManager.tinycamLocalIp.collectAsState(initial = "")
    val tcRemoteIp by settingsManager.tinycamRemoteIp.collectAsState(initial = "")
    val tcPort by settingsManager.tinycamPort.collectAsState(initial = "8083")
    val tcUserVal by settingsManager.tinycamUser.collectAsState(initial = "admin")
    val tcPassVal by settingsManager.tinycamPass.collectAsState(initial = "password")

    // State for fields
    var nrLocal by remember(nrLocalIp) { mutableStateOf(nrLocalIp) }
    var nrRemote by remember(nrRemoteIp) { mutableStateOf(nrRemoteIp) }
    var nrP by remember(nrPort) { mutableStateOf(nrPort) }
    
    var mqttLocal by remember(mqttLocalIp) { mutableStateOf(mqttLocalIp) }
    var mqttRemote by remember(mqttRemoteIp) { mutableStateOf(mqttRemoteIp) }
    var mqttP by remember(mqttPort) { mutableStateOf(mqttPort) }
    
    var tcLocal by remember(tcLocalIp) { mutableStateOf(tcLocalIp) }
    var tcRemote by remember(tcRemoteIp) { mutableStateOf(tcRemoteIp) }
    var tcP by remember(tcPort) { mutableStateOf(tcPort) }
    var tcUser by remember(tcUserVal) { mutableStateOf(tcUserVal) }
    var tcPass by remember(tcPassVal) { mutableStateOf(tcPassVal) }

    val isDarkMode by settingsManager.darkMode.collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Configurazione") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Tema Scuro", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = isDarkMode ?: isSystemInDarkTheme(),
                        onCheckedChange = { scope.launch { settingsManager.saveDarkMode(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SolarGreen,
                            checkedTrackColor = SolarGreen.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            SettingsSection("Node-RED", nrLocal, { nrLocal = it }, nrRemote, { nrRemote = it }, nrP, { nrP = it })
            SettingsSection("Broker MQTT", mqttLocal, { mqttLocal = it }, mqttRemote, { mqttRemote = it }, mqttP, { mqttP = it })
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tinycam Pro", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = tcLocal, onValueChange = { tcLocal = it }, label = { Text("IP Locale") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = tcRemote, onValueChange = { tcRemote = it }, label = { Text("IP Remoto") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = tcP, onValueChange = { tcP = it }, label = { Text("Porta") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = tcUser, onValueChange = { tcUser = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = tcPass, onValueChange = { tcPass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        settingsManager.saveNodeRed(nrLocal, nrRemote, nrP)
                        settingsManager.saveMqtt(mqttLocal, mqttRemote, mqttP)
                        settingsManager.saveTinycam(tcLocal, tcRemote, tcP, tcUser, tcPass)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salva Configurazioni")
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    localIp: String, onLocalIpChange: (String) -> Unit,
    remoteIp: String, onRemoteIpChange: (String) -> Unit,
    port: String, onPortChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = localIp, onValueChange = onLocalIpChange, label = { Text("IP Locale") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = remoteIp, onValueChange = onRemoteIpChange, label = { Text("IP Remoto (Tailscale)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = port, onValueChange = onPortChange, label = { Text("Porta") }, modifier = Modifier.fillMaxWidth())
        }
    }
}
