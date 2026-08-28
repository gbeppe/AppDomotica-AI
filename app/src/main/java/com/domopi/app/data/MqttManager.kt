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
    val batterySoc: Float = 0f,
    val pufferAcs: Float = 0f,
    val pufferAlto: Float = 0f,
    val pufferBasso: Float = 0f,
    val solarCollectorTemp: Float = 0f,
    val solarPumpSpeed: Int = 0
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

data class DomoticaSettings(
    val holidayMode: Boolean = false,
    val ecoLights: Boolean = false,
    val poolLightsAuto: Boolean = false,
    val porchSensor: Boolean = false,
    val acAuto: Boolean = false
)

class MqttManager(private val context: Context) {
    private var mqttClient: MqttAsyncClient? = null
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

    private val _domoticaSettings = MutableStateFlow(DomoticaSettings())
    val domoticaSettings: StateFlow<DomoticaSettings> = _domoticaSettings

    private val _hvacState = MutableStateFlow(HvacState())
    val hvacState: StateFlow<HvacState> = _hvacState

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
        if (!topic.startsWith("zara/interface/")) return
        addTrafficLog("IN: $topic")
        
        val parts = topic.split("/")
        if (parts.size < 4) return

        val domain = parts[2]
        val device = parts[3]
        val property = parts.getOrNull(4) ?: ""
        
        val cleanPayload = payload.trim().lowercase()
        val isOn = cleanPayload == "true" || cleanPayload == "on" || cleanPayload == "1"
        val value = payload.toFloatOrNull() ?: 0f
        val rounded = (value * 10).roundToInt() / 10f

