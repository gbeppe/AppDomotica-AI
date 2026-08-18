package com.domopi.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ConnectionMode {
    LOCAL, REMOTE, OFFLINE
}

class DomoPiConnectivityManager(private val context: Context) {

    private val _connectionMode = MutableStateFlow(ConnectionMode.OFFLINE)
    val connectionMode: StateFlow<ConnectionMode> = _connectionMode

    suspend fun checkConnectivity(localIp: String) {
        val isReachable = withContext(Dispatchers.IO) {
            try {
                val address = InetAddress.getByName(localIp)
                address.isReachable(1000) // 1 second timeout
            } catch (e: Exception) {
                false
            }
        }

        _connectionMode.value = if (isReachable) {
            ConnectionMode.LOCAL
        } else {
            // Qui potremmo aggiungere un check per Tailscale se necessario
            ConnectionMode.REMOTE
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
