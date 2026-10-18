package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

class VmcOverrideTest {
    @Test
    fun commandIsTwoHoursWithTimeZone() {
        val now = OffsetDateTime.parse("2026-10-18T17:45:12.345+02:00")
        assertEquals("""{"speed":3,"until":"2026-10-18T19:45:12+02:00"}""", VmcOverride.command(3, now))
    }

    @Test(expected = IllegalArgumentException::class)
    fun speedOutOfRangeIsRefused() { VmcOverride.command(5) }
}
