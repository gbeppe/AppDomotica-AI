package com.domopi.app.network

/**
 * Pure domain representation of Access Path states according to Section 3.3.
 * Completely decoupled from UI rendering / Compose Color.
 */
enum class AccessPathStatus {
    /** Before the first classification (L-1a): neutral, never an alarm. */
    PENDING,
    NON_IN_RETE,
    LOCALE,
    REMOTO_CONNECTED,
    REMOTO_DISCONNECTED,
    UNKNOWN
}

data class AccessPathState(
    val status: AccessPathStatus,
    val label: String,
    val isFlashing: Boolean = false
) {
    companion object {
        val Pending = AccessPathState(AccessPathStatus.PENDING, "…")
        val NonInRete = AccessPathState(AccessPathStatus.NON_IN_RETE, "NON IN RETE")
        val Locale = AccessPathState(AccessPathStatus.LOCALE, "LOCALE")
        val RemotoConnected = AccessPathState(AccessPathStatus.REMOTO_CONNECTED, "REMOTO")
        val RemotoDisconnected = AccessPathState(AccessPathStatus.REMOTO_DISCONNECTED, "REMOTO")
        val Unknown = AccessPathState(AccessPathStatus.UNKNOWN, "UNKNOWN", isFlashing = true)
    }
}
