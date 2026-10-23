package com.domopi.app.data

/** Exact partition: joining the chunks restores the displayed answer, including whitespace. */
fun speechChunks(text: String, limit: Int): List<String> {
    require(limit > 0)
    val chunks = mutableListOf<String>()
    var offset = 0
    while (offset < text.length) {
        var end = minOf(offset + limit, text.length)
        if (end < text.length) {
            val space = text.lastIndexOf(' ', end - 1)
            if (space > offset) end = space + 1
            else if (end > offset + 1 && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
        }
        chunks += text.substring(offset, end)
        offset = end
    }
    return chunks
}

private val MONTHS = listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio",
    "agosto", "settembre", "ottobre", "novembre", "dicembre")
private val WEEKDAYS = mapOf("lun" to "lunedì", "mar" to "martedì", "mer" to "mercoledì", "gio" to "giovedì",
    "ven" to "venerdì", "sab" to "sabato", "dom" to "domenica")
private const val DASH = "[-–—]"
private val ISO_DATE = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
private val DAY_MONTH = Regex("""(?<![\d/])(\d{1,2})/(\d{1,2})(?:/(\d{4}))?(?![\d/])""")
private val WEEKDAY_RANGE = Regex("""\b(lun|mar|mer|gio|ven|sab|dom)\s*$DASH\s*(lun|mar|mer|gio|ven|sab|dom)\b""")
private val TIME_RANGE = Regex("""(?:\bdalle\s+)?(\d{1,2}:\d{2})\s*$DASH\s*(\d{1,2}:\d{2})""")
private val NUMBER_RANGE = Regex("""(\d+(?:,\d+)?)\s*$DASH\s*(\d+(?:,\d+)?)""")
// "—" stands for a missing value when it follows ":" or the start, or precedes a unit or the end.
private val MISSING = Regex("""(?<=^|:\s?|\()—(?=\s|$|[.,;)])|—(?=\s*(?:kWh|kW|W|gradi|°|%|$))""")
private val SEPARATOR = Regex("""\s+$DASH\s+|\s*[–—]\s*""")
private val WORD_DASH = Regex("""(?<=\p{L})-(?=\p{L})""")

private fun spokenDay(day: Int, month: Int, year: String?) =
    (if (day == 1) "primo" else "$day") + " " + MONTHS[month - 1] + (year?.let { " $it" } ?: "")

/**
 * The text read aloud: Android's voice says "21/10" as "ventuno decimi" and reads dashes as
 * "trattino". Dates, ranges and dashes become words or pauses; the screen keeps the original.
 */
fun spokenText(text: String): String = text
    .replace(ISO_DATE) { m ->
        val (y, mo, d) = m.destructured
        if (mo.toInt() in 1..12 && d.toInt() in 1..31) spokenDay(d.toInt(), mo.toInt(), y) else m.value
    }
    .replace(DAY_MONTH) { m ->
        val (d, mo, y) = m.destructured
        if (mo.toInt() in 1..12 && d.toInt() in 1..31) spokenDay(d.toInt(), mo.toInt(), y.ifEmpty { null }) else m.value
    }
    .replace(WEEKDAY_RANGE) { m -> "${WEEKDAYS[m.groupValues[1]]} a ${WEEKDAYS[m.groupValues[2]]}" }
    .replace(TIME_RANGE) { m -> "dalle ${m.groupValues[1]} alle ${m.groupValues[2]}" }
    .replace(NUMBER_RANGE) { m -> "${m.groupValues[1]} a ${m.groupValues[2]}" }
    .replace(MISSING, "non disponibile")
    .replace(SEPARATOR, ", ")
    .replace(WORD_DASH, " ")
    .replace(Regex("""\s*,\s*([.,;:])"""), "$1")
