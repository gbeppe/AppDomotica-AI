package com.domopi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.domopi.app.data.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** OpenSprinkler (IRRIGATION.1): zones, programs, stop all, controller on/off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrrigationScreen(mqttManager: MqttManager, onBack: () -> Unit) {
    val state by mqttManager.irrigationState.collectAsState()
    val result by mqttManager.irrigationResult.collectAsState()
    val connected by mqttManager.isConnected.collectAsState()
    androidx.activity.compose.BackHandler { onBack() }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Irrigazione") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro") }
        })
    }) { padding ->
        IrrigationContent(state, result, connected, Modifier.padding(padding)) { mqttManager.sendIrrigationCommand(it) }
    }
}

@Composable
fun IrrigationContent(
    state: IrrigationState?,
    result: IrrigationCommandResult?,
    connected: Boolean,
    modifier: Modifier = Modifier,
    send: (Pair<String, String>?) -> Boolean,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(5_000); now = System.currentTimeMillis() } }
    val usable = connected && state != null && state.isFresh(now)
    var notice by remember { mutableStateOf<String?>(null) }
    var startZone by remember { mutableStateOf<IrrigationZone?>(null) }
    var startProgram by remember { mutableStateOf<IrrigationProgram?>(null) }
    var confirmStopAll by remember { mutableStateOf(false) }
    fun dispatch(command: Pair<String, String>?) {
        notice = if (send(command)) "Comando inviato…" else "Casa non collegata: comando non inviato."
    }
    LaunchedEffect(result) {
        result?.let { notice = "${IrrigationCommands.label(it.command)}: " +
            if (it.ok) "eseguito." else "rifiutato (${it.reason ?: "OpenSprinkler, codice ${it.result}"})." }
    }

    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("OpenSprinkler", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Switch(checked = state?.controllerEnabled == true, enabled = usable,
                            onCheckedChange = { dispatch(IrrigationCommands.controllerEnabled(it)) },
                            modifier = Modifier.testTag("irrigation-controller"))
                    }
                    Text(when {
                        state == null -> "Stato non ancora ricevuto."
                        !connected -> "Casa non collegata: ultimi dati ricevuti."
                        !state.isFresh(now) -> "Dati non aggiornati (ultimo: ${clock(state.updatedAtMs)})."
                        state.controllerEnabled -> "Attivo: i programmi abilitati partono agli orari previsti."
                        else -> "Disattivato: nessun programma parte da solo."
                    }, style = MaterialTheme.typography.bodySmall)
                    if (state?.rainDelay == true) Text("Ritardo pioggia attivo.", style = MaterialTheme.typography.bodySmall)
                    notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("irrigation-notice")) }
                }
            }
        }
        item { Text("Zone", style = MaterialTheme.typography.titleMedium) }
        items(state?.zones?.size ?: 0) { index ->
            val zone = state!!.zones[index]
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(zone.name.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Medium)
                        Text(if (zone.running) "In irrigazione · ${minutesSeconds(zone.remainingS)} rimanenti" else "Ferma",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (zone.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (zone.running) OutlinedButton(enabled = usable, onClick = { dispatch(IrrigationCommands.zoneStop(zone.id)) }) { Text("Ferma") }
                    else Button(enabled = usable, onClick = { startZone = zone }, modifier = Modifier.testTag("irrigation-zone-${zone.id}")) { Text("Avvia") }
                }
            }
        }
        item { Text("Programmi", style = MaterialTheme.typography.titleMedium) }
        items(state?.programs?.size ?: 0) { index ->
            val program = state!!.programs[index]
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(program.name, fontWeight = FontWeight.Medium)
                        Text(if (program.enabled) "Programmato in OpenSprinkler" else "Non programmato: solo avvio manuale",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(enabled = usable, onClick = { startProgram = program }) { Text("Avvia") }
                }
            }
        }
        item {
            Button(enabled = usable, onClick = { confirmStopAll = true }, modifier = Modifier.fillMaxWidth().testTag("irrigation-stop-all"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Ferma tutto") }
        }
    }

    startZone?.let { zone ->
        var minutes by remember(zone.id) { mutableFloatStateOf(10f) }
        AlertDialog(onDismissRequest = { startZone = null },
            title = { Text("Avvia ${zone.name}") },
            text = {
                Column {
                    Text("${minutes.toInt()} minuti", style = MaterialTheme.typography.titleLarge)
                    Slider(value = minutes, onValueChange = { minutes = it }, valueRange = 1f..IrrigationCommands.MAX_MINUTES.toFloat(),
                        steps = IrrigationCommands.MAX_MINUTES - 2)
                }
            },
            confirmButton = { TextButton(onClick = { dispatch(IrrigationCommands.zoneStart(zone.id, minutes.toInt())); startZone = null }) { Text("Avvia") } },
            dismissButton = { TextButton(onClick = { startZone = null }) { Text("Annulla") } })
    }
    startProgram?.let { program ->
        AlertDialog(onDismissRequest = { startProgram = null },
            title = { Text("Avviare il programma ${program.name}?") },
            text = { Text("Le zone del programma partono una dopo l'altra, adesso.") },
            confirmButton = { TextButton(onClick = { dispatch(IrrigationCommands.programStart(program.id)); startProgram = null }) { Text("Avvia") } },
            dismissButton = { TextButton(onClick = { startProgram = null }) { Text("Annulla") } })
    }
    if (confirmStopAll) AlertDialog(onDismissRequest = { confirmStopAll = false },
        title = { Text("Fermare tutte le zone?") },
        confirmButton = { TextButton(onClick = { dispatch(IrrigationCommands.stopAll()); confirmStopAll = false }) { Text("Ferma tutto") } },
        dismissButton = { TextButton(onClick = { confirmStopAll = false }) { Text("Annulla") } })
}

private fun minutesSeconds(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)
private fun clock(ms: Long) = SimpleDateFormat("HH:mm:ss", Locale.ITALIAN).format(Date(ms))
