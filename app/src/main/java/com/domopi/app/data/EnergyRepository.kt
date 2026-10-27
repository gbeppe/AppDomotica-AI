package com.domopi.app.data

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import java.time.ZoneId
import java.time.ZonedDateTime

data class HistoryPoint(val timestamp: Long, val value: Float)

/**
 * Powerwall gateway firmware (feeds 453, 476, 477, TESLA-FW-INSTALL.1). The gateway does not report when the
 * firmware was installed: Node-RED takes the gateway boot time when the version changes ([installedExact]),
 * otherwise the last known boot.
 */
data class GatewayFirmware(val version: String, val installedSec: Long?, val installedExact: Boolean)

data class EnergyHistory(
    val solar: List<HistoryPoint> = emptyList(),
    val consumption: List<HistoryPoint> = emptyList(),
    val grid: List<HistoryPoint> = emptyList(),
    val battery: List<HistoryPoint> = emptyList(),
    val soc: List<HistoryPoint> = emptyList()
)

/** Why EmonCMS data could not be shown: never rendered as an empty or zero chart. */
sealed interface EnergyFetchError {
    data object NotConfigured : EnergyFetchError
    data object Unreachable : EnergyFetchError
    data class Http(val status: Int) : EnergyFetchError
    data class Rejected(val message: String) : EnergyFetchError
    data object InvalidResponse : EnergyFetchError
    data object NoData : EnergyFetchError
}

sealed interface EnergyResult<out T> {
    data class Success<T>(val value: T) : EnergyResult<T>
    data class Failure(val error: EnergyFetchError) : EnergyResult<Nothing>
}

/** One EmonCMS sample; null value = no measurement stored for that slot (a gap, not zero). */
data class FeedSample(val timestamp: Long, val value: Float?)

fun describeEnergyError(error: EnergyFetchError): String = when (error) {
    EnergyFetchError.NotConfigured -> "EmonCMS non configurato: imposta indirizzo e chiave API di lettura in Configurazione."
    EnergyFetchError.Unreachable -> "EmonCMS non raggiungibile: verifica Wi-Fi o VPN."
    is EnergyFetchError.Http -> "EmonCMS ha risposto con errore HTTP ${error.status}."
    is EnergyFetchError.Rejected -> "EmonCMS ha rifiutato la richiesta: ${error.message}"
    EnergyFetchError.InvalidResponse -> "Risposta di EmonCMS non riconosciuta."
    EnergyFetchError.NoData -> "Nessun dato registrato da EmonCMS nel periodo."
}

object EmonCmsParser {
    /** Classifies an HTTP answer of feed/data.json without coercing missing values to zero. */
    fun parseFeed(status: Int, body: String): EnergyResult<List<FeedSample>> {
        if (status !in 200..299) return EnergyResult.Failure(EnergyFetchError.Http(status))
        val element = try {
            Json.parseToJsonElement(body)
        } catch (_: Exception) {
            return EnergyResult.Failure(EnergyFetchError.InvalidResponse)
        }
        if (element is JsonObject) {
            val message = (element["message"] as? JsonPrimitive)?.contentOrNull
            return EnergyResult.Failure(
                if (message.isNullOrBlank()) EnergyFetchError.InvalidResponse
                else EnergyFetchError.Rejected(message.take(120))
            )
        }
        if (element !is JsonArray) return EnergyResult.Failure(EnergyFetchError.InvalidResponse)
        return EnergyResult.Success(element.mapNotNull { item ->
            val pair = item as? JsonArray ?: return@mapNotNull null
            val ts = (pair.getOrNull(0) as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            FeedSample(ts, (pair.getOrNull(1) as? JsonPrimitive)?.floatOrNull)
        })
    }

    /** Values of feed/fetch.json ([263400, 1791182201, 0]); null for a feed without a value. */
    fun parseFetch(status: Int, body: String): List<Double?>? {
        if (status !in 200..299) return null
        val element = try { Json.parseToJsonElement(body) } catch (_: Exception) { return null }
        return (element as? JsonArray)?.map { (it as? JsonPrimitive)?.doubleOrNull }
    }

    /** Powerwall gateway firmware as stored by Node-RED on the .15 (263400 -> "26.34.0"). */
    fun firmwareText(code: Double?): String? {
        val c = code?.toLong()?.takeIf { it > 0 } ?: return null
        return "${c / 10000}.${c / 100 % 100}.${c % 100}"
    }

    fun gatewayFirmware(values: List<Double?>?): GatewayFirmware? {
        if (values == null) return null
        val version = firmwareText(values.getOrNull(0)) ?: return null
        val installed = values.getOrNull(1)?.toLong()?.takeIf { it > 0 }
        return GatewayFirmware(version, installed, values.getOrNull(2) == 1.0)
    }

    /** Normalizes EmonCMS millisecond timestamps to seconds. */
    fun seconds(timestamp: Long): Long = if (timestamp > 2_000_000_000L) timestamp / 1000 else timestamp

    /**
     * Grid import in kWh split at [todayStartSec]. A sample contributes only up to the
     * next sample and only across one regular slot: gaps are never integrated.
     */
    fun gridImportKwh(samples: List<FeedSample>, todayStartSec: Long, intervalSec: Long): Pair<Float, Float> {
        var kwhIeri = 0f
        var kwhOggi = 0f
        for (i in 0 until samples.size - 1) {
            val value = samples[i].value ?: continue
            val ptSec = seconds(samples[i].timestamp)
            val dtSeconds = seconds(samples[i + 1].timestamp) - ptSec
            if (value <= 0f || dtSeconds <= 0 || dtSeconds > 2 * intervalSec) continue
            val kwh = (value * dtSeconds) / (3600f * 1000f)
            if (ptSec < todayStartSec) kwhIeri += kwh else kwhOggi += kwh
        }
        return kwhIeri to kwhOggi
    }
}

/** [apiKey]: EmonCMS read-only key from the encrypted settings, never from the code. */
class EnergyRepository(private val emoncmsIp: String, private val apiKey: String) {
    private val client = HttpClient()

