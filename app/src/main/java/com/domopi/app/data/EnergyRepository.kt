package com.domopi.app.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import java.time.ZoneId
import java.time.ZonedDateTime

data class HistoryPoint(val timestamp: Long, val value: Float)

data class EnergyHistory(
    val solar: List<HistoryPoint> = emptyList(),
    val consumption: List<HistoryPoint> = emptyList(),
    val grid: List<HistoryPoint> = emptyList(),
    val battery: List<HistoryPoint> = emptyList(),
    val soc: List<HistoryPoint> = emptyList()
)

class EnergyRepository(private val emoncmsIp: String) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    
    private val apiKey = "e04a090163e34e823f1ae560859b1c47"

    suspend fun fetchHistory(hours: Int): EnergyHistory = withContext(Dispatchers.IO) {
        try {
            val end = System.currentTimeMillis() / 1000
            val start = end - (hours * 3600)
            val interval = if (hours <= 6) 60 else 300 // 1 min for 6h, 5 min for 24h

            // Feed IDs for Tesla Powerwall on EmonCMS (Node: TeslaPowerwall)
            val feeds = mapOf(
                "solar" to 307,
                "consumption" to 303,
                "grid" to 305,
                "battery" to 306,
                "soc" to 304
            )

            val results = feeds.mapValues { (_, id) ->
                fetchFeedData(id, start, end, interval)
            }

            EnergyHistory(
                solar = results["solar"] ?: emptyList(),
                consumption = results["consumption"] ?: emptyList(),
                grid = results["grid"] ?: emptyList(),
                battery = results["battery"] ?: emptyList(),
                soc = results["soc"] ?: emptyList()
            )
        } catch (e: Exception) {
            Log.e("EnergyRepo", "Error fetching history", e)
            EnergyHistory()
        }
    }

    suspend fun fetchGridImportSummary(): Pair<Float, Float> = withContext(Dispatchers.IO) {
        try {
            val nowZone = ZonedDateTime.now(ZoneId.systemDefault())
            val todayStartSec = nowZone.toLocalDate().atStartOfDay(nowZone.zone).toEpochSecond()
            val yesterdayStartSec = nowZone.toLocalDate().minusDays(1).atStartOfDay(nowZone.zone).toEpochSecond()
            val endSec = nowZone.toEpochSecond()
            val interval = 300 // 5-minute sampling interval

            val gridPoints = fetchFeedData(305, yesterdayStartSec, endSec, interval)
            if (gridPoints.size < 2) return@withContext Pair(0f, 0f)
            
            var kwhIeri = 0f
            var kwhOggi = 0f

            for (i in 0 until gridPoints.size - 1) {
                val pt = gridPoints[i]
                val nextPt = gridPoints[i + 1]

                // Normalizziamo i timestamp in secondi (EmonCMS data.json restituisce ms 13 cifre)
                val ptSec = if (pt.timestamp > 2000000000L) pt.timestamp / 1000 else pt.timestamp
                val nextPtSec = if (nextPt.timestamp > 2000000000L) nextPt.timestamp / 1000 else nextPt.timestamp

                val pWatts = pt.value.coerceAtLeast(0f)
                val dtSeconds = (nextPtSec - ptSec).coerceAtLeast(0L)

                if (pWatts > 0f && dtSeconds > 0) {
                    val kwh = (pWatts * dtSeconds) / (3600f * 1000f)
                    if (ptSec < todayStartSec) {
                        kwhIeri += kwh
                    } else {
                        kwhOggi += kwh
                    }
                }
            }

            Log.d("EnergyRepo", "Grid import summary (00:00 window): Ieri = $kwhIeri kWh, Oggi = $kwhOggi kWh")
            Pair(kwhIeri, kwhOggi)
        } catch (e: Exception) {
            Log.e("EnergyRepo", "Error calculating grid import summary", e)
            Pair(0f, 0f)
        }
    }

    private suspend fun fetchFeedData(feedId: Int, start: Long, end: Long, interval: Int): List<HistoryPoint> {
        if (emoncmsIp.isEmpty()) return emptyList()

        val urlPrimary = "http://$emoncmsIp/emoncms/feed/data.json?id=$feedId&start=$start&end=$end&interval=$interval&apikey=$apiKey"
        val urlFallback = "http://$emoncmsIp/feed/data.json?id=$feedId&start=$start&end=$end&interval=$interval&apikey=$apiKey"

        val responseText = try {
            client.get(urlPrimary).body<String>()
        } catch (e: Exception) {
            try {
                client.get(urlFallback).body<String>()
            } catch (e2: Exception) {
                Log.e("EnergyRepo", "Error fetching feed $feedId from $emoncmsIp: ${e2.message}")
                return emptyList()
            }
        }

        return try {
            val jsonArray = Json.parseToJsonElement(responseText).jsonArray
            Log.d("EnergyRepo", "Feed $feedId: received ${jsonArray.size} points")
            jsonArray.mapNotNull { element ->
                try {
                    val arr = element.jsonArray
                    val ts = arr[0].jsonPrimitive.long
                    val value = arr[1].jsonPrimitive.floatOrNull ?: 0f
                    HistoryPoint(ts, value)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("EnergyRepo", "Error parsing feed $feedId response: $responseText", e)
            emptyList()
        }
    }
}
