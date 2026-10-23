package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenTextTest {
    @Test
    fun datesAreReadAsDayAndMonth() {
        // 2026-10-23: "21/10" was read as "ventuno decimi".
        assertEquals("L'ultimo evento precedente è del 21 ottobre alle 07:52.",
            spokenText("L'ultimo evento precedente è del 21/10 alle 07:52."))
        assertEquals("primo novembre", spokenText("1/11"))
        assertEquals("il 6 settembre 2025", spokenText("il 06/09/2025"))
        assertEquals("dal 22 ottobre 2026", spokenText("dal 2026-10-22"))
    }

    @Test
    fun notDatesAreLeftAlone() {
        listOf("32/10", "21/13", "1/2/3/4", "07:52", "50,8 per cento").forEach {
            assertEquals(it, spokenText(it))
        }
    }

    @Test
    fun rangesBecomeWords() {
        assertEquals("Solo la sera: dalle 17:00 alle 21:00", spokenText("Solo la sera: 17:00–21:00"))
        assertEquals("dalle 05:05 alle 07:00 lunedì a venerdì", spokenText("05:05–07:00 lun–ven"))
        assertEquals("dalle 12:00 alle 13:00", spokenText("dalle 12:00-13:00"))
        assertEquals("Soggiorno 12 a 20, bagno 17 a 22", spokenText("Soggiorno 12–20, bagno 17–22"))
        assertEquals("tra 16,0 a 22,0 gradi", spokenText("tra 16,0-22,0 gradi"))
    }

    @Test
    fun dashesBecomePausesOrMissingValues() {
        // 2026-10-23: dashes were read as "trattino".
        assertEquals("Il condizionatore è spento, con il motivo registrato",
            spokenText("Il condizionatore è spento — con il motivo registrato"))
        assertEquals("Profilo: non disponibile.", spokenText("Profilo: —."))
        assertEquals("non disponibile kWh", spokenText("— kWh"))
        assertEquals("Bagno non disponibile", spokenText("Bagno —"))
        assertEquals("lunedì domenica", spokenText("lunedì-domenica"))
        assertEquals("Mese di calendario, settembre, o ultimi 30 giorni?",
            spokenText("Mese di calendario – settembre – o ultimi 30 giorni?"))
        assertEquals("Fa -2 gradi", spokenText("Fa -2 gradi"))
    }
}
