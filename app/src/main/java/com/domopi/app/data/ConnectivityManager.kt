package com.domopi.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

enum class ConnectionMode {
    LOCAL, REMOTE, OFFLINE
}

class DomoPiConnectivityManager(private val context: Context) {

    private val _connectionMode = MutableStateFlow(ConnectionMode.LOCAL)
    val connectionMode: StateFlow<ConnectionMode> = _connectionMode

    fun updateConnectionMode(mode: ConnectionMode) {
        _connectionMode.value = mode
    }

    suspend fun checkServiceReachable(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), 1000)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }
}
