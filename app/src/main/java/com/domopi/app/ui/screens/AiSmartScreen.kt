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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    settingsManager: SettingsManager? = null,
    credentialStore: CredentialStore? = null,
    reserveState: PredictiveReserveSmartState = PredictiveReserveSmartState(),
    thermostatState: ThermostatSmartState = ThermostatSmartState(),
    onThermostatCommand: (String, String) -> Boolean = { _, _ -> false },
    onSceneCommand: (String) -> Boolean = { false },
    environmentState: EnvironmentSmartState = EnvironmentSmartState(),
    heatingState: HeatingSmartState = HeatingSmartState(),
    onFireplaceSettings: (FireplaceSettingsCommand) -> Boolean = { false },
    onFireplacePower: (Boolean) -> Boolean = { false },
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var question by rememberSaveable { mutableStateOf("") }
    var voiceNotice by remember { mutableStateOf<String?>(null) }
    var speechReady by remember { mutableStateOf(false) }
    var speech by remember { mutableStateOf<TextToSpeech?>(null) }
    var serviceUrl by rememberSaveable { mutableStateOf(HouseAiRepository.DEFAULT_BASE_URL) }
    var serviceToken by remember { mutableStateOf("") }
    // Indirizzo nelle impostazioni, token cifrato con Android Keystore; salvati dopo ogni modifica.
    var credentialsLoaded by remember { mutableStateOf(settingsManager == null && credentialStore == null) }
    LaunchedEffect(Unit) {
        settingsManager?.let { serviceUrl = it.houseAiUrl.first() }
        (credentialStore?.get(Credential.HOUSE_AI_TOKEN) as? CredentialResult.Present)?.let { serviceToken = it.value }
        credentialsLoaded = true
    }
    LaunchedEffect(serviceUrl, serviceToken, credentialsLoaded) {
        if (!credentialsLoaded) return@LaunchedEffect
        delay(800)
        settingsManager?.saveHouseAiUrl(serviceUrl)
        credentialStore?.let {
            if (serviceToken.isBlank()) it.clear(Credential.HOUSE_AI_TOKEN) else it.set(Credential.HOUSE_AI_TOKEN, serviceToken)
        }
    }
    var response by remember { mutableStateOf<EnergyAssistantAnswer?>(null) }
    // Manual fireplace on/off proposed by the backend: sent only after "Conferma".
    var pendingFireplacePower by remember { mutableStateOf<Boolean?>(null) }
    var fireplaceNotice by remember { mutableStateOf<String?>(null) }
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

    var askedQuestion by rememberSaveable { mutableStateOf<String?>(null) }
    fun speak(text: String) {
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
    }
    // "Com'è andata oggi": shown like an answer, read aloud only on "Leggi risposta".
    fun daySummary() {
        askedQuestion = "Com'è andata oggi?"
        scope.launch {
            loading = true
            dynamicError = null
            response = null
            val started = System.currentTimeMillis()
            try {
                response = repository.daySummary(serviceUrl, serviceToken)
                stackHealthManager.logTraffic(TrafficLogEntry(tag = "HOUSE-AI", endpoint = "/v1/summary/day",
                    statusCode = 200, latencyMs = System.currentTimeMillis() - started,
                    details = "Riepilogo del giorno completato"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                dynamicError = e.message ?: "Risposta del backend non valida."
                stackHealthManager.logTraffic(TrafficLogEntry(tag = "HOUSE-AI", endpoint = "/v1/summary/day",
                    details = "Riepilogo del giorno non riuscito", isError = true))
            } catch (e: Exception) {
                dynamicError = HouseAiErrors.network(e)
                stackHealthManager.logTraffic(TrafficLogEntry(tag = "HOUSE-AI", endpoint = "/v1/summary/day",
                    details = "Riepilogo del giorno non riuscito", isError = true))
            } finally {
                loading = false
                healthRefresh++
            }
        }
    }
    // A spoken question is sent at once and its answer (or error) is read aloud; typed ones are not.
    fun ask(text: String, spoken: Boolean) {
        askedQuestion = text
        scope.launch {
            loading = true
            dynamicError = null
            response = null
            pendingFireplacePower = null
            fireplaceNotice = null
            try {
                val started = System.currentTimeMillis()
                val result = repository.assistant(serviceUrl, serviceToken, text, energyState, connected,
                    climateState, state, assistantContext, reserve = reserveState,
                    thermostats = thermostatState, environment = environmentState, heating = heatingState)
                result.lightCommands.forEach { command ->
                    check(connected && onLightCommand(command.lightId, command.state)) {
                        "Comando luce non inviato: connessione o target non valido."
                    }
                }
                result.thermostatCommands.forEach { command ->
                    check(connected && onThermostatCommand(command.thermostat, command.targetC)) {
                        "Comando termostato non inviato: connessione o valore non valido."
                    }
                }
                result.sceneCommands.forEach { scene ->
                    check(connected && onSceneCommand(scene)) {
                        "Scena luci non inviata: connessione o scena non valida."
                    }
                }
                result.fireplaceSettings.forEach { command ->
                    check(connected && onFireplaceSettings(command)) {
                        "Comando caminetto non inviato: connessione o valori non validi."
                    }
                }
                pendingFireplacePower = result.fireplacePowerRequest
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
            } catch (e: Exception) {
                dynamicError = HouseAiErrors.network(e)
                stackHealthManager.logTraffic(TrafficLogEntry(tag = "HOUSE-AI", endpoint = "/v1/assistant/query",
                    details = "Richiesta non riuscita", isError = true))
                healthRefresh++
            } finally {
                loading = false
                if (spoken && speechReady) (response?.text ?: dynamicError)?.let(::speak)
            }
        }
    }
    fun confirmFireplace() {
        val on = pendingFireplacePower ?: return
        pendingFireplacePower = null
        fireplaceNotice = when {
            !connected -> "Comando non inviato: la casa non è collegata."
            !onFireplacePower(on) -> "Comando non inviato: il caminetto non è in modalità Manuale."
            on -> "Ok, accendo il caminetto."
            else -> "Ok, spengo il caminetto."
        }
        if (speechReady) fireplaceNotice?.let(::speak)
    }
    val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (result.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) {
            question = text
            when {
                text.length > 1000 -> voiceNotice = "Domanda troppo lunga: al massimo 1000 caratteri."
                serviceUrl.isBlank() || serviceToken.isBlank() -> voiceNotice = "Configura indirizzo e token dell'assistente."
                else -> { voiceNotice = null; ask(text, spoken = true) }
            }
        } else {
            voiceNotice = "Nessuna domanda riconosciuta. Riprova o scrivi la domanda."
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
        environmentState = environmentState,
        climateState = climateState,
        serviceUrl = serviceUrl,
        serviceToken = serviceToken,
        onServiceUrlChange = { serviceUrl = it; dynamicError = null; response = null },
        onServiceTokenChange = { serviceToken = it; dynamicError = null; response = null },
        dynamicAnswer = response?.text,
        askedQuestion = askedQuestion,
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
        onAskDynamic = { ask(it, spoken = false) },
        onDaySummary = { daySummary() },
        onDictate = {
            speech?.stop()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Chiedi o comanda la casa")
            }
            try { recognizer.launch(intent) }
            catch (_: ActivityNotFoundException) { voiceNotice = "Riconoscimento vocale non disponibile. Puoi scrivere la domanda." }
            catch (_: SecurityException) { voiceNotice = "Accesso al riconoscimento vocale negato. Puoi scrivere la domanda." }
        },
        onSpeak = { speak(it) },
        onStopSpeaking = { speech?.stop(); voiceNotice = "Lettura interrotta." },
        pendingFireplacePower = pendingFireplacePower,
        fireplaceNotice = fireplaceNotice,
        onConfirmFireplace = { confirmFireplace() },
        onCancelFireplace = { pendingFireplacePower = null; fireplaceNotice = "Comando annullato." }
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
    environmentState: EnvironmentSmartState = EnvironmentSmartState(),
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
    onDaySummary: () -> Unit = {},
    askedQuestion: String? = null,
    pendingFireplacePower: Boolean? = null,
    fireplaceNotice: String? = null,
    onConfirmFireplace: () -> Unit = {},
    onCancelFireplace: () -> Unit = {},
) {
    BackHandler(onBack = onClassic)
    var submittedQuestion by rememberSaveable { mutableStateOf<String?>(null) }
    var showSources by rememberSaveable { mutableStateOf(false) }
    var showStackSheet by rememberSaveable { mutableStateOf(false) }
    var showAuxiliaryMenu by remember { mutableStateOf(false) }
    var showConfiguration by rememberSaveable { mutableStateOf(false) }
    var summaryExpanded by rememberSaveable { mutableStateOf(true) }

    // Fase 3.5 A: temperatures and energy only, written (read aloud only on "Leggi riepilogo").
    val summary = if (dynamicMode) HouseSummary.text(energyState, environmentState, connected)
    else state.summary(connected)
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
                        com.domopi.app.ui.components.SecretTextField(serviceToken, onServiceTokenChange,
                            "Token backend", testTag = "smart-token")
                        Text("Il token è salvato cifrato sul telefono (Android Keystore). Svuota il campo per cancellarlo.",
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = speechReady, onClick = { onSpeak(summary) }) { Text("Leggi riepilogo") }
                            if (dynamicMode) {
                                TextButton(enabled = !loading && serviceUrl.isNotBlank() && serviceToken.isNotBlank(),
                                    onClick = onDaySummary, modifier = Modifier.testTag("smart-day-summary")) {
                                    Text("Com'è andata oggi")
                                }
                            }
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth().testTag("smart-query-card")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Chiedi alla casa", style = MaterialTheme.typography.titleMedium)
                    Text(if (dynamicMode) "Tocca Parla e fai la tua domanda: parte da sola e la risposta viene letta. " +
                        "Puoi comandare luci, scene luci, termostati e caminetto."
                        else "Puoi chiedere luci e temperature disponibili.", style = MaterialTheme.typography.bodySmall)
                    // Voice is the primary input: the recognized question is sent without a second tap.
                    Button(enabled = !loading, onClick = onDictate,
                        modifier = Modifier.fillMaxWidth().height(64.dp).testTag("smart-speak")) {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Parla", style = MaterialTheme.typography.titleMedium)
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    voiceNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    OutlinedTextField(question, onQuestionChange, label = { Text("Oppure scrivi la domanda") },
                        modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 5, enabled = !loading)
                    if (question.length > 1000) Text("La query può contenere al massimo 1000 caratteri.", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(enabled = question.isNotBlank() && question.length <= 1000 && !loading &&
                        (!dynamicMode || serviceUrl.isNotBlank() && serviceToken.isNotBlank()), onClick = {
                        onStopSpeaking()
                        submittedQuestion = question.trim()
                        if (dynamicMode) onAskDynamic(question.trim())
                    }) { Text("Invia") }
                }
            }
            Card(Modifier.fillMaxWidth().testTag("smart-response-card")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Risposta", style = MaterialTheme.typography.titleMedium)
                    val asked = askedQuestion ?: submittedQuestion
                    if (asked != null && (answer != null || dynamicError != null || loading)) {
                        Text("Hai chiesto: $asked", Modifier.testTag("smart-asked"), style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic)
                    }
                    dynamicError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (answer == null && dynamicError == null && !loading) {
                        Text("La risposta apparirà qui.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    answer?.let { text ->
                        dynamicResponse?.let { result ->
                            Text(result.statusLabel, style = MaterialTheme.typography.labelLarge)
                            if (result.generatedAt.isNotBlank()) Text("Risposta del ${SpokenTime.moment(result.generatedAt)}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (dynamicMode) Text("Risposta riferita alla richiesta, non aggiornata automaticamente.", style = MaterialTheme.typography.bodySmall)
                        Text(text, Modifier.testTag("smart-answer"))
                        pendingFireplacePower?.let { on ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onConfirmFireplace, modifier = Modifier.testTag("smart-fireplace-confirm")) {
                                    Text(if (on) "Conferma accensione" else "Conferma spegnimento")
                                }
                                OutlinedButton(onClick = onCancelFireplace, modifier = Modifier.testTag("smart-fireplace-cancel")) {
                                    Text("Annulla")
                                }
                            }
                        }
                        fireplaceNotice?.let { Text(it, Modifier.testTag("smart-fireplace-notice")) }
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
                                        else "Ricezione: ${SpokenTime.moment(observation.receivedAtMs)} · retained: ${observation.retained}. Età della misura ignota.",
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
                    Text("Comandi: luci, scene luci, termostati, modalità e orari del caminetto; acceso/spento del caminetto solo in Manuale e con conferma. Gli altri impianti sono in sola lettura.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