    suspend fun fetchHistory(hours: Int): EnergyResult<EnergyHistory> = withContext(Dispatchers.IO) {
        val end = System.currentTimeMillis() / 1000
        val start = end - (hours * 3600)
        val interval = if (hours <= 6) 60 else 300 // 1 min for 6h, 5 min for 24h

        // Feed IDs for Tesla Powerwall on EmonCMS (Node: TeslaPowerwall)
        val feeds = linkedMapOf("solar" to 307, "consumption" to 303, "grid" to 305, "battery" to 306, "soc" to 304)
        val series = mutableMapOf<String, List<HistoryPoint>>()
        for ((name, id) in feeds) {
            when (val result = fetchFeedData(id, start, end, interval)) {
                is EnergyResult.Failure -> return@withContext result
                is EnergyResult.Success -> series[name] = result.value.mapNotNull { s -> s.value?.let { HistoryPoint(s.timestamp, it) } }
            }
        }
        if (series.getValue("solar").isEmpty() && series.getValue("consumption").isEmpty()) {
            return@withContext EnergyResult.Failure(EnergyFetchError.NoData)
        }
        EnergyResult.Success(EnergyHistory(series.getValue("solar"), series.getValue("consumption"),
            series.getValue("grid"), series.getValue("battery"), series.getValue("soc")))
    }

    /** Yesterday/today grid import (kWh), or the reason it cannot be computed. */
    suspend fun fetchGridImportSummary(): EnergyResult<Pair<Float, Float>> = withContext(Dispatchers.IO) {
        val nowZone = ZonedDateTime.now(ZoneId.systemDefault())
        val todayStartSec = nowZone.toLocalDate().atStartOfDay(nowZone.zone).toEpochSecond()
        val yesterdayStartSec = nowZone.toLocalDate().minusDays(1).atStartOfDay(nowZone.zone).toEpochSecond()
        val interval = 300 // 5-minute sampling interval
        when (val result = fetchFeedData(305, yesterdayStartSec, nowZone.toEpochSecond(), interval)) {
            is EnergyResult.Failure -> result
            is EnergyResult.Success ->
                if (result.value.count { it.value != null } < 2) EnergyResult.Failure(EnergyFetchError.NoData)
                else EnergyResult.Success(EmonCmsParser.gridImportKwh(result.value, todayStartSec, interval.toLong()))
        }
    }

    /** Latest firmware values; null when EmonCMS is not configured or does not answer. */
    suspend fun fetchGatewayFirmware(): GatewayFirmware? = withContext(Dispatchers.IO) {
        if (emoncmsIp.isEmpty() || apiKey.isBlank()) return@withContext null
        val query = "feed/fetch.json?ids=453,476,477&apikey=$apiKey"
        for (url in listOf("http://$emoncmsIp/emoncms/$query", "http://$emoncmsIp/$query")) {
            try {
                val response = client.get(url)
                EmonCmsParser.parseFetch(response.status.value, response.bodyAsText())?.let {
                    return@withContext EmonCmsParser.gatewayFirmware(it)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        null
    }

    private suspend fun fetchFeedData(feedId: Int, start: Long, end: Long, interval: Int): EnergyResult<List<FeedSample>> {
        if (emoncmsIp.isEmpty() || apiKey.isBlank()) return EnergyResult.Failure(EnergyFetchError.NotConfigured)
        val query = "feed/data.json?id=$feedId&start=$start&end=$end&interval=$interval&apikey=$apiKey"
        val primary = request("http://$emoncmsIp/emoncms/$query")
        if (primary is EnergyResult.Success || primary.isFinal()) return primary
        // Installations serving EmonCMS at the web root.
        val fallback = request("http://$emoncmsIp/$query")
        if (fallback is EnergyResult.Success) return fallback
        val error = if ((primary as EnergyResult.Failure).error == EnergyFetchError.Http(404)) fallback else primary
        Log.w("EnergyRepo", "Feed $feedId unavailable: ${(error as EnergyResult.Failure).error}")
        return error
    }

    private fun EnergyResult<List<FeedSample>>.isFinal() =
        this is EnergyResult.Failure && (error is EnergyFetchError.Rejected || error == EnergyFetchError.Unreachable)

    private suspend fun request(url: String): EnergyResult<List<FeedSample>> = try {
        val response = client.get(url)
        EmonCmsParser.parseFeed(response.status.value, response.bodyAsText())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        EnergyResult.Failure(EnergyFetchError.Unreachable)
    }
}
