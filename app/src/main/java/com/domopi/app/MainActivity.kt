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
