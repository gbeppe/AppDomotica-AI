package com.domopi.app.data

import org.json.JSONException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Short Italian messages for House AI failures; AI Smart also reads them aloud.
 * The backend sends error codes (rate_limited, planner_unavailable, source_unavailable);
 * older backends send free text and get the generic messages.
 */
object HouseAiErrors {
    const val DEFAULT_RETRY_S = 30
    const val UNREACHABLE = "Assistente non raggiungibile o risposta non valida. " +
        "In casa basta il Wi-Fi; fuori casa serve Tailscale attivo. Poi riprova."

    // Error bodies are tiny flat JSON objects; plain patterns keep this testable on the JVM.
    private val ERROR = Regex(""""error"\s*:\s*"([^"]{1,64})"""")
    private val RETRY = Regex(""""retry_after_s"\s*:\s*(\d{1,4})\b""")

    fun http(code: Int, body: String?): String {
        val error = body?.let { ERROR.find(it)?.groupValues?.get(1) }.orEmpty()
        return when {
            code == 401 -> "Token backend non valido."
            code == 400 -> "Domanda o dati inviati non validi."
            code == 429 -> {
                val seconds = body?.let { RETRY.find(it)?.groupValues?.get(1)?.toInt() }
                    ?.takeIf { it in 1..60 } ?: DEFAULT_RETRY_S
                "Troppe domande ravvicinate: riprova tra $seconds secondi."
            }
            code == 502 && error == "planner_unavailable" ->
                "Il servizio di intelligenza artificiale non risponde. Riprova tra poco."
            code == 502 && error == "source_unavailable" ->
                "I dati di casa non sono raggiungibili in questo momento."
            code == 502 && error == "Backend unavailable" -> "Il backend di casa non risponde."
            code == 502 -> "Pianificatore o sorgente non disponibili. Riprova più tardi."
            else -> "Servizio non disponibile (HTTP $code)."
        }
    }

    fun network(error: Throwable): String = when (error) {
        is UnknownHostException -> "Assistente non trovato: fuori casa attiva Tailscale."
        is SSLException -> "Connessione sicura non verificata."
        is SocketTimeoutException, is ConnectException, is NoRouteToHostException ->
            "Il server di casa non risponde."
        is JSONException -> "Risposta dell'assistente non valida."
        else -> UNREACHABLE
    }
}
