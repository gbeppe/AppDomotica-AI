@file:OptIn(InternalSerializationApi::class)
package com.domopi.app.data

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** OpenSprinkler state published by Node-RED (IRRIGATION.1) on zara/interface/irrigation/state/stat. */
@Serializable
data class IrrigationZone(
    val id: Int,
    val name: String,
    val running: Boolean = false,
    @SerialName("remaining_s") val remainingS: Int = 0,
)

@Serializable
data class IrrigationProgram(val id: Int, val name: String, val enabled: Boolean = false)

@Serializable
data class IrrigationState(
    val schema: String = "",
    @SerialName("controller_enabled") val controllerEnabled: Boolean = false,
    @SerialName("rain_delay") val rainDelay: Boolean = false,
    @SerialName("rain_delay_until_s") val rainDelayUntilS: Long = 0,
    val zones: List<IrrigationZone> = emptyList(),
    val programs: List<IrrigationProgram> = emptyList(),
    @SerialName("updated_at_ms") val updatedAtMs: Long = 0,
) {
    /** Node-RED publishes on change and at least every 60 s; older than 90 s means stale. */
    fun isFresh(nowMs: Long): Boolean = nowMs - updatedAtMs <= 90_000

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(payload: String): IrrigationState? = runCatching {
            json.decodeFromString<IrrigationState>(payload).takeIf { it.schema == "domopi.irrigation.v1" }
        }.getOrNull()
    }
}

/** Result of the last command, from zara/interface/irrigation/last_command/stat. */
@Serializable
data class IrrigationCommandResult(
    val ok: Boolean = false,
    val command: String = "",
    val reason: String? = null,
    val result: Int? = null,
    @SerialName("at_ms") val atMs: Long = 0,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(payload: String): IrrigationCommandResult? =
            runCatching { json.decodeFromString<IrrigationCommandResult>(payload) }.getOrNull()
    }
}

/** Command topics accepted by the IRRIGATION.1 flow; invalid input gives null (nothing sent). */
object IrrigationCommands {
    const val MAX_MINUTES = 60
    private const val BASE = "zara/interface/irrigation"

    fun zoneStart(id: Int, minutes: Int): Pair<String, String>? =
        if (id > 0 && minutes in 1..MAX_MINUTES) "$BASE/zone/$id/start/cmd" to minutes.toString() else null
    fun zoneStop(id: Int): Pair<String, String>? = if (id > 0) "$BASE/zone/$id/stop/cmd" to "true" else null
    fun programStart(id: Int): Pair<String, String>? = if (id > 0) "$BASE/program/$id/start/cmd" to "true" else null
    fun stopAll(): Pair<String, String> = "$BASE/all/stop/cmd" to "true"
    fun controllerEnabled(enabled: Boolean): Pair<String, String> = "$BASE/controller/enabled/cmd" to enabled.toString()

    fun label(command: String): String = when {
        command == "all/stop" -> "Ferma tutto"
        command == "controller/enabled" -> "Attivazione OpenSprinkler"
        command.startsWith("zone/") && command.endsWith("/start") -> "Avvio zona ${command.split("/")[1]}"
        command.startsWith("zone/") && command.endsWith("/stop") -> "Arresto zona ${command.split("/")[1]}"
        command.startsWith("program/") -> "Avvio programma ${command.split("/")[1]}"
        else -> command
    }
}
