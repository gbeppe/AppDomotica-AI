package com.domopi.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class StackNode(val displayName: String) {
    PRIVATE_ROUTE("Percorso TLS privato / Tailscale"), BACKEND("Backend House AI (.20)"),
    DIGITAL_TWIN("Digital Twin MQTT"), GATEWAY("Gateway modello (.20)"), GROQ("Groq"),
    EMONCMS("Storico EmonCMS (.15)"), LOGS("Log Node-RED (.20)")
}

enum class NodeStatus { ONLINE, DEGRADED, OFFLINE, UNKNOWN, CHECKING }
enum class StackOverallStatus { ONLINE, DEGRADED, OFFLINE, UNKNOWN, CHECKING }

data class StackNodeState(val node: StackNode, val status: NodeStatus,
    val latencyMs: Long? = null, val detail: String = "", val lastCheckedMs: Long? = null)

data class TrafficLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String = currentTimeFormatted(), val tag: String, val endpoint: String,
    val statusCode: Int? = null, val latencyMs: Long? = null,
    val details: String = "", val isError: Boolean = false,
) {
    companion object {
        fun currentTimeFormatted(): String =
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }
}

data class StackHealthState(
    val overallStatus: StackOverallStatus = StackOverallStatus.UNKNOWN,
    val nodeStates: Map<StackNode, StackNodeState> = emptyMap(),
    val isChecking: Boolean = false, val lastOverallCheckTime: String = "")

class StackHealthManager {
    private val _healthState = MutableStateFlow(StackHealthState())
    val healthState: StateFlow<StackHealthState> = _healthState.asStateFlow()
    private val _trafficLogs = MutableStateFlow<List<TrafficLogEntry>>(emptyList())
    val trafficLogs: StateFlow<List<TrafficLogEntry>> = _trafficLogs.asStateFlow()

    fun checking() { _healthState.value = _healthState.value.copy(
        overallStatus = StackOverallStatus.CHECKING, isChecking = true) }

    fun update(state: StackHealthState) { _healthState.value = state.copy(
        isChecking = false, lastOverallCheckTime = TrafficLogEntry.currentTimeFormatted()) }

    fun reset() { _healthState.value = StackHealthState() }

    fun unavailable(detail: String) {
        val nodes = StackNode.entries.associateWith { node -> StackNodeState(node,
            if (node in setOf(StackNode.PRIVATE_ROUTE, StackNode.BACKEND)) NodeStatus.OFFLINE
            else NodeStatus.UNKNOWN, detail = detail) }
        update(StackHealthState(StackOverallStatus.OFFLINE, nodes))
    }

    fun logTraffic(entry: TrafficLogEntry) {
        _trafficLogs.value = (listOf(entry) + _trafficLogs.value).take(MAX_LOG_ENTRIES)
    }
    fun clearLogs() { _trafficLogs.value = emptyList() }

    companion object { const val MAX_LOG_ENTRIES = 50 }
}
