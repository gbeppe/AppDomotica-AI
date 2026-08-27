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
    val minOnTime: String = "15",
    val minOffTime: String = "15",
    val targetHumidex: Float = 29f,
    val vmcMaxNight: Int = 2,
    val deficitTolerance: String = "10",
    val graceModeSolar: Boolean = false,
    val emergencyHumidex: String = "33"
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
        val topics = arrayOf("zara/interface/#", "zara/android/domotica/#", "TeslaPowerwall/#", "emon/#")
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
                            // Sincronizzazione rigorosa sui 9 topic ufficiali Digital Twin Env
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
                        "climate" -> {
                            if (deviceName == "ai_enabling") {
                                _aiSettings.value = _aiSettings.value.copy(systemEnabled = isStatusOn)
                            }
                        }
                    }
                }
            }

            // 2. Clima AI (Fornisce Humidex e stati aggregati di backup)
            topic == "zara/android/domotica/casa/clima/stato_completo" -> {
                try {
                    val data: AiManagedData = json.decodeFromString(payload)
                    _aiManagedData.value = data
                    
                    // Usiamo lo stato completo per sincronizzare i valori se i topic /env/ non sono ancora arrivati
                    _environmentState.value = _environmentState.value.copy(
                        living = _environmentState.value.living.copy(
                            temperature = data.metriche_ambientali.temperatura_c,
                            humidex = data.metriche_ambientali.humidex_living
                        ),
                        bedroom = _environmentState.value.bedroom.copy(
                            temperature = data.metriche_ambientali.temp_cameraMatrimoniale,
                            humidex = data.metriche_ambientali.humidex_bedroom
                        ),
                        outdoor = _environmentState.value.outdoor.copy(
                            temperature = data.stato_vmc.temperatura_esterna_c
                        )
                    )
                } catch (e: Exception) {
                    Log.e("MQTT", "JSON Parse Error", e)
                }
            }

            // 3. Mirroring Bridge (Fallback per sensori non ancora in DT)
            topic.startsWith("zara/android/domotica/env/") -> {
                val sensor = topic.substringAfterLast("/")
                _environmentState.value = when(sensor) {
                    "humLiving" -> _environmentState.value.copy(living = _environmentState.value.living.copy(humidity = roundedValue))
                    "humBedroom" -> _environmentState.value.copy(bedroom = _environmentState.value.bedroom.copy(humidity = roundedValue))
                    "humOutdoor" -> _environmentState.value.copy(outdoor = _environmentState.value.outdoor.copy(humidity = roundedValue))
                    else -> _environmentState.value
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
        val bridgeState = if (nextState) "ON" else "OFF"
        val cleanId = lightId.lowercase()
        
        publish("zara/android/domotica/light/$lightId/set", bridgeState)
        
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
        publish("zara/android/domotica/scene/set", payload)
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
