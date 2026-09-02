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
import java.util.concurrent.atomic.AtomicInteger
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
)

data class SensorData(
    val temperature: Float = 0f,
    val humidity: Float = 0f,
    val humidex: Float = 0f
)

data class EnvironmentState(
    val living: SensorData = SensorData(),
    val bedroom: SensorData = SensorData(),
    val outdoor: SensorData = SensorData()
)

data class AiSettings(
    val systemEnabled: Boolean = true,
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

class MqttManager {
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

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _trafficLog = MutableStateFlow<List<String>>(emptyList())
    val trafficLog: StateFlow<List<String>> = _trafficLog

    private val _messageRate = MutableStateFlow(0)
    val messageRate: StateFlow<Int> = _messageRate

    private val messageCounter = AtomicInteger(0)
    private var lastRateCalculationTime = System.currentTimeMillis()
    private var lastLogUpdateTime = 0L

    private fun updateRateCounter() {
        val now = System.currentTimeMillis()
        messageCounter.incrementAndGet()
        if (now - lastRateCalculationTime >= 1000) {
            _messageRate.value = messageCounter.getAndSet(0)
            lastRateCalculationTime = now
        }
    }

    private fun addTrafficLog(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastLogUpdateTime < 1000) return 
        lastLogUpdateTime = now

        val timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        _trafficLog.update { current ->
            val newList = current.toMutableList()
            newList.add(0, "[$timestamp] $message")
            if (newList.size > 20) newList.removeAt(newList.size - 1)
            newList
        }
    }

    private var currentBrokerUrl: String? = null
    private var isConnecting = false

    fun connect(brokerUrl: String, user: String? = null, pass: String? = null) {
        if (mqttClient?.isConnected == true && currentBrokerUrl == brokerUrl) return
        
        if (isConnecting && currentBrokerUrl == brokerUrl) {
            Log.d("MQTT", "Connessione già in corso per $brokerUrl")
            return
        }

        Log.d("MQTT", "Tentativo di connessione a $brokerUrl")
        currentBrokerUrl = brokerUrl
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
                isCleanSession = false // Mantieni sessione per evitare riconnessioni costose
                connectionTimeout = 15
                keepAliveInterval = 60
                if (!user.isNullOrEmpty()) userName = user
                if (!pass.isNullOrEmpty()) password = pass.toCharArray()
            }
            mqttClient?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.i("MQTT", "Connesso a $serverURI")
                    addTrafficLog("CONNESSO: $serverURI")
                    isConnecting = false
                    _isConnected.value = true
                    subscribeToUnifiedTopics()
                    processMessageQueue()
                }
                override fun connectionLost(cause: Throwable?) { 
                    isConnecting = false
                    _isConnected.value = false 
                    Log.e("MQTT", "Connessione perduta: ${cause?.message}")
                    addTrafficLog("DISCONNESSO: ${cause?.message}")
                }
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    scope.launch { handleIncomingMessage(topic ?: "", message?.toString() ?: "") }
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

    private fun subscribeToUnifiedTopics() {
        val topics = arrayOf(
            "zara/interface/lights/#", "zara/interface/pool/#", "zara/interface/env/#",
            "zara/interface/energy/#", "zara/interface/heating/#", "zara/interface/climate/#",
            "zara/interface/ai/#", "zara/interface/ai_climate/#", "zara/interface/fireplace/#", "zara/interface/ventilation/#",
            "zara/interface/settings/#", "zara/interface/garage/#", 
            "zara/interface/stato_condizionatore/#",
            "zara/interface/logica_controllo/#"
        )
        mqttClient?.subscribe(topics, IntArray(topics.size) { 1 })
    }

    private fun handleIncomingMessage(topic: String, payload: String) {
        if (!topic.startsWith("zara/interface/")) return
        updateRateCounter()
        
        val parts = topic.split("/")
        if (parts.size < 4) return

        val domain = parts[2]
        
        // --- NUOVO PARSER UNIFICATO ---
        val cleanParts = if (parts.last() == "stat" || parts.last() == "cmd") parts.dropLast(1) else parts
        val device = if (cleanParts.size >= 4) cleanParts[3] else ""
        val property = if (cleanParts.size >= 5) cleanParts[4] else device

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
            "logica_controllo" -> handleLogicControlDomain(property, rounded, payload, isOn)
            "stato_condizionatore" -> handleAcStateDomain(property, payload, rounded)
            "lights", "pool" -> handleLightsDomain(device, isOn)
            "env" -> handleEnvDomain(device, property, rounded)
            "heating" -> handleHeatingDomain(device, property, rounded, payload)
            "climate" -> handleClimateDomain(device, property, rounded, isOn)
            "ai" -> handleAiDomain(device, isOn, payload)
            "ai_climate" -> handleAiClimateDomain(property, payload)
            "fireplace" -> handleFireplaceDomain(device, property, payload, isOn)
            "ventilation" -> handleVentilationDomain(device, property, payload)
            "settings" -> handleSettingsDomain(device, isOn)
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
        _energyData.update { current ->
            when (device) {
                "solar" -> current.copy(solarPower = value)
                "home" -> current.copy(homeConsumption = value)
                "grid" -> current.copy(gridPower = value)
                "battery" -> if (prop == "soc") current.copy(batterySoc = value) else current.copy(batteryPower = value)
                "puffer_acs" -> current.copy(pufferAcs = value)
                "puffer" -> when(prop) {
                    "acs" -> current.copy(pufferAcs = value)
                    "top_temperature" -> current.copy(pufferAlto = value)
                    "bottom_temperature" -> current.copy(pufferBasso = value)
                    else -> current
                }
                else -> current
            }
        }
    }

    private fun handleLogicControlDomain(prop: String, value: Float, raw: String, isOn: Boolean) {
        val floatVal = raw.toFloatOrNull() ?: 0f
        val intVal = floatVal.toInt()
        _aiManagedData.update { current ->
            val updatedLogic = when(prop) {
                "soc_minimo_applied" -> current.logica_controllo.copy(soc_minimo_applied = value)
                "soglia_attivazione_applicata" -> current.logica_controllo.copy(soglia_attivazione_applicata = value)
                "tempo_mancante_anticiclo_minuti" -> current.logica_controllo.copy(tempo_mancante_anticiclo_minuti = intVal)
                "kwh_stimati_in_batteria" -> current.logica_controllo.copy(kwh_stimati_in_batteria = value)
                "previsione_ricarica_batteria_percent" -> current.logica_controllo.copy(previsione_ricarica_battery_percent = intVal)
                "previsione_solare_data" -> current.logica_controllo.copy(previsione_solare_data = raw)
                "previsione_solare_domani_kwh" -> current.logica_controllo.copy(previsione_solare_domani_kwh = value)
                "blocco_emergenza_attivo" -> current.logica_controllo.copy(blocco_emergenza_attivo = isOn)
                "cuscinetto_sicurezza_kwh" -> current.logica_controllo.copy(cuscinetto_sicurezza_kwh = value)
                "cuscinetto_richiesto_kwh" -> current.logica_controllo.copy(cuscinetto_richiesto_kwh = value)
                "stagione_attuale" -> current.logica_controllo.copy(stagione_attuale = raw)
                "stanza_rilevamento_vmc" -> current.logica_controllo.copy(stanza_rilevamento_vmc = raw)
                "vmc_portata_stimata_m3h" -> {
                    val updated = current.logica_controllo.copy(vmc_portata_stimata_m3h = intVal)
                    // Sincronizziamo lo stato VMC per l'animazione
                    _hvacState.update { hvac ->
                        hvac.copy(vmc = hvac.vmc.copy(active = intVal > 0))
                    }
                    updated
                }
                else -> current.logica_controllo
            }
            current.copy(logica_controllo = updatedLogic)
        }
    }

    private fun handleAcStateDomain(prop: String, raw: String, value: Float) {
        var updatedStato: StatoCondizionatore? = null
        _aiManagedData.update { current ->
            val updatedAc = when(prop) {
                "modalita_aria" -> current.stato_condizionatore.copy(modalita_aria = raw)
                "temperatura_impostata_c" -> current.stato_condizionatore.copy(temperatura_impostata_c = value)
                "motivo_logica" -> current.stato_condizionatore.copy(motivo_logica = raw)
                "stato_attuale" -> current.stato_condizionatore.copy(stato_attuale = raw)
                else -> current.stato_condizionatore
            }
            updatedStato = updatedAc
            current.copy(stato_condizionatore = updatedAc)
        }

        // Sincronizziamo hvacState per l'animazione degli impianti
        updatedStato?.let { acData ->
            _hvacState.update { hvac ->
                val statusUpper = acData.stato_attuale.uppercase()
                val isActive = acData.stato_attuale.isNotEmpty() &&
                        statusUpper != "OFF" &&
                        statusUpper != "DISATTIVATO" &&
                        statusUpper != "SPENTO"
                hvac.copy(ac = hvac.ac.copy(
                    active = isActive,
                    mode = acData.modalita_aria,
                    tempSet = acData.temperatura_impostata_c
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
            "hifi" -> "lampadahifi"
            else -> device
        }
        _lightStates.update { current ->
            val newList = current.toMutableMap()
            newList[internalId] = isOn
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
        if (device == "living") {
            _aiManagedData.update { current ->
                when(prop) {
                    "temperature" -> current.copy(metriche_ambientali = current.metriche_ambientali.copy(temperatura_c = value))
                    "humidex" -> current.copy(metriche_ambientali = current.metriche_ambientali.copy(humidex = value, humidex_living = value))
                    else -> current
                }
            }
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

    private fun handleClimateDomain(device: String, prop: String, value: Float, isOn: Boolean) {
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
        if (device == "vmc" && prop == "speed") {
            val speed = raw.toIntOrNull() ?: 1
            _hvacState.update { it.copy(vmc = it.vmc.copy(speed = speed, active = true)) }
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
        val stringState = if (!currentState) "true" else "false"
        val cleanId = lightId.lowercase()
        val (domain, device) = when (cleanId) {
            "pompapiscina" -> "pool" to "pump"
            "skimmerpiscina" -> "pool" to "skimmer"
            "lucipiscina" -> "pool" to "water"
            "lucipedanapiscina" -> "pool" to "deck"
            "lucecamera" -> "lights" to "bedroom"
            "lampadahifi" -> "lights" to "hifi"
            "televisione" -> "lights" to "tv"
            "sala" -> "lights" to "living"
            "tavolinolettura" -> "lights" to "reading"
            else -> "lights" to cleanId
        }
        publish("zara/interface/$domain/$device/power/cmd", stringState)
    }

    fun sendLightScene(scene: String) {
        val payload = if (scene == "TV Mode" || scene == "Sleep Mode") "on" else scene
        publish("zara/interface/lights/scene/cmd", payload)
    }

    fun publish(topic: String, payload: String, retained: Boolean = false) {
        val client = mqttClient
        if (client == null || !client.isConnected) {
            messageQueue.add(MqttQueuedMessage(topic, payload, retained))
            return
        }
        try {
            client.publish(topic, MqttMessage(payload.toByteArray()).apply { qos = 1; isRetained = retained })
        } catch (_: Exception) { messageQueue.add(MqttQueuedMessage(topic, payload, retained)) }
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
