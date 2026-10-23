package com.domopi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.domopi.app.data.AcOverride

private data class AcButton(val label: String, val mode: String, val setpoint: Int?, val confirm: String)

private val BUTTONS = listOf(
    AcButton("Spegni", "off", null, "spento"),
    AcButton("Caldo 20°", "heat", 20, "caldo 20 °C"),
    AcButton("Fresco 24°", "cool", 24, "raffrescamento 24 °C"),
    AcButton("Deumidifica", "dry", 24, "deumidifica 24 °C"),
)

/**
 * Step 4 of phase T4 (docs/AI_MODE/COMANDI_A_TEMPO.md): the manual priority of the air conditioner
 * (AI-CLIMATE-AC-OVERRIDE.1 on .20) with "Auto" to give it back to AI Climate. Every button asks for a
 * confirmation and lasts 2 hours (user's decisions of 23/10/2026); compressor timers and battery
 * protection stay with the .20.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AcOverrideCard(override: AcOverride?, closedReason: String?, publish: (String) -> Unit) {
    var pending by remember { mutableStateOf<AcButton?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Condizionatore a tempo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                override?.let { "Priorità manuale: ${it.text}, poi di nuovo AI Climate" }
                    ?: "Automatico (AI Climate) · un tasto = comando manuale per ${AcOverride.BUTTON_HOURS} ore",
                style = MaterialTheme.typography.bodySmall,
            )
            closedReason?.let {
                Text("Ultimo comando manuale chiuso dalla protezione della batteria ($it).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = override == null, onClick = { publish(AcOverride.AUTO) }, label = { Text("Auto") })
                BUTTONS.forEach { b ->
                    FilterChip(selected = override?.mode == b.mode && override.setpoint == b.setpoint,
                        onClick = { pending = b }, label = { Text(b.label) })
                }
            }
        }
    }
    pending?.let { b ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Condizionatore") },
            text = { Text("Condizionatore ${b.confirm} per ${AcOverride.BUTTON_HOURS} ore, poi torna ad AI Climate. " +
                "I tempi del compressore e la protezione della batteria restano attivi.") },
            confirmButton = { TextButton(onClick = { publish(AcOverride.command(b.mode, b.setpoint)); pending = null }) { Text("Conferma") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Annulla") } },
        )
    }
}
