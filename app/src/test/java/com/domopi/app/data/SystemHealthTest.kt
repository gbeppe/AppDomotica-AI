package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemHealthTest {
    private val now = 1_792_744_000_000L
    private fun summary(host: String, ok: Boolean, at: Long = now - 60_000, peerOk: Boolean = true) = """
        {"schema":"domopi.system_health.v1","host":"$host","checked_at_ms":$at,"ok":$ok,"checks":[
          {"id":"peer","title":"Raspberry","ok":$peerOk,"detail":"raggiungibile","advice":""},
          {"id":"service:ntfy","title":"Servizio ntfy","ok":$ok,"detail":"stato ${if (ok) "active" else "inactive"}",
           "advice":"${if (ok) "" else "Servizio fermo"}","extra":1}]}
    """.trimIndent()

    @Test
    fun parsesTheS1SummaryAndRejectsOthers() {
        val h = SystemHealth.parse(summary(".20", ok = false))!!
        assertEquals(".20", h.host)
        assertEquals(2, h.checks.size)
        assertEquals("Servizio fermo", h.checks[1].advice)
        assertNull(SystemHealth.parse(summary(".99", ok = true)))
        assertNull(SystemHealth.parse(summary(".20", ok = true).replace("domopi.system_health.v1", "other.v1")))
        assertNull(SystemHealth.parse("boh"))
        assertTrue(SystemHealth.isTopic("zara/system/health/15/stat"))
    }

    @Test
    fun trafficLightAndHeart() {
        val ok20 = SystemHealth.parse(summary(".20", ok = true))!!
        val bad15 = SystemHealth.parse(summary(".15", ok = false))!!
        val old15 = SystemHealth.parse(summary(".15", ok = true, at = now - 7 * 60_000))!!
        assertEquals(HealthLight.GREEN, SystemHealth.light(ok20, now))
        assertEquals(HealthLight.RED, SystemHealth.light(bad15, now))
        assertEquals(HealthLight.GREY, SystemHealth.light(old15, now))
        assertEquals(HealthLight.GREY, SystemHealth.light(null, now))
        assertEquals(HealthLight.RED, SystemHealth.overall(mapOf(".20" to ok20, ".15" to bad15), now))
        assertEquals(HealthLight.GREY, SystemHealth.overall(mapOf(".20" to ok20, ".15" to old15), now))
        assertEquals(HealthLight.GREY, SystemHealth.overall(mapOf(".20" to ok20), now))
        val ok15 = SystemHealth.parse(summary(".15", ok = true))!!
        assertEquals(HealthLight.GREEN, SystemHealth.overall(mapOf(".20" to ok20, ".15" to ok15), now))
        assertEquals("1 minuto fa", SystemHealth.age(now - 90_000, now))
        assertEquals("3 ore fa", SystemHealth.age(now - 3 * 3_600_000, now))
    }

    @Test
    fun phoneAdviceForTheRouterCase() {
        val p = SystemHealth.PROBES
        fun probes(vararg down: String) = p.associateWith { it.host !in down }
        val hosts = mapOf(".20" to SystemHealth.parse(summary(".20", ok = true))!!,
            ".15" to SystemHealth.parse(summary(".15", ok = true))!!)
        assertNull(SystemHealth.phoneAdvice(probes(), hosts, now))
        assertEquals("Il telefono non raggiunge il .15, ma il .20 lo vede: riavvia il router ASUS.",
            SystemHealth.phoneAdvice(probes(".15"), hosts, now))
        val notSeen = hosts + (".20" to SystemHealth.parse(summary(".20", ok = true, peerOk = false))!!)
        assertEquals("Il telefono non raggiunge il .15: controlla alimentazione e cavo di rete del .15.",
            SystemHealth.phoneAdvice(probes(".15"), notSeen, now))
        assertEquals("Il telefono non raggiunge nulla della casa: controlla di essere sul Wi-Fi di casa.",
            SystemHealth.phoneAdvice(probes(".20", ".15", "router"), hosts, now))
        assertNull(SystemHealth.phoneAdvice(emptyMap(), hosts, now))
    }
}
