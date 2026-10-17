package com.domopi.app.data

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.roundToInt

data class EnergyData(
    val solarPower: Float = 0f,
    val homeConsumption: Float = 0f,
    val gridPower: Float = 0f,
    val batteryPower: Float = 0f,
    val batterySoc: Float = 0f,
    val pufferAcs: Float = 0f,
    val pufferAlto: Float = 0f,
    val pufferBasso: Float = 0f,
    val solarCollectorTemp: Float = 0f,
    val solarPumpSpeed: Int = 0,
    val gridImportKwhOggi: Float = 0f,
    val gridImportKwhIeri: Float = 0f,
    // Android receive time (ms) of the flow values; 0 = never received (energy flow freshness).
    val solarAtMs: Long = 0L,
    val homeAtMs: Long = 0L,
    val gridAtMs: Long = 0L,
    val batteryAtMs: Long = 0L,
    val socAtMs: Long = 0L,
    // When the daily grid-import summary was last obtained (MQTT or EmonCMS); 0 = unknown, shown as "—".
    val gridImportAtMs: Long = 0L,
)

data class SensorData(
    val temperature: Float = 0f,
    val humidity: Float = 0f,
    val humidex: Float = 0f,
)

data class EnvironmentState(
    val living: SensorData = SensorData(),
    val bedroom: SensorData = SensorData(),
    val outdoor: SensorData = SensorData()
)

data class AiSettings(
    val systemEnabled: Boolean = true,
    val predictiveReserveEnabled: Boolean = false,
    // PR-MODE.1, read-only: null until Node-RED publishes them.
    val predictiveReserveMode: String? = null,
    val predictiveReserveReady: Boolean? = null,
    val compressorOnMin: Int = 15,
    val compressorOffMin: Int = 15,
    val nightHumidexThreshold: Int = 30,
    val nightVmcMaxSpeed: Int = 2,
    val deficitToleranceMin: Int = 10,
    val morningAcManagement: Boolean = false,
    val morningHumidexEmergency: Int = 33,
    val alarm: AiAlarm? = null
)

data class DomoticaSettings(
    val holidayMode: Boolean = false,
    val ecoLights: Boolean = false,
    val poolLightsAuto: Boolean = false,
    val porchSensor: Boolean = false,
    val acAuto: Boolean = false
)

private const val RATE_WINDOW_SECONDS = 10
private const val TRAFFIC_LOG_SIZE = 50

class MqttManager {
    private val _aiSmartState = MutableStateFlow(AiSmartState())
    val aiSmartState: StateFlow<AiSmartState> = _aiSmartState
    private val _energySmartState = MutableStateFlow(EnergySmartState())
    val energySmartState: StateFlow<EnergySmartState> = _energySmartState
    private val _climateSmartState = MutableStateFlow(ClimateSmartState())
    val climateSmartState: StateFlow<ClimateSmartState> = _climateSmartState
    private val _irrigationState = MutableStateFlow<IrrigationState?>(null)
    val irrigationState: StateFlow<IrrigationState?> = _irrigationState
    private val _irrigationResult = MutableStateFlow<IrrigationCommandResult?>(null)
    val irrigationResult: StateFlow<IrrigationCommandResult?> = _irrigationResult

    private val _predictiveReserveSmartState = MutableStateFlow(PredictiveReserveSmartState())
    val predictiveReserveSmartState: StateFlow<PredictiveReserveSmartState> = _predictiveReserveSmartState
    private val _thermostatSmartState = MutableStateFlow(ThermostatSmartState())
    val thermostatSmartState: StateFlow<ThermostatSmartState> = _thermostatSmartState
    private val _environmentSmartState = MutableStateFlow(EnvironmentSmartState())
    val environmentSmartState: StateFlow<EnvironmentSmartState> = _environmentSmartState
    private val _rulesState = MutableStateFlow(RulesState())
    val rulesState: StateFlow<RulesState> = _rulesState

    private val _heatingProfile = MutableStateFlow(HeatingProfileState())
    val heatingProfile: StateFlow<HeatingProfileState> = _heatingProfile

    private val _heatingSmartState = MutableStateFlow(HeatingSmartState())
    val heatingSmartState: StateFlow<HeatingSmartState> = _heatingSmartState

    private var mqttClient: MqttAsyncClient? = null
    private val messageQueue = ConcurrentLinkedQueue<MqttQueuedMessage>()
    
    @OptIn(ExperimentalCoroutinesApi::class)
    private val mqttDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(mqttDispatcher + SupervisorJob())

    private val _lightStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val lightStates: StateFlow<Map<String, Boolean>> = _lightStates

    private val _energyData = MutableStateFlow(EnergyData())
    val energyData: StateFlow<EnergyData> = _energyData

    private val _environmentState = MutableStateFlow(EnvironmentState())
    val environmentState: StateFlow<EnvironmentState> = _environmentState

    private val _aiManagedData = MutableStateFlow(AiManagedData())
    val aiManagedData: StateFlow<AiManagedData> = _aiManagedData

    private val _aiSettings = MutableStateFlow(AiSettings())
    val aiSettings: StateFlow<AiSettings> = _aiSettings

    private val _domoticaSettings = MutableStateFlow(DomoticaSettings())
    val domoticaSettings: StateFlow<DomoticaSettings> = _domoticaSettings