        when (domain) {
            "lights", "pool" -> handleLightsDomain(device, isOn)
            "env" -> handleEnvDomain(device, property, rounded)
            "energy" -> handleEnergyDomain(device, property, rounded)
            "heating" -> handleHeatingDomain(device, property, rounded, payload)
            "climate" -> handleClimateDomain(device, property, rounded, isOn)
            "ai" -> handleAiDomain(device, property, cleanPayload, payload)
            "fireplace" -> handleFireplaceDomain(device, property, payload, isOn)
            "ventilation" -> handleVentilationDomain(device, property, payload)
            "settings" -> handleSettingsDomain(device, isOn)
            "garage" -> {} // Impulsi in uscita, nessun feedback previsto per ora
        }
    }

    // --- DOMAIN HANDLERS (Refactored for maintainability) ---

    private fun handleLightsDomain(device: String, isOn: Boolean) {
        val internalId = when(device) {
            "living" -> "sala"
            "reading" -> "tavolinolettura"
            "tv" -> "televisione"
            "bedroom" -> "lucecamera"
            "water" -> "lucipiscina"
            "deck" -> "lucipedanapiscina"
            "pump" -> "pompapiscina"
            "skimmer" -> "skimmerpiscina"
            "hifi" -> "lampadahifi"
            else -> device
        }
        updateLightState(internalId, isOn)
    }

    private fun handleEnvDomain(device: String, prop: String, valRounded: Float) {
        _environmentState.value = when (device) {
            "living" -> _environmentState.value.copy(living = updateSensor(_environmentState.value.living, prop, valRounded))
            "bedroom" -> _environmentState.value.copy(bedroom = updateSensor(_environmentState.value.bedroom, prop, valRounded))
            "outdoor" -> _environmentState.value.copy(outdoor = updateSensor(_environmentState.value.outdoor, prop, valRounded))
            else -> _environmentState.value
        }
        
        // Sincronizziamo anche AiManagedData per compatibilità con le schede informative
        if (device == "living") {
            _aiManagedData.value = when(prop) {
                "temperature" -> _aiManagedData.value.copy(metriche_ambientali = _aiManagedData.value.metriche_ambientali.copy(temperatura_c = valRounded))
                "humidex" -> _aiManagedData.value.copy(metriche_ambientali = _aiManagedData.value.metriche_ambientali.copy(humidex = valRounded, humidex_living = valRounded))
                else -> _aiManagedData.value
            }
        } else if (device == "bedroom") {
            _aiManagedData.value = when(prop) {
                "temperature" -> _aiManagedData.value.copy(metriche_ambientali = _aiManagedData.value.metriche_ambientali.copy(temp_cameraMatrimoniale = valRounded))
                "humidex" -> _aiManagedData.value.copy(metriche_ambientali = _aiManagedData.value.metriche_ambientali.copy(humidex_bedroom = valRounded))
                else -> _aiManagedData.value
            }
        }
    }

    private fun updateSensor(current: SensorData, prop: String, value: Float): SensorData = when(prop) {
        "temperature" -> current.copy(temperature = value)
        "humidity" -> current.copy(humidity = value)
        "humidex" -> current.copy(humidex = value)
        else -> current
    }

    private fun handleEnergyDomain(device: String, prop: String, value: Float) {
        _energyData.value = when (device) {
            "solar" -> _energyData.value.copy(solarPower = value)
            "home" -> _energyData.value.copy(homeConsumption = value)
            "grid" -> _energyData.value.copy(gridPower = value)
            "battery" -> if (prop == "soc") _energyData.value.copy(batterySoc = value) else _energyData.value.copy(batteryPower = value)
            "puffer_acs" -> _energyData.value.copy(pufferAcs = value)
            else -> _energyData.value
        }
    }

    private fun handleHeatingDomain(device: String, prop: String, value: Float, raw: String) {
        when (device) {
            "puffer" -> {
                _energyData.value = when (prop) {
                    "top_temperature" -> _energyData.value.copy(pufferAlto = value)
                    "bottom_temperature" -> _energyData.value.copy(pufferBasso = value)
                    else -> _energyData.value
                }
            }
            "solar_thermal" -> {
                _energyData.value = when (prop) {
                    "collector_temperature" -> _energyData.value.copy(solarCollectorTemp = value)
                    "pump_speed" -> _energyData.value.copy(solarPumpSpeed = raw.toIntOrNull() ?: 0)
                    else -> _energyData.value
                }
            }
            "gas_boiler" -> {
                _hvacState.value = when (prop) {
                    "flame" -> _hvacState.value.copy(boiler = _hvacState.value.boiler.copy(active = (raw == "true" || raw == "1")))
                    "modulation" -> _hvacState.value.copy(boiler = _hvacState.value.boiler.copy(modulation = raw.toIntOrNull() ?: 0))
                    else -> _hvacState.value
                }
            }
            "floor_pump" -> {
                _hvacState.value = when (prop) {
                    "enabled" -> _hvacState.value.copy(floorHeating = _hvacState.value.floorHeating.copy(enabled = (raw == "true" || raw == "1")))
                    "running" -> _hvacState.value.copy(floorHeating = _hvacState.value.floorHeating.copy(pumpActive = (raw == "true" || raw == "1")))
                    else -> _hvacState.value
                }
            }
        }
    }

    private fun handleAiDomain(device: String, prop: String, clean: String, raw: String) {
        val isOn = clean == "true" || clean == "on" || clean == "1"
        val intVal = raw.toIntOrNull() ?: 0
        val floatVal = raw.toFloatOrNull() ?: 0f

        _aiSettings.value = when (device) {
            "system_enabled" -> _aiSettings.value.copy(systemEnabled = isOn)
            "compressor_on_min" -> _aiSettings.value.copy(compressorOnMin = intVal)
            "compressor_off_min" -> _aiSettings.value.copy(compressorOffMin = intVal)
            "night_humidex_threshold" -> _aiSettings.value.copy(nightHumidexThreshold = intVal)
            "night_vmc_max_speed" -> _aiSettings.value.copy(nightVmcMaxSpeed = intVal)
            "deficit_tolerance_min" -> _aiSettings.value.copy(deficitToleranceMin = intVal)
            "morning_ac_management" -> _aiSettings.value.copy(morningAcManagement = isOn)
            "morning_humidex_emergency" -> _aiSettings.value.copy(morningHumidexEmergency = intVal)
            else -> _aiSettings.value
        }

        _aiManagedData.value = when (device) {
            "op_state" -> _aiManagedData.value.copy(stato_condizionatore = _aiManagedData.value.stato_condizionatore.copy(stato_attuale = raw))
            "op_reason" -> _aiManagedData.value.copy(stato_condizionatore = _aiManagedData.value.stato_condizionatore.copy(motivo_logica = raw))
            "op_mode" -> _aiManagedData.value.copy(stato_condizionatore = _aiManagedData.value.stato_condizionatore.copy(modalita_aria = raw))
            "vmc_speed" -> _aiManagedData.value.copy(stato_vmc = _aiManagedData.value.stato_vmc.copy(velocita_attuale = intVal))
            "solar_forecast" -> _aiManagedData.value.copy(logica_controllo = _aiManagedData.value.logica_controllo.copy(previsione_solare_domani_kwh = floatVal))
            "battery_forecast" -> _aiManagedData.value.copy(logica_controllo = _aiManagedData.value.logica_controllo.copy(previsione_ricarica_battery_percent = intVal))
            "battery_kwh" -> _aiManagedData.value.copy(logica_controllo = _aiManagedData.value.logica_controllo.copy(kwh_stimati_in_batteria = floatVal))
            "active_season" -> _aiManagedData.value.copy(stagione_attiva = raw)
            "soc_minimo_applied", "soc_minimo" -> _aiManagedData.value.copy(logica_controllo = _aiManagedData.value.logica_controllo.copy(soc_minimo_applied = floatVal))
            "soglia_attivazione_applicata", "soglia_humidex" -> _aiManagedData.value.copy(logica_controllo = _aiManagedData.value.logica_controllo.copy(soglia_attivazione_applicata = floatVal))
            "timer_anticiclo", "tempo_mancante_anticiclo_minuti" -> _aiManagedData.value.copy(logica_controllo = _aiManagedData.value.logica_controllo.copy(tempo_mancante_anticiclo_minuti = intVal))
            else -> _aiManagedData.value
        }
    }

    private fun handleFireplaceDomain(device: String, prop: String, raw: String, isOn: Boolean) {
        if (device != "main") return
        _hvacState.value = when (prop) {
            "power" -> _hvacState.value.copy(palazzetti = _hvacState.value.palazzetti.copy(active = isOn))
            "level" -> _hvacState.value.copy(palazzetti = _hvacState.value.palazzetti.copy(level = raw.toIntOrNull() ?: 1))
            "mode" -> _hvacState.value.copy(palazzetti = _hvacState.value.palazzetti.copy(mode = raw))
            "start_time" -> _hvacState.value.copy(palazzetti = _hvacState.value.palazzetti.copy(startTime = raw))
            "stop_time" -> _hvacState.value.copy(palazzetti = _hvacState.value.palazzetti.copy(stopTime = raw))
            "auto_power" -> _hvacState.value.copy(palazzetti = _hvacState.value.palazzetti.copy(autoPower = isOn))
            else -> _hvacState.value
        }
    }

    private fun handleClimateDomain(device: String, prop: String, value: Float, isOn: Boolean) {
        val current = if (device == "thermostat_living") _hvacState.value.thermostatLiving else _hvacState.value.thermostatBath
        val updated = when (prop) {
            "current_temperature" -> current.copy(currentTemp = value)
            "target_temperature" -> current.copy(targetTemp = value)
            "min_temperature" -> current.copy(minTemp = value)
            "max_temperature" -> current.copy(maxTemp = value)
            "power" -> current.copy(power = isOn)
            else -> current
        }
        _hvacState.value = if (device == "thermostat_living") _hvacState.value.copy(thermostatLiving = updated) else _hvacState.value.copy(thermostatBath = updated)
    }

    private fun handleVentilationDomain(device: String, prop: String, raw: String) {
        if (device == "vmc" && prop == "speed") {
            val speed = raw.toIntOrNull() ?: 1
            _hvacState.value = _hvacState.value.copy(vmc = _hvacState.value.vmc.copy(speed = speed, active = true))
            // Sincronizziamo anche AiManagedData per la scheda operativa
            _aiManagedData.value = _aiManagedData.value.copy(stato_vmc = _aiManagedData.value.stato_vmc.copy(velocita_attuale = speed))
        }
    }

    private fun handleSettingsDomain(device: String, isOn: Boolean) {
        _domoticaSettings.value = when (device) {
            "holiday_mode" -> _domoticaSettings.value.copy(holidayMode = isOn)
            "eco_lights" -> _domoticaSettings.value.copy(ecoLights = isOn)
            "pool_lights_auto" -> _domoticaSettings.value.copy(poolLightsAuto = isOn)
            "porch_sensor" -> _domoticaSettings.value.copy(porchSensor = isOn)
            "ac_auto" -> _domoticaSettings.value.copy(acAuto = isOn)
            else -> _domoticaSettings.value
        }
    }

    // --- UTILS ---

    private fun updateLightState(id: String, state: Boolean) {
        val cleanId = id.lowercase()
        if (cleanId !in VALID_LIGHT_IDS) return
        val current = _lightStates.value.toMutableMap()
        current[cleanId] = state
        _lightStates.value = current
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
