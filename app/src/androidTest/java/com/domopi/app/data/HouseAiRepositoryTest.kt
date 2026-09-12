package com.domopi.app.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real loopback HTTP with a synthetic response; never connects to household services. */
class HouseAiRepositoryTest {
    private fun exchange(status: Int = 200, body: String, action: (String) -> Unit): String {
        ServerSocket(0).use { server ->
            server.soTimeout = 10000
            val executor = Executors.newSingleThreadExecutor()
            try {
                val request = executor.submit<String> {
                    server.accept().use { socket ->
                        socket.soTimeout = 10000
                        val reader = socket.getInputStream().bufferedReader()
                        val lines = mutableListOf<String>()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            lines += line
                        }
                        val size = lines.first { it.startsWith("Content-Length:", true) }.substringAfter(':').trim().toInt()
                        val data = CharArray(size)
                        var received = 0
                        while (received < size) {
                            val count = reader.read(data, received, size - received)
                            check(count > 0)
                            received += count
                        }
                        val bytes = body.toByteArray()
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 $status Test\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(bytes)
                            flush()
                        }
                        lines.joinToString("\n") + "\n\n" + String(data)
                    }
                }
                action("http://127.0.0.1:${server.localPort}")
                return request.get(10, TimeUnit.SECONDS)
            } finally { executor.shutdownNow() }
        }
    }

    @Test fun serializesQuestionAndSnapshotAndParsesTheSameAnswer() {
        val body = """{"schema":"house_ai.assistant_answer.v1","status":"partial","question":"Rete?","answer":"Immissione: 500 W. Freschezza ignota.","generated_at":"2026-09-12T18:00:00+02:00","results":[],"limitations":["Dato ricevuto, non conferma fisica."]}"""
        val state = EnergySmartState().observe("energy/grid/power_raw/stat", "-500", 1789200000000, true,
            "zara/interface/energy/grid/power_raw/stat")
        val request = exchange(body = body) { url ->
            val result = runBlocking { HouseAiRepository().assistant(url, "test-token", "Rete?", state, true) }
            assertEquals("Immissione: 500 W. Freschezza ignota.", result.text)
            assertEquals("Dati con limiti", result.statusLabel)
            assertEquals(1, result.evidence.size)
        }
        assertTrue(request.startsWith("POST /v1/assistant/query HTTP/1.1"))
        assertTrue(request.contains("Authorization: Bearer test-token", true))
        val payload = JSONObject(request.substringAfter("\n\n"))
        assertEquals("Rete?", payload.getString("question"))
        val observation = payload.getJSONObject("current_energy").getJSONObject("observations").getJSONObject("grid_power_w")
        assertEquals(-500.0, observation.getDouble("value"), 0.0)
        assertTrue(observation.getBoolean("retained"))
    }

    @Test fun authenticationFailureAndInvalidSchemaAreExplicit() {
        for ((status, body) in listOf(401 to "{}", 200 to "{}")) {
            exchange(status, body) { url ->
                var failed = false
                try { runBlocking { HouseAiRepository().assistant(url, "test-token", "Rete?", EnergySmartState(), false) } }
                catch (_: Exception) { failed = true }
                assertTrue(failed)
            }
        }
    }
}
