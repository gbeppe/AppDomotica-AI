package com.domopi.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One check of the health summary published by backend/health (phase S1, docs/MANUALE_SALUTE_IMPIANTO.md). */
@Serializable
data class HealthCheck(val id: String, val title: String, val ok: Boolean, val detail: String = "", val advice: String = "")

/** zara/system/health/<host>/stat, schema domopi.system_health.v1 (retained, every 2 minutes). */
@Serializable
data class HostHealth(
    val schema: String,
    val host: String,
    @SerialName("checked_at_ms") val checkedAtMs: Long,
    val ok: Boolean,
    val checks: List<HealthCheck>,
)

enum class HealthLight { GREEN, RED, GREY }

/** A TCP probe from the phone, only on the home Wi-Fi (the 21/10/2026 case: the Pis saw each other, the phone did not). */
data class PhoneProbe(val label: String, val host: String, val ip: String, val port: Int)

/**
 * Phase S3 of docs/AI_MODE/SALUTE_IMPIANTO.md: the "Salute impianto" page reads the two summaries,
 * turns them into a traffic light and adds the probes made from the phone.
 */
object SystemHealth {
    const val TOPIC = "zara/system/health/+/stat"
    private const val PREFIX = "zara/system/health/"
    const val SCHEMA = "domopi.system_health.v1"
    /** Three missed rounds of the 2 minute timer: that Pi, or its check, is not working. */
    const val STALE_MS = 6 * 60_000L
    val HOSTS = listOf(".20", ".15")
    val PROBES = listOf(
        PhoneProbe("Broker MQTT del .20", ".20", "192.168.1.20", 1883),
        PhoneProbe("Node-RED del .20", ".20", "192.168.1.20", 1880),
        PhoneProbe("Broker MQTT del .15", ".15", "192.168.1.15", 1883),
        PhoneProbe("Node-RED del .15", ".15", "192.168.1.15", 1880),
        PhoneProbe("Router", "router", "192.168.1.1", 80),
    )
    private val json = Json { ignoreUnknownKeys = true }

    fun isTopic(topic: String) = topic.startsWith(PREFIX) && topic.endsWith("/stat")

    /** A valid summary or null (other schema, unknown host, garbage). */
    fun parse(raw: String): HostHealth? = try {
        json.decodeFromString<HostHealth>(raw).takeIf { it.schema == SCHEMA && it.host in HOSTS && it.checks.size <= 200 }
    } catch (_: Exception) { null }

    fun light(health: HostHealth?, nowMs: Long): HealthLight = when {
        health == null || nowMs - health.checkedAtMs > STALE_MS -> HealthLight.GREY
        health.ok && health.checks.all { it.ok } -> HealthLight.GREEN
        else -> HealthLight.RED
    }

    /** Top bar heart: red if any Pi has a fault, grey if one is silent, green only with both up to date. */
    fun overall(hosts: Map<String, HostHealth>, nowMs: Long): HealthLight {
        val lights = HOSTS.map { light(hosts[it], nowMs) }
        return when {
            HealthLight.RED in lights -> HealthLight.RED
            HealthLight.GREY in lights -> HealthLight.GREY
            else -> HealthLight.GREEN
        }
    }

    /** "2 minuti fa" for the last check. */
    fun age(checkedAtMs: Long, nowMs: Long): String {
        val minutes = ((nowMs - checkedAtMs).coerceAtLeast(0) / 60_000).toInt()
        return when {
            minutes < 1 -> "adesso"
            minutes == 1 -> "1 minuto fa"
            minutes < 60 -> "$minutes minuti fa"
            minutes < 120 -> "1 ora fa"
            else -> "${minutes / 60} ore fa"
        }
    }

    /**
     * Advice from the phone probes: a Pi the phone cannot reach while the other Pi sees it is the
     * 21/10/2026 case (router ARP), fixed by restarting the router. Null when nothing to say.
     */
    fun phoneAdvice(reachable: Map<PhoneProbe, Boolean>, hosts: Map<String, HostHealth>, nowMs: Long): String? {
        if (reachable.isEmpty()) return null
        fun up(host: String) = reachable.filterKeys { it.host == host }.values.any { it }
        if (!up(".20") && !up(".15") && !up("router")) return "Il telefono non raggiunge nulla della casa: controlla di essere sul Wi-Fi di casa."
        for ((host, other) in listOf(".15" to ".20", ".20" to ".15")) {
            if (up(host)) continue
            val otherReport = hosts[other]?.takeIf { light(it, nowMs) != HealthLight.GREY }
            val seenByOther = otherReport?.checks?.firstOrNull { it.id == "peer" }?.ok == true
            return if (seenByOther) "Il telefono non raggiunge il $host, ma il $other lo vede: riavvia il router ASUS."
            else "Il telefono non raggiunge il $host: controlla alimentazione e cavo di rete del $host."
        }
        return null
    }
}
