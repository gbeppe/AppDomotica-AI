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
            clean == "ANALISI_IN_CORSO" -> AcReasonInfo(
                code = clean,
                description = "Valore iniziale predefinito assegnato alla variabile all'inizio di ogni esecuzione del ciclo, prima che la macchina a stati valuti le condizioni ambientali ed energetiche.",
                category = AcReasonCategory.UNKNOWN
            )

            clean == "SISTEMA_DISABILITATO_DA_UTENTE" -> AcReasonInfo(
                code = clean,
                description = "Generato quando l'interruttore generale di abilitazione (AI_climate_enabling) viene impostato su false.",
                category = AcReasonCategory.WARNING
            )

            clean == "WATCHDOG_FALSO_OFF" -> AcReasonInfo(
                code = clean,
                description = "Attivato se lo stato logico del sistema risulta OFF ma il sensore di potenza rileva un consumo dell'AC (ac_power) superiore a 30W dopo il tempo minimo di spegnimento.",
                category = AcReasonCategory.WARNING
            )

            clean.startsWith("WATCHDOG_TENTATIVO_RIAVVIO") -> AcReasonInfo(
                code = clean,
                description = "Generato quando il sistema è acceso ma il consumo misurato dell'AC è inferiore a 10W per oltre 6 minuti, indicando un mancato avvio.",
                category = AcReasonCategory.WARNING
            )

            clean == "WATCHDOG_SOSPENSIONE_SISTEMA_GUASTO" -> AcReasonInfo(
                code = clean,
                description = "Attivato quando il watchdog fallisce per 3 volte consecutive nel tentativo di riavviare l'unità, portando alla disabilitazione automatica della IA per sicurezza.",
                category = AcReasonCategory.CRITICAL
            )

            clean == "LOGICA_INVERNALE_ATTIVA" -> AcReasonInfo(
                code = clean,
                description = "Impostato stabilmente durante i mesi invernali per gestire il riscaldamento.",
                category = AcReasonCategory.NORMAL
            )

            clean.startsWith("PEAK_SHAVING_ATTIVO") -> AcReasonInfo(
                code = clean,
                description = "Generato in estate, nella fascia serale, se la potenza di scarica della batteria supera i 2500W, imponendo un blocco di protezione.",
                category = AcReasonCategory.CRITICAL
            )

            clean.startsWith("SOPRAVVIVENZA_NOTTE_ATTIVA") -> AcReasonInfo(
                code = clean,
                description = "Generato in estate, nella fascia serale, quando il cuscinetto energetico stimato in batteria è inferiore alla riserva minima richiesta per coprire la notte.",
                category = AcReasonCategory.CRITICAL
            )

            clean == "BLOCCO_TIMER_ANTICICLO_OFF" -> AcReasonInfo(
                code = clean,
                description = "Impostato in estate, in stato OFF, se il timer di protezione anti-ciclo di accensione (minMancantiAccensione) è ancora attivo.",
                category = AcReasonCategory.WARNING
            )

            clean == "BLOCCO_NOTTE_BATTERIA_BASSA" -> AcReasonInfo(
                code = clean,
                description = "Generato in estate, durante la fascia notturna, se il livello di carica della batteria (soc) è inferiore o uguale al minimo logico stabilito.",
                category = AcReasonCategory.CRITICAL
            )

            clean == "COMFORT_NOTTURNO_RAGGIUNTO" -> AcReasonInfo(
                code = clean,
                description = "Attivato di notte quando l'Humidex non supera la soglia di attivazione prevista.",
                category = AcReasonCategory.NORMAL
            )

            clean == "BLOCCO_NOTTE_PAUSA_45M" -> AcReasonInfo(
                code = clean,
                description = "Generato di notte se è trascorso meno di un intervallo minimo di 45 minuti dall'ultimo spegnimento notturno dell'unità.",
                category = AcReasonCategory.WARNING
            )

            clean == "PRONTO_ACCENSIONE_NIGHT_DRY" -> AcReasonInfo(
                code = clean,
                description = "Condizione in cui tutte le verifiche notturne (batteria, isteresi, timer) sono superate ed il sistema è pronto ad avviare la deumidificazione.",
                category = AcReasonCategory.NORMAL
            )

            clean == "BLOCCO_GRAZIA_VINCOLO_SOLARE" -> AcReasonInfo(
                code = clean,
                description = "Attivato nel periodo di grazia diurno se la modalità solare restrittiva è attiva ma il surplus fotovoltaico è inferiore a 1000W.",
                category = AcReasonCategory.WARNING
            )

            clean == "BLOCCO_DIURNO_BATTERIA" -> AcReasonInfo(
                code = clean,
                description = "Generato di giorno se la batteria è sotto il limite minimo e l'Humidex interno non ha raggiunto la soglia di emergenza assoluta.",
                category = AcReasonCategory.CRITICAL
            )

            clean == "ATTESA_SURPLUS_SOLARE" -> AcReasonInfo(
                code = clean,
                description = "Attivato di giorno quando il surplus virtuale fotovoltaico non è sufficiente (inferiore o uguale a 150W).",
                category = AcReasonCategory.WARNING
            )

            clean == "COMFORT_DIURNO_RAGGIUNTO" -> AcReasonInfo(
                code = clean,
                description = "Condizione diurna in cui l'Humidex si mantiene al di sotto della soglia di comfort impostata.",
                category = AcReasonCategory.NORMAL
            )

            clean == "PRONTO_ACCENSIONE_COOLING" || clean == "PRONTO_ACCENSIONE_OVERRIDE_SOLARE" -> AcReasonInfo(
                code = clean,
                description = "Segnalano che le condizioni di surplus o di superamento dell'Humidex diurno consentono l'avvio del raffrescamento.",
                category = AcReasonCategory.NORMAL
            )

            clean == "ESECUZIONE_ACCENSIONE_DIURNA" || clean == "ESECUZIONE_ACCENSIONE_NOTTURNA" -> AcReasonInfo(
                code = clean,
                description = "Registrati nell'istante esatto in cui l'algoritmo invia il comando a infrarossi per attivare rispettivamente il raffrescamento diurno o la deumidificazione notturna.",
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

            clean.startsWith("MANTENIMENTO_") -> AcReasonInfo(
                code = clean,
                description = "Generati dinamicamente durante i cicli di regime in cui il sistema conferma la regolare prosecuzione dello stato attivo corrente.",
                category = AcReasonCategory.NORMAL
            )

            clean == "COMFORT_RAGGIUNTO_ATTESA_TIMER" -> AcReasonInfo(
                code = clean,
                description = "Attivato quando il comfort climatico è raggiunto ma l'unità deve rimanere accesa per rispettare il tempo minimo obbligatorio di funzionamento (MIN_RUN_TIME).",
                category = AcReasonCategory.NORMAL
            )

            clean == "STANDBY_COMFORT_RAGGIUNTO" -> AcReasonInfo(
                code = clean,
                description = "Generato quando il comfort è raggiunto e il timer minimo è scaduto, permettendo il passaggio dell'unità in stato di standby.",
                category = AcReasonCategory.NORMAL
            )

            clean == "DEFICIT_RILEVATO_AVVIO_TIMER" -> AcReasonInfo(
                code = clean,
                description = "Attivato quando il bilancio energetico passa in deficit rispetto alla soglia e viene avviato il conteggio del tempo di tolleranza.",
                category = AcReasonCategory.WARNING
            )

            clean == "TOLLERANZA_DEFICIT_IN_CORSO" -> AcReasonInfo(
                code = clean,
                description = "Segnala che la casa si trova in deficit energetico e il timer di tolleranza (deficit_tolerance_time) è attivo ma non è ancora scaduto.",
                category = AcReasonCategory.WARNING
            )

            clean == "STANDBY_DEFICIT_SOLARE" -> AcReasonInfo(
                code = clean,
                description = "Generato quando il timer di tolleranza al deficit scade e il sistema sceglie di dirottare l'unità in standby per preservare la batteria.",
                category = AcReasonCategory.WARNING
            )

            clean == "RIPRESA_COOLING_DA_STANDBY" -> AcReasonInfo(
                code = clean,
                description = "Attivato quando l'unità si trovava in standby inverter e l'Humidex torna a salire oltre la soglia, facendo ripartire il compressore.",
                category = AcReasonCategory.NORMAL
            )

            clean == "BATTERIA_SCARICA_ATTESA_TIMER" -> AcReasonInfo(
                code = clean,
                description = "Generato in modalità notturna quando la batteria si scarica sotto il limite ma si attende il completamento del timer di spegnimento obbligatorio.",
                category = AcReasonCategory.WARNING
            )

            clean == "ESECUZIONE_SPEGNIMENTO" -> AcReasonInfo(
                code = clean,
                description = "Registrato nell'istante in cui la logica impone e invia il comando effettivo di spegnimento dell'aria condizionata.",
                category = AcReasonCategory.NORMAL
            )

            clean == "ESECUZIONE_STANDBY_INVERTER" -> AcReasonInfo(
                code = clean,
                description = "Registrato nell'istante in cui l'unità viene comandata in modalità standby energetico.",
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
