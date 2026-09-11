package com.domopi.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Separate read-only service: never publishes MQTT commands. */
class HouseAiRepository {
    suspend fun report(baseUrl: String, token: String, day: String): JSONObject = withContext(Dispatchers.IO) {
        java.time.LocalDate.parse(day)
        val base = URL(baseUrl.trim().trimEnd('/'))
        require(base.protocol in listOf("http", "https") && base.userInfo == null && base.query == null && base.ref == null)
        require(token.isNotBlank())
        val connection = URL("${base.toExternalForm()}/v1/report?day=$day").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 60000
            connection.setRequestProperty("Authorization", "Bearer $token")
            check(connection.responseCode == 200) { "Servizio non disponibile (HTTP ${connection.responseCode})" }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text).also { check(it.optString("schema") == "house_ai.daily_report.v1") }
        } finally {
            connection.disconnect()
        }
    }
}
