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

    private suspend fun fetchFeedData(feedId: Int, start: Long, end: Long, interval: Int): List<HistoryPoint> {
        val url = "http://$emoncmsIp/emoncms/feed/data.json?id=$feedId&start=$start&end=$end&interval=$interval&apikey=$apiKey"
        return try {
            val response: JsonArray = client.get(url).body()
            Log.d("EnergyRepo", "Feed $feedId: received ${response.size} points")
            if (response.isNotEmpty()) {
                Log.d("EnergyRepo", "Feed $feedId sample: ${response[0]}")
            }
            response.mapNotNull { 
                try {
                    val arr = it.jsonArray
                    val ts = arr[0].jsonPrimitive.long
                    val value = arr[1].jsonPrimitive.floatOrNull ?: 0f
                    HistoryPoint(ts, value)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("EnergyRepo", "Error fetching feed $feedId", e)
            emptyList()
        }
    }
}
