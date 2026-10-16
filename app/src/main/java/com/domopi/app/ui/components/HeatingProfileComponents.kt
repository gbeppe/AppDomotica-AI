package com.domopi.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.domopi.app.data.HeatingProfileState
import com.domopi.app.data.HeatingProfileText

private val HEAT_ORANGE = Color(0xFFFFB86C)
private val HEAT_RED = Color(0xFFFF8A80)

fun profileColor(profile: String?): Color = when (profile) {
    "pieno" -> HEAT_RED; "ridotto" -> HEAT_ORANGE; else -> Color(0xFF938F99)
}

/** Compact card above the thermostats: running profile, who chose it, why, next change. */
@Composable
fun HeatingProfileCard(state: HeatingProfileState, onOpen: () -> Unit) {
    val accent = profileColor(state.profile)
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onOpen).testTag("heating-profile-card"),
        border = BorderStroke(1.dp, accent),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = accent)
                    Text("PROFILO RISCALDAMENTO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onOpen) { Icon(Icons.Default.Info, contentDescription = "Come funzionano i profili") }
            }
            if (!state.known) {
                Text("In attesa dei dati del profilo dalla casa…", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(HeatingProfileState.label(state.profile).uppercase(), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.testTag("heating-profile-name"))
                AssistChipLabel(if (state.automatic) "Automatico" else "Scelto da te")
                if (state.shadow) AssistChipLabel("In prova", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            }
            Text(HeatingProfileText.reason(state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HeatingProfileText.next(state)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (state.shadow) Text("In prova: calcola e registra, non cambia ancora i termostati. Tocca per scegliere o capire i profili.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AssistChipLabel(text: String, bg: Color = MaterialTheme.colorScheme.secondaryContainer, fg: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(8.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

/** "Chiede calore" (orange flame) / "Non chiede calore" (grey flame): same words as AI Smart. */
@Composable
fun HeatDemandChip(calling: Boolean) {
    val fg = if (calling) HEAT_ORANGE else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = if (calling) Color(0xFF3A2A12) else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = fg, shape = RoundedCornerShape(16.dp),
        modifier = Modifier.testTag(if (calling) "heat-demand-on" else "heat-demand-off")
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Text(if (calling) "Chiede calore" else "Non chiede calore", style = MaterialTheme.typography.labelLarge,
                fontWeight = if (calling) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

/** Bottom sheet: choose the profile (with Conferma) or read how the profiles work. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatingProfileSheet(state: HeatingProfileState, startOnLegend: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var legend by remember { mutableStateOf(startOnLegend) }
    var chosen by remember { mutableStateOf(state.mode ?: "auto") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("heating-profile-sheet")) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (legend) "Come funzionano i profili" else "Profilo riscaldamento", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Chiudi") }
            }
            if (legend) {
                Legend(state)
                TextButton(onClick = { legend = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("← Scegli il profilo") }
            } else {
                Text("Ora: ${HeatingProfileState.label(state.profile)}" + if (state.automatic) ", scelto dall'automatico." else ", scelto da te.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "auto" to "Decide la casa dalla temperatura esterna",
                        "spento" to "Termostati sempre al minimo",
                        "ridotto" to "Solo la sera: 17:00–21:00",
                        "pieno" to "Soggiorno 12–20, bagno 17–22 e al mattino"
                    ).forEach { (mode, descr) ->
                        val on = chosen == mode
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth().selectable(selected = on, role = Role.RadioButton, onClick = { chosen = mode })
                                .testTag("heating-profile-option-$mode"),
                            border = BorderStroke(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RadioButton(selected = on, onClick = null)
                                Column {
                                    Text(if (mode == "auto") "Automatico (consigliato)" else HeatingProfileState.label(mode),
                                        style = MaterialTheme.typography.titleMedium, color = if (mode == "auto") MaterialTheme.colorScheme.onSurface else profileColor(mode))
                                    Text(descr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                    Text(HeatingProfileText.choice(chosen, state) + " Con Conferma il profilo vale da subito.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) { Text("Annulla") }
                    Button(onClick = { onConfirm(chosen) }, enabled = chosen != state.mode,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("heating-profile-confirm")) { Text("Conferma") }
                }
                TextButton(onClick = { legend = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Come funzionano i profili →") }
            }
        }
    }
}

@Composable
private fun Legend(state: HeatingProfileState) {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    Text("I profili", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Text("Il profilo è unico per la casa; ogni stanza ha le sue fasce. In fascia il termostato va al massimo, fuori al minimo (valori nella scheda di ogni termostato).",
        style = MaterialTheme.typography.bodySmall, color = sub)
    val rows = listOf(
        Triple("spento", "sempre minimo", "sempre minimo"),
        Triple("ridotto", "17:00–21:00", "17:00–21:00"),
        Triple("pieno", "12:00–20:00", "17:00–22:00 · 05:05–07:00 lun–ven · 07:30–09:30 sab–dom")
    )
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row { Text("", Modifier.width(72.dp)); Text("Soggiorno", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = sub)
                Text("Bagno", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = sub) }
            rows.forEach { (p, living, bath) ->
                Row {
                    Text(HeatingProfileState.label(p), Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge, color = profileColor(p), fontWeight = FontWeight.Bold)
                    Text(living, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(bath, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    Text("Come decide l'automatico", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Text("Media della temperatura esterna delle ultime 72 ore: Pieno sotto 10 °C, Ridotto tra 10 e 16 °C, Spento sopra 16 °C. Margine di 1 °C per non cambiare avanti e indietro; servono 48 ore di dati. Soglie ricavate dai tuoi inverni 2017–2025: sotto 10 °C hai riscaldato quasi ogni giorno, sopra 16 °C quasi mai.",
        style = MaterialTheme.typography.bodySmall)
    state.outdoorMean72h?.let { Text("Ora: media ${HeatingProfileText.deg(it)} su ${state.hoursOfData} ore di dati.", style = MaterialTheme.typography.bodySmall, color = sub) }
    Text("Cosa ha imparato dalle tue abitudini", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    if (state.corrections.isEmpty()) {
        Text("Nessuna correzione registrata ancora: ogni volta che cambi a mano un setpoint, compare qui con la temperatura esterna del momento.",
            style = MaterialTheme.typography.bodySmall, color = sub)
    } else {
        Text("Ultime correzioni registrate:", style = MaterialTheme.typography.bodySmall, color = sub)
        state.corrections.take(5).forEach { c ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(HeatingProfileText.correction(c), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
            }
        }
    }
    Text(if (state.learningActive) "Le soglie si adattano alle tue correzioni ripetute; ogni modifica è registrata."
        else "Soglie non ancora modificate: l'apprendimento automatico sarà attivato dopo la prova.",
        style = MaterialTheme.typography.bodySmall, color = sub)
}
