package com.domopi.app.network


/** One record per Android Network; null means the OS did not expose the fact. */
data class AndroidNetworkEvidence(
    val networkId: String,
    val hasUsableIp: Boolean? = null,
    val isWifi: Boolean? = null,
    val isVpn: Boolean? = null,
    val domains: List<String>? = null,
    val routes: List<NetworkRoutePrefix>? = null
)

/** Structured destination from Android IpPrefix, without text heuristics. */
data class NetworkRoutePrefix(val addressBytes: List<Byte>, val prefixLength: Int) {
    /** Exact match with the configured home subnet (L-1c); null subnet matches nothing. */
    fun isHomeSubnet(home: Ipv4Subnet? = HomeNetworkSettings().parsedSubnet): Boolean =
        home != null && prefixLength == home.prefixLength && addressBytes == home.addressBytes
}

data class NetworkEvidence(
    val networks: List<AndroidNetworkEvidence> = emptyList(),
    val enumerationComplete: Boolean = false
)
