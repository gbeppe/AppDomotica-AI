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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settingsManager: SettingsManager) {
    val scope = rememberCoroutineScope()
    
    val nrLocalIp by settingsManager.nodeRedLocalIp.collectAsState(initial = "192.168.1.20")
    val nrRemoteIp by settingsManager.nodeRedRemoteIp.collectAsState(initial = "100.x.x.x")
    val nrPort by settingsManager.nodeRedPort.collectAsState(initial = "1880")
    
    val mqttLocalIp by settingsManager.mqttLocalIp.collectAsState(initial = "192.168.1.20")
    val mqttRemoteIp by settingsManager.mqttRemoteIp.collectAsState(initial = "100.x.x.x")
    val mqttPort by settingsManager.mqttPort.collectAsState(initial = "1883")
    
    val tcLocalIp by settingsManager.tinycamLocalIp.collectAsState(initial = "192.168.1.20")
    val tcRemoteIp by settingsManager.tinycamRemoteIp.collectAsState(initial = "100.x.x.x")
    val tcPort by settingsManager.tinycamPort.collectAsState(initial = "8083")

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
                        onCheckedChange = { scope.launch { settingsManager.saveDarkMode(it) } }
                    )
                }
            }

            SettingsSection("Node-RED", nrLocal, { nrLocal = it }, nrRemote, { nrRemote = it }, nrP, { nrP = it })
            SettingsSection("Broker MQTT", mqttLocal, { mqttLocal = it }, mqttRemote, { mqttRemote = it }, mqttP, { mqttP = it })
            SettingsSection("Tinycam Pro", tcLocal, { tcLocal = it }, tcRemote, { tcRemote = it }, tcP, { tcP = it })

            Button(
                onClick = {
                    scope.launch {
                        settingsManager.saveNodeRed(nrLocal, nrRemote, nrP)
                        settingsManager.saveMqtt(mqttLocal, mqttRemote, mqttP)
                        settingsManager.saveTinycam(tcLocal, tcRemote, tcP)
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
