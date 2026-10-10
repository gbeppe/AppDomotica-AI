package com.domopi.app.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class AndroidNetworkCollector(private val context: Context) {
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun collectEvidence(): NetworkEvidence {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkEvidence()
        val networks = manager.allNetworks.map { network ->
            val capabilities = manager.getNetworkCapabilities(network)
            val properties = manager.getLinkProperties(network)
            AndroidNetworkEvidence(
                networkId = network.toString(),
                hasUsableIp = capabilities?.let {
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                },
                isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                isVpn = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                domains = properties?.let {
                    it.domains?.split(Regex("[\\s,]+"))?.filter { token -> token.isNotEmpty() }
                        ?: emptyList()
                },
                routes = properties?.routes?.map { route ->
                    NetworkRoutePrefix(route.destination.address.address.toList(), route.destination.prefixLength)
                }
            )
        }
        return NetworkEvidence(networks = networks, enumerationComplete = true)
    }
}
