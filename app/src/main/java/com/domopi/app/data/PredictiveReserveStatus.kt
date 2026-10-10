package com.domopi.app.data

enum class PrSeverity { NEUTRAL, OK, WARNING, UNKNOWN }

/** Effect of the Predictive Reserve on new night OFF -> NIGHT_DRY starts (PR-MODE.1). */
data class PrEffect(val badge: String, val severity: PrSeverity, val explanation: String)

/**
 * Pure mapping of the three backend states. Node-RED stays authoritative: this only
 * explains what the published values mean, it never decides or commands anything.
 */
object PredictiveReserveStatus {
    fun effect(controlEnabled: Boolean, operatingMode: String?, ready: Boolean?): PrEffect {
        if (!controlEnabled) {
            return PrEffect("PR OFF", PrSeverity.NEUTRAL,
                "Il PR non interviene: l'avvio notturno in deumidificazione segue la logica AI Climate normale.")
        }
        return when (operatingMode) {
            "SHADOW" -> PrEffect("PR ON · SHADOW", PrSeverity.WARNING,
                "Modalità SHADOW: il PR simula soltanto ma, con il controllo attivo, nega ogni nuovo avvio " +
                    "notturno in deumidificazione. Il condizionatore non partirà di notte.")
            "ACTIVE" -> when (ready) {
                true -> PrEffect("PR ON · ACTIVE", PrSeverity.OK,
                    "Modalità ACTIVE: il PR autorizza l'avvio notturno solo con il regime qualificato (eco26dry), " +
                        "decisione recente e bilancio energetico favorevole; altrimenti lo nega.")
                false -> PrEffect("PR ON · NON PRONTO", PrSeverity.WARNING,
                    "Modalità ACTIVE ma modello non pronto: ogni nuovo avvio notturno in deumidificazione viene negato.")
                null -> PrEffect("PR ON · ACTIVE", PrSeverity.UNKNOWN,
                    "Modalità ACTIVE; prontezza del modello non ancora ricevuta.")
            }
            else -> PrEffect("PR ON", PrSeverity.UNKNOWN,
                "Modalità del PR non ancora ricevuta: l'effetto sul condizionatore non è determinabile.")
        }
    }

    /** Backend facts as published, for the detail popup. */
    fun details(operatingMode: String?, ready: Boolean?): String =
        "Modalità: ${operatingMode ?: "non ricevuta"} · Modello: " +
            when (ready) { true -> "pronto"; false -> "non pronto"; null -> "stato non ricevuto" }

    /** Text for the confirmation dialog, describing what enabling/disabling will do now. */
    fun toggleExplanation(willEnable: Boolean, operatingMode: String?, ready: Boolean?): String =
        if (willEnable) effect(true, operatingMode, ready).explanation
        else "Il PR smetterà di intervenire: l'avvio notturno tornerà alla logica AI Climate normale."
}
