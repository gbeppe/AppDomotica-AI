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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.domopi.app.data.AiSmartState
import com.domopi.app.data.EnergySmartState
import com.domopi.app.data.EnergyAssistantAnswer
import com.domopi.app.data.HouseAiRepository
import com.domopi.app.data.speechChunks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Uses the existing state observer. This screen has no command or MQTT client capability. */
@Composable
fun AiSmartScreen(
    state: AiSmartState,
    energyState: EnergySmartState,
    connected: Boolean,
    onClassic: () -> Unit,
    onHistory: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var question by rememberSaveable { mutableStateOf("") }
    var voiceNotice by remember { mutableStateOf<String?>(null) }
    var speechReady by remember { mutableStateOf(false) }
    var speech by remember { mutableStateOf<TextToSpeech?>(null) }
    var serviceUrl by rememberSaveable { mutableStateOf("") }
    var serviceToken by remember { mutableStateOf("") }
    var response by remember { mutableStateOf<EnergyAssistantAnswer?>(null) }
    var dynamicError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repository = remember { HouseAiRepository() }
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
            override fun onDone(utteranceId: String?) { if (utteranceId?.endsWith("-last") == true) notice("Lettura terminata.") }
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
        dynamicMode = true, energyState = energyState, serviceUrl = serviceUrl, serviceToken = serviceToken,
        onServiceUrlChange = { serviceUrl = it; dynamicError = null; response = null },
        onServiceTokenChange = { serviceToken = it; dynamicError = null; response = null },
        dynamicAnswer = response?.text, dynamicResponse = response, dynamicError = dynamicError, loading = loading,
        onAskDynamic = { text ->
            scope.launch {
                loading = true
                dynamicError = null
                response = null
                try {
                    val result = repository.assistant(serviceUrl, serviceToken, text, energyState, connected)
                    response = result
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IllegalStateException) {
                    dynamicError = e.message ?: "Risposta del backend non valida."
                } catch (_: Exception) {
                    dynamicError = "Assistente energia non raggiungibile o risposta non valida. Verifica indirizzo e connessione, poi riprova."
                } finally {
                    loading = false
                }
            }
        },
        onDictate = {
            speech?.stop()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Chiedi dello stato o dell’energia della casa")
            }
            try { recognizer.launch(intent) }
            catch (_: ActivityNotFoundException) { voiceNotice = "Riconoscimento vocale non disponibile. Puoi scrivere la domanda." }
            catch (_: SecurityException) { voiceNotice = "Accesso al riconoscimento vocale negato. Puoi scrivere la domanda." }
        },
        onSpeak = { text ->
            val chunks = speechChunks(text, TextToSpeech.getMaxSpeechInputLength())
            for ((index, chunk) in chunks.withIndex()) {
                val id = "energy-$index" + if (index == chunks.lastIndex) "-last" else ""
                if (speech?.speak(chunk, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id) == TextToSpeech.ERROR) {
                    speech?.stop()
                    voiceNotice = "Lettura vocale non riuscita. La risposta resta disponibile a schermo."
                    break
                }
            }
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
    onDictate: () -> Unit = {}, onSpeak: (String) -> Unit = {}, onStopSpeaking: () -> Unit = {},
    dynamicMode: Boolean = false,
    energyState: EnergySmartState = EnergySmartState(),
    dynamicResponse: EnergyAssistantAnswer? = null,
    serviceUrl: String = "", serviceToken: String = "",
    onServiceUrlChange: (String) -> Unit = {}, onServiceTokenChange: (String) -> Unit = {},
    dynamicAnswer: String? = null, dynamicError: String? = null, loading: Boolean = false,
    onAskDynamic: (String) -> Unit = {},
) {
    BackHandler(onBack = onClassic)
    var submittedQuestion by rememberSaveable { mutableStateOf<String?>(null) }
    var showSources by rememberSaveable { mutableStateOf(false) }
    val summary = if (dynamicMode) energyState.summary(connected) else state.summary(connected)
    // Re-evaluate against the latest observations so a cached answer cannot hide a disconnection.
    val answer = if (dynamicMode) dynamicAnswer else submittedQuestion?.let { state.answer(it, connected) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("AI smart") }, navigationIcon = {
            TextButton(onClick = onClassic) { Text("Indietro") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (dynamicMode) "Energia della casa" else "La casa, dai dati disponibili", style = MaterialTheme.typography.headlineSmall)
            Text(if (connected) "Collegamento attivo · età delle misure ignota" else "Collegamento assente · dati non aggiornabili",
                color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (dynamicMode) "Ultime letture energetiche" else "Ultimi stati ricevuti", style = MaterialTheme.typography.titleMedium)
                    Text(summary, Modifier.testTag("smart-summary"))
                    TextButton(enabled = speechReady, onClick = { onSpeak(summary) }) { Text("Leggi riepilogo") }
                }
            }
            Text("Chiedi alla casa", style = MaterialTheme.typography.titleLarge)
            Text(if (dynamicMode) "Puoi fare domande libere sul fotovoltaico, i consumi, la rete e la Powerwall. Lo stato corrente arriva dal Digital Twin; lo storico da EmonCMS; previsioni e decisioni registrate dai log Node-RED."
                 else "Puoi chiedere quante luci risultano accese e le temperature di living e acqua sanitaria ACS, anche insieme.")
            if (dynamicMode) {
                OutlinedTextField(serviceUrl, onServiceUrlChange,
                    label = { Text("Indirizzo backend AI") }, placeholder = { Text("https://…") },
                    singleLine = true, enabled = !loading, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(serviceToken, onServiceTokenChange,
                    label = { Text("Token backend") }, singleLine = true, enabled = !loading,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())
                Text("Il token resta in memoria per questa schermata e non viene salvato nell’app.",
                    style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(question, onQuestionChange, label = { Text("La tua domanda") },
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5, enabled = !loading)
            if (question.length > 1000) Text("La domanda può contenere al massimo 1000 caratteri.", color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = question.isNotBlank() && question.length <= 1000 && !loading &&
                    (!dynamicMode || serviceUrl.isNotBlank() && serviceToken.isNotBlank()), onClick = {
                    onStopSpeaking()
                    submittedQuestion = question.trim()
                    if (dynamicMode) onAskDynamic(question.trim())
                }) { Text("Chiedi") }
                OutlinedButton(enabled = !loading, onClick = onDictate) { Text("Detta domanda") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("La dettatura usa il servizio vocale del dispositivo, che può elaborare l’audio online. Controlla la trascrizione prima di inviarla.",
                style = MaterialTheme.typography.bodySmall)
            voiceNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            dynamicError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            answer?.let { text ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Risposta", style = MaterialTheme.typography.titleMedium)
                        Text("Domanda: ${dynamicResponse?.question?.takeIf { it.isNotBlank() } ?: submittedQuestion.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                        dynamicResponse?.let { result ->
                            Text(result.statusLabel, style = MaterialTheme.typography.labelLarge)
                            if (result.generatedAt.isNotBlank()) Text("Risposta del ${result.generatedAt}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (dynamicMode) Text("Risposta riferita alla richiesta, non aggiornata automaticamente.", style = MaterialTheme.typography.bodySmall)
                        Text(text, Modifier.testTag("smart-answer"))
                        TextButton(enabled = speechReady, onClick = { onSpeak(text) }) { Text("Leggi risposta") }
                    }
                }
            }
            TextButton(enabled = speechReady, onClick = onStopSpeaking) { Text("Ferma lettura") }
            TextButton(onClick = { showSources = !showSources }) {
                Text(if (showSources) "Nascondi provenienza" else "Mostra provenienza e limiti")
            }
            if (!dynamicMode) Text("Copertura parziale: otto punti luce, living e ACS. Prolunga / Allarme è escluso in attesa di classificazione; lavanderia, portico, cucina ed esterno non sono mappati.",
                style = MaterialTheme.typography.bodySmall)
            if (showSources) {
                Text("Provenienza delle letture", style = MaterialTheme.typography.titleLarge)
                if (dynamicMode) {
                    dynamicResponse?.evidence?.forEachIndexed { index, detail ->
                        Card(Modifier.fillMaxWidth().testTag("energy-evidence-$index")) {
                            Text(detail, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    EnergySmartState.topics.entries.map { it.value to it.key }.forEach { (metric, topic) ->
                        val observation = energyState.reading(metric)
                        Card(Modifier.fillMaxWidth().testTag("energy-source-$metric")) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(energyState.readingText(metric))
                                Text(observation?.sourceTopic ?: "Topic atteso: $topic", style = MaterialTheme.typography.bodySmall)
                                Text(if (observation == null) "Nessun messaggio ricevuto." else
                                    "Ricezione: ${java.time.Instant.ofEpochMilli(observation.receivedAtMs)} · retained: ${observation.retained}",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("Età della misura sorgente ignota.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (!dynamicMode) state.readings().forEach { reading ->
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
            Text(if (dynamicMode) "Consultazione energetica di sola lettura. Il pianificatore sceglie strumenti validati; non può inviare comandi ai dispositivi."
                 else "Consultazione di luci, living e ACS. Le risposte sono preparate dai dati mappati; le altre richieste non sono ancora disponibili.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Storico e motivazioni") }
            TextButton(onClick = onClassic, modifier = Modifier.fillMaxWidth()) { Text("Apri app classica") }
        }
    }
}
