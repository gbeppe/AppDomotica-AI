package com.domopi.app.data

/**
 * Fase 3.5 B: written alerts while the app is open, only for changes (never for the state
 * found at opening). Pure state machine: call [update] on every change and periodically.
 */
class HouseAlerts {
    companion object {
        const val DISCONNECT_GRACE_MS = 30_000L
        const val MIN_INTERVAL_MS = 30 * 60_000L
        const val FULL_AT = 99.5
        const val FULL_REARM_BELOW = 95.0
        const val LOW_BELOW = 20.0
        const val LOW_REARM_ABOVE = 25.0
        const val DISCONNECTED = "Casa non collegata."
        const val RECONNECTED = "Casa di nuovo collegata."
        const val FULL = "Batteria carica al 100 per cento."
        const val LOW = "Batteria sotto il 20 per cento."
    }

    private var everConnected = false
    private var disconnectedSince: Long? = null
    private var disconnectAnnounced = false
    private var fullArmed: Boolean? = null  // null until the first battery reading
    private var lowArmed: Boolean? = null
    private val lastShown = mutableMapOf<String, Long>()

    /** Returns the alerts to show now, oldest first. */
    fun update(connected: Boolean, soc: Double?, nowMs: Long): List<String> {
        val alerts = mutableListOf<String>()
        if (connected) {
            if (disconnectAnnounced) alerts += RECONNECTED
            everConnected = true
            disconnectedSince = null
            disconnectAnnounced = false
        } else if (everConnected) {
            val since = disconnectedSince ?: nowMs.also { disconnectedSince = it }
            if (!disconnectAnnounced && nowMs - since >= DISCONNECT_GRACE_MS && allowed(DISCONNECTED, nowMs)) {
                disconnectAnnounced = true
                alerts += DISCONNECTED
            }
        }
        // Battery readings are only trusted while connected (retained values may be old).
        if (soc != null && connected) {
            if (fullArmed == null) {
                fullArmed = soc < FULL_AT
                lowArmed = soc >= LOW_BELOW
            } else {
                if (fullArmed == true && soc >= FULL_AT && allowed(FULL, nowMs)) {
                    fullArmed = false
                    alerts += FULL
                } else if (fullArmed == false && soc < FULL_REARM_BELOW) fullArmed = true
                if (lowArmed == true && soc < LOW_BELOW && allowed(LOW, nowMs)) {
                    lowArmed = false
                    alerts += LOW
                } else if (lowArmed == false && soc > LOW_REARM_ABOVE) lowArmed = true
            }
        }
        return alerts
    }

    private fun allowed(kind: String, nowMs: Long): Boolean {
        val last = lastShown[kind]
        if (last != null && nowMs - last < MIN_INTERVAL_MS) return false
        lastShown[kind] = nowMs
        return true
    }
}
