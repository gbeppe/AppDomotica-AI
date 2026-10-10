package com.domopi.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AccessPathClassifierTest {
    private fun route(last: Int = 0, prefix: Int = 24) =
        NetworkRoutePrefix(listOf(192.toByte(), 168.toByte(), 1.toByte(), last.toByte()), prefix)
    private fun network(id: String = "A", wifi: Boolean = false, vpn: Boolean = false,
        domains: List<String>? = emptyList(), routes: List<NetworkRoutePrefix>? = emptyList(),
        usable: Boolean? = true) = AndroidNetworkEvidence(id, usable, wifi, vpn, domains, routes)
    private fun classify(vararg networks: AndroidNetworkEvidence) =
        AccessPathClassifier.classify(NetworkEvidence(networks.toList(), true))

    @Test fun homeWifiSameNetwork() = assertEquals(AccessPathState.Locale,
        classify(network(wifi = true, domains = listOf("casa.zara"))))
    @Test fun wifiAndDomainAcrossNetworksNeverMatch() = assertEquals(AccessPathState.RemotoDisconnected,
        classify(network(wifi = true), network(id = "B", domains = listOf("casa.zara"))))
    @Test fun subnetWithoutDomainNeverIdentifiesHome() = assertEquals(AccessPathState.RemotoDisconnected,
        classify(network(wifi = true, routes = listOf(route()))))
    @Test fun exactVpnRoute() = assertEquals(AccessPathState.RemotoConnected,
        classify(network(), network(id = "VPN", vpn = true, routes = listOf(route()))))
    @Test fun nonVpnHomeRouteDoesNotCount() = assertEquals(AccessPathState.RemotoDisconnected,
        classify(network(routes = listOf(route())), network(id = "VPN", vpn = true)))
    @Test fun differentPrefixesDoNotCount() {
        for (prefix in listOf(8, 16, 23, 25, 32)) {
            assertFalse(route(prefix = prefix).isHomeSubnet())
            assertEquals(AccessPathState.RemotoDisconnected,
                classify(network(vpn = true, routes = listOf(route(prefix = prefix)))))
        }
        assertFalse(route(last = 121, prefix = 32).isHomeSubnet())
        assertFalse(NetworkRoutePrefix(listOf(10, 0, 0, 0).map { it.toByte() }, 24).isHomeSubnet())
        assertFalse(NetworkRoutePrefix(List(16) { 0.toByte() }, 24).isHomeSubnet())
    }
    @Test fun unavailableVpnRoutesAreUnknown() = assertEquals(AccessPathState.Unknown,
        classify(network(), network(id = "VPN", vpn = true, routes = null)))
    @Test fun confirmedNoHomeRouteIsRemoteRed() = assertEquals(AccessPathState.RemotoDisconnected,
        classify(network(vpn = true, routes = listOf(route(last = 121, prefix = 32)))))
    @Test fun noUsableNetwork() = assertEquals(AccessPathState.NonInRete,
        classify(network(usable = false)))
    @Test fun emptyConfirmedEnumeration() = assertEquals(AccessPathState.NonInRete, classify())
    @Test fun missingCapabilitiesAreUnknown() = assertEquals(AccessPathState.Unknown,
        classify(AndroidNetworkEvidence("missing")))
    @Test fun incompleteEnumerationIsUnknown() = assertEquals(AccessPathState.Unknown,
        AccessPathClassifier.classify(NetworkEvidence(listOf(network()), false)))
    @Test fun unavailableWifiDomainsAreUnknown() = assertEquals(AccessPathState.Unknown,
        classify(network(wifi = true, domains = null)))
    @Test fun vpnDoesNotMaskSeparateHomeWifi() = assertEquals(AccessPathState.Locale,
        classify(network(wifi = true, domains = listOf("casa.zara")), network(id = "VPN", vpn = true)))
    @Test fun unusableHomeWifiNeverMatches() = assertEquals(AccessPathState.RemotoDisconnected,
        classify(network(wifi = true, domains = listOf("casa.zara"), usable = false), network(id = "B")))
    @Test fun domainMustBeExactToken() = assertEquals(AccessPathState.RemotoDisconnected,
        classify(network(wifi = true, domains = listOf("other.casa.zara", "casa.zara.example"))))
    @Test fun coreStateCannotInfluencePureNetworkInput() {
        val evidence = NetworkEvidence(listOf(network(wifi = true, domains = listOf("casa.zara"))), true)
        assertEquals(AccessPathState.Locale, AccessPathClassifier.classify(evidence))
        assertEquals(AccessPathState.Locale, AccessPathClassifier.classify(evidence.copy()))
    }
}
