package com.domopi.app.data

import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import java.util.Base64
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TinyCamTest {
    private fun jpeg(tag: Int) = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), tag.toByte(), 1, 2, 0xFF.toByte(), 0xD9.toByte())
    private val frames = listOf(jpeg(1), jpeg(2), jpeg(3))
    private fun multipart(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (f in frames) {
            out.write("--myboundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${f.size}\r\n\r\n".toByteArray())
            out.write(f); out.write("\r\n".toByteArray())
        }
        return out.toByteArray()
    }

    /** Minimal HTTP/1.0 server on a socket (com.sun.net.httpserver is not on the Android test classpath). */
    private lateinit var server: ServerSocket
    private val auth = "Basic " + Base64.getEncoder().encodeToString("user:secret".toByteArray())

    @Before fun start() {
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val socket = server
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val conn = try { socket.accept() } catch (_: Exception) { break }
                conn.use { c ->
                    val reader = c.getInputStream().bufferedReader()
                    val requestLine = reader.readLine() ?: return@use
                    var authorization: String? = null
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Authorization:", ignoreCase = true)) authorization = line.substringAfter(":").trim()
                    }
                    val target = requestLine.split(" ")[1]
                    val id = target.substringAfter("cameraId=")
                    val (status, body) = when {
                        authorization != auth -> 401 to "unauthorized".toByteArray()
                        id != "42" -> 404 to "no camera".toByteArray()
                        target.contains("image.cgi") -> 200 to frames[0]
                        else -> 200 to multipart()
                    }
                    val out = c.getOutputStream()
                    out.write("HTTP/1.0 $status X\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    out.write(body); out.flush()
                }
            }
        }
    }

    @After fun stop() = server.close()

    private fun client(pass: String = "secret") = TinyCamClient("127.0.0.1", server.localPort.toString(), "user", pass)
    private fun failure(r: Result<*>) = (r.exceptionOrNull() as CameraException).failure

    @Test fun readerExtractsFramesWhateverTheBoundary() {
        val reader = MjpegFrameReader(ByteArrayInputStream(multipart()))
        frames.forEach { assertArrayEquals(it, reader.next()) }
        assertNull(reader.next())
    }

    @Test fun snapshotWithCredentials() {
        assertArrayEquals(frames[0], client().snapshot("42").getOrThrow())
    }

    @Test fun streamDeliversEveryFrame() {
        val got = mutableListOf<ByteArray>()
        assertTrue(client().stream("42") { got += it; true }.isSuccess)
        assertEquals(3, got.size)
        assertArrayEquals(frames[2], got[2])
    }

    @Test fun clearFailures() {
        assertEquals(CameraFailure.Unauthorized, failure(client("wrong").snapshot("42")))
        assertEquals(CameraFailure.NotFound, failure(client().snapshot("7")))
        assertEquals(CameraFailure.NotConfigured, failure(TinyCamClient("", "8083", "u", "p").snapshot("42")))
        val unreachable = TinyCamClient("127.0.0.1", server.localPort.toString(), "user", "secret")
        server.close()
        assertEquals(CameraFailure.Unreachable, failure(unreachable.snapshot("42")))
        start()   // @After closes it again
    }

    @Test fun cameraListParsing() {
        assertEquals(listOf(TinyCamCamera("Ingresso", "936942165"), TinyCamCamera("Soggiorno", "1708386743")),
            TinyCamCameras.parse(TinyCamCameras.DEFAULT))
        assertEquals(listOf(TinyCamCamera("Garage", "5")), TinyCamCameras.parse("Garage=5, rotta, X=abc, =3"))
    }
}
