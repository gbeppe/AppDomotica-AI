package com.domopi.app.data

import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.Base64

/** TinyCam Pro web server (Axis-compatible endpoints) with HTTP Basic authentication. */
data class TinyCamCamera(val name: String, val id: String)

object TinyCamCameras {
    /** Default list (was hard-coded in the screens). Format: "Nome=ID, Nome=ID". */
    const val DEFAULT = "Ingresso=936942165, Soggiorno=1708386743"

    fun parse(text: String): List<TinyCamCamera> = text.split(',').mapNotNull { item ->
        val (name, id) = item.split('=', limit = 2).map { it.trim() }.takeIf { it.size == 2 } ?: return@mapNotNull null
        if (name.isEmpty() || !id.all(Char::isDigit) || id.isEmpty()) null else TinyCamCamera(name, id)
    }
}

sealed interface CameraFailure {
    data object NotConfigured : CameraFailure
    data object Unreachable : CameraFailure
    data object Unauthorized : CameraFailure
    data object NotFound : CameraFailure
    data class Http(val status: Int) : CameraFailure
    data object InvalidImage : CameraFailure
}

fun CameraFailure.message(): String = when (this) {
    CameraFailure.NotConfigured -> "TinyCam non configurato: imposta indirizzo, utente e password in Configurazione."
    CameraFailure.Unreachable -> "Server TinyCam non raggiungibile: avvia il server web di TinyCam sul dispositivo."
    CameraFailure.Unauthorized -> "Utente o password TinyCam errati (Configurazione → TinyCam Pro)."
    CameraFailure.NotFound -> "Telecamera non trovata in TinyCam: controlla l'ID in Configurazione."
    is CameraFailure.Http -> "Il server TinyCam ha risposto con errore HTTP $status."
    CameraFailure.InvalidImage -> "Immagine della telecamera non valida."
}

class TinyCamClient(private val host: String, private val port: String,
                    private val user: String, private val pass: String) {
    val configured: Boolean get() = host.isNotBlank() && port.isNotBlank() && user.isNotBlank() && pass.isNotBlank()

    private fun open(path: String, readTimeoutMs: Int): HttpURLConnection {
        val connection = URL("http://$host:$port$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = readTimeoutMs
        connection.instanceFollowRedirects = false
        val token = Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
        connection.setRequestProperty("Authorization", "Basic $token")
        return connection
    }

    private fun failureFor(status: Int): CameraFailure = when (status) {
        401, 403 -> CameraFailure.Unauthorized
        404 -> CameraFailure.NotFound
        else -> CameraFailure.Http(status)
    }

    private inline fun <T> guarded(block: () -> Result<T>): Result<T> = try {
        block()
    } catch (_: UnknownHostException) { Result.failure(CameraException(CameraFailure.Unreachable))
    } catch (_: ConnectException) { Result.failure(CameraException(CameraFailure.Unreachable))
    } catch (_: SocketTimeoutException) { Result.failure(CameraException(CameraFailure.Unreachable))
    } catch (_: java.io.IOException) { Result.failure(CameraException(CameraFailure.Unreachable)) }

    /** One JPEG frame (for the dashboard card). */
    fun snapshot(cameraId: String): Result<ByteArray> {
        if (!configured) return Result.failure(CameraException(CameraFailure.NotConfigured))
        return guarded {
            val connection = open("/axis-cgi/jpg/image.cgi?cameraId=$cameraId", 10_000)
            try {
                val status = connection.responseCode
                if (status != 200) Result.failure(CameraException(failureFor(status)))
                else connection.inputStream.use { stream ->
                    val bytes = stream.readBytes()
                    if (MjpegFrameReader.isJpeg(bytes)) Result.success(bytes)
                    else Result.failure(CameraException(CameraFailure.InvalidImage))
                }
            } finally { connection.disconnect() }
        }
    }

    /** Continuous MJPEG stream: [onFrame] is called for each JPEG until it returns false or the stream ends. */
    fun stream(cameraId: String, onFrame: (ByteArray) -> Boolean): Result<Unit> {
        if (!configured) return Result.failure(CameraException(CameraFailure.NotConfigured))
        return guarded {
            val connection = open("/axis-cgi/mjpg/video.cgi?cameraId=$cameraId", 15_000)
            try {
                val status = connection.responseCode
                if (status != 200) Result.failure(CameraException(failureFor(status)))
                else connection.inputStream.use { stream ->
                    val reader = MjpegFrameReader(stream)
                    while (true) {
                        val frame = reader.next() ?: break
                        if (!onFrame(frame)) break
                    }
                    Result.success(Unit)
                }
            } finally { connection.disconnect() }
        }
    }
}

class CameraException(val failure: CameraFailure) : Exception(failure.toString())

/**
 * Extracts JPEG frames from an MJPEG (multipart/x-mixed-replace) stream by scanning for the JPEG
 * start (FF D8) and end (FF D9) markers, so boundaries and part headers do not matter.
 */
class MjpegFrameReader(private val input: InputStream, private val maxFrameBytes: Int = 8 * 1024 * 1024) {
    companion object {
        fun isJpeg(bytes: ByteArray) = bytes.size > 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
            bytes[bytes.size - 2] == 0xFF.toByte() && bytes[bytes.size - 1] == 0xD9.toByte()
    }

    fun next(): ByteArray? {
        var previous = -1
        // Find the start of image.
        while (true) {
            val b = input.read()
            if (b < 0) return null
            if (previous == 0xFF && b == 0xD8) break
            previous = b
        }
        val out = java.io.ByteArrayOutputStream(64 * 1024)
        out.write(0xFF); out.write(0xD8)
        previous = -1
        while (true) {
            val b = input.read()
            if (b < 0) return null
            out.write(b)
            if (previous == 0xFF && b == 0xD9) return out.toByteArray()
            if (out.size() > maxFrameBytes) return null
            previous = b
        }
    }
}
