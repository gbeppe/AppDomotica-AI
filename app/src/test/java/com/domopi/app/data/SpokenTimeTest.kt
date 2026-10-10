package com.domopi.app.data

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenTimeTest {
    private val now = ZonedDateTime.of(2026, 10, 11, 9, 0, 0, 0, ZoneId.of("Europe/Rome"))

    @Test fun currentYearIsDayMonthAndMinutes() {
        val ms = ZonedDateTime.of(2026, 10, 10, 23, 48, 39, 0, ZoneId.of("Europe/Rome")).toInstant().toEpochMilli()
        assertEquals("10/10 alle 23:48", SpokenTime.moment(ms, now))
    }

    @Test fun otherYearKeepsTheYear() {
        val ms = ZonedDateTime.of(2025, 12, 31, 8, 5, 0, 0, ZoneId.of("Europe/Rome")).toInstant().toEpochMilli()
        assertEquals("31/12/2025 alle 08:05", SpokenTime.moment(ms, now))
    }

    @Test fun backendIsoIsConvertedToRome() {
        assertEquals("10/10 alle 23:58", SpokenTime.moment("2026-10-10T23:58:42.123456+02:00", now))
        assertEquals("11/10 alle 01:30", SpokenTime.moment("2026-10-10T23:30:00Z", now))
        assertEquals("non valido", SpokenTime.moment("non valido", now))
    }
}
