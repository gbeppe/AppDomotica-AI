package com.domopi.app.data

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** AI Smart texts are read aloud: gg/mm and hh:mm (year only when not the current one). */
object SpokenTime {
    private val rome: ZoneId = ZoneId.of("Europe/Rome")

    fun moment(epochMs: Long, now: ZonedDateTime = ZonedDateTime.now(rome)): String =
        format(Instant.ofEpochMilli(epochMs).atZone(rome), now)

    /** Backend ISO timestamps (e.g. generated_at); unparsable text is returned unchanged. */
    fun moment(iso: String, now: ZonedDateTime = ZonedDateTime.now(rome)): String =
        runCatching { format(OffsetDateTime.parse(iso).atZoneSameInstant(rome), now) }.getOrDefault(iso)

    private fun format(time: ZonedDateTime, now: ZonedDateTime): String {
        val day = if (time.year == now.year) "dd/MM" else "dd/MM/yyyy"
        return time.format(DateTimeFormatter.ofPattern("$day 'alle' HH:mm"))
    }
}
