package com.domopi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
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
        mqttManager = MqttManager(this)

        // --- Gateway Centrale .20 (DomoPi) ---
        // Monitora i parametri e gestisce l'unica connessione necessaria.
        lifecycleScope.launch {
            combine(
                settingsManager.domopiIp,
                settingsManager.domopiPort,
                settingsManager.domopiUser,
                settingsManager.domopiPass
            ) { ip, port, user, pass ->
                // Determina la modalità di connessione basandosi sull'IP
                if (ip.startsWith("192.168.")) {
                    connectivityManager.updateConnectionMode(ConnectionMode.LOCAL)
                } else {
                    connectivityManager.updateConnectionMode(ConnectionMode.REMOTE)
                }
                
                // Connette al broker centrale che fa da gateway per tutto (anche per EmonPi)
                mqttManager.connect("tcp://$ip:$port", user, pass)
            }.collect {}
        }

        setContent {
            val isDarkMode by settingsManager.darkMode.collectAsState(initial = null)
            
            DomoPiTheme(darkTheme = isDarkMode ?: isSystemInDarkTheme()) {
                var currentScreen by remember { mutableStateOf("home") }
                
                Surface(color = MaterialTheme.colorScheme.background) {
                    when (currentScreen) {
                        "home" -> MainDashboard(
                            mqttManager = mqttManager,
                            connectivityManager = connectivityManager,
                            settingsManager = settingsManager,
                            onNavigate = { currentScreen = it }
                        )
                        "lights" -> LightsScreen(
                            mqttManager = mqttManager,
                            onBack = { currentScreen = "home" }
                        )
                        "clima" -> AiManagedScreen(
                            mqttManager = mqttManager,
                            onBack = { currentScreen = "home" }
                        )
                        "ambienti" -> AmbientiScreen(
                            mqttManager = mqttManager,
                            onBack = { currentScreen = "home" }
                        )
                        "energy_detail" -> {
                            // Anche qui puntiamo al .20, poiché i dati EmonCMS sono bridgeati o comunque
                            // seguiamo la logica del repository che usa l'IP configurato.
                            val epIp by settingsManager.emonpiIp.collectAsState("192.168.1.15")
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
