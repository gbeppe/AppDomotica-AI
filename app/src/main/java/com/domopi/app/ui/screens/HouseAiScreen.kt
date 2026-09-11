package com.domopi.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.domopi.app.data.HouseAiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseAiScreen(onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    var url by rememberSaveable { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf(LocalDate.now(ZoneId.of("Europe/Rome")).minusDays(1).toString()) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repository = remember { HouseAiRepository() }
    Scaffold(topBar = { TopAppBar(title = { Text("Chiedi alla casa") },
        navigationIcon = { TextButton(onClick = onBack) { Text("Indietro") } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Storico e motivazioni", style = MaterialTheme.typography.headlineSmall)
            Text("Consulta i dati della casa e le decisioni registrate. Le domande libere saranno disponibili dopo la configurazione del modello AI.")
            OutlinedTextField(url, { url = it; result = null }, label = { Text("Indirizzo del servizio") },
                placeholder = { Text("https://…") }, singleLine = true, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(token, { token = it }, label = { Text("Token di accesso") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(day, { day = it; result = null }, label = { Text("Giorno (AAAA-MM-GG)") },
                singleLine = true, enabled = !loading, modifier = Modifier.fillMaxWidth())
            Button(enabled = !loading && url.isNotBlank() && token.isNotBlank(), onClick = {
                scope.launch {
                    loading = true; error = null; result = null
                    try { result = repository.report(url, token, day) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = "Impossibile leggere il rapporto. Verifica data, indirizzo, accesso e connessione." }
                    finally { loading = false }
                }
            }) { Text("Analizza la giornata") }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            result?.let { report ->
                Text(report.getString("day") + " · Europe/Rome", style = MaterialTheme.typography.titleMedium)
                Text(report.getString("summary"))
                val series = report.getJSONObject("series")
                for (key in listOf("ac", "soc", "battery")) {
                    val data = series.getJSONObject(key)
                    Text("${data.getString("label")} (${data.getString("unit")})", style = MaterialTheme.typography.titleMedium)
                    HistoryEvidenceChart(data.getJSONArray("points"), report.getJSONArray("period_ms"))
                }
                val evidence = report.getJSONObject("evidence")
                val events = evidence.getJSONArray("events")
                Text("Eventi registrati: ${events.length()}", style = MaterialTheme.typography.titleLarge)
                Text("Record esclusi: ${evidence.getJSONArray("excluded").length()}. Il comando registrato non equivale alla conferma fisica.")
                if (events.length() == 0) Text("Nessun evento disponibile per questa data: non implica assenza di attività.")
                for (i in 0 until events.length()) {
                    val event = events.getJSONObject(i)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val hour = Instant.ofEpochMilli(event.getLong("timestamp_ms")).atZone(ZoneId.of("Europe/Rome"))
                                .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                            Text("$hour · ${event.optString("state")}", style = MaterialTheme.typography.titleMedium)
                            Text(if (event.isNull("reason_ac")) "Motivo non disponibile" else event.getString("reason_ac"))
                            if (event.optString("causal_detail") == "unspecified") Text("La causa specifica non è documentata.")
                            val observation = event.getJSONObject("ac_observation_2m")
                            fun power(side: String): String {
                                val window = observation.getJSONObject(side)
                                return if (window.isNull("mean_w")) "n/d" else "%.0f W (%d campioni)".format(window.getDouble("mean_w"), window.getInt("samples"))
                            }
                            Text("Media AC nei 2 minuti prima: ${power("before")}; dopo: ${power("after")}")
                            val source = event.getJSONObject("source")
                            Text("${source.getString("file")}, riga ${source.getInt("line")}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                val limits = report.getJSONArray("limitations")
                for (i in 0 until limits.length()) Text(limits.getString(i), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HistoryEvidenceChart(points: JSONArray, period: JSONArray) {
    val color = MaterialTheme.colorScheme.primary
    val values = (0 until points.length()).mapNotNull { i -> points.getJSONArray(i).let { if (it.isNull(1)) null else it.getDouble(1) } }
    if (values.isEmpty()) { Text("Serie non disponibile"); return }
    val minimum = minOf(0.0, values.minOrNull()!!)
    val maximum = maxOf(minimum + 1, values.maxOrNull()!!)
    Text("Min %.1f · Max %.1f · 00:00 → 24:00".format(values.minOrNull(), values.maxOrNull()), style = MaterialTheme.typography.bodySmall)
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val start = period.getDouble(0); val duration = period.getDouble(1) - start
        var previous: Pair<Double, Offset>? = null
        for (i in 0 until points.length()) {
            val point = points.getJSONArray(i)
            if (point.isNull(1)) { previous = null; continue }
            val t = point.getDouble(0)
            val position = Offset(((t-start)/duration*size.width).toFloat(),
                (size.height*(1-(point.getDouble(1)-minimum)/(maximum-minimum))).toFloat())
            previous?.let { (lastTime, lastPoint) -> if (t-lastTime <= 30000) drawLine(color,lastPoint,position,2f) }
            previous = t to position
        }
    }
}