    private val _hvacState = MutableStateFlow(HvacState())
    val hvacState: StateFlow<HvacState> = _hvacState

    private val _isConnected = MutableStateFlow(value = false)
    val isConnected: StateFlow<Boolean> = _isConnected

    // Release identity published by tools/release_android.py (outside the Digital Twin namespace).
    private val _publishedBuild = MutableStateFlow<PublishedBuild>(PublishedBuild.Waiting)
    val publishedBuild: StateFlow<PublishedBuild> = _publishedBuild

    private val _startupGraceOver = MutableStateFlow(false)
    val startupGraceOver: StateFlow<Boolean> = _startupGraceOver
    private var retainedWaitJob: Job? = null

    init {
        scope.launch {
            delay(BuildAlignment.STARTUP_GRACE_MS)
            _startupGraceOver.value = true
        }
    }

    private val _trafficLog = MutableStateFlow<List<String>>(emptyList())
    val trafficLog: StateFlow<List<String>> = _trafficLog

    // Live Digital Twin traffic only: retained replays after (re)subscription are not counted.
    private val rateMeter = MessageRateMeter(RATE_WINDOW_SECONDS)
    private val _messageRate = MutableStateFlow(MessageRate())
    val messageRate: StateFlow<MessageRate> = _messageRate

    init {
        scope.launch {
            while (isActive) {
                delay(1_000)
                _messageRate.value = rateMeter.snapshot(System.currentTimeMillis())
            }
        }
    }

    private fun addTrafficLog(message: String) {
        val timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        _trafficLog.update { current ->
            (listOf("[$timestamp] $message") + current).take(TRAFFIC_LOG_SIZE)
        }
    }

    private var currentBrokerUrl: String? = null
    // Hash of the login in use: new credentials force a reconnect (the old session keeps the old login).
    private var currentLogin: Int? = null
    private var isConnecting = false

    /** Identifies a login without keeping the password in plain text in this object. */
    internal fun loginKey(user: String?, pass: String?): Int = "${user.orEmpty()}\u0000${pass.orEmpty()}".hashCode()

