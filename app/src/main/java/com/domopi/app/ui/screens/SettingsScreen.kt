package com.domopi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Configurazione Rete") })
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
            Text("Node-RED", style = MaterialTheme.typography.titleMedium)
            SettingsField(label = "IP Locale", initialValue = "192.168.1.20")
            SettingsField(label = "IP Remoto (Tailscale)", initialValue = "100.x.x.x")
            
            Divider()
            
            Text("Broker MQTT", style = MaterialTheme.typography.titleMedium)
            SettingsField(label = "IP Locale", initialValue = "192.168.1.20")
            SettingsField(label = "Porta", initialValue = "1883")

            Divider()

            Text("Tinycam Pro", style = MaterialTheme.typography.titleMedium)
            SettingsField(label = "IP Locale", initialValue = "192.168.1.20")
            SettingsField(label = "Porta Web Server", initialValue = "8083")

            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = { /* Save to DataStore */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salva Configurazioni")
            }
        }
    }
}

@Composable
fun SettingsField(label: String, initialValue: String) {
    var value by remember { mutableStateOf(initialValue) }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}
