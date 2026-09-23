package com.domopi.app.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Architecture guards: Compose/Activity wiring is not executable in a plain JVM test.
 * These checks prove endpoint dependencies, not network reachability or reconnect timing.
 */
class CanonicalLanRoutingSourceTest {
    private fun source(relative: String): String {
        val root = listOf(File("src/main/java/com/domopi/app"),
            File("app/src/main/java/com/domopi/app")).first { it.isDirectory }
        return File(root, relative).readText()
    }

    @Test fun mqttUsesConfiguredHostWithoutNetworkOrRemoteSelection() {
        val activity = source("MainActivity.kt")
        val wiring = activity.substringAfter("combine(").substringBefore("setContent {")
        assertTrue(wiring.contains("settingsManager.domopiIp"))
        assertTrue(wiring.contains("mqttManager.connect(\"tcp://\$host:\$port\", user, pass)"))
        listOf("RemoteIp", "isOnLocalSubnet", "checkServiceReachable", "ConnectionMode",
            "getLocalIpAddress", "connectionMode").forEach {
            assertFalse("MQTT must not depend on $it", wiring.contains(it))
        }
    }

    @Test fun emoncmsConsumesItsOwnConfiguredEndpoint() {
        val route = source("MainActivity.kt").substringAfter("\"energy_detail\" -> {")
            .substringBefore("\"diagnosis\" ->")
        assertTrue(route.contains("settingsManager.emoncmsIp"))
        assertTrue(route.contains("EnergyDetailScreen(emoncmsIp = epIp"))
        listOf("RemoteIp", "ConnectionMode", "connectionMode", "mqttManager", "isConnected").forEach {
            assertFalse("EmonCMS endpoint must not depend on $it", route.contains(it))
        }
    }

    @Test fun bothCameraConsumersAndDiagnosisUseSameConfiguredLanHost() {
        listOf("MainDashboard.kt", "CamerasScreen.kt", "DiagnosisScreen.kt").forEach { name ->
            val screen = source("ui/screens/$name")
            assertTrue("$name must consume the persisted LAN host",
                screen.contains("val tcIp by settingsManager.tinycamLocalIp"))
            assertTrue(screen.contains("settingsManager.tinycamPort"))
            listOf("tinycamRemoteIp", "ConnectionMode", "connectionMode").forEach {
                assertFalse("$name must not select cameras using $it", screen.contains(it))
            }
        }
    }

    @Test fun noServiceConsumerCanUseGlobalGeographicModeToChangeEndpoints() {
        listOf("MainActivity.kt", "ui/screens/MainDashboard.kt", "ui/screens/CamerasScreen.kt",
            "ui/screens/DiagnosisScreen.kt", "ui/screens/EnergyDetailScreen.kt",
            "data/EnergyRepository.kt").forEach { name ->
            val content = source(name)
            assertFalse(name, content.contains("ConnectionMode"))
            assertFalse(name, content.contains("connectionMode"))
            assertFalse(name, content.contains("RemoteIp"))
        }
    }
}
