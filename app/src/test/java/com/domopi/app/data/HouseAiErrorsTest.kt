package com.domopi.app.data

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class HouseAiErrorsTest {
    @Test
    fun rateLimitUsesTheBackendWait() {
        assertEquals("Troppe domande ravvicinate: riprova tra 12 secondi.",
            HouseAiErrors.http(429, """{"error": "rate_limited", "retry_after_s": 12}"""))
    }

    @Test
    fun rateLimitWithoutOrWithOddWaitFallsBackTo30Seconds() {
        listOf(null, "", "not json", """{"retry_after_s": 0}""", """{"retry_after_s": 900}""").forEach {
            assertEquals("Troppe domande ravvicinate: riprova tra 30 secondi.", HouseAiErrors.http(429, it))
        }
    }

    @Test
    fun plannerAndSourceFailuresAreDistinguished() {
        assertEquals("Il servizio di intelligenza artificiale non risponde. Riprova tra poco.",
            HouseAiErrors.http(502, """{"error": "planner_unavailable"}"""))
        assertEquals("I dati di casa non sono raggiungibili in questo momento.",
            HouseAiErrors.http(502, """{"error": "source_unavailable"}"""))
        assertEquals("Il backend di casa non risponde.",
            HouseAiErrors.http(502, """{"error": "Backend unavailable"}"""))
    }

    @Test
    fun olderBackendsKeepTheGenericMessages() {
        assertEquals("Pianificatore o sorgente non disponibili. Riprova più tardi.",
            HouseAiErrors.http(502, """{"error": "Planner or source unavailable"}"""))
        assertEquals("Token backend non valido.", HouseAiErrors.http(401, null))
        assertEquals("Domanda o dati inviati non validi.", HouseAiErrors.http(400, "{}"))
        assertEquals("Servizio non disponibile (HTTP 503).", HouseAiErrors.http(503, null))
    }

    @Test
    fun networkFailuresSayWhatToCheck() {
        assertEquals("Assistente non trovato: fuori casa attiva Tailscale.",
            HouseAiErrors.network(UnknownHostException("domopi.tailf30ba8.ts.net")))
        assertEquals("Connessione sicura non verificata.", HouseAiErrors.network(SSLHandshakeException("x")))
        assertEquals("Il server di casa non risponde.", HouseAiErrors.network(ConnectException()))
        assertEquals("Il server di casa non risponde.", HouseAiErrors.network(SocketTimeoutException()))
        assertEquals("Risposta dell'assistente non valida.", HouseAiErrors.network(JSONException("x")))
        assertEquals(HouseAiErrors.UNREACHABLE, HouseAiErrors.network(RuntimeException()))
    }
}
