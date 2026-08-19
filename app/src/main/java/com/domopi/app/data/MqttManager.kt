package com.domopi.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID

data class EnergyData(
    val solarPower: Float = 0f,
    val homeConsumption: Float = 0f,
    val gridPower: Float = 0f,
    val batteryPower: Float = 0f, // Watts (positive = discharging, negative = charging)
    val batterySoc: Float = 0f     // Percentage
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
    private val clients = mutableMapOf<String, MqttAsyncClient>()
    private val json = Json { ignoreUnknownKeys = true }
    
    private val _lightStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val lightStates: StateFlow<Map<String, Boolean>> = _lightStates

    private val _energyData = MutableStateFlow(EnergyData())
    val energyData: StateFlow<EnergyData> = _energyData

    private val _sensorData = MutableStateFlow(SensorData())
    val sensorData: StateFlow<SensorData> = _sensorData

    private val _environmentState = MutableStateFlow(EnvironmentState())
    val environmentState: StateFlow<EnvironmentState> = _environmentState

    private val _aiManagedData = MutableStateFlow(AiManagedData())
    val aiManagedData: StateFlow<AiManagedData> = _aiManagedData

    private val _aiSettings = MutableStateFlow(AiSettings())
    val aiSettings: StateFlow<AiSettings> = _aiSettings

    private val _isConnected = MutableStateFlow(mutableMapOf<String, Boolean>())
    val isConnected: StateFlow<Map<String, Boolean>> = _isConnected

    private val _trafficLog = MutableStateFlow<List<String>>(emptyList())
    val trafficLog: StateFlow<List<String>> = _trafficLog

    private fun addTrafficLog(message: String) {
        val current = _trafficLog.value.toMutableList()
        current.add(0, "${java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))}: $message")
        if (current.size > 50) current.removeAt(current.size - 1)
        _trafficLog.value = current
    }

    fun connect(brokerUrl: String, user: String? = null, pass: String? = null, identifier: String) {
        if (clients[identifier]?.isConnected == true) {
            Log.d("MQTT", "Client $identifier already connected")
            return
        }

        try {
            val clientId = "DomoPi_${identifier}_" + UUID.randomUUID().toString().substring(0, 8)
            val client = MqttAsyncClient(brokerUrl, clientId, MemoryPersistence())
            clients[identifier] = client
            
            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 60
                user?.let { userName = it }
                pass?.let { password = it.toCharArray() }
            }

            client.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.d("MQTT", "[$identifier] Connected. Reconnect: $reconnect")
                    val currentMap = _isConnected.value.toMutableMap()
                    currentMap[identifier] = true
                    _isConnected.value = currentMap
                    subscribeToTopics(identifier)
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.w("MQTT", "[$identifier] Connection lost: ${cause?.message}")
                    val currentMap = _isConnected.value.toMutableMap()
                    currentMap[identifier] = false
                    _isConnected.value = currentMap
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    topic?.let { t ->
                        val payload = message?.toString() ?: ""
                        handleMessage(identifier, t, payload)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            client.connect(options)

        } catch (e: Exception) {
            Log.e("MQTT", "Error connecting $identifier", e)
        }
    }

    private fun subscribeToTopics(identifier: String) {
        val client = clients[identifier] ?: return
        if (!client.isConnected) return
        
        try {
            when (identifier) {
                "domopi" -> {
                    client.subscribe(arrayOf(
                        "zara/android/domotica/#",
                        "zara/interface/#",
                        "stat/+/POWER",
                        "casa/clima/cmnd/+",
                        "casa/clima/stat/+",
                        "casa/stanza1/humidex",
                        "casa/cameraMatrimoniale/humidex",
                        "zara/domotics/lights/+",
                        "shellies/+/relay/0",
                        "zigbee2mqtt/+"
                    ), intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1))
                }
                "emonpi" -> {
                    client.subscribe(arrayOf(
                        "emon/+/+",
                        "TeslaPowerwall/+",
                        "emon/weather/+"
                    ), intArrayOf(1, 1, 1))
                }
            }
        } catch (e: Exception) {
            Log.e("MQTT", "Subscription failed for $identifier", e)
        }
    }

    private fun handleMessage(identifier: String, topic: String, payload: String) {
        Log.v("MQTT", "[$identifier] Data: $topic -> $payload")
        addTrafficLog("[$identifier] IN: $topic")
        
        when (identifier) {
            "domopi" -> handleDomoPiMessage(topic, payload)
            "emonpi" -> handleEmonPiMessage(topic, payload)
        }
    }

    private fun handleDomoPiMessage(topic: String, payload: String) {
        // 1. Unified App Topics (via Bridge Router)
        if (topic.startsWith("zara/android/domotica/light/")) {
            val device = topic.split("/").getOrNull(4)
            device?.let { id ->
                val state = payload.equals("ON", ignoreCase = true) || payload == "1" || payload.equals("on", ignoreCase = true) || payload.equals("true", ignoreCase = true)
                _lightStates.value = _lightStates.value.toMutableMap().apply { put(id, state) }
            }
            return
        }

        // 2. Digital Twin App Status
        if (topic.startsWith("zara/interface/")) {
            val parts = topic.split("/")
            if (parts.size >= 6 && parts[5] == "stat") {
                val deviceId = when (parts.getOrNull(3)) {
                    "living" -> "sala"
                    "libreria" -> "libreria"
                    "hifi" -> "lampadaHifi"
                    else -> null
                }
                deviceId?.let { id ->
                    val state = payload.equals("true", ignoreCase = true) || payload.equals("on", ignoreCase = true) || payload == "1"
                    _lightStates.value = _lightStates.value.toMutableMap().apply { put(id, state) }
                }
            }
            return
        }

        // 3. Direct Legacy Topics (Direct from devices or dashboard links)
        when {
            topic == "zara/domotics/lights/livinglamp" -> {
                val state = payload.equals("on", ignoreCase = true)
                _lightStates.value = _lightStates.value.toMutableMap().apply { put("sala", state) }
            }
            topic == "zara/domotics/lights/shelflamp" -> {
                val state = payload.equals("on", ignoreCase = true)
                _lightStates.value = _lightStates.value.toMutableMap().apply { put("libreria", state) }
            }
            topic == "zara/domotics/lights/tvlamp" -> {
                val state = payload.equals("on", ignoreCase = true)
                _lightStates.value = _lightStates.value.toMutableMap().apply { put("televisione", state) }
            }
            topic == "zara/domotics/lights/readinglight" -> {
                val state = payload.equals("on", ignoreCase = true)
                _lightStates.value = _lightStates.value.toMutableMap().apply { put("tavolinoLettura", state) }
            }
            topic == "stat/tasmota_86AD14/POWER" -> {
                val state = payload.equals("ON", ignoreCase = true) || payload == "1"
                _lightStates.value = _lightStates.value.toMutableMap().apply { put("lampadaHifi", state) }
            }
            topic.startsWith("shellies/") && topic.endsWith("/relay/0") -> {
                val state = payload.equals("on", ignoreCase = true)
                val deviceId = when {
                    topic.contains("B8C284") -> "lavanderia"
                    topic.contains("B92AF0") -> "ingressoServizio"
                    topic.contains("771284") -> "portico"
                    topic.contains("esterno") -> "esterno"
                    else -> null
                }
                deviceId?.let { id ->
                    _lightStates.value = _lightStates.value.toMutableMap().apply { put(id, state) }
                }
            }
            topic.startsWith("zigbee2mqtt/") -> {
                val deviceId = when {
                    topic.contains("Luce_Cucina") -> "cucina"
                    topic.contains("Luce_Libreria") -> "libreria"
                    else -> null
                }
                deviceId?.let { id ->
                    val state = if (payload.startsWith("{")) {
                        payload.contains("\"state\":\"ON\"") || payload.contains("\"state\": \"ON\"")
                    } else {
                        payload.equals("ON", ignoreCase = true)
                    }
                    _lightStates.value = _lightStates.value.toMutableMap().apply { put(id, state) }
                }
            }
        }

        if (topic == "zara/android/domotica/casa/clima/stato_completo") {
            try {
                val data: AiManagedData = json.decodeFromString(payload)
                _aiManagedData.value = data
                _environmentState.value = _environmentState.value.copy(
                    living = _environmentState.value.living.copy(
                        temperature = data.metriche_ambientali.temperatura_c,
                        humidex = data.metriche_ambientali.humidex
                    )
                )
            } catch (e: Exception) {
                Log.e("MQTT", "Error parsing AI state", e)
            }
            return
        }

        if (topic == "casa/stanza1/humidex") {
            _environmentState.value = _environmentState.value.copy(
                living = _environmentState.value.living.copy(humidex = payload.toFloatOrNull() ?: 0f)
            )
            return
        }
        if (topic == "casa/cameraMatrimoniale/humidex") {
            _environmentState.value = _environmentState.value.copy(
                bedroom = _environmentState.value.bedroom.copy(humidex = payload.toFloatOrNull() ?: 0f)
            )
            return
        }

        if (topic.startsWith("casa/clima/cmnd/") || topic.startsWith("casa/clima/stat/") || topic.startsWith("zara/android/domotica/")) {
            val key = topic.substringAfterLast("/")
            val current = _aiSettings.value
            _aiSettings.value = when (key) {
                "AI_climate_enabling", "set" -> current.copy(systemEnabled = payload.toBoolean() || payload == "1")
                "min_run_time" -> current.copy(minOnTime = payload)
                "min_off_time" -> current.copy(minOffTime = payload)
                "target_humidex" -> current.copy(targetHumidex = payload.toFloatOrNull() ?: current.targetHumidex)
                "vmc_max_notte" -> current.copy(vmcMaxNight = payload.toIntOrNull() ?: current.vmcMaxNight)
                "deficit_tolerance_time" -> current.copy(deficitTolerance = payload)
                "grace_mode_solar" -> current.copy(graceModeSolar = payload.toBoolean() || payload == "1")
                "emergency_humidex_away" -> current.copy(emergencyHumidex = payload)
                else -> current
            }
        }

        if (topic.startsWith("zara/android/domotica/energy/")) {
            val type = topic.substringAfterLast("/")
            val value = payload.toFloatOrNull() ?: 0f
            val current = _energyData.value
            _energyData.value = when (type) {
                "production" -> current.copy(solarPower = value)
                "consumption" -> current.copy(homeConsumption = value)
                "grid" -> current.copy(gridPower = value)
                "battery" -> current.copy(batterySoc = value)
                "battery_power" -> current.copy(batteryPower = value)
                else -> current
            }
        }

        val parts = topic.split("/")
        when {
            topic.startsWith("stat/") && topic.endsWith("/POWER") -> {
                val lightId = parts.getOrNull(1)
                lightId?.let { id ->
                    val state = payload.equals("ON", ignoreCase = true) || payload == "1"
                    _lightStates.value = _lightStates.value.toMutableMap().apply { put(id, state) }
                }
            }
            topic.startsWith("zara/android/domotica/sensors/soggiorno/") -> {
                val type = parts.lastOrNull()
                val value = payload.toFloatOrNull() ?: 0f
                val current = _sensorData.value
                _sensorData.value = when (type) {
                    "temp" -> current.copy(temperature = value)
                    "hum" -> current.copy(humidity = value)
                    else -> current
                }
            }
        }
    }

    private fun handleEmonPiMessage(topic: String, payload: String) {
        val value = payload.toFloatOrNull() ?: 0f

        when {
            topic.startsWith("TeslaPowerwall/") -> {
                val type = topic.substringAfter("/")
                val current = _energyData.value
                _energyData.value = when (type) {
                    "solar_instant_power" -> current.copy(solarPower = value)
                    "load_instant_power" -> current.copy(homeConsumption = value)
                    "site_instant_power" -> current.copy(gridPower = value)
                    "battery_instant_power" -> current.copy(batteryPower = value)
                    "SOE" -> current.copy(batterySoc = value)
                    else -> current
                }
            }
            
            topic == "emon/emonth5/temperature_calibrated" -> {
                _environmentState.value = _environmentState.value.copy(
                    living = _environmentState.value.living.copy(temperature = value)
                )
                _sensorData.value = _sensorData.value.copy(temperature = value)
            }
            topic == "emon/emonth5/humidity" -> {
                _environmentState.value = _environmentState.value.copy(
                    living = _environmentState.value.living.copy(humidity = value)
                )
                _sensorData.value = _sensorData.value.copy(humidity = value)
            }
            topic == "emon/cameraMatrimoniale/temperature" -> {
                _environmentState.value = _environmentState.value.copy(
                    bedroom = _environmentState.value.bedroom.copy(temperature = value)
                )
            }
            topic == "emon/cameraMatrimoniale/humidity" -> {
                _environmentState.value = _environmentState.value.copy(
                    bedroom = _environmentState.value.bedroom.copy(humidity = value)
                )
            }
            topic == "emon/weather/extTemp" -> {
                _environmentState.value = _environmentState.value.copy(
                    outdoor = _environmentState.value.outdoor.copy(temperature = value)
                )
            }
            topic == "emon/weather/RelativeHumidity" -> {
                _environmentState.value = _environmentState.value.copy(
                    outdoor = _environmentState.value.outdoor.copy(humidity = value)
                )
            }
        }
    }

    fun publish(identifier: String, topic: String, payload: String, retained: Boolean = false) {
        val client = clients[identifier] ?: return
        if (!client.isConnected) return
        try {
            addTrafficLog("[$identifier] OUT: $topic")
            val message = MqttMessage(payload.toByteArray()).apply { 
                qos = 1
                isRetained = retained
            }
            client.publish(topic, message)
        } catch (e: Exception) {
            Log.e("MQTT", "Publish failed for $identifier", e)
        }
    }

    fun toggleLight(lightId: String, currentState: Boolean) {
        val nextState = !currentState
        val payload = if (nextState) "on" else "off"
        
        when (lightId) {
            "sala" -> publish("domopi", "zara/interface/lights/living/power/cmd", nextState.toString())
            "libreria" -> publish("domopi", "zara/interface/lights/libreria/power/cmd", nextState.toString())
            "lampadaHifi" -> publish("domopi", "zara/interface/lights/hifi/power/cmd", nextState.toString())
            
            "televisione" -> publish("domopi", "zara/domotics/lights/tvlamp", payload)
            "tavolinoLettura" -> publish("domopi", "zara/domotics/lights/readinglight", payload)
            "lavanderia" -> publish("domopi", "shellies/shelly1-B8C284/relay/0", payload)
            "ingressoServizio" -> publish("domopi", "shellies/shelly1-B92AF0/relay/0", payload)
            "portico" -> publish("domopi", "shellies/shelly1-771284/relay/0", payload)
            
            "cucina" -> {
                val z2mPayload = if (nextState) "{\"state\":\"ON\"}" else "{\"state\":\"OFF\"}"
                publish("domopi", "zigbee2mqtt/Luce_Cucina/set", z2mPayload)
            }
            "esterno" -> publish("domopi", "shellies/shelly1-esterno/relay/0/command", payload)
            
            else -> publish("domopi", "zara/android/domotica/light/$lightId/set", if (nextState) "ON" else "OFF")
        }
    }

    fun sendLightScene(scene: String) {
        val payload = when (scene) {
            "TV Mode" -> "on"
            "Sleep Mode" -> "on"
            else -> scene
        }
        publish("domopi", "zara/android/domotica/scene/set", payload)
    }

    fun disconnect() {
        clients.values.forEach { 
            try { it.disconnect() } catch (e: Exception) {}
        }
        clients.clear()
    }
}
