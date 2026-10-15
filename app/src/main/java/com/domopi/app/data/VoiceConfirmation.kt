package com.domopi.app.data

/**
 * Spoken answer to a pending fireplace on/off. Only the whole utterance counts, so a "confermo"
 * inside a longer sentence (TV, other people) is not taken as a confirmation.
 */
object VoiceConfirmation {
    const val VALID_MS = 60_000L
    private val CONFIRM = setOf("confermo", "conferma")
    private val CANCEL = setOf("no", "annulla", "annullo")

    /** true = confirm, false = cancel, null = not understood. */
    fun classify(text: String?): Boolean? {
        val words = text.orEmpty().lowercase().replace(Regex("[^\\p{L} ]"), " ").trim().split(Regex("\\s+"))
        val word = words.singleOrNull() ?: return null
        return when (word) {
            in CONFIRM -> true
            in CANCEL -> false
            else -> null
        }
    }

    fun expired(sinceMs: Long, nowMs: Long): Boolean = nowMs - sinceMs !in 0..VALID_MS
}
