package com.domopi.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import com.domopi.app.BuildConfig

sealed class GithubStatus {
    object Checking : GithubStatus()
    data class UpToDate(val latestHash: String) : GithubStatus()
    data class Behind(val behindBy: Int, val latestHash: String) : GithubStatus()
    data class LocalDev(val isDirty: Boolean) : GithubStatus()
    data class Error(val message: String) : GithubStatus()
}

object GithubVersionChecker {

    private val _status = MutableStateFlow<GithubStatus>(GithubStatus.Checking)
    val status: StateFlow<GithubStatus> = _status

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkVersion() = withContext(Dispatchers.IO) {
        val currentHash = BuildConfig.GIT_HASH.replace("+", "").trim()
        val isDirty = BuildConfig.GIT_HASH.contains("+")

        if (currentHash.isEmpty() || currentHash == "unknown") {
            _status.value = GithubStatus.Error("Git Hash non disponibile")
            return@withContext
        }

        try {
            _status.value = GithubStatus.Checking
            val url = URL("https://api.github.com/repos/gbeppe/AppDomotica-AI/compare/$currentHash...main")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "ZAI-Android-App")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 5000
                readTimeout = 5000
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = json.parseToJsonElement(responseText).jsonObject
                
                val statusStr = jsonObj["status"]?.jsonPrimitive?.content ?: "unknown"
                val behindBy = jsonObj["behind_by"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                
                // Estraiamo lo SHA del commit di destinazione se disponibile
                val commits = jsonObj["commits"]
                val latestSha = if (commits != null && commits.toString().contains("sha")) {
                    jsonObj["base_commit"]?.jsonObject?.get("sha")?.jsonPrimitive?.content?.take(7) ?: currentHash
                } else {
                    currentHash
                }

                _status.value = when {
                    statusStr == "identical" && isDirty -> GithubStatus.LocalDev(isDirty = true)
                    statusStr == "identical" -> GithubStatus.UpToDate(latestSha)
                    statusStr == "behind" -> GithubStatus.Behind(behindBy, latestSha)
                    statusStr == "ahead" -> GithubStatus.LocalDev(isDirty = isDirty)
                    else -> GithubStatus.UpToDate(currentHash)
                }
                Log.d("GithubChecker", "Status: ${_status.value}")
            } else if (connection.responseCode == 404) {
                // Se il commit locale non esiste su GitHub, siamo in dev/local
                _status.value = GithubStatus.LocalDev(isDirty = isDirty)
                Log.d("GithubChecker", "404 - Local dev build: ${_status.value}")
            } else {
                _status.value = GithubStatus.Error("HTTP ${connection.responseCode}")
                Log.e("GithubChecker", "HTTP error: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e("GithubChecker", "Errore verifica versione GitHub", e)
            _status.value = GithubStatus.Error("Offline / Errore Rete")
        }
    }
}
