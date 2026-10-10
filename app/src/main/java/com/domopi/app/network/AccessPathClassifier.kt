package com.domopi.app.network

/** Pure classification of the frozen Access Path matrix. */
object AccessPathClassifier {
    fun classify(evidence: NetworkEvidence, home: HomeNetworkSettings = HomeNetworkSettings()): AccessPathState {
        val networks = evidence.networks
        if (networks.none { it.hasUsableIp == true }) {
            return if (evidence.enumerationComplete && networks.all { it.hasUsableIp == false })
                AccessPathState.NonInRete else AccessPathState.Unknown
        }
        if (networks.any { isHomeLan(it, home.domain) }) return AccessPathState.Locale

        // Missing Wi-Fi domains/capabilities cannot establish that we are outside Home LAN.
        if (!evidence.enumerationComplete || networks.any {
            it.hasUsableIp == null || (it.hasUsableIp == true &&
                (it.isWifi == null || it.isVpn == null ||
                    (it.isWifi == true && it.isVpn == false && it.domains == null)))
        }) return AccessPathState.Unknown

        val homeSubnet = home.parsedSubnet
        if (networks.any { it.isVpn == true && it.routes?.any { route -> route.isHomeSubnet(homeSubnet) } == true })
            return AccessPathState.RemotoConnected

        // Absence requires known VPN capabilities on every network and routes on every VPN.
        if (networks.any { it.isVpn == null || (it.isVpn == true && it.routes == null) })
            return AccessPathState.Unknown
        return AccessPathState.RemotoDisconnected
    }

    private fun isHomeLan(network: AndroidNetworkEvidence, homeDomain: String): Boolean =
        network.hasUsableIp == true && network.isWifi == true && network.isVpn == false &&
            network.domains?.any { it == homeDomain } == true
}
