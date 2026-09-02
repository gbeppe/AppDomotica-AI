package com.domopi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.domopi.app.data.SettingsManager
import com.domopi.app.ui.theme.SolarGreen
import com.domopi.app.BuildConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationScreen(settingsManager: SettingsManager, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var showDiscardDialog by remember { mutableStateOf(false) }
    
    var domopiIp by remember { mutableStateOf("") }
    var domopiRemoteIp by remember { mutableStateOf("") }
    var domopiPort by remember { mutableStateOf("") }
    var domopiUser by remember { mutableStateOf("") }
    var domopiPass by remember { mutableStateOf("") }
    
    var emoncmsIp by remember { mutableStateOf("") }
    var emoncmsRemoteIp by remember { mutableStateOf("") }
    
    var tinycamIp by remember { mutableStateOf("") }
    var tinycamRemoteIp by remember { mutableStateOf("") }
    var tinycamPort by remember { mutableStateOf("") }
    var tinycamUser by remember { mutableStateOf("") }
    var tinycamPass by remember { mutableStateOf("") }
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
        tinycamIp != initialValues!!["tcIp"] ||
        tinycamRemoteIp != initialValues!!["tcRemoteIp"] ||
        tinycamPort != initialValues!!["tcPort"] ||
        tinycamUser != initialValues!!["tcUser"] ||
        tinycamPass != initialValues!!["tcPass"] ||
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
        
        val tcIp = settingsManager.tinycamLocalIp.first()
        val tcRemoteIp = settingsManager.tinycamRemoteIp.first()
        val tcPort = settingsManager.tinycamPort.first()
        val tcUser = settingsManager.tinycamUser.first()
        val tcPass = settingsManager.tinycamPass.first()
        val dm = settingsManager.darkMode.first() ?: true
        val pin = settingsManager.adminPin.first()
        
        domopiIp = dpIp
        domopiRemoteIp = dpRemoteIp
        domopiPort = dpPort
        domopiUser = dpUser
        domopiPass = dpPass
        
        emoncmsIp = ecIp
        emoncmsRemoteIp = ecRemoteIp
        
        tinycamIp = tcIp
        tinycamRemoteIp = tcRemoteIp
        tinycamPort = tcPort
        tinycamUser = tcUser
        tinycamPass = tcPass
        darkMode = dm
        adminPin = pin
        
        initialValues = mapOf(
            "dpIp" to dpIp, "dpRemoteIp" to dpRemoteIp, "dpPort" to dpPort, "dpUser" to dpUser, "dpPass" to dpPass,
            "ecIp" to ecIp, "ecRemoteIp" to ecRemoteIp,
            "tcIp" to tcIp, "tcRemoteIp" to tcRemoteIp, "tcPort" to tcPort, "tcUser" to tcUser, "tcPass" to tcPass,
            "darkMode" to dm, "adminPin" to pin
        )
    }

    fun saveAll() {
        scope.launch {
            settingsManager.saveDomoPiBroker(domopiIp, domopiRemoteIp, domopiPort, domopiUser, domopiPass)
            settingsManager.saveEmonCms(emoncmsIp, emoncmsRemoteIp)
            settingsManager.saveTinycam(tinycamIp, tinycamRemoteIp, tinycamPort, tinycamUser, tinycamPass)
            settingsManager.saveDarkMode(darkMode)
            settingsManager.saveAdminPin(adminPin)
            initialValues = mapOf(
                "dpIp" to domopiIp, "dpRemoteIp" to domopiRemoteIp, "dpPort" to domopiPort, "dpUser" to domopiUser, "dpPass" to domopiPass,
                "ecIp" to emoncmsIp, "ecRemoteIp" to emoncmsRemoteIp,
                "tcIp" to tinycamIp, "tcRemoteIp" to tinycamRemoteIp, "tcPort" to tinycamPort, "tcUser" to tinycamUser, "tcPass" to tinycamPass,
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
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text(
                        "Informazioni Build",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    BuildInfoRow("Versione", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    BuildInfoRow("Git Hash", BuildConfig.GIT_HASH)
                    BuildInfoRow("Git Branch", BuildConfig.GIT_BRANCH)
                    BuildInfoRow("Data Build", BuildConfig.BUILD_TIME)
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
                OutlinedTextField(value = domopiPass, onValueChange = { domopiPass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
            }
            
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("EmonCMS", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = emoncmsIp, onValueChange = { emoncmsIp = it }, label = { Text("IP Locale") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = emoncmsRemoteIp, onValueChange = { emoncmsRemoteIp = it }, label = { Text("IP Remoto / Tailscale") }, modifier = Modifier.fillMaxWidth())
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("TinyCam Pro", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = tinycamIp, onValueChange = { tinycamIp = it }, label = { Text("IP Locale") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tinycamRemoteIp, onValueChange = { tinycamRemoteIp = it }, label = { Text("IP Remoto / Cloud") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tinycamPort, onValueChange = { tinycamPort = it }, label = { Text("Porta") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tinycamUser, onValueChange = { tinycamUser = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tinycamPass, onValueChange = { tinycamPass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun BuildInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}
