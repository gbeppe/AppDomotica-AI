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

        // Monitor settings and connect/reconnect MQTT
        lifecycleScope.launch {
            combine(
                settingsManager.domopiIp,
                settingsManager.domopiPort,
                settingsManager.domopiUser,
                settingsManager.domopiPass
            ) { ip, port, user, pass ->
                Triple(ip, port, user to pass)
            }.collect { (ip, port, auth) ->
                mqttManager.connect("tcp://$ip:$port", auth.first, auth.second, "domopi")
            }
        }

        lifecycleScope.launch {
            combine(
                settingsManager.emonpiIp,
                settingsManager.emonpiPort,
                settingsManager.emonpiUser,
                settingsManager.emonpiPass
            ) { ip, port, user, pass ->
                Triple(ip, port, user to pass)
            }.collect { (ip, port, auth) ->
                mqttManager.connect("tcp://$ip:$port", auth.first, auth.second, "emonpi")
            }
        }

        // Dummy connection mode detection (Should be replaced with real reachability logic)
        lifecycleScope.launch {
            settingsManager.domopiIp.collect { ip ->
                if (ip.startsWith("192.168.")) {
                    connectivityManager.updateConnectionMode(ConnectionMode.LOCAL)
                } else {
                    connectivityManager.updateConnectionMode(ConnectionMode.REMOTE)
                }
            }
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
                        "cameras" -> CamerasScreen(
                            settingsManager = settingsManager,
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
