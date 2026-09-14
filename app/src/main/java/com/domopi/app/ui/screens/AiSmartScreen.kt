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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.domopi.app.data.*
import com.domopi.app.ui.components.StackStatusBottomSheet
import com.domopi.app.ui.components.StackStatusIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/** Uses the existing state observer. This screen has no command or MQTT client capability. */
@Composable
fun AiSmartScreen(
    state: AiSmartState,
    energyState: EnergySmartState,
    climateState: ClimateSmartState,
    connected: Boolean,
    onClassic: () -> Unit,
    onHistory: () -> Unit,
    onLightCommand: (String, Boolean) -> Boolean = { _, _ -> false },
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var question by rememberSaveable { mutableStateOf("") }
    var voiceNotice by remember { mutableStateOf<String?>(null) }
    var speechReady by remember { mutableStateOf(false) }
    var speech by remember { mutableStateOf<TextToSpeech?>(null) }
    var serviceUrl by rememberSaveable { mutableStateOf(HouseAiRepository.DEFAULT_BASE_URL) }
    var serviceToken by remember { mutableStateOf("") }
    var response by remember { mutableStateOf<EnergyAssistantAnswer?>(null) }
    var assistantContext by remember { mutableStateOf<AssistantContext?>(null) }
    var dynamicError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repository = remember { HouseAiRepository() }

    val stackHealthManager = remember { StackHealthManager() }
    val stackHealthState by stackHealthManager.healthState.collectAsState()
    val trafficLogs by stackHealthManager.trafficLogs.collectAsState()
    var healthRefresh by remember { mutableIntStateOf(0) }

    suspend fun refreshHealth() {
        if (serviceToken.isBlank()) return
        stackHealthManager.checking()
        val started = System.currentTimeMillis()
        try {
            stackHealthManager.update(repository.stackHealth(serviceUrl, serviceToken, connected))
            stackHealthManager.logTraffic(TrafficLogEntry(tag = "HEALTH", endpoint = "/v1/health/details",
                statusCode = 200, latencyMs = System.currentTimeMillis() - started,
                details = "Controllo stack completato"))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            stackHealthManager.unavailable("Percorso HTTPS o backend non raggiungibile")
            stackHealthManager.logTraffic(TrafficLogEntry(tag = "HEALTH", endpoint = "/v1/health/details",
                latencyMs = System.currentTimeMillis() - started, details = "Controllo non riuscito", isError = true))
        }
    }

    LaunchedEffect(serviceUrl, serviceToken, connected, healthRefresh) {
        if (serviceToken.isBlank()) {
            stackHealthManager.reset()
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refreshHealth()
                delay(60_000)
            }
        }
    }

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
    AiSmartContent(
        state = state,
        connected = connected,
        question = question,
        onQuestionChange = { question = it },
        onClassic = onClassic,
        onHistory = onHistory,
        voiceNotice = voiceNotice,
        speechReady = speechReady,
        dynamicMode = true,
        energyState = energyState,
        climateState = climateState,
        serviceUrl = serviceUrl,
        serviceToken = serviceToken,
        onServiceUrlChange = { serviceUrl = it; dynamicError = null; response = null },
        onServiceTokenChange = { serviceToken = it; dynamicError = null; response = null },
        dynamicAnswer = response?.text,
        dynamicResponse = response,
        dynamicError = dynamicError,
        loading = loading,
        stackHealthState = stackHealthState,
        trafficLogs = trafficLogs,
        onRefreshStackHealth = {
            healthRefresh++
        },
        onClearTrafficLogs = {
            stackHealthManager.clearLogs()
        },
        onAskDynamic = { text ->
            scope.launch {
                    loading = true
                    dynamicError = null
                    response = null
                    try {
                        val started = System.currentTimeMillis()
                        val result = repository.assistant(serviceUrl, serviceToken, text, energyState, connected,
                            climateState, state, assistantContext)
                        result.lightCommands.forEach { command ->
                            check(connected && onLightCommand(command.lightId, command.state)) {
                                "Comando luce non inviato: connessione o target non valido."
                            }
                        }
                        response = result
                        assistantContext = result.context ?: assistantContext
                        stackHealthManager.logTraffic(TrafficLogEntry(tag = "HOUSE-AI", endpoint = "/v1/assistant/query",
                            statusCode = 200, latencyMs = System.currentTimeMillis() - started,
                            details = "Richiesta completata"))
                        healthRefresh++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IllegalStateException) {
                        dynamicError = e.message ?: "Risposta del backend non valida."
                        stackHealthManager.logTraffic(TrafficLogEntry(tag = "HOUSE-AI", endpoint = "/v1/assistant/query",
                            details = "Risposta backend non valida", isError = true))
                        healthRefresh++
                    } catch (_: Exception) {
                        dynamicError = "Assistente energia non raggiungibile o risposta non valida. Verifica indirizzo e connessione, poi riprova."
                        stackHealthManager.logTraffic(TrafficLogEntry(tag = "HOUSE-AI", endpoint = "/v1/assistant/query",
                            details = "Richiesta non riuscita", isError = true))
                        healthRefresh++
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
            val limit = TextToSpeech.getMaxSpeechInputLength()
            val chunks = speechChunks(text, limit)
            for ((index, chunk) in chunks.withIndex()) {
                val id = "energy-$index" + if (index == chunks.lastIndex) "-last" else ""
                if (speech?.speak(chunk, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id) == TextToSpeech.ERROR) {
                    speech?.stop()
                    voiceNotice = "Lettura vocale non riuscita. La risposta resta disponibile a schermo."
                    break
                }
            }
        },
        onStopSpeaking = { speech?.stop(); voiceNotice = "Lettura interrotta." }
    )
}

/** Platform-free content also used by isolated UI tests; no connection is opened here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiSmartContent(
    state: AiSmartState,
    connected: Boolean,
    question: String,
    onQuestionChange: (String) -> Unit,
    onClassic: () -> Unit,
    onHistory: () -> Unit,
    voiceNotice: String? = null,
    speechReady: Boolean = false,
    onDictate: () -> Unit = {},
    onSpeak: (String) -> Unit = {},
    onStopSpeaking: () -> Unit = {},
    dynamicMode: Boolean = false,
    energyState: EnergySmartState = EnergySmartState(),
    climateState: ClimateSmartState = ClimateSmartState(),
    dynamicResponse: EnergyAssistantAnswer? = null,
    serviceUrl: String = "",
    serviceToken: String = "",
    onServiceUrlChange: (String) -> Unit = {},
    onServiceTokenChange: (String) -> Unit = {},
    dynamicAnswer: String? = null,
    dynamicError: String? = null,
    loading: Boolean = false,
    stackHealthState: StackHealthState = StackHealthState(),
    trafficLogs: List<TrafficLogEntry> = emptyList(),
    onRefreshStackHealth: () -> Unit = {},
    onClearTrafficLogs: () -> Unit = {},
    onAskDynamic: (String) -> Unit = {},
) {
    BackHandler(onBack = onClassic)
    var submittedQuestion by rememberSaveable { mutableStateOf<String?>(null) }
    var showSources by rememberSaveable { mutableStateOf(false) }
    var showStackSheet by rememberSaveable { mutableStateOf(false) }
    var showAuxiliaryMenu by remember { mutableStateOf(false) }
    var showConfiguration by rememberSaveable { mutableStateOf(false) }
    var summaryExpanded by rememberSaveable { mutableStateOf(true) }

    val active = state.activeDeviceLabels() + listOfNotNull(climateState.declaredActiveLabel())
    val summary = if (dynamicMode) listOf(
        if (connected) "Collegamento attivo." else "Collegamento assente: mostro gli ultimi dati ricevuti.",
        if (active.isEmpty()) "Nessun dispositivo attivo rilevato tra quelli mappati."
        else "Dispositivi attivi dichiarati: ${active.joinToString(", ")}.",
        state.environmentalMeasuresText(),
        AiSmartState.freshnessText,
    ).joinToString(" ") else state.summary(connected)
    // Re-evaluate against the latest observations so a cached answer cannot hide a disconnection.
    val answer = if (dynamicMode) dynamicAnswer else submittedQuestion?.let { state.answer(it, connected) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI smart") },
                navigationIcon = {
                    TextButton(onClick = onClassic) { Text("Indietro") }
                },
                actions = {
                    StackStatusIcon(
                        overallStatus = stackHealthState.overallStatus,
                        isChecking = stackHealthState.isChecking,
                        onClick = { showStackSheet = true }
                    )
                    Box {
                        IconButton(onClick = { showAuxiliaryMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Menu configurazione e diagnostica")
                        }
                        DropdownMenu(expanded = showAuxiliaryMenu,
                            onDismissRequest = { showAuxiliaryMenu = false }) {
                            DropdownMenuItem(text = { Text("Configurazione assistente") }, onClick = {
                                showAuxiliaryMenu = false; showConfiguration = true
                            })
                            DropdownMenuItem(text = { Text("Diagnostica stack") }, onClick = {
                                showAuxiliaryMenu = false; showStackSheet = true
                            })
                            DropdownMenuItem(text = { Text("Storico e motivazioni") }, onClick = {
                                showAuxiliaryMenu = false; onHistory()
                            })
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (showStackSheet) {
            StackStatusBottomSheet(
                healthState = stackHealthState,
                trafficLogs = trafficLogs,
                onDismiss = { showStackSheet = false },
                onRefresh = onRefreshStackHealth,
                onClearLogs = onClearTrafficLogs
            )
        }
        if (showConfiguration) {
            AlertDialog(onDismissRequest = { showConfiguration = false },
                title = { Text("Configurazione assistente") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(serviceUrl, onServiceUrlChange,
                            label = { Text("Indirizzo backend AI") }, singleLine = true)
                        OutlinedTextField(serviceToken, onServiceTokenChange,
                            label = { Text("Token backend") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation())
                        Text("Il token resta in memoria e non viene salvato nell’app.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = { TextButton(onClick = { showConfiguration = false }) { Text("Chiudi") } })
        }

        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("smart-home-summary")
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { summaryExpanded = !summaryExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Stato della domotica", style = MaterialTheme.typography.titleMedium)
                            if (!summaryExpanded) {
                                Text(
                                    "Minimizzato · tocca per espandere",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { summaryExpanded = !summaryExpanded }) {
                            Icon(
                                imageVector = if (summaryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (summaryExpanded) "Minimizza riquadro" else "Espandi riquadro"
                            )
                        }
                    }
                    if (summaryExpanded) {
                        Text(summary, Modifier.testTag("smart-summary"))
                        TextButton(enabled = speechReady, onClick = { onSpeak(summary) }) { Text("Leggi riepilogo") }
                    }
                }
            }
            Card(Modifier.fillMaxWidth().testTag("smart-query-card")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Chiedi alla casa", style = MaterialTheme.typography.titleMedium)
                    Text(if (dynamicMode) "Scrivi o detta una query. Puoi anche accendere o spegnere una luce mappata."
                        else "Puoi chiedere luci e temperature disponibili.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(question, onQuestionChange, label = { Text("La tua query") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5, enabled = !loading)
                    if (question.length > 1000) Text("La query può contenere al massimo 1000 caratteri.", color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = question.isNotBlank() && question.length <= 1000 && !loading &&
                            (!dynamicMode || serviceUrl.isNotBlank() && serviceToken.isNotBlank()), onClick = {
                            onStopSpeaking()
                            submittedQuestion = question.trim()
                            if (dynamicMode) onAskDynamic(question.trim())
                        }) { Text("Chiedi") }
                        OutlinedButton(enabled = !loading, onClick = onDictate) { Text("Detta") }
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Controlla la trascrizione prima di inviarla.", style = MaterialTheme.typography.bodySmall)
                    voiceNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Card(Modifier.fillMaxWidth().testTag("smart-response-card")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Risposta", style = MaterialTheme.typography.titleMedium)
                    dynamicError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (answer == null && dynamicError == null && !loading) {
                        Text("La risposta apparirà qui.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    answer?.let { text ->
                        Text("Domanda: ${dynamicResponse?.question?.takeIf { it.isNotBlank() } ?: submittedQuestion.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                        dynamicResponse?.let { result ->
                            Text(result.statusLabel, style = MaterialTheme.typography.labelLarge)
                            if (result.generatedAt.isNotBlank()) Text("Risposta del ${result.generatedAt}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (dynamicMode) Text("Risposta riferita alla richiesta, non aggiornata automaticamente.", style = MaterialTheme.typography.bodySmall)
                        Text(text, Modifier.testTag("smart-answer"))
                        TextButton(enabled = speechReady, onClick = { onSpeak(text) }) { Text("Leggi risposta") }
                    }
                    TextButton(enabled = speechReady, onClick = onStopSpeaking) { Text("Ferma lettura") }
                    TextButton(onClick = { showSources = !showSources }) {
                        Text(if (showSources) "Nascondi provenienza" else "Mostra provenienza e limiti")
                    }
                    if (showSources) {
                        HorizontalDivider()
                        Text("Provenienza e limiti", style = MaterialTheme.typography.titleSmall)
                        if (dynamicMode) {
                            dynamicResponse?.evidence?.forEachIndexed { index, detail ->
                                Text(detail, Modifier.testTag("energy-evidence-$index"), style = MaterialTheme.typography.bodySmall)
                            }
                            EnergySmartState.topics.entries.map { it.value to it.key }.forEach { (metric, topic) ->
                                val observation = energyState.reading(metric)
                                Column(Modifier.testTag("energy-source-$metric")) {
                                    Text(energyState.readingText(metric), style = MaterialTheme.typography.bodySmall)
                                    Text(observation?.sourceTopic ?: "Topic atteso: $topic", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        if (observation == null) "Nessun messaggio ricevuto. Età della misura ignota."
                                        else "Ricezione: ${Instant.ofEpochMilli(observation.receivedAtMs)} · retained: ${observation.retained}. Età della misura ignota.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        if (!dynamicMode) state.readings().forEach { reading ->
                            Column(Modifier.testTag("source-${reading.entity.topic}")) {
                                Text("${reading.entity.label}: ${reading.value}", style = MaterialTheme.typography.bodySmall)
                                Text(reading.observation?.sourceTopic ?: "Topic atteso: ${reading.entity.topic}",
                                    style = MaterialTheme.typography.bodySmall)
                                Text(reading.provenance(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Text("I comandi ON/OFF sono abilitati solo per le luci mappate; gli altri domini restano in consultazione.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
