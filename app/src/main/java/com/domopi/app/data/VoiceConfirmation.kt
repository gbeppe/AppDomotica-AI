package com.domopi.app.data

/**
 * Spoken answer to pending heating commands. Only the whole utterance counts, so a "confermo"
 * inside a longer sentence (TV, other people) is not taken as a confirmation.
 */
object VoiceConfirmation {
    const val VALID_MS = 60_000L
    private val CONFIRM = setOf("confermo", "conferma")
    private val CANCEL = setOf("no", "annulla", "annullo")

    /**
     * true = confirm, false = cancel, null = not understood. Accents and spaces are ignored
     * ("confermò", "con fermo"), so the whole utterance must still spell only that word.
     */
    fun classify(text: String?): Boolean? {
        val plain = java.text.Normalizer.normalize(text.orEmpty().lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "").replace(Regex("[^\\p{L}]"), "")
        return when (plain) {
            in CONFIRM -> true
            in CANCEL -> false
            else -> null
        }
    }

    /** The recognizer's alternatives, best first: the first one that is a confirm or cancel word. */
    fun classify(alternatives: List<String>): Boolean? = alternatives.firstNotNullOfOrNull { classify(it) }

    fun expired(sinceMs: Long, nowMs: Long): Boolean = nowMs - sinceMs !in 0..VALID_MS
}