    fun connect(brokerUrl: String, user: String? = null, pass: String? = null) {
        val login = loginKey(user, pass)
        val sameTarget = currentBrokerUrl == brokerUrl && currentLogin == login
        if (mqttClient?.isConnected == true && sameTarget) return

        if (isConnecting && sameTarget) {
            Log.d("MQTT", "Connessione già in corso per $brokerUrl")
            return
        }

        Log.d("MQTT", "Tentativo di connessione a $brokerUrl")
        currentBrokerUrl = brokerUrl
        currentLogin = login
        isConnecting = true
        
        try {
            mqttClient?.setCallback(null)
            if (mqttClient?.isConnected == true) {
                mqttClient?.disconnectForcibly()
            }
            mqttClient?.close(true)
        } catch (_: Exception) {}

        try {
            val clientId = "ZAI_" + UUID.randomUUID().toString().substring(0, 8)
            mqttClient = MqttAsyncClient(brokerUrl, clientId, MemoryPersistence())
            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                // Clean session: the client id is random per connection, so a persistent session
                // would stay on the broker forever (354 orphans found on .20, 17/10/2026). Every
                // (re)connect resubscribes in connectComplete and receives the retained states again.
                isCleanSession = true
                connectionTimeout = 15
                keepAliveInterval = 60
                // A username without password is rejected by Mosquitto's password file:
                // send credentials only as a pair, otherwise connect anonymously.
                if (!user.isNullOrEmpty() && !pass.isNullOrEmpty()) {
                    userName = user
                    password = pass.toCharArray()
                }
            }
            mqttClient?.setCallback(
                object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.i("MQTT", "Connesso a $serverURI")
                    addTrafficLog("CONNESSO: $serverURI")
                    isConnecting = false
                    _aiSmartState.value = AiSmartState()
                    _energySmartState.value = EnergySmartState()
                    _climateSmartState.value = ClimateSmartState()
                    _predictiveReserveSmartState.value = PredictiveReserveSmartState()
                    _thermostatSmartState.value = ThermostatSmartState()
                    _environmentSmartState.value = EnvironmentSmartState()
                    _heatingSmartState.value = HeatingSmartState()
                    _isConnected.value = true
                    subscribeToUnifiedTopics()
                    subscribeToBuildIdentity()
                    processMessageQueue()
                }
                override fun connectionLost(cause: Throwable?) { 
                    isConnecting = false
                    _isConnected.value = false 
                    Log.e("MQTT", "Connessione perduta: ${cause?.message}")
                    addTrafficLog("DISCONNESSO: ${cause?.message}")
                }
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val receivedAt = System.currentTimeMillis()
                    val receivedPrefix = digitalTwinPrefix.removeSuffix("/") + "/"
                    if (topic != null && message != null && topic.startsWith(receivedPrefix) && !message.isRetained) {
                        rateMeter.record(receivedAt)
                    }
                    scope.launch {
                        if (topic != null && message != null && topic.startsWith(receivedPrefix) &&
                            receivedPrefix == digitalTwinPrefix.removeSuffix("/") + "/") {
                            _aiSmartState.update { it.observe(topic.removePrefix(receivedPrefix), message.toString(), receivedAt, message.isRetained, sourceTopic = topic) }
                            _energySmartState.update { it.observe(topic.removePrefix(receivedPrefix), message.toString(), receivedAt, message.isRetained, topic) }
                            _climateSmartState.update { it.observe(topic.removePrefix(receivedPrefix), message.toString(), receivedAt, message.isRetained, topic) }
                            _predictiveReserveSmartState.update { it.observe(topic.removePrefix(receivedPrefix), message.toString(), receivedAt, message.isRetained, topic) }
                            _thermostatSmartState.update { it.observe(topic.removePrefix(receivedPrefix), message.toString(), receivedAt, message.isRetained, topic) }
                            _environmentSmartState.update { it.observe(topic.removePrefix(receivedPrefix), message.toString(), receivedAt, message.isRetained, topic) }
                            _heatingSmartState.update { it.observe(topic.removePrefix(receivedPrefix), message.toString(), receivedAt, message.isRetained, topic) }
                        }
                        handleIncomingMessage(topic ?: "", message?.toString() ?: "")
                    }
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })
            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {}
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    isConnecting = false
                    _isConnected.value = false
                    Log.e("MQTT", "Errore connessione: ${exception?.message}", exception)
                    addTrafficLog("ERRORE: ${exception?.message}")
                }
            })
        } catch (_: Exception) { 
            isConnecting = false
            Log.e("MQTT", "Eccezione in connect") 
        }
    }

    var digitalTwinPrefix: String = "zara/interface"
        private set

    fun setDigitalTwinPrefix(prefix: String) {
        val clean = prefix.trim().removeSuffix("/")
        if (clean.isNotEmpty() && digitalTwinPrefix != clean) {
            _aiSmartState.value = AiSmartState()
            _energySmartState.value = EnergySmartState()
            _climateSmartState.value = ClimateSmartState()
            _predictiveReserveSmartState.value = PredictiveReserveSmartState()
            _thermostatSmartState.value = ThermostatSmartState()
            _environmentSmartState.value = EnvironmentSmartState()
            _heatingSmartState.value = HeatingSmartState()
            digitalTwinPrefix = clean
            if (mqttClient?.isConnected == true) {
                subscribeToUnifiedTopics()
            }
        }
    }

    private fun subscribeToUnifiedTopics() {
        val p = digitalTwinPrefix.removeSuffix("/")
        val topics = arrayOf(
            "$p/semantic/v1/routed_commands/+/stat",
            "$p/semantic/v1/dispatched_commands/+/stat",
            "$p/semantic/v1/rm_receipts/+/stat",
            "$p/semantic/v1/publisher_inputs/+/stat",
            "$p/lights/#", "$p/pool/#", "$p/env/#",
            "$p/energy/#", "$p/heating/#", "$p/climate/#",
            "$p/ai/#", "$p/ai_climate/#", "$p/fireplace/#", "$p/ventilation/#",
            "$p/settings/#", "$p/garage/#", 
            "$p/stato_condizionatore/#",
            "$p/logica_controllo/#",
            "$p/predictive_reserve/#",
            "$p/irrigation/#",
            "$p/rules/list/stat"
        )
        mqttClient?.subscribe(topics, IntArray(topics.size) { 1 })
    }

    private fun subscribeToBuildIdentity() {
        if (_publishedBuild.value !is PublishedBuild.Received) _publishedBuild.value = PublishedBuild.Waiting
        try {
            mqttClient?.subscribe(BuildAlignment.TOPIC, 1)
        } catch (_: Exception) {}
        retainedWaitJob?.cancel()
        retainedWaitJob = scope.launch {
            delay(BuildAlignment.RETAINED_WAIT_MS)
            if (_publishedBuild.value == PublishedBuild.Waiting) _publishedBuild.value = PublishedBuild.Absent
        }
    }

    private fun handleIncomingMessage(topic: String, payload: String) {
        if (topic == BuildAlignment.TOPIC) {
            _publishedBuild.value = BuildAlignment.parse(payload)
            return
        }
        val p = digitalTwinPrefix.removeSuffix("/")
        if (!topic.startsWith(p)) return
        
        val relativeTopic = topic.removePrefix(p).removePrefix("/")
        val parts = relativeTopic.split("/")
        if (parts.isEmpty()) return

        val domain = parts[0]
        
        // --- PARSER UNIFICATO ---
        val cleanParts = if (parts.last() == "stat" || parts.last() == "cmd") parts.dropLast(1) else parts
        val device = if (cleanParts.size >= 2) cleanParts[1] else ""
        val property = if (cleanParts.size >= 3) cleanParts[2] else device

        // Filtro Log
        if (domain != "logica_controllo" && domain != "energy" && domain != "env" && domain != "stato_condizionatore") {
            addTrafficLog("IN: $topic")
        }
        
        val cleanPayload = payload.trim().lowercase()
        val isOn = cleanPayload == "true" || cleanPayload == "on" || cleanPayload == "1"
        val valueRaw = payload.replace(",", ".").toFloatOrNull()
        val rounded = if (valueRaw != null && valueRaw.isFinite()) (valueRaw * 10).roundToInt() / 10f else 0f

        when (domain) {
            "energy" -> handleEnergyDomain(device, property, rounded)
            "logica_controllo" -> handleLogicControlDomain(device, property, rounded, payload, isOn)
            "stato_condizionatore" -> handleAcStateDomain(property, payload, rounded)
            "lights", "pool" -> handleLightsDomain(device, isOn)
            "env" -> handleEnvDomain(device, property, rounded)
            "heating" -> handleHeatingDomain(device, property, rounded, payload)
            "climate" -> handleClimateDomain(device, property, rounded, isOn, payload)
            "ai" -> handleAiDomain(device, isOn, payload)
            "ai_climate" -> handleAiClimateDomain(property, payload)
            "predictive_reserve" -> handlePredictiveReserveDomain(property, isOn, payload)
            "fireplace" -> handleFireplaceDomain(device, property, payload, isOn)
            "ventilation" -> handleVentilationDomain(device, property, payload)
            "settings" -> handleSettingsDomain(device, isOn)
            "rules" -> if (relativeTopic == "rules/list/stat") RulesState.parse(payload)?.let { _rulesState.value = it }
            "irrigation" -> when (relativeTopic) {
                "irrigation/state/stat" -> IrrigationState.parse(payload)?.let { _irrigationState.value = it }
                "irrigation/last_command/stat" -> IrrigationCommandResult.parse(payload)?.let { _irrigationResult.value = it }
            }
        }
    }

    private fun handlePredictiveReserveDomain(prop: String, isOn: Boolean, payload: String) {
        when (prop) {
            "control_enabled" -> _aiSettings.update { it.copy(predictiveReserveEnabled = isOn) }
            "operating_mode" -> {
                val mode = payload.trim().uppercase().takeIf { it == "SHADOW" || it == "ACTIVE" }
                _aiSettings.update { it.copy(predictiveReserveMode = mode) }
            }
            "ready" -> {
                val clean = payload.trim().lowercase()
                val ready = when (clean) { "true", "1" -> true; "false", "0" -> false; else -> null }
                _aiSettings.update { it.copy(predictiveReserveReady = ready) }
            }
        }
    }

    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }

    private fun handleAiClimateDomain(prop: String, payload: String) {
        if (prop == "allarme") {
            val clean = payload.trim()
            if (clean.isEmpty() || clean.lowercase() == "off" || clean == "0" || clean == "false") {
                _aiSettings.update { it.copy(alarm = null) }
            } else {
                try {
                    val alarmData = json.decodeFromString<AiAlarm>(clean)
                    // Se lo stato è NORMALE, rimuoviamo l'allarme dall'interfaccia
                    if (alarmData.stato.uppercase() == "NORMALE") {
                        _aiSettings.update { it.copy(alarm = null) }
                    } else {
                        _aiSettings.update { it.copy(alarm = alarmData) }
                    }
                } catch (_: Exception) {
                    _aiSettings.update { it.copy(alarm = AiAlarm(stato = "ATTIVO", motivo = clean)) }
                }
            }
        }
    }

    private fun handleEnergyDomain(device: String, prop: String, value: Float) {
        val now = System.currentTimeMillis()
        _energyData.update { current ->
            when (device) {
                "solar" -> when (prop) {
                    "power" -> current.copy(solarPower = value, solarAtMs = now)
                    else -> current
                }
                "home" -> when (prop) {
                    "consumption" -> current.copy(homeConsumption = value, homeAtMs = now)
                    else -> current
                }
                "grid" -> when (prop) {
                    "power_raw" -> current.copy(gridPower = value, gridAtMs = now)
                    "import_kwh_oggi", "import_today" -> current.copy(gridImportKwhOggi = value, gridImportAtMs = now)
                    "import_kwh_ieri", "import_yesterday" -> current.copy(gridImportKwhIeri = value, gridImportAtMs = now)
                    else -> current
                }
                "battery" -> when (prop) {
                    "power_raw" -> current.copy(batteryPower = value, batteryAtMs = now)
                    "soc" -> current.copy(batterySoc = value, socAtMs = now)
                    else -> current
                }
                "puffer_acs" -> current.copy(pufferAcs = value)
                "puffer" -> when (prop) {
                    "acs" -> current.copy(pufferAcs = value)
                    "top_temperature" -> current.copy(pufferAlto = value)
                    "bottom_temperature" -> current.copy(pufferBasso = value)
                    else -> current
                }
                else -> current
            }
        }

        _aiManagedData.update { current ->
            val elec = current.metricheElettriche
            val updatedElec = when (device) {
                "solar" -> when (prop) {
                    "power" -> elec.copy(produzioneFvW = value)
                    "surplus" -> elec.copy(surplusW = value)
                    else -> elec
                }
                "home" -> when (prop) {
                    "consumption" -> elec.copy(consumoCasaW = value)
                    "historical_average_band" -> elec.copy(consumoMedioStoricoFasciaW = value)
                    else -> elec
                }
                "grid" -> when (prop) {
                    "power_raw" -> elec.copy(gridPowerW = value)
                    "import" -> elec.copy(gridImportW = value)
                    "export" -> elec.copy(gridExportW = value)
                    "import_kwh_oggi", "import_today" -> elec.copy(gridImportKwhOggi = value)
                    "import_kwh_ieri", "import_yesterday" -> elec.copy(gridImportKwhIeri = value)
                    else -> elec
                }
                "battery" -> when (prop) {
                    "power_raw" -> elec.copy(batteryPowerW = value)
                    "charge" -> elec.copy(batteryChargeW = value)
                    "discharge" -> elec.copy(batteryDischargeW = value)
                    "soc" -> elec.copy(powerwallSocPercent = value)
                    else -> elec
                }
                "ac" -> when (prop) {
                    "power" -> elec.copy(consumoAcW = value)
                    else -> elec
                }
                else -> elec
            }
            current.copy(metricheElettriche = updatedElec)
        }
    }

    private fun handleLogicControlDomain(device: String, prop: String, value: Float, raw: String, isOn: Boolean) {
        val cleanRaw = raw.trim()
        val parsedFloatOrNull = cleanRaw.replace(",", ".").toFloatOrNull()
        val parsedFloat = parsedFloatOrNull ?: value
        val parsedIntOrNull = parsedFloatOrNull?.toInt()
        val intVal = parsedIntOrNull ?: value.toInt()

        _aiManagedData.update { current ->
            if (device == "diagnostica_ac") {
                val currentDiag = current.logicaControllo.diagnosticaAc
                val updatedDiag = when (prop) {
                    "causa_transizione" -> currentDiag.copy(causaTransizione = cleanRaw.ifEmpty { null })
                    "timer_tipo" -> currentDiag.copy(timerTipo = cleanRaw.ifEmpty { null })
                    "timer_residuo_minuti" -> currentDiag.copy(timerResiduoMinuti = parsedIntOrNull)
                    "timer_durata_minuti" -> currentDiag.copy(timerDurataMinuti = parsedIntOrNull)
                    "deficit_tolleranza_minuti" -> currentDiag.copy(deficitTolleranzaMinuti = parsedIntOrNull)
                    "deficit_residuo_minuti" -> currentDiag.copy(deficitResiduoMinuti = parsedIntOrNull)
                    "pausa_notturna_residuo_minuti" -> currentDiag.copy(pausaNotturnaResiduoMinuti = parsedIntOrNull)
                    else -> currentDiag
                }
                current.copy(logicaControllo = current.logicaControllo.copy(diagnosticaAc = updatedDiag))
            } else if (prop == "stagione_attuale") {
                current.copy(
                    stagioneAttiva = raw,
                    logicaControllo = current.logicaControllo.copy(stagioneAttuale = raw)
                )
            } else {
                val updatedLogic = when (prop) {
                    "soc_minimo_applied" -> current.logicaControllo.copy(socMinimoApplied = parsedFloat)
                    "soglia_attivazione_applicata" -> current.logicaControllo.copy(sogliaAttivazioneApplicata = parsedFloat)
                    "soglia_spegnimento_comfort" -> current.logicaControllo.copy(sogliaSpegnimentoComfort = parsedFloat)
                    "ampiezza_isteresi_humidex" -> current.logicaControllo.copy(ampiezzaIsteresiHumidex = parsedFloat)
                    "night_dry_humidex_margin" -> current.logicaControllo.copy(nightDryHumidexMargin = parsedFloat)
                    "night_dry_humidex_threshold" -> current.logicaControllo.copy(nightDryHumidexThreshold = parsedFloat)
                    "soglia_temperatura_avvio_cooling_c" -> current.logicaControllo.copy(sogliaTemperaturaAvvioCoolingC = parsedFloatOrNull)
                    "soglia_temperatura_off_comfort_c" -> current.logicaControllo.copy(sogliaTemperaturaOffComfortC = parsedFloatOrNull)
                    "tempo_mancante_anticiclo_minuti" -> current.logicaControllo.copy(tempoMancanteAnticicloMinuti = intVal)
                    "kwh_stimati_in_batteria" -> current.logicaControllo.copy(kwhStimatiInBatteria = value)
                    "previsione_ricarica_batteria_percent" -> current.logicaControllo.copy(previsioneRicaricaBatteryPercent = intVal)
                    "previsione_solare_data" -> current.logicaControllo.copy(previsioneSolareData = raw)
                    "previsione_solare_domani_kwh" -> current.logicaControllo.copy(previsioneSolareDomaniKwh = value)
                    "blocco_emergenza_attivo" -> current.logicaControllo.copy(bloccoEmergenzaAttivo = isOn)
                    "cuscinetto_sicurezza_kwh" -> current.logicaControllo.copy(cuscinettoSicurezzaKwh = value)
                    "cuscinetto_richiesto_kwh" -> current.logicaControllo.copy(cuscinettoRichiestoKwh = value)
                    "stanza_rilevamento_vmc" -> current.logicaControllo.copy(stanzaRilevamentoVmc = raw)
                    "vmc_portata_stimata_m3h" -> {
                        val updated = current.logicaControllo.copy(vmcPortataStimataM3h = intVal)
                        // Sincronizziamo lo stato VMC per l'animazione
                        _hvacState.update { hvac ->
                            hvac.copy(vmc = hvac.vmc.copy(active = intVal > 0))
                        }
                        updated
                    }
                    else -> current.logicaControllo
                }
                current.copy(logicaControllo = updatedLogic)
            }
        }
    }

    private fun handleAcStateDomain(prop: String, raw: String, value: Float) {
        var updatedStato: StatoCondizionatore? = null
        _aiManagedData.update { current ->
            val updatedAc = when(prop) {
                "modalita_aria" -> current.statoCondizionatore.copy(modalitaAria = raw)
                "temperatura_impostata_c" -> current.statoCondizionatore.copy(temperaturaImpostataC = value)
                "motivo_logica" -> current.statoCondizionatore.copy(motivoLogica = raw)
                "stato_attuale" -> current.statoCondizionatore.copy(statoAttuale = raw)
                else -> current.statoCondizionatore
            }
            updatedStato = updatedAc
            current.copy(statoCondizionatore = updatedAc)
        }

        // Sincronizziamo hvacState per l'animazione degli impianti
        updatedStato?.let { acData ->
            _hvacState.update { hvac ->
                val statusUpper = acData.statoAttuale.uppercase()
                val isActive = acData.statoAttuale.isNotEmpty() &&
                        statusUpper != "OFF" &&
                        statusUpper != "DISATTIVATO" &&
                        statusUpper != "SPENTO"
                hvac.copy(ac = hvac.ac.copy(
                    active = isActive,
                    mode = acData.modalitaAria,
                    tempSet = acData.temperaturaImpostataC
                ))
            }
        }
    }

    private fun handleLightsDomain(device: String, isOn: Boolean) {
        val internalId = when(device) {
            "living", "sala" -> "sala"
            "reading" -> "tavolinolettura"
            "tv", "televisione" -> "televisione"
            "bedroom" -> "lucecamera"
            "water" -> "lucipiscina"
            "deck" -> "lucipedanapiscina"
            "pump" -> "pompapiscina"
            "skimmer" -> "skimmerpiscina"
            "laundry", "lavanderia" -> "lavanderia"
            "porch_entrance", "porch", "portico", "portico_ingresso" -> "portico_ingresso"
            "service_entrance", "ingressoservizio", "ingresso_servizio" -> "ingressoservizio"
            else -> device
        }
        _lightStates.update { current ->
            val newList = current.toMutableMap()
            newList[internalId] = isOn
            newList[device] = isOn
            newList
        }
    }

    private fun handleEnvDomain(device: String, prop: String, value: Float) {
        _environmentState.update { current ->
            when (device) {
                "living" -> current.copy(living = updateSensor(current.living, prop, value))
                "bedroom" -> current.copy(bedroom = updateSensor(current.bedroom, prop, value))
                "outdoor" -> current.copy(outdoor = updateSensor(current.outdoor, prop, value))
                else -> current
            }
        }

        _aiManagedData.update { current ->
            val env = current.metricheAmbientali
            val updatedEnv = when (device) {
                "living" -> when (prop) {
                    "temperature" -> env.copy(temperaturaC = value)
                    "humidex" -> env.copy(humidexLiving = value)
                    else -> env
                }
                "bedroom" -> when (prop) {
                    "temperature" -> env.copy(tempCameraMatrimoniale = value)
                    "humidex" -> env.copy(humidexBedroom = value)
                    else -> env
                }
                "solar_altitude" -> env.copy(altitudineSole = value)
                else -> env
            }
            current.copy(metricheAmbientali = updatedEnv)
        }
    }

    private fun updateSensor(current: SensorData, prop: String, value: Float): SensorData = when(prop) {
        "temperature" -> current.copy(temperature = value)
        "humidity" -> current.copy(humidity = value)
        "humidex" -> current.copy(humidex = value)
        else -> current
    }

    private fun handleHeatingDomain(device: String, prop: String, value: Float, raw: String) {
        when (device) {
            // HEATING-PROFILE on .20: profile (season), timer plan and the user's corrections.
            "profile" -> when (prop) {
                "profile" -> _heatingProfile.update { it.withProfile(raw) }
                "learning" -> _heatingProfile.update { it.withLearning(raw) }
            }
            "timer" -> if (prop == "plan") _heatingProfile.update { it.withPlan(raw) }
            "puffer" -> _energyData.update { current ->
                when (prop) {
                    "top_temperature" -> current.copy(pufferAlto = value)
                    "bottom_temperature" -> current.copy(pufferBasso = value)
                    else -> current
                }
            }
            "solar_thermal" -> _energyData.update { current ->
                when (prop) {
                    "collector_temperature" -> current.copy(solarCollectorTemp = value)
                    "pump_speed" -> current.copy(solarPumpSpeed = raw.toIntOrNull() ?: 0)
                    else -> current
                }
            }
            "gas_boiler" -> _hvacState.update { current ->
                when (prop) {
                    "flame" -> current.copy(boiler = current.boiler.copy(active = (raw == "true" || raw == "1")))
                    "modulation" -> current.copy(boiler = current.boiler.copy(modulation = raw.toIntOrNull() ?: 0))
                    else -> current
                }
            }
            "floor_pump" -> _hvacState.update { current ->
                when (prop) {
                    "enabled" -> current.copy(floorHeating = current.floorHeating.copy(enabled = (raw == "true" || raw == "1")))
                    "running" -> current.copy(floorHeating = current.floorHeating.copy(pumpActive = (raw == "true" || raw == "1")))
                    else -> current
                }
            }
        }
    }

    private fun handleAiDomain(device: String, isOn: Boolean, raw: String) {
        _aiSettings.update { current ->
            val intVal = raw.toIntOrNull() ?: 0
            when (device) {
                "system_enabled" -> current.copy(systemEnabled = isOn)
                "compressor_on_min" -> current.copy(compressorOnMin = intVal)
                "compressor_off_min" -> current.copy(compressorOffMin = intVal)
                "night_humidex_threshold" -> current.copy(nightHumidexThreshold = intVal)
                "night_vmc_max_speed" -> current.copy(nightVmcMaxSpeed = intVal)
                "deficit_tolerance_min" -> current.copy(deficitToleranceMin = intVal)
                "morning_ac_management" -> current.copy(morningAcManagement = isOn)
                "morning_humidex_emergency" -> current.copy(morningHumidexEmergency = intVal)
                else -> current
            }
        }
    }

    private fun handleFireplaceDomain(device: String, prop: String, raw: String, isOn: Boolean) {
        if (device != "main") return
        _hvacState.update { current ->
            val updatedPala = when (prop) {
                "power" -> current.palazzetti.copy(active = isOn)
                "level" -> current.palazzetti.copy(level = raw.toIntOrNull() ?: 1)
                "mode" -> current.palazzetti.copy(mode = raw)
                "start_time" -> current.palazzetti.copy(startTime = raw)
                "stop_time" -> current.palazzetti.copy(stopTime = raw)
                "auto_power" -> current.palazzetti.copy(autoPower = isOn)
                else -> current.palazzetti
            }
            current.copy(palazzetti = updatedPala)
        }
    }

    private fun handleClimateDomain(device: String, prop: String, value: Float, isOn: Boolean, payload: String) {
        if (device == "control" && prop == "humidex_reference") {
            _aiManagedData.update { current ->
                current.copy(metricheAmbientali = current.metricheAmbientali.copy(humidex = value))
            }
            return
        }

        if (device == "full_state") {
            if (prop == "timestamp") {
                val ts = payload.trim().toLongOrNull()
                if (ts != null) {
                    _aiManagedData.update { it.copy(timestamp = ts) }
                }
            } else if (prop == "data_ora_formattata") {
                if (payload.isNotBlank()) {
                    _aiManagedData.update { it.copy(dataOraFormattata = payload) }
                }
            }
            return
        }

        if (device != "thermostat_living" && device != "thermostat_bath") {
            return
        }

        _hvacState.update { current ->
            val thermostat = if (device == "thermostat_living") current.thermostatLiving else current.thermostatBath
            val updated = when (prop) {
                "current_temperature" -> thermostat.copy(currentTemp = value)
                "target_temperature" -> thermostat.copy(targetTemp = value)
                "min_temperature" -> thermostat.copy(minTemp = value)
                "max_temperature" -> thermostat.copy(maxTemp = value)
                "power" -> thermostat.copy(power = isOn)
                else -> thermostat
            }
            if (device == "thermostat_living") current.copy(thermostatLiving = updated) else current.copy(thermostatBath = updated)
        }
    }

    private fun handleVentilationDomain(device: String, prop: String, raw: String) {
        if (device != "vmc") return
        val floatVal = raw.replace(",", ".").toFloatOrNull() ?: 0f

        when (prop) {
            "speed" -> {
                val speed = raw.toIntOrNull() ?: 1
                _hvacState.update { it.copy(vmc = it.vmc.copy(speed = speed, active = true)) }
                _aiManagedData.update { current ->
                    current.copy(statoVmc = current.statoVmc.copy(velocitaAttuale = speed))
                }
            }
            "reason" -> {
                _aiManagedData.update { current ->
                    current.copy(statoVmc = current.statoVmc.copy(motivoLogica = raw.trim()))
                }
            }
            "outdoor_humidex" -> {
                _aiManagedData.update { current ->
                    current.copy(statoVmc = current.statoVmc.copy(humidexEsterno = floatVal))
                }
            }
            "outdoor_temperature" -> {
                _aiManagedData.update { current ->
                    current.copy(statoVmc = current.statoVmc.copy(temperaturaEsternaC = floatVal))
                }
            }
        }
    }

    private fun handleSettingsDomain(device: String, isOn: Boolean) {
        _domoticaSettings.update { current ->
            when (device) {
                "holiday_mode" -> current.copy(holidayMode = isOn)
                "eco_lights" -> current.copy(ecoLights = isOn)
                "pool_lights_auto" -> current.copy(poolLightsAuto = isOn)
                "porch_sensor" -> current.copy(porchSensor = isOn)
                "ac_auto" -> current.copy(acAuto = isOn)
                else -> current
            }
        }
    }

    fun toggleLight(lightId: String, currentState: Boolean) {
        val targetState = !currentState
        val cleanId = lightId.lowercase()
        val (domain, device) = when (cleanId) {
            "pompapiscina" -> "pool" to "pump"
            "skimmerpiscina" -> "pool" to "skimmer"
            "lucipiscina" -> "pool" to "water"
            "lucipedanapiscina" -> "pool" to "deck"
            "televisione" -> "lights" to "tv"
            "sala" -> "lights" to "living"
            "tavolinolettura" -> "lights" to "reading"
            "lavanderia", "laundry" -> "lights" to "laundry"
            "portico", "portico_ingresso", "porticoingresso", "porch_entrance" -> "lights" to "porch_entrance"
            "ingressoservizio", "ingresso_servizio", "service_entrance" -> "lights" to "service_entrance"
            else -> "lights" to cleanId
        }
        _lightStates.update { current ->
            val newList = current.toMutableMap()
            newList[cleanId] = targetState
            newList[device] = targetState
            newList
        }
        publish("zara/interface/$domain/$device/power/cmd", targetState.toString())
    }

    /** Closed command surface used by Smart: IDs come from the validated registry mapping. */
    fun setSmartLightState(lightId: String, state: Boolean): Boolean {
        val relativeStateTopic = AiSmartState.lightIdToTopic[lightId] ?: return false
        publish("zara/interface/${relativeStateTopic.removeSuffix("stat")}cmd", state.toString())
        return true
    }

    /** Only the closed thermostat/setpoint set validated by the backend and the answer parser. */
    fun setSmartThermostatTarget(thermostat: String, targetC: String): Boolean {
        if (thermostat !in ThermostatSmartState.THERMOSTATS || targetC !in ThermostatSmartState.TARGETS) return false
        publish("zara/interface/climate/$thermostat/target_temperature/cmd", targetC)
        return true
    }

    /** Fireplace settings from AI Smart: closed modes, HH:MM times, auto power; never the level. */
    fun setSmartFireplaceSettings(command: FireplaceSettingsCommand): Boolean {
        if (!command.isValid()) return false
        val p = "zara/interface/fireplace/main"
        command.mode?.let { publish("$p/mode/cmd", it) }
        command.startTime?.let { publish("$p/start_time/cmd", it) }
        command.stopTime?.let { publish("$p/stop_time/cmd", it) }
        command.autoPower?.let { publish("$p/auto_power/cmd", it.toString()) }
        return true
    }

    /** Manual on/off after the user's confirmation, only in "Manuale" (seen from .15 or set by the same confirmation). */
    fun setSmartFireplacePower(on: Boolean, manualInSameAnswer: Boolean = false): Boolean {
        if (!manualInSameAnswer && _heatingSmartState.value.fireplaceMode() != "Manuale") return false
        publish("zara/interface/fireplace/main/power/cmd", on.toString())
        return true
    }

    /** Power level from AI Smart, only in "Manuale" (seen from .15 or set by the same answer). */
    fun setSmartFireplaceLevel(level: Int, manualInSameAnswer: Boolean = false): Boolean {
        if (level !in 1..6 || !manualInSameAnswer && _heatingSmartState.value.fireplaceMode() != "Manuale") return false
        publish("zara/interface/fireplace/main/level/cmd", level.toString())
        return true
    }

    /** A rule created or deleted by AI Smart after the user's confirmation; RULES-ENGINE on .20 re-validates it. */
    fun publishRuleAction(action: RuleAction): Boolean {
        if (action.verb !in setOf("create", "delete") || action.payload.isBlank() || action.payload.length > 4000) return false
        publish("zara/interface/rules/${action.verb}/cmd", action.payload)
        return true
    }

    /** Heating profile chosen in the Clima sheet after "Conferma": auto, spento, ridotto or pieno. */
    fun setHeatingProfileMode(mode: String): Boolean {
        if (mode !in HeatingProfileState.MODES) return false
        publish("zara/interface/heating/profile/mode/cmd", mode)
        return true
    }

    /** Only the scenes accepted by the Node-RED Scene Dispatcher. */
    fun setSmartLightScene(scene: String): Boolean {
        if (scene !in AiSmartState.lightScenes) return false
        sendLightScene(scene)
        return true
    }

    fun sendLightScene(scene: String) {
        publish("zara/interface/lights/scene/cmd", scene)
    }

    fun updateGridImportKwhSummary(kwhOggi: Float, kwhIeri: Float) {
        _energyData.update { current ->
            current.copy(
                gridImportKwhOggi = kwhOggi,
                gridImportKwhIeri = kwhIeri,
                gridImportAtMs = System.currentTimeMillis()
            )
        }
        _aiManagedData.update { current ->
            val elec = current.metricheElettriche
            current.copy(
                metricheElettriche = elec.copy(
                    gridImportKwhOggi = kwhOggi,
                    gridImportKwhIeri = kwhIeri
                )
            )
        }
    }

    /**
     * Irrigation commands are never queued: a start sent minutes later, when the connection
     * comes back, would water at an unexpected time. Returns false when not connected.
     */
    fun sendIrrigationCommand(command: Pair<String, String>?): Boolean {
        if (command == null || mqttClient?.isConnected != true) return false
        publish(command.first, command.second)
        return true
    }

    fun publish(topic: String, payload: String, retained: Boolean = false) {
        val p = digitalTwinPrefix.removeSuffix("/")
        val actualTopic = if (topic.startsWith("zara/interface")) {
            topic.replace("zara/interface", p)
        } else {
            topic
        }

        val client = mqttClient
        if (client == null || !client.isConnected) {
            messageQueue.add(MqttQueuedMessage(actualTopic, payload, retained))
            return
        }
        try {
            client.publish(actualTopic, MqttMessage(payload.toByteArray()).apply { qos = 1; isRetained = retained })
        } catch (_: Exception) { messageQueue.add(MqttQueuedMessage(actualTopic, payload, retained)) }
    }

    private fun processMessageQueue() {
        while (messageQueue.isNotEmpty()) {
            val qm = messageQueue.poll()
            qm?.let { publish(it.topic, it.payload, it.retained) }
        }
    }

    fun disconnect() {
        try { mqttClient?.disconnect(); mqttClient = null } catch (_: Exception) {}
    }
}

data class MqttQueuedMessage(val topic: String, val payload: String, val retained: Boolean)
