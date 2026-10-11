package com.domopi.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrrigationStateTest {
    // Shape published by the IRRIGATION.1 Node-RED flow.
    private val sample = """{"schema":"domopi.irrigation.v1","controller_enabled":false,"rain_delay":false,
        "rain_delay_until_s":0,"zones":[{"id":1,"name":"retro piazzola","running":false,"remaining_s":0},
        {"id":2,"name":"retro cucina","running":true,"remaining_s":300}],
        "programs":[{"id":1,"name":"Vacanza","enabled":true}],"updated_at_ms":1000}"""

    @Test fun parsesTheFlowState() {
        val s = IrrigationState.parse(sample)!!
        assertFalse(s.controllerEnabled)
        assertEquals(listOf("retro piazzola", "retro cucina"), s.zones.map { it.name })
        assertEquals(300, s.zones[1].remainingS)
        assertTrue(s.zones[1].running)
        assertEquals("Vacanza", s.programs[0].name)
    }

    @Test fun rejectsOtherPayloads() {
        assertNull(IrrigationState.parse("{}"))
        assertNull(IrrigationState.parse("not json"))
        assertNull(IrrigationState.parse(sample.replace("domopi.irrigation.v1", "other")))
    }

    @Test fun staleAfter90Seconds() {
        val s = IrrigationState.parse(sample)!!
        assertTrue(s.isFresh(91_000))
        assertFalse(s.isFresh(91_001))
    }

    @Test fun commandTopicsMatchTheFlow() {
        assertEquals("zara/interface/irrigation/zone/3/start/cmd" to "15", IrrigationCommands.zoneStart(3, 15))
        assertEquals("zara/interface/irrigation/zone/3/stop/cmd" to "true", IrrigationCommands.zoneStop(3))
        assertEquals("zara/interface/irrigation/program/1/start/cmd" to "true", IrrigationCommands.programStart(1))
        assertEquals("zara/interface/irrigation/all/stop/cmd" to "true", IrrigationCommands.stopAll())
        assertEquals("zara/interface/irrigation/controller/enabled/cmd" to "false", IrrigationCommands.controllerEnabled(false))
    }

    @Test fun outOfRangeMinutesSendNothing() {
        assertNull(IrrigationCommands.zoneStart(1, 0))
        assertNull(IrrigationCommands.zoneStart(1, 61))
        assertNull(IrrigationCommands.zoneStart(0, 10))
    }

    @Test fun commandResultIsReadable() {
        val r = IrrigationCommandResult.parse("""{"ok":false,"command":"zone/1/start","reason":"minuti non validi","at_ms":5}""")!!
        assertFalse(r.ok)
        assertEquals("Avvio zona 1", IrrigationCommands.label(r.command))
    }

    @Test fun appSubscribesToIrrigationTopics() {
        val source = listOf(File("src/main/java/com/domopi/app/data/MqttManager.kt"),
            File("app/src/main/java/com/domopi/app/data/MqttManager.kt")).first { it.exists() }.readText()
        assertTrue(source.substringAfter("fun subscribeToUnifiedTopics").substringBefore("mqttClient?.subscribe")
            .contains("\"${'$'}p/irrigation/#\""))
    }

    @Test fun irrigationCommandsAreNeverQueuedOffline() {
        val source = listOf(File("src/main/java/com/domopi/app/data/MqttManager.kt"),
            File("app/src/main/java/com/domopi/app/data/MqttManager.kt")).first { it.exists() }.readText()
        val body = source.substringAfter("fun sendIrrigationCommand").substringBefore("fun publish(")
        assertTrue(body.contains("mqttClient?.isConnected != true) return false"))
    }
}
