package com.domopi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.domopi.app.ui.components.HouseAlertBanner
import com.domopi.app.ui.theme.DomoPiTheme
import com.domopi.app.ui.screens.*
import com.domopi.app.data.ZaiConnectivityManager
import com.domopi.app.data.SettingsManager
import com.domopi.app.data.MqttManager
import com.domopi.app.data.CredentialStore
import com.domopi.app.data.BuildAlignment
import com.domopi.app.data.BuildIdentity
import com.domopi.app.network.AccessPathClassifier
import com.domopi.app.network.AccessPathState
import com.domopi.app.network.AndroidNetworkObserver
import com.domopi.app.network.HomeNetworkSettings
import com.domopi.app.network.NetworkEvidence
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
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
        
        val credentialStore = CredentialStore(this)
        settingsManager = SettingsManager(this, credentialStore)
        val connectivityManager = ZaiConnectivityManager(this)
        mqttManager = MqttManager()
        // Secrets live only in the encrypted store; old plain values are migrated here once.
        lifecycleScope.launch { settingsManager.loadSecrets() }

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

                    // New host or credentials: apply them now, even if a session is open.
                    mqttManager.connect("tcp://$host:$port", user, pass)
                    delay(8.seconds)
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

        // Topbar: percorso di accesso (solo indicatore, gli endpoint restano LAN canonici)
        val accessPathState = combine(
            AndroidNetworkObserver(this).observe()
                .map<NetworkEvidence, NetworkEvidence?> { it }
                .onStart { emit(null) },
            settingsManager.homeNetwork,
        ) { evidence, home ->
            evidence?.let { AccessPathClassifier.classify(it, home) } ?: AccessPathState.Pending
        }.stateIn(lifecycleScope, SharingStarted.WhileSubscribed(5000), AccessPathState.Pending)

        // Topbar: allineamento con l'ultima release pubblicata su domopi/build/android
        val localBuild = BuildIdentity(
            gitSha = BuildConfig.GIT_SHA,
            dirty = BuildConfig.BUILD_DIRTY,
            buildId = BuildConfig.BUILD_ID,
            buildTimestamp = BuildConfig.BUILD_TIMESTAMP,
        )
        val buildAlignment = combine(
            mqttManager.isConnected,
            mqttManager.startupGraceOver,
            mqttManager.publishedBuild,
        ) { connected, graceOver, published ->
            BuildAlignment.evaluate(connected, graceOver, published, localBuild)
        }.stateIn(lifecycleScope, SharingStarted.Eagerly, BuildAlignment.evaluate(false, false, mqttManager.publishedBuild.value, localBuild))

        setContent {
            val accessPath by accessPathState.collectAsState()
            val alignment by buildAlignment.collectAsState()
            val isDarkModeSetting by settingsManager.darkMode.collectAsState(initial = null)
            val isDarkMode = isDarkModeSetting ?: true // Default a Dark Mode se non impostato
            
            DomoPiTheme(darkTheme = isDarkMode) {
                var currentScreen by rememberSaveable { mutableStateOf("home") }
                // Memorizziamo la pagina del carosello per tornare esattamente dove eravamo
                // Inizializziamo a un multiplo di 10 (numero attuale di schede) per partire da ENERGIA (indice 0)
                var lastDashboardPage by rememberSaveable { mutableIntStateOf(1000 * 10) } 
                
                Surface(color = MaterialTheme.colorScheme.background) {
                  Box(Modifier.fillMaxSize()) {
                    when (currentScreen) {
                        "home" -> MainDashboard(
                            mqttManager = mqttManager,
                            connectivityManager = connectivityManager,
                            settingsManager = settingsManager,
                            initialPage = lastDashboardPage,
                            onPageChanged = { lastDashboardPage = it },
                            onNavigate = { currentScreen = it },
                            accessPathState = accessPath,
                            buildAlignment = alignment,
                        )
                        "access_path" -> {
                            val home by settingsManager.homeNetwork.collectAsState(initial = HomeNetworkSettings())
                            AccessPathDetailScreen(
                                state = accessPath,
                                homeNetwork = home,
                                onEditHomeNetwork = { currentScreen = "configuration" },
                                onBack = { currentScreen = "home" }
                            )
                        }
                        "build_alignment" -> {
                            val published by mqttManager.publishedBuild.collectAsState()
                            BuildAlignmentScreen(
                                status = alignment,
                                published = published,
                                onOpenDiagnostics = { currentScreen = "diagnosis" },
                                onBack = { currentScreen = "home" }
                            )
                        }
                        "ai_smart" -> {
                            val state by mqttManager.aiSmartState.collectAsState()
                            val energyState by mqttManager.energySmartState.collectAsState()
                            val climateState by mqttManager.climateSmartState.collectAsState()
                            val reserveState by mqttManager.predictiveReserveSmartState.collectAsState()
                            val thermostatState by mqttManager.thermostatSmartState.collectAsState()
                            val environmentState by mqttManager.environmentSmartState.collectAsState()
                            val heatingState by mqttManager.heatingSmartState.collectAsState()
                            val rulesState by mqttManager.rulesState.collectAsState()
                            val connected by mqttManager.isConnected.collectAsState()
                            AiSmartScreen(state, energyState, climateState, connected,
                                reserveState = reserveState,
                                onClassic = { currentScreen = "home" },
                                onHistory = { currentScreen = "house_ai" },
                                onLightCommand = mqttManager::setSmartLightState,
                                settingsManager = settingsManager,
                                credentialStore = credentialStore,
                                thermostatState = thermostatState,
                                onThermostatCommand = mqttManager::setSmartThermostatTarget,
                                onSceneCommand = mqttManager::setSmartLightScene,
                                environmentState = environmentState,
                                heatingState = heatingState,
                                onFireplaceSettings = mqttManager::setSmartFireplaceSettings,
                                onFireplacePower = mqttManager::setSmartFireplacePower,
                                onFireplaceLevel = mqttManager::setSmartFireplaceLevel,
                                rulesState = rulesState,
                                onRuleAction = mqttManager::publishRuleAction)
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
                            val epKey by settingsManager.emoncmsApiKey.collectAsState(initial = "")
                            EnergyDetailScreen(emoncmsIp = epIp, apiKey = epKey, onBack = { currentScreen = "home" })
                        }
                        "diagnosis" -> DiagnosisScreen(
                            mqttManager = mqttManager,
                            settingsManager = settingsManager,
                            connectivityManager = connectivityManager,
                            onBack = { currentScreen = "home" }
                        )
                        "configuration" -> ConfigurationScreen(
                            settingsManager = settingsManager,
                            onBack = { currentScreen = "home" },
                            buildAlignment = alignment,
                            onOpenBuildAlignment = { currentScreen = "build_alignment" }
                        )
                        "irrigation" -> IrrigationScreen(
                            mqttManager = mqttManager,
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
                    // Fase 3.5 B: written alerts (house disconnected, battery full/low) on every screen.
                    val alertConnected by mqttManager.isConnected.collectAsState()
                    val alertEnergy by mqttManager.energySmartState.collectAsState()
                    HouseAlertBanner(alertConnected,
                        alertEnergy.validObservations()["battery_soc_percent"]?.value,
                        Modifier.align(Alignment.TopCenter))
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
