package com.domopi.app.data

enum class AcReasonCategory {
    CRITICAL,
    WARNING,
    NORMAL,
    UNKNOWN
}

data class AcReasonInfo(
    val code: String,
    val description: String,
    val category: AcReasonCategory
)

object AcReasonMapper {

    fun getAcReasonInfo(rawReason: String): AcReasonInfo {
        val clean = rawReason.trim().uppercase()
        if (clean.isEmpty()) {
            return AcReasonInfo(
                code = "N/D",
                description = "Nessun motivo di logica comunicato dal sistema.",
                category = AcReasonCategory.UNKNOWN
            )
        }

        return when {
            clean == "WATCHDOG_SOSPENSIONE_SISTEMA_GUASTO" -> AcReasonInfo(
                code = clean,
                description = "Attivato quando il consumo elettrico dell'AC (emeter_power) rimane sotto i 10W per più di 6 minuti nonostante i tentativi di riavvio; il sistema si blocca autonomamente per sicurezza.",
                category = AcReasonCategory.CRITICAL
            )

            clean.startsWith("WATCHDOG_TENTATIVO_RIAVVIO") -> AcReasonInfo(
                code = clean,
                description = "Segnala che il sistema ha rilevato un falso stato di spegnimento e sta tentando un re-invio del comando.",
                category = AcReasonCategory.WARNING
            )

            clean == "ATTESA_SURPLUS_SOLARE" -> AcReasonInfo(
                code = clean,
                description = "Condizionatore inattivo in attesa che il surplus fotovoltaico superi la soglia utile di 150W.",
                category = AcReasonCategory.WARNING
            )

            clean == "BLOCCO_DIURNO_BATTERIA" || clean == "BLOCCO_NOTTE_BATTERIA_BASSA" -> AcReasonInfo(
                code = clean,
                description = "Impediscono l'accensione perché il livello di carica della batteria è inferiore al limite di sicurezza impostato.",
                category = AcReasonCategory.CRITICAL
            )

            clean == "SOPRAVVIVENZA_NOTTE_ATTIVA" || clean == "PEAK_SHAVING_ATTIVO" -> AcReasonInfo(
                code = clean,
                description = "Scattano in presenza di carichi elevati o riserva energetica insufficiente per la notte, imponendo il blocco o lo spegnimento immediato dell'AC.",
                category = AcReasonCategory.CRITICAL
            )

            clean == "DEFICIT_RILEVATO_AVVIO_TIMER" || clean == "TOLLERANZA_DEFICIT_IN_CORSO" -> AcReasonInfo(
                code = clean,
                description = "Indicano che la casa è in deficit energetico e che è partito il conto alla rovescia basato sulla tolleranza configurata prima di spegnere l'impianto.",
                category = AcReasonCategory.WARNING
            )

            clean == "COMFORT_RAGGIUNTO_ATTESA_TIMER" -> AcReasonInfo(
                code = clean,
                description = "Il clima ideale è stato raggiunto, ma l'unità resta accesa per rispettare il tempo minimo obbligatorio di funzionamento (MIN_RUN_TIME).",
                category = AcReasonCategory.NORMAL
            )

            clean == "MANTENIMENTO_COOLING_ON" -> AcReasonInfo(
                code = clean,
                description = "Indica che il condizionatore è acceso in modalità raffrescamento diurno e il sistema sta confermando il corretto mantenimento dei parametri di comfort e surplus energetico.",
                category = AcReasonCategory.NORMAL
            )

            clean == "MANTENIMENTO_NIGHT_DRY" -> AcReasonInfo(
                code = clean,
                description = "Segnala la regolare prosecuzione del ciclo di deumidificazione notturna, con condizioni di umidità e batteria stabili.",
                category = AcReasonCategory.NORMAL
            )

            clean == "MANTENIMENTO_STANDBY_INVERTER" -> AcReasonInfo(
                code = clean,
                description = "Compare quando l'unità è in una fase di pausa/standby energetico gestita dall'inverter, ma il sistema mantiene attiva la supervisione logica in attesa di una ripresa o di uno spegnimento definitivo.",
                category = AcReasonCategory.NORMAL
            )

            else -> AcReasonInfo(
                code = rawReason,
                description = rawReason,
                category = AcReasonCategory.UNKNOWN
            )
        }
    }
}
