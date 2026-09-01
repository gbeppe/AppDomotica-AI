package com.domopi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.domopi.app.ui.theme.DomoPiTheme
import com.domopi.app.ui.screens.*
import com.domopi.app.data.DomoPiConnectivityManager
import com.domopi.app.data.SettingsManager
import com.domopi.app.data.MqttManager
import com.domopi.app.data.ConnectionMode
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var mqttManager: MqttManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        settingsManager = SettingsManager(this)
        val connectivityManager = DomoPiConnectivityManager(this)
        mqttManager = MqttManager(this, settingsManager)

        // --- Gateway Centrale .20 (Z-AI) ---
        lifecycleScope.launch {
            combine(
                settingsManager.domopiIp,
                settingsManager.domopiRemoteIp,
                settingsManager.domopiPort,
                settingsManager.domopiUser,
                settingsManager.domopiPass
            ) { args -> args }.collect { params ->
                val localIp = params[0]
                val remoteIp = params[1]
                val port = params[2]
                val user = params[3]
                val pass = params[4]
                val portInt = port.toIntOrNull() ?: 1883
                
                // Logica ottimizzata: 
                // 1. Se siamo sulla subnet locale (192.168.1.x), proviamo l'IP locale.
                // 2. Altrimenti, andiamo dritti sull'IP remoto.
                
                val ipToUse = if (connectivityManager.isOnLocalSubnet()) {
                    val isLocalAvailable = connectivityManager.checkServiceReachable(localIp, portInt)
                    if (isLocalAvailable) {
                        connectivityManager.updateConnectionMode(ConnectionMode.LOCAL)
                        localIp
                    } else {
                        connectivityManager.updateConnectionMode(ConnectionMode.REMOTE)
                        remoteIp
                    }
                } else {
                    connectivityManager.updateConnectionMode(ConnectionMode.REMOTE)
                    remoteIp
                }
                
                mqttManager.connect("tcp://$ipToUse:$port", user, pass)
            }
        }

        setContent {
            val isDarkModeSetting by settingsManager.darkMode.collectAsState(initial = null)
            val isDarkMode = isDarkModeSetting ?: true // Default a Dark Mode se non impostato
            
            DomoPiTheme(darkTheme = isDarkMode) {
                var currentScreen by remember { mutableStateOf("home") }
                // Memorizziamo la pagina del carosello per tornare esattamente dove eravamo
                // Inizializziamo a un multiplo di 9 (numero attuale di schede) per partire da ENERGIA (indice 0)
                var lastDashboardPage by remember { mutableIntStateOf(1000 * 9) } 
                
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
                            val mode by connectivityManager.connectionMode.collectAsState()
                            val epLocalIp by settingsManager.emonpiIp.collectAsState("192.168.1.15")
                            val epRemoteIp by settingsManager.emonpiRemoteIp.collectAsState("100.x.x.x")
                            val epIp = if (mode == ConnectionMode.LOCAL) epLocalIp else epRemoteIp
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
