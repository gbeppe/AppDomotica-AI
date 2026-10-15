package com.domopi.app.data

import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FireplaceSmartCommandTest {
    private val p = "zara/interface/fireplace/main/"

    @Suppress("UNCHECKED_CAST")
    private fun MqttManager.queue() = MqttManager::class.java.getDeclaredField("messageQueue")
        .apply { isAccessible = true }.get(this) as ConcurrentLinkedQueue<MqttQueuedMessage>

    @Suppress("UNCHECKED_CAST")
    private fun MqttManager.seeMode(mode: String) {
        val field = MqttManager::class.java.getDeclaredField("_heatingSmartState").apply { isAccessible = true }
        val flow = field.get(this) as MutableStateFlow<HeatingSmartState>
        flow.value = flow.value.observe("fireplace/main/mode/stat", mode, 1000L, true, p + "mode/stat")
    }

    @Test fun settingsAreValidatedLikeTheBackend() {
        assertTrue(FireplaceSettingsCommand("Acqua Sanitaria", "12:00", "13:00", null).isValid())
        assertTrue(FireplaceSettingsCommand(null, null, null, false).isValid())
        assertFalse(FireplaceSettingsCommand(null, null, null, null).isValid())
        assertFalse(FireplaceSettingsCommand("inverno", null, null, null).isValid())
        assertFalse(FireplaceSettingsCommand(null, "13:00", "12:00", null).isValid())
        assertFalse(FireplaceSettingsCommand(null, "1200", null, null).isValid())
    }

    @Test fun settingsPublishModeTimesAndAutoPowerNeverTheLevel() {
        val manager = MqttManager()
        assertTrue(manager.setSmartFireplaceSettings(FireplaceSettingsCommand("Acqua Sanitaria", "12:00", "13:00", true)))
        assertFalse(manager.setSmartFireplaceSettings(FireplaceSettingsCommand("inverno", null, null, null)))
        assertEquals(listOf(
            MqttQueuedMessage(p + "mode/cmd", "Acqua Sanitaria", false),
            MqttQueuedMessage(p + "start_time/cmd", "12:00", false),
            MqttQueuedMessage(p + "stop_time/cmd", "13:00", false),
            MqttQueuedMessage(p + "auto_power/cmd", "true", false)), manager.queue().toList())
    }

    @Test fun manualPowerOnlyInManuale() {
        val manager = MqttManager()
        assertFalse(manager.setSmartFireplacePower(true))
        manager.seeMode("Riscaldamento")
        assertFalse(manager.setSmartFireplacePower(true))
        manager.seeMode("Manuale")
        assertTrue(manager.setSmartFireplacePower(false))
        assertEquals(listOf(MqttQueuedMessage(p + "power/cmd", "false", false)), manager.queue().toList())
    }
}
