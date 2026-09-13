package com.domopi.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.domopi.app.data.*

/** Future Smart controls rendered against live state. No command callback exists in this screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartControlsScreen(state: SmartControlState, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(topBar = { TopAppBar(title = { Text("Controlli Smart") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp).testTag("smart-controls-list"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Sola lettura", style = MaterialTheme.typography.titleMedium)
                            Text("Stati reali visibili. Invio comandi non ancora abilitato.")
                        }
                    }
                }
            }
            SmartControlDomain.entries.forEach { domain ->
                item { Text(if (domain == SmartControlDomain.CLIMATE) "Clima" else "Impianti",
                    style = MaterialTheme.typography.headlineSmall) }
                SmartControlCatalog.controls.filter { it.domain == domain }.groupBy { it.group }.forEach { (group, specs) ->
                    item { Text(group, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary) }
                    specs.forEach { spec -> item { ReadOnlyControl(spec, state.observation(spec.id)) } }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyControl(spec: SmartControlSpec, observation: SmartControlObservation?) {
    val raw = observation?.value
    Card(Modifier.fillMaxWidth().testTag("smart-control-${spec.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(spec.label, style = MaterialTheme.typography.bodyLarge)
                    Text(raw ?: "Dato non disponibile", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (spec.kind == SmartControlKind.SWITCH) {
                    val checked = raw?.lowercase() in setOf("true", "on", "1")
                    Switch(checked = checked, onCheckedChange = null, enabled = false,
                        modifier = Modifier.testTag("smart-control-input-${spec.id}"))
                }
            }
            val number = raw?.replace(',', '.')?.toFloatOrNull()
            when (spec.kind) {
                SmartControlKind.SLIDER -> if (number != null && spec.minimum != null && spec.maximum != null) {
                    Slider(value = number.coerceIn(spec.minimum, spec.maximum), onValueChange = {},
                        enabled = false, valueRange = spec.minimum..spec.maximum)
                }
                SmartControlKind.CHOICE -> Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    spec.choices.forEach { choice -> FilterChip(selected = raw == choice, onClick = {},
                        enabled = false, label = { Text(choice) }) }
                }
                SmartControlKind.STEPPER -> Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {}, enabled = false) { Text("−") }
                    Text(raw ?: "—", Modifier.padding(horizontal = 16.dp))
                    OutlinedButton(onClick = {}, enabled = false) { Text("+") }
                }
                SmartControlKind.TIME -> OutlinedTextField(value = raw ?: "", onValueChange = {},
                    enabled = false, label = { Text("HH:mm") }, modifier = Modifier.fillMaxWidth())
                SmartControlKind.SWITCH -> Unit
            }
            if (observation != null) {
                Text("${observation.sourceTopic} · retained ${if (observation.retained) "sì" else "no"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
