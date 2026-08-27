package com.domopi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.domopi.app.data.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationScreen(settingsManager: SettingsManager, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var showDiscardDialog by remember { mutableStateOf(false) }
    
    var domopiIp by remember { mutableStateOf("") }
    var domopiPort by remember { mutableStateOf("") }
    var domopiUser by remember { mutableStateOf("") }
    var domopiPass by remember { mutableStateOf("") }
    
    var emonpiIp by remember { mutableStateOf("") }
    var emonpiPort by remember { mutableStateOf("") }
    var emonpiUser by remember { mutableStateOf("") }
    var emonpiPass by remember { mutableStateOf("") }
    
    var tinycamIp by remember { mutableStateOf("") }
    var tinycamRemoteIp by remember { mutableStateOf("") }
    var tinycamPort by remember { mutableStateOf("") }
    var tinycamUser by remember { mutableStateOf("") }
    var tinycamPass by remember { mutableStateOf("") }

    var initialValues by remember { mutableStateOf<Map<String, String>?>(null) }
    
    val isDirty = initialValues != null && (
        domopiIp != initialValues!!["dpIp"] ||
        domopiPort != initialValues!!["dpPort"] ||
        domopiUser != initialValues!!["dpUser"] ||
        domopiPass != initialValues!!["dpPass"] ||
        emonpiIp != initialValues!!["epIp"] ||
        emonpiPort != initialValues!!["epPort"] ||
        emonpiUser != initialValues!!["epUser"] ||
        emonpiPass != initialValues!!["epPass"] ||
        tinycamIp != initialValues!!["tcIp"] ||
        tinycamRemoteIp != initialValues!!["tcRemoteIp"] ||
        tinycamPort != initialValues!!["tcPort"] ||
        tinycamUser != initialValues!!["tcUser"] ||
        tinycamPass != initialValues!!["tcPass"]
    )

    LaunchedEffect(Unit) {
        val dpIp = settingsManager.domopiIp.first()
        val dpPort = settingsManager.domopiPort.first()
        val dpUser = settingsManager.domopiUser.first()
        val dpPass = settingsManager.domopiPass.first()
        
        val epIp = settingsManager.emonpiIp.first()
        val epPort = settingsManager.emonpiPort.first()
        val epUser = settingsManager.emonpiUser.first()
        val epPass = settingsManager.emonpiPass.first()
        
        val tcIp = settingsManager.tinycamLocalIp.first()
        val tcRemoteIp = settingsManager.tinycamRemoteIp.first()
        val tcPort = settingsManager.tinycamPort.first()
        val tcUser = settingsManager.tinycamUser.first()
        val tcPass = settingsManager.tinycamPass.first()
        
        domopiIp = dpIp
        domopiPort = dpPort
        domopiUser = dpUser
        domopiPass = dpPass
        
        emonpiIp = epIp
        emonpiPort = epPort
        emonpiUser = epUser
        emonpiPass = epPass
        
        tinycamIp = tcIp
        tinycamRemoteIp = tcRemoteIp
        tinycamPort = tcPort
        tinycamUser = tcUser
        tinycamPass = tcPass
        
        initialValues = mapOf(
            "dpIp" to dpIp, "dpPort" to dpPort, "dpUser" to dpUser, "dpPass" to dpPass,
            "epIp" to epIp, "epPort" to epPort, "epUser" to epUser, "epPass" to epPass,
            "tcIp" to tcIp, "tcRemoteIp" to tcRemoteIp, "tcPort" to tcPort, "tcUser" to tcUser, "tcPass" to tcPass
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Modifiche non salvate") },
            text = { Text("Hai apportato delle modifiche. Vuoi salvarle o scartarle?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        settingsManager.saveDomoPiBroker(domopiIp, domopiPort, domopiUser, domopiPass)
                        settingsManager.saveEmonPiBroker(emonpiIp, emonpiPort, emonpiUser, emonpiPass)
                        settingsManager.saveTinycam(tinycamIp, tinycamRemoteIp, tinycamPort, tinycamUser, tinycamPass)
                        onBack()
                    }
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
                        TextButton(onClick = {
                            scope.launch {
                                settingsManager.saveDomoPiBroker(domopiIp, domopiPort, domopiUser, domopiPass)
                                settingsManager.saveEmonPiBroker(emonpiIp, emonpiPort, emonpiUser, emonpiPass)
                                settingsManager.saveTinycam(tinycamIp, tinycamRemoteIp, tinycamPort, tinycamUser, tinycamPass)
                                initialValues = mapOf(
                                    "dpIp" to domopiIp, "dpPort" to domopiPort, "dpUser" to domopiUser, "dpPass" to domopiPass,
                                    "epIp" to emonpiIp, "epPort" to emonpiPort, "epUser" to emonpiUser, "epPass" to emonpiPass,
                                    "tcIp" to tinycamIp, "tcRemoteIp" to tinycamRemoteIp, "tcPort" to tinycamPort, "tcUser" to tinycamUser, "tcPass" to tinycamPass
                                )
                            }
                        }) {
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
                Text("Broker DomoPi", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = domopiIp, onValueChange = { domopiIp = it }, label = { Text("IP / Host") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = domopiPort, onValueChange = { domopiPort = it }, label = { Text("Porta") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = domopiUser, onValueChange = { domopiUser = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = domopiPass, onValueChange = { domopiPass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
            }
            
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Broker EmonPi", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = emonpiIp, onValueChange = { emonpiIp = it }, label = { Text("IP / Host") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = emonpiPort, onValueChange = { emonpiPort = it }, label = { Text("Porta") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = emonpiUser, onValueChange = { emonpiUser = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = emonpiPass, onValueChange = { emonpiPass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
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
