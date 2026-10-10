package com.domopi.app.data

import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightSceneSmartTest {
    @Suppress("UNCHECKED_CAST")
    @Test fun onlyDispatcherScenesArePublished() {
        val manager = MqttManager()
        val queue = MqttManager::class.java.getDeclaredField("messageQueue")
            .apply { isAccessible = true }.get(manager) as ConcurrentLinkedQueue<MqttQueuedMessage>
        assertTrue(manager.setSmartLightScene("sleep"))
        assertFalse(manager.setSmartLightScene("cinema"))
        assertFalse(manager.setSmartLightScene("ALL_OFF"))
        assertEquals(listOf(MqttQueuedMessage("zara/interface/lights/scene/cmd", "sleep", false)), queue.toList())
    }

    @Test fun prolungaIsInTheSmartCatalog() {
        assertEquals("lights/prolunga/power/stat", AiSmartState.lightIdToTopic["lights_prolunga"])
        val state = AiSmartState().observe("lights/prolunga/power/stat", "true", 1000L, true)
        assertEquals(listOf("Prolunga"), state.activeDeviceLabels())
        assertTrue(state.lightsText().contains("1 punti luce accesi"))
        assertTrue(state.lightsText().contains("su 10 punti mappati"))
        assertEquals("true", state.validLightObservations().getValue("lights_prolunga").payload)
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun prolungaCommandUsesItsRegistryTopic() {
        val manager = MqttManager()
        val queue = MqttManager::class.java.getDeclaredField("messageQueue")
            .apply { isAccessible = true }.get(manager) as ConcurrentLinkedQueue<MqttQueuedMessage>
        assertTrue(manager.setSmartLightState("lights_prolunga", false))
        assertEquals(listOf(MqttQueuedMessage("zara/interface/lights/prolunga/power/cmd", "false", false)), queue.toList())
    }
}
