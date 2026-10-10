package com.domopi.app.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

class AndroidNetworkObserver(context: Context) {
    private val appContext = context.applicationContext

    @SuppressLint("MissingPermission")
    fun observe(): Flow<NetworkEvidence> {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return flowOf(NetworkEvidence())
        val collector = AndroidNetworkCollector(appContext)
        return NetworkEvidenceObserver(NetworkInvalidations { invalidate ->
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { invalidate() }
                override fun onLost(network: Network) { invalidate() }
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { invalidate() }
                override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) { invalidate() }
            }
            // Observe all app-visible networks, including VPN and non-default networks.
            val request = NetworkRequest.Builder().clearCapabilities().build()
            manager.registerNetworkCallback(request, callback)
            AutoCloseable { manager.unregisterNetworkCallback(callback) }
        }, collector::collectEvidence).observe().flowOn(Dispatchers.IO)
    }
}
