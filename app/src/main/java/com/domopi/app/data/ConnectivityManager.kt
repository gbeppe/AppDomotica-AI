package com.domopi.app.data

import android.content.Context
import android.util.Log
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
        if (ip.isEmpty() || ip == "100.x.x.x") return@withContext false
        try {
            Log.d("CONN_CHECK", "Verifica raggiungibilità $ip:$port...")
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), 2000)
            socket.close()
            Log.d("CONN_CHECK", "$ip:$port raggiungibile!")
            true
        } catch (e: Exception) {
            Log.w("CONN_CHECK", "$ip:$port NON raggiungibile: ${e.message}")
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

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {}
        return null
    }

    fun isOnLocalSubnet(): Boolean {
        val ip = getLocalIpAddress() ?: return false
        // Rileva subnet classica casa O subnet virtuale emulatore (10.0.2.x)
        return ip.startsWith("192.168.1.") || ip.startsWith("10.0.2.")
    }
}
