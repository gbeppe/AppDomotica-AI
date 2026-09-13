package com.domopi.app.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StackHealthManagerTest {

    private lateinit var manager: StackHealthManager

    @Before
    fun setUp() {
        manager = StackHealthManager()
    }

    @Test
    fun initialStateIsUnknownAndIdle() {
        val state = manager.healthState.value
        assertEquals(StackOverallStatus.UNKNOWN, state.overallStatus)
        assertTrue(state.nodeStates.isEmpty())
        assertFalse(state.isChecking)
    }

    @Test
    fun logTrafficLimitsToMaxEntries() {
        for (i in 1..60) {
            manager.logTraffic(
                TrafficLogEntry(
                    tag = "TEST",
                    endpoint = "endpoint-$i",
                    details = "Log number $i"
                )
            )
        }

        val logs = manager.trafficLogs.value
        assertEquals(StackHealthManager.MAX_LOG_ENTRIES, logs.size)
        // Newest entry should be first (endpoint-60)
        assertEquals("endpoint-60", logs.first().endpoint)
        // Oldest entry retained should be endpoint-11
        assertEquals("endpoint-11", logs.last().endpoint)
    }

    @Test
    fun clearLogsEmptiesBuffer() {
        manager.logTraffic(
            TrafficLogEntry(
                tag = "TEST",
                endpoint = "endpoint-1",
                details = "Sample details"
            )
        )
        assertFalse(manager.trafficLogs.value.isEmpty())

        manager.clearLogs()
        assertTrue(manager.trafficLogs.value.isEmpty())
    }

    @Test
    fun trafficLogEntryDefaults() {
        val entry = TrafficLogEntry(
            tag = "HOUSE-AI",
            endpoint = "192.168.1.20:8000",
            statusCode = 200,
            latencyMs = 150,
            details = "200 OK"
        )

        assertNotNull(entry.id)
        assertNotNull(entry.timestamp)
        assertEquals("HOUSE-AI", entry.tag)
        assertEquals(200, entry.statusCode)
        assertEquals(150L, entry.latencyMs!!)
        assertFalse(entry.isError)
    }

    @Test
    fun checkingUpdateAndUnavailableAreDeterministic() {
        manager.checking()
        assertTrue(manager.healthState.value.isChecking)
        manager.update(StackHealthState(StackOverallStatus.ONLINE, mapOf(
            StackNode.BACKEND to StackNodeState(StackNode.BACKEND, NodeStatus.ONLINE))))
        assertEquals(StackOverallStatus.ONLINE, manager.healthState.value.overallStatus)
        assertFalse(manager.healthState.value.isChecking)
        manager.unavailable("TLS non raggiungibile")
        val state = manager.healthState.value
        assertEquals(StackOverallStatus.OFFLINE, state.overallStatus)
        assertEquals(NodeStatus.OFFLINE, state.nodeStates[StackNode.PRIVATE_ROUTE]?.status)
        assertEquals(NodeStatus.UNKNOWN, state.nodeStates[StackNode.GROQ]?.status)
        manager.reset()
        assertEquals(StackOverallStatus.UNKNOWN, manager.healthState.value.overallStatus)
        assertTrue(manager.healthState.value.nodeStates.isEmpty())
    }
}
