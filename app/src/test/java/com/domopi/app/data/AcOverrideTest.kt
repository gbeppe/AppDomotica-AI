package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

class AcOverrideTest {
    @Test
    fun parsesTheRetainedState() {
        val (heat, closed) = AcOverride.parse(
            """{"schema":"domopi.ac_override.v1","active":true,"mode":"heat","setpoint":20,"until":"2026-10-23T22:00:00+02:00","label":"22:00"}""")
        assertEquals(AcOverride("heat", 20, "22:00"), heat)
        assertEquals("caldo 20 °C fino alle 22:00", heat!!.text)
        assertNull(closed)
        assertEquals("spento fino alle 18:00",
            AcOverride.parse("""{"active":true,"mode":"off","setpoint":null,"label":"18:00"}""").first!!.text)
        assertEquals(null to null, AcOverride.parse("""{"schema":"domopi.ac_override.v1","active":false}"""))
        assertEquals(null to "PEAK_SHAVING_ATTIVO (Scarica: 2700W)",
            AcOverride.parse("""{"active":false,"closed_reason":"PEAK_SHAVING_ATTIVO (Scarica: 2700W)"}"""))
        assertNull(AcOverride.parse("""{"active":true,"mode":"heat","setpoint":25,"label":"22:00"}""").first)
        assertNull(AcOverride.parse("boh").first)
    }

    @Test
    fun buttonsAreTwoHourPriorities() {
        val now = OffsetDateTime.parse("2026-10-23T16:00:00+02:00")
        assertEquals("""{"mode":"heat","setpoint":20,"until":"2026-10-23T18:00+02:00"}""", AcOverride.command("heat", 20, now))
        assertEquals("""{"mode":"off","until":"2026-10-23T18:00+02:00"}""", AcOverride.command("off", now = now))
        assertEquals("""{"mode":"heat","setpoint":20,"until":"2026-10-23T18:00+02:00","solar_only":true}""",
            AcOverride.command("heat", 20, now, solarOnly = true))
        assertEquals("caldo 20 °C solo sole fino alle 22:00",
            AcOverride.parse("""{"active":true,"mode":"heat","setpoint":20,"label":"22:00","solar_only":true}""").first!!.text)
    }
}
