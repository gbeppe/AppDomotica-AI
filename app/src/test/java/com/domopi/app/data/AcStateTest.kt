package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.util.TimeZone

class AcStateTest {
    // the summary of 30/10 at 14:04:30: manual off held by the 25 minute run of the compressor
    private val held = """{"schema":"domopi.ac_state.v1","ai_enabled":true,
        "current":{"mode":"heat","setpoint":20,"code":"eco20heat","since":"2026-10-30T13:38:44+01:00","by":"watchdog"},
        "confirm":{"status":"running","power_w":1075},
        "target":{"mode":"off","setpoint":null,"by":"user","until":"2026-10-30T15:53:56+01:00"},
        "guard":{"type":"min_run","until":"2026-10-30T14:09:24+01:00","minutes":25},
        "alarms":["WATCHDOG_RESTART"],"reason":"MANUALE_IN_ATTESA_COMPRESSORE_5_MIN",
        "context":{"surplus_w":850,"soc":99,"temp":18.8,"humidex":20.3,"vmc":1},"ts":"2026-10-30T14:04:30+01:00"}"""

    @Test
    fun heldBackChangeShowsThreeStepsWithCountdown() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"))
        val s = AcState.parse(held)!!
        assertTrue(s.inTransition)
        assertEquals("Caldo 20°", s.currentLabel)
        assertEquals("riacceso dal vigilante", s.byText)
        assertEquals("compressore: tempo minimo acceso (25 min)", s.guardText)
        assertEquals("richiesto da te fino alle 15:53", s.targetText)
        val now = OffsetDateTime.parse("2026-10-30T14:04:30+01:00")
        assertEquals("4:54", s.countdown(now))
        assertEquals("a momenti", s.countdown(now.plusMinutes(6)))
        assertEquals("Caldo 20° → ⏳ 4:54 → Spento · richiesto da te fino alle 15:53", s.line(now))
        assertEquals(listOf("Il vigilante ha rimandato il comando (spento dal telecomando?)"), s.alarmTexts)
        assertEquals(listOf(850, 99, 1), listOf(s.surplusW, s.soc, s.vmc))
    }

    @Test
    fun withoutTransitionOnlyTheCurrentStateAndItsConfirmation() {
        val off = AcState.parse("""{"schema":"domopi.ac_state.v1","ai_enabled":false,
            "current":{"mode":"off","setpoint":null,"code":null,"since":"2026-10-30T14:31:34+01:00","by":"ai"},
            "confirm":{"status":"off","power_w":4},"target":null,"guard":null,"alarms":[],
            "reason":"SISTEMA_DISABILITATO_DA_UTENTE","context":{"surplus_w":1166,"soc":100,"temp":18.9,"humidex":20.4,"vmc":1}}""")!!
        assertFalse(off.inTransition)
        assertEquals("Spento · ✓ spento", off.line())
        assertTrue(off.alarmTexts.single().startsWith("AI Climate disabilitato"))
        val fan = AcState.parse(held.replace("\"running\",\"power_w\":1075", "\"starting\",\"power_w\":24")
            .replace(Regex(""""target":\{[^}]*\}"""), "\"target\":null").replace(Regex(""""guard":\{[^}]*\}"""), "\"guard\":null"))!!
        assertEquals("Caldo 20° · ◌ in avvio (24 W, solo ventola)", fan.line())
        val noPower = AcState.parse(held.replace("\"running\"", "\"no_power\"").replace("1075", "3")
            .replace("[\"WATCHDOG_RESTART\"]", "[\"ON_NO_POWER\"]"))!!
        assertEquals("⚠ comandato acceso ma consuma 3 W", noPower.confirmText)
        assertEquals("Comandato acceso ma non consuma: forse spento dal telecomando", noPower.alarmTexts.single())
    }

    @Test
    fun rejectsOtherSchemasAndBrokenPayloads() {
        assertNull(AcState.parse(held.replace("domopi.ac_state.v1", "domopi.ac_state.v2")))
        assertNull(AcState.parse(held.replace("\"mode\":\"heat\"", "\"mode\":\"turbo\"")))
        assertNull(AcState.parse("boh"))
        assertEquals("Standby 28°", AcState.label("standby", 28.0))
        assertEquals("Deumidifica 24°", AcState.label("dry", 24.0))
        assertEquals("Auto 24°", AcState.parse(held.replace("eco20heat", "auto24").replace("\"setpoint\":20", "\"setpoint\":24"))!!.currentLabel)
    }
}
