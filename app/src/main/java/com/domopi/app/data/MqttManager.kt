package com.domopi.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val batterySoc: Float = 0f
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
    val morningHumidexEmergency: Int = 33
)

class MqttManager(private val context: Context) {
    private var mqttClient: MqttAsyncClient? = null
    private val json = Json { ignoreUnknownKeys = true }
    private val messageQueue = ConcurrentLinkedQueue<MqttQueuedMessage>()

    private val VALID_LIGHT_IDS = listOf(
        "sala", "libreria", "cucina", "televisione", "tavolinolettura", 
        "lampadahifi", "lavanderia", "ingressoservizio", "portico", "esterno",
        "lucecamera", "prolunga", "pompapiscina", "skimmerpiscina", "lucipiscina", "lucipedanapiscina"
    )

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

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _trafficLog = MutableStateFlow<List<String>>(emptyList())
    val trafficLog: StateFlow<List<String>> = _trafficLog

    private fun addTrafficLog(message: String) {
        val timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        val current = _trafficLog.value.toMutableList()
        current.add(0, "[$timestamp] $message")
        if (current.size > 50) current.removeAt(current.size - 1)
        _trafficLog.value = current
    }

    fun connect(brokerUrl: String, user: String? = null, pass: String? = null) {
        if (mqttClient?.isConnected == true) return
        try {
            val clientId = "ZaraDashV2_" + UUID.randomUUID().toString().substring(0, 8)
            mqttClient = MqttAsyncClient(brokerUrl, clientId, MemoryPersistence())
            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
                connectionTimeout = 15
                keepAliveInterval = 60
                user?.let { userName = it }
                pass?.let { password = it.toCharArray() }
            }
            mqttClient?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    _isConnected.value = true
                    subscribeToUnifiedTopics()
                    processMessageQueue()
                }
                override fun connectionLost(cause: Throwable?) { _isConnected.value = false }
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    handleIncomingMessage(topic ?: "", message?.toString() ?: "")
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })
            mqttClient?.connect(options)
        } catch (e: Exception) { Log.e("MQTT", "Connect error", e) }
    }

    private fun subscribeToUnifiedTopics() {
        val topics = arrayOf("zara/interface/#")
        mqttClient?.subscribe(topics, IntArray(topics.size) { 1 })
    }

    private fun handleIncomingMessage(topic: String, payload: String) {
        addTrafficLog("IN: $topic")
        val cleanPayload = payload.trim().lowercase()
        val isStatusOn = cleanPayload == "true" || cleanPayload == "on" || cleanPayload == "1" || cleanPayload.contains("\"state\":\"on\"")
        val value = payload.toFloatOrNull() ?: 0f
        val roundedValue = (value * 10).roundToInt() / 10f

        when {
            // 1. Digital Twin Status (Priorità Massima)
            topic.startsWith("zara/interface/") -> {
                val parts = topic.split("/")
                if (parts.size >= 5 && parts.last() == "stat") {
                    val domain = parts[2]
                    val deviceName = parts[3]
                    // Property is the second to last part (e.g. zara/interface/lights/living/power/stat -> power)
                    // If 5 parts (zara/interface/climate/ai_enabling/stat), property is "ai_enabling" or we use a more direct mapping
                    val property = parts[parts.size - 2]

                    when (domain) {
                        "lights", "pool" -> {
                            val internalId = when(deviceName) {
                                "living" -> "sala"
                                "reading" -> "tavolinolettura"
                                "tv" -> "televisione"
                                "bedroom" -> "lucecamera"
                                "water" -> "lucipiscina"
                                "deck" -> "lucipedanapiscina"
                                "pump" -> "pompapiscina"
                                "skimmer" -> "skimmerpiscina"
                                "hifi" -> "lampadahifi"
                                else -> deviceName.lowercase()
                            }
                            updateLightState(internalId, isStatusOn)
                        }
                        "env" -> {
                            // Sincronizzazione rigorosa sui topic ufficiali Digital Twin Env
                            _environmentState.value = when (deviceName) {
                                "living" -> _environmentState.value.copy(
                                    living = when(property) {
                                        "temperature" -> _environmentState.value.living.copy(temperature = roundedValue)
                                        "humidity" -> _environmentState.value.living.copy(humidity = roundedValue)
                                        "humidex" -> _environmentState.value.living.copy(humidex = roundedValue)
                                        else -> _environmentState.value.living
                                    }
                                )
                                "bedroom" -> _environmentState.value.copy(
                                    bedroom = when(property) {
                                        "temperature" -> _environmentState.value.bedroom.copy(temperature = roundedValue)
                                        "humidity" -> _environmentState.value.bedroom.copy(humidity = roundedValue)
                                        "humidex" -> _environmentState.value.bedroom.copy(humidex = roundedValue)
                                        else -> _environmentState.value.bedroom
                                    }
                                )
                                "outdoor" -> _environmentState.value.copy(
                                    outdoor = when(property) {
                                        "temperature" -> _environmentState.value.outdoor.copy(temperature = roundedValue)
                                        "humidity" -> _environmentState.value.outdoor.copy(humidity = roundedValue)
                                        else -> _environmentState.value.outdoor
                                    }
                                )
                                else -> _environmentState.value
                            }
                        }
                        "energy" -> {
                            _energyData.value = when (deviceName) {
                                "solar" -> _energyData.value.copy(solarPower = roundedValue)
                                "home" -> _energyData.value.copy(homeConsumption = roundedValue)
                                "grid" -> _energyData.value.copy(gridPower = roundedValue)
                                "battery" -> if (property == "soc") _energyData.value.copy(batterySoc = roundedValue) else _energyData.value.copy(batteryPower = roundedValue)
                                else -> _energyData.value
                            }
                        }
                        "ai" -> {
                            val intVal = payload.toIntOrNull() ?: 0
                            val floatVal = payload.toFloatOrNull() ?: 0f
                            
                            // 1. Aggiornamento Impostazioni (aiSettings)
                            _aiSettings.value = when (deviceName) {
                                "system_enabled" -> _aiSettings.value.copy(systemEnabled = isStatusOn)
                                "compressor_on_min" -> _aiSettings.value.copy(compressorOnMin = intVal)
                                "compressor_off_min" -> _aiSettings.value.copy(compressorOffMin = intVal)
                                "night_humidex_threshold" -> _aiSettings.value.copy(nightHumidexThreshold = intVal)
                                "night_vmc_max_speed" -> _aiSettings.value.copy(nightVmcMaxSpeed = intVal)
                                "deficit_tolerance_min" -> _aiSettings.value.copy(deficitToleranceMin = intVal)
                                "morning_ac_management" -> _aiSettings.value.copy(morningAcManagement = isStatusOn)
                                "morning_humidex_emergency" -> _aiSettings.value.copy(morningHumidexEmergency = intVal)
                                else -> _aiSettings.value
                            }
                            
                            // 2. Aggiornamento Stato Operativo (aiManagedData)
                            // Mappiamo i singoli topic di stato puliti verso la struttura dati della UI
                            _aiManagedData.value = when (deviceName) {
                                "op_state" -> _aiManagedData.value.copy(stato_condizionatore = _aiManagedData.value.stato_condizionatore.copy(stato_attuale = payload))
                                "op_reason" -> _aiManagedData.value.copy(stato_condizionatore = _aiManagedData.value.stato_condizionatore.copy(motivo_logica = payload))
                                "op_mode" -> _aiManagedData.value.copy(stato_condizionatore = _aiManagedData.value.stato_condizionatore.copy(modalita_aria = payload))
                                "vmc_speed" -> _aiManagedData.value.copy(stato_vmc = _aiManagedData.value.stato_vmc.copy(velocita_attuale = intVal))
                                "solar_forecast" -> _aiManagedData.value.copy(logica_controllo = _aiManagedData.value.logica_controllo.copy(previsione_solare_domani_kwh = floatVal))
                                "battery_forecast" -> _aiManagedData.value.copy(logica_controllo = _aiManagedData.value.logica_controllo.copy(previsione_ricarica_battery_percent = intVal))
                                "battery_kwh" -> _aiManagedData.value.copy(logica_controllo = _aiManagedData.value.logica_controllo.copy(kwh_stimati_in_batteria = floatVal))
                                "active_season" -> _aiManagedData.value.copy(stagione_attiva = payload)
                                else -> _aiManagedData.value
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateLightState(id: String, state: Boolean) {
        val cleanId = id.lowercase()
        if (cleanId !in VALID_LIGHT_IDS) return
        val current = _lightStates.value.toMutableMap()
        current[cleanId] = state
        _lightStates.value = current
    }

    fun toggleLight(lightId: String, currentState: Boolean) {
        val nextState = !currentState
        val stringState = if (nextState) "true" else "false"
        val cleanId = lightId.lowercase()
        
        val (domain, device) = when (cleanId) {
            "sala" -> "lights" to "living"
            "libreria" -> "lights" to "libreria"
            "televisione" -> "lights" to "tv"
            "tavolinolettura" -> "lights" to "reading"
            "lucecamera" -> "lights" to "bedroom"
            "prolunga" -> "lights" to "prolunga"
            "lampadahifi" -> "lights" to "hifi"
            "pompapiscina" -> "pool" to "pump"
            "skimmerpiscina" -> "pool" to "skimmer"
            "lucipiscina" -> "pool" to "water"
            "lucipedanapiscina" -> "pool" to "deck"
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
        } catch (e: Exception) { messageQueue.add(MqttQueuedMessage(topic, payload, retained)) }
    }

    private fun processMessageQueue() {
        while (messageQueue.isNotEmpty()) {
            val qm = messageQueue.poll()
            qm?.let { publish(it.topic, it.payload, it.retained) }
        }
    }

    fun disconnect() {
        try { mqttClient?.disconnect(); mqttClient = null } catch (e: Exception) {}
    }
}

data class MqttQueuedMessage(val topic: String, val payload: String, val retained: Boolean)
