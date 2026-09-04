package com.domopi.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OutgoingMqttContractSourceTest {

    private fun getScreenFile(fileName: String): File {
        val userDir = System.getProperty("user.dir") ?: "."
        val pathsToTry = listOf(
            File(userDir, "src/main/java/com/domopi/app/ui/screens/$fileName"),
            File(userDir, "app/src/main/java/com/domopi/app/ui/screens/$fileName"),
            File("src/main/java/com/domopi/app/ui/screens/$fileName"),
            File("app/src/main/java/com/domopi/app/ui/screens/$fileName")
        )
        val found = pathsToTry.firstOrNull { it.exists() }
        assertTrue("Source file $fileName must exist at one of $pathsToTry", found != null && found.exists())
        return found!!
    }

    private val screenFiles = listOf(
        "GarageControlScreen.kt",
        "DomoticaSettingsScreen.kt",
        "HvacScreen.kt",
        "AiManagedScreen.kt"
    )

    @Test
    fun testNoLegacyNamespacesInScreenPublishCalls() {
        val legacyNamespaces = listOf(
            "zara/domotics/",
            "casa/clima/",
            "cmnd/",
            "TeslaPowerwall/"
        )

        screenFiles.forEach { screenName ->
            val file = getScreenFile(screenName)
            val content = file.readText()

            legacyNamespaces.forEach { legacyNs ->
                assertFalse(
                    "File $screenName must not contain legacy namespace '$legacyNs'",
                    content.contains(legacyNs)
                )
            }
        }
    }

    @Test
    fun testAllScreenPublishTopicsBelongToPublicInterfaceCmdContract() {
        val publishRegex = Regex("""publish\(\s*"([^"]+)"\s*,""")

        screenFiles.forEach { screenName ->
            val file = getScreenFile(screenName)
            val content = file.readText()

            publishRegex.findAll(content).forEach { match ->
                val topicPattern = match.groupValues[1]
                assertTrue(
                    "Topic '$topicPattern' in $screenName must start with 'zara/interface/'",
                    topicPattern.startsWith("zara/interface/")
                )
                assertTrue(
                    "Topic '$topicPattern' in $screenName must end with '/cmd'",
                    topicPattern.endsWith("/cmd")
                )
            }
        }
    }

    @Test
    fun testGarageScreenPublishContract() {
        val content = getScreenFile("GarageControlScreen.kt").readText()
        assertTrue(content.contains("zara/interface/garage/gate_1/cmd"))
        assertTrue(content.contains("zara/interface/garage/gate_2/cmd"))
    }

    @Test
    fun testDomoticaSettingsScreenPublishContract() {
        val content = getScreenFile("DomoticaSettingsScreen.kt").readText()
        assertTrue(content.contains("zara/interface/settings/holiday_mode/cmd"))
        assertTrue(content.contains("zara/interface/settings/eco_lights/cmd"))
        assertTrue(content.contains("zara/interface/settings/pool_lights_auto/cmd"))
        assertTrue(content.contains("zara/interface/settings/porch_sensor/cmd"))
        assertTrue(content.contains("zara/interface/settings/ac_auto/cmd"))
    }

    @Test
    fun testHvacScreenPublishContract() {
        val content = getScreenFile("HvacScreen.kt").readText()
        assertTrue(content.contains("zara/interface/ventilation/vmc/speed/cmd"))
        assertTrue(content.contains("zara/interface/heating/floor_pump/enabled/cmd"))
        assertTrue(content.contains("zara/interface/climate/\$deviceId/target_temperature/cmd"))
        assertTrue(content.contains("zara/interface/climate/\$deviceId/min_temperature/cmd"))
        assertTrue(content.contains("zara/interface/climate/\$deviceId/max_temperature/cmd"))
        assertTrue(content.contains("zara/interface/fireplace/main/power/cmd"))
        assertTrue(content.contains("zara/interface/fireplace/main/mode/cmd"))
        assertTrue(content.contains("zara/interface/fireplace/main/start_time/cmd"))
        assertTrue(content.contains("zara/interface/fireplace/main/stop_time/cmd"))
        assertTrue(content.contains("zara/interface/fireplace/main/level/cmd"))
        assertTrue(content.contains("zara/interface/fireplace/main/auto_power/cmd"))
    }

    @Test
    fun testAiManagedScreenPublishContract() {
        val content = getScreenFile("AiManagedScreen.kt").readText()
        assertTrue(content.contains("zara/interface/ai/system_enabled/cmd"))
        assertTrue(content.contains("zara/interface/ai/compressor_on_min/cmd"))
        assertTrue(content.contains("zara/interface/ai/compressor_off_min/cmd"))
        assertTrue(content.contains("zara/interface/ai/night_humidex_threshold/cmd"))
        assertTrue(content.contains("zara/interface/ai/night_vmc_max_speed/cmd"))
        assertTrue(content.contains("zara/interface/ai/deficit_tolerance_min/cmd"))
        assertTrue(content.contains("zara/interface/ai/morning_ac_management/cmd"))
        assertTrue(content.contains("zara/interface/ai/morning_humidex_emergency/cmd"))
    }
}
