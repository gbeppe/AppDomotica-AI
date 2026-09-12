package com.domopi.app.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.domopi.app.data.AiSmartState

/** Uses the existing state observer. This screen has no command or MQTT client capability. */
@Composable
fun AiSmartScreen(state: AiSmartState, connected: Boolean, onClassic: () -> Unit, onHistory: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var question by rememberSaveable { mutableStateOf("") }
    var voiceNotice by remember { mutableStateOf<String?>(null) }
    var speechReady by remember { mutableStateOf(false) }
    var speech by remember { mutableStateOf<TextToSpeech?>(null) }
    val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) {
            question = text
            voiceNotice = "Trascrizione pronta: controlla la domanda e premi Chiedi."
        } else {
            voiceNotice = "Nessuna trascrizione ricevuta. Puoi scrivere la domanda."
        }
    }
    DisposableEffect(context, lifecycleOwner) {
        var disposed = false
        val main = Handler(Looper.getMainLooper())
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            main.post {
                if (!disposed) {
                    val offlineVoice = if (status == TextToSpeech.SUCCESS)
                        engine?.voices?.filter { it.locale.language == "it" && !it.isNetworkConnectionRequired }
                            ?.sortedBy { it.name }?.firstOrNull() else null
                    speechReady = offlineVoice != null && engine?.setVoice(offlineVoice) == TextToSpeech.SUCCESS
                    if (!speechReady) voiceNotice = "Lettura vocale non disponibile: serve una voce italiana offline nelle impostazioni del dispositivo."
                }
            }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            private fun notice(text: String) { main.post { if (!disposed) voiceNotice = text } }
            override fun onStart(utteranceId: String?) { notice("Lettura in corso…") }
            override fun onDone(utteranceId: String?) { notice("Lettura terminata.") }
            @Deprecated("Required by the platform listener")
            override fun onError(utteranceId: String?) { notice("Lettura vocale non riuscita. La risposta resta a schermo.") }
        })
        speech = engine
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) engine?.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            disposed = true
            lifecycleOwner.lifecycle.removeObserver(observer)
            main.removeCallbacksAndMessages(null)
            engine?.stop()
            engine?.shutdown()
        }
    }
    // Do not keep speaking a response after the connection status changes.
    LaunchedEffect(connected) { speech?.stop() }
    AiSmartContent(state, connected, question, { question = it }, onClassic, onHistory,
        voiceNotice = voiceNotice, speechReady = speechReady,
        onDictate = {
            speech?.stop()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Chiedi delle luci o delle temperature living e ACS")
            }
            try { recognizer.launch(intent) }
            catch (_: ActivityNotFoundException) { voiceNotice = "Riconoscimento vocale non disponibile. Puoi scrivere la domanda." }
            catch (_: SecurityException) { voiceNotice = "Accesso al riconoscimento vocale negato. Puoi scrivere la domanda." }
        },
        onSpeak = { text ->
            if (speech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "house-state") == TextToSpeech.ERROR)
                voiceNotice = "Lettura vocale non riuscita. La risposta resta disponibile a schermo."
        },
        onStopSpeaking = { speech?.stop(); voiceNotice = "Lettura interrotta." })
}

/** Platform-free content also used by isolated UI tests; no connection is opened here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiSmartContent(
    state: AiSmartState, connected: Boolean, question: String, onQuestionChange: (String) -> Unit,
    onClassic: () -> Unit, onHistory: () -> Unit,
    voiceNotice: String? = null, speechReady: Boolean = false,
    onDictate: () -> Unit = {}, onSpeak: (String) -> Unit = {}, onStopSpeaking: () -> Unit = {}
) {
    BackHandler(onBack = onClassic)
    var submittedQuestion by rememberSaveable { mutableStateOf<String?>(null) }
    var showSources by rememberSaveable { mutableStateOf(false) }
    val summary = state.summary(connected)
    // Re-evaluate against the latest observations so a cached answer cannot hide a disconnection.
    val answer = submittedQuestion?.let { state.answer(it, connected) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("AI smart") }, navigationIcon = {
            TextButton(onClick = onClassic) { Text("Indietro") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("La casa, dai dati disponibili", style = MaterialTheme.typography.headlineSmall)
            Text(if (connected) "Collegamento attivo · età delle misure ignota" else "Collegamento assente · dati non aggiornabili",
                color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ultimi stati ricevuti", style = MaterialTheme.typography.titleMedium)
                    Text(summary, Modifier.testTag("smart-summary"))
                    TextButton(enabled = speechReady, onClick = { onSpeak(summary) }) { Text("Leggi riepilogo") }
                }
            }
            Text("Chiedi alla casa", style = MaterialTheme.typography.titleLarge)
            Text("Puoi chiedere quante luci risultano accese e le temperature di living e acqua sanitaria ACS, anche insieme.")
            OutlinedTextField(question, onQuestionChange, label = { Text("La tua domanda") },
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = question.isNotBlank(), onClick = {
                    onStopSpeaking()
                    submittedQuestion = question.trim()
                }) { Text("Chiedi") }
                OutlinedButton(onClick = onDictate) { Text("Detta domanda") }
            }
            Text("La dettatura usa il servizio vocale del dispositivo, che può elaborare l’audio online. Controlla la trascrizione prima di inviarla.",
                style = MaterialTheme.typography.bodySmall)
            voiceNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            answer?.let { text ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Risposta", style = MaterialTheme.typography.titleMedium)
                        Text("Domanda: $submittedQuestion", style = MaterialTheme.typography.bodySmall)
                        Text(text, Modifier.testTag("smart-answer"))
                        TextButton(enabled = speechReady, onClick = { onSpeak(text) }) { Text("Leggi risposta") }
                    }
                }
            }
            TextButton(enabled = speechReady, onClick = onStopSpeaking) { Text("Ferma lettura") }
            TextButton(onClick = { showSources = !showSources }) {
                Text(if (showSources) "Nascondi provenienza" else "Mostra provenienza e limiti")
            }
            Text("Copertura parziale: otto punti luce, living e ACS. Prolunga / Allarme è escluso in attesa di classificazione; lavanderia, portico, cucina ed esterno non sono mappati.",
                style = MaterialTheme.typography.bodySmall)
            if (showSources) {
                Text("Provenienza delle letture", style = MaterialTheme.typography.titleLarge)
                state.readings().forEach { reading ->
                    Card(Modifier.fillMaxWidth().testTag("source-${reading.entity.topic}")) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(reading.entity.label, style = MaterialTheme.typography.titleMedium)
                            Text(reading.value)
                            Text(reading.provenance(), style = MaterialTheme.typography.bodySmall)
                            Text(reading.observation?.sourceTopic ?: "Topic relativo atteso: ${reading.entity.topic}",
                                style = MaterialTheme.typography.bodySmall)
                            if (reading.entity.isLight) Text("Stato dichiarato; conferma fisica non verificata.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            HorizontalDivider()
            Text("Consultazione di luci, living e ACS. Le risposte sono preparate dai dati mappati; le altre richieste non sono ancora disponibili.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Storico e motivazioni") }
            TextButton(onClick = onClassic, modifier = Modifier.fillMaxWidth()) { Text("Apri app classica") }
        }
    }
}
