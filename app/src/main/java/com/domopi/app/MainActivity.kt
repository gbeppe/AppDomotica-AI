package com.domopi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.domopi.app.ui.theme.DomoPiTheme
import com.domopi.app.ui.screens.*
import com.domopi.app.data.ZaiConnectivityManager
import com.domopi.app.data.SettingsManager
import com.domopi.app.data.MqttManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var mqttManager: MqttManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        settingsManager = SettingsManager(this)
        val connectivityManager = ZaiConnectivityManager(this)
        mqttManager = MqttManager()

        // Sync prefisso Digital Twin
        lifecycleScope.launch {
            settingsManager.digitalTwinPrefix.collectLatest { prefix ->
                mqttManager.setDigitalTwinPrefix(prefix)
            }
        }

        // --- Gateway Centrale .20 (Z-AI) ---
        lifecycleScope.launch {
            combine(
                settingsManager.domopiIp,
                settingsManager.domopiPort,
                settingsManager.domopiUser,
                settingsManager.domopiPass,
            ) { args -> args.copyOf() }
                .distinctUntilChanged { old, new -> old.contentEquals(new) }
                .collectLatest { params ->
                    // The configured LAN host is canonical; Android/Tailscale handles routing.
                    val host = params[0]
                    val port = params[1]
                    val user = params[2]
                    val pass = params[3]

                    // Ciclo di riconnessione se fallisce
                    while (true) {
                        if (mqttManager.isConnected.value) {
                            delay(15.seconds)
                            continue
                        }

                        mqttManager.connect("tcp://$host:$port", user, pass)
                        delay(8.seconds)
                    }
                }
        }

        setContent {
            val isDarkModeSetting by settingsManager.darkMode.collectAsState(initial = null)
            val isDarkMode = isDarkModeSetting ?: true // Default a Dark Mode se non impostato
            
            DomoPiTheme(darkTheme = isDarkMode) {
                var currentScreen by rememberSaveable { mutableStateOf("home") }
                // Memorizziamo la pagina del carosello per tornare esattamente dove eravamo
                // Inizializziamo a un multiplo di 9 (numero attuale di schede) per partire da ENERGIA (indice 0)
                var lastDashboardPage by rememberSaveable { mutableIntStateOf(1000 * 9) } 
                
                Surface(color = MaterialTheme.colorScheme.background) {
                    when (currentScreen) {
                        "home" -> MainDashboard(
                            mqttManager = mqttManager,
                            connectivityManager = connectivityManager,
                            settingsManager = settingsManager,
                            initialPage = lastDashboardPage,
                            onPageChanged = { lastDashboardPage = it },
                            onNavigate = { currentScreen = it }
                        )
                        "ai_smart" -> {
                            val state by mqttManager.aiSmartState.collectAsState()
                            val energyState by mqttManager.energySmartState.collectAsState()
                            val climateState by mqttManager.climateSmartState.collectAsState()
                            val connected by mqttManager.isConnected.collectAsState()
                            AiSmartScreen(state, energyState, climateState, connected,
                                onClassic = { currentScreen = "home" },
                                onHistory = { currentScreen = "house_ai" },
                                onLightCommand = mqttManager::setSmartLightState)
                        }
                        "house_ai" -> HouseAiScreen(onBack = { currentScreen = "ai_smart" })
                        "lights" -> LightsScreen(
                            mqttManager = mqttManager,
                            onBack = { currentScreen = "home" }
                        )
                        "clima" -> AiManagedScreen(
                            mqttManager = mqttManager,
                            settingsManager = settingsManager,
                            onBack = { currentScreen = "home" }
                        )
                        "ambienti" -> AmbientiScreen(
                            mqttManager = mqttManager,
                            onBack = { currentScreen = "home" }
                        )
                        "energy_detail" -> {
                            val epIp by settingsManager.emoncmsIp.collectAsState(initial = "")
                            EnergyDetailScreen(emoncmsIp = epIp, onBack = { currentScreen = "home" })
                        }
                        "diagnosis" -> DiagnosisScreen(
                            mqttManager = mqttManager,
                            settingsManager = settingsManager,
                            connectivityManager = connectivityManager,
                            onBack = { currentScreen = "home" }
                        )
                        "configuration" -> ConfigurationScreen(
                            settingsManager = settingsManager,
                            onBack = { currentScreen = "home" }
                        )
                        "pool" -> PoolScreen(
                            mqttManager = mqttManager,
                            onBack = { currentScreen = "home" }
                        )
                        "cameras" -> CamerasScreen(
                            settingsManager = settingsManager,
                            connectivityManager = connectivityManager,
                            onBack = { currentScreen = "home" }
                        )
                        "hvac" -> HvacScreen(
                            mqttManager = mqttManager,
                            onBack = { currentScreen = "home" }
                        )
                        "domotica_settings" -> DomoticaSettingsScreen(
                            mqttManager = mqttManager,
                            onBack = { currentScreen = "home" }
                        )
                        "garage" -> GarageControlScreen(
                            mqttManager = mqttManager,
                            onBack = { currentScreen = "home" }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttManager.disconnect()
    }
}
