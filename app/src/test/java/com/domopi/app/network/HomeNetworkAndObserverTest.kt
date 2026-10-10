package com.domopi.app.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from WallBox TopBarIndicatorsUnitTest / ReactiveNetworkUnitTest (network part only). */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeNetworkAndObserverTest {
    private fun wifi(domain: String) = AndroidNetworkEvidence("wifi", true, true, false, listOf(domain), emptyList())
    private fun vpn(a: Int, b: Int, c: Int, d: Int, prefix: Int) = AndroidNetworkEvidence("vpn", true, false, true,
        emptyList(), listOf(NetworkRoutePrefix(listOf(a, b, c, d).map { it.toByte() }, prefix)))
    private fun cellular() = AndroidNetworkEvidence("cell", true, false, false, emptyList(), emptyList())

    @Test fun defaultsAreCasaZaraAndHomeSubnet() {
        assertEquals("casa.zara", HomeNetworkSettings.DEFAULT_DOMAIN)
        assertEquals("192.168.1.0/24", HomeNetworkSettings.DEFAULT_SUBNET)
    }

    @Test fun classifierUsesConfiguredHomeDomainAndSubnet() {
        val custom = HomeNetworkSettings(domain = "home.lan", subnet = "10.0.0.0/16")
        assertEquals(AccessPathState.Locale,
            AccessPathClassifier.classify(NetworkEvidence(listOf(wifi("home.lan")), true), custom))
        assertNotEquals(AccessPathState.Locale,
            AccessPathClassifier.classify(NetworkEvidence(listOf(wifi("casa.zara")), true), custom))
        assertEquals(AccessPathState.RemotoConnected,
            AccessPathClassifier.classify(NetworkEvidence(listOf(cellular(), vpn(10, 0, 0, 0, 16)), true), custom))
        assertEquals(AccessPathState.RemotoDisconnected,
            AccessPathClassifier.classify(NetworkEvidence(listOf(cellular(), vpn(192, 168, 1, 0, 24)), true), custom))
        // Defaults unchanged.
        assertEquals(AccessPathState.Locale, AccessPathClassifier.classify(NetworkEvidence(listOf(wifi("casa.zara")), true)))
        assertEquals(AccessPathState.RemotoConnected,
            AccessPathClassifier.classify(NetworkEvidence(listOf(cellular(), vpn(192, 168, 1, 0, 24)), true)))
    }

    @Test fun invalidStoredSubnetMatchesNoRoute() {
        val broken = HomeNetworkSettings(subnet = "not-a-subnet")
        assertEquals(AccessPathState.RemotoDisconnected,
            AccessPathClassifier.classify(NetworkEvidence(listOf(cellular(), vpn(192, 168, 1, 0, 24)), true), broken))
    }

    @Test fun strictDomainAndSubnetValidation() {
        for (ok in listOf("casa.zara", "home.lan", "a-b.example.com", "x")) assertTrue(ok, HomeNetworkSettings.isValidDomain(ok))
        for (bad in listOf("", " casa.zara", "casa zara", "casa..zara", "-casa.zara", "casa_zara", "casa.zara."))
            assertFalse(bad, HomeNetworkSettings.isValidDomain(bad))
        assertEquals(Ipv4Subnet(listOf(192, 168, 1, 0).map { it.toByte() }, 24), Ipv4Subnet.parse("192.168.1.0/24"))
        for (ok in listOf("10.0.0.0/8", "192.168.1.128/25", "0.0.0.0/0", "192.168.1.5/32")) assertTrue(ok, HomeNetworkSettings.isValidSubnet(ok))
        for (bad in listOf("", "192.168.1.0", "192.168.1.5/24", "192.168.1.0/33", "256.1.1.0/24", "192.168.01.0/24",
                "192.168.1/24", "192.168.1.0/", "fe80::/64", " 192.168.1.0/24"))
            assertFalse(bad, HomeNetworkSettings.isValidSubnet(bad))
    }

    private class Events : NetworkInvalidations {
        var listener: (() -> Unit)? = null
        var registered = 0
        var unregistered = 0
        override fun register(invalidate: () -> Unit): AutoCloseable {
            listener = invalidate
            registered++
            return AutoCloseable { listener = null; unregistered++ }
        }
        fun event() { listener?.invoke() }
    }
    private val home = NetworkEvidence(listOf(wifi("casa.zara")), true)
    private val offline = NetworkEvidence(emptyList(), true)

    @Test fun initialSnapshotAndCancellation() = runTest {
        val events = Events()
        var reads = 0
        val received = mutableListOf<NetworkEvidence>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            NetworkEvidenceObserver(events) { reads++; home }.observe().collect { received.add(it) }
        }
        runCurrent()
        assertEquals(listOf(home), received)
        assertEquals(1, reads)
        assertEquals(1, events.registered)
        job.cancel(); runCurrent()
        assertEquals(1, events.unregistered)
        assertNull(events.listener)
    }

    @Test fun invalidationRecollectsWholeSnapshot() = runTest {
        val events = Events()
        var snapshot = home
        var reads = 0
        val received = mutableListOf<NetworkEvidence>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            NetworkEvidenceObserver(events) { reads++; snapshot }.observe().collect { received.add(it) }
        }
        runCurrent()
        snapshot = offline
        events.event(); runCurrent()
        assertEquals(listOf(home, offline), received)
        assertEquals(2, reads)
        job.cancel(); runCurrent()
    }
}
