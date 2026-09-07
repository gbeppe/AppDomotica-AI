package com.domopi.app.data

import java.util.Locale

enum class AcReasonCategory {
    CRITICAL,
    WARNING,
    NORMAL,
    UNKNOWN
}

data class AcReasonMetric(
    val label: String,
    val value: String
)

data class AcReasonInfo(
    val code: String,
    val description: String,
    val quandoCompare: String = "",
    val effetto: String = "",
    val soglieCostanti: String = "",
    val category: AcReasonCategory,
    val metrics: List<AcReasonMetric> = emptyList()
)

object AcReasonMapper {

    fun getAcReasonInfo(rawReason: String, aiData: AiManagedData? = null): AcReasonInfo {
        val clean = rawReason.trim().uppercase()
        if (clean.isEmpty()) {
            return AcReasonInfo(
                code = "N/D",
                description = "Nessun motivo di logica comunicato dal sistema.",
                category = AcReasonCategory.UNKNOWN
            )
        }

        val baseInfo = when {
            clean == "ANALISI_IN_CORSO" -> AcReasonInfo(
                code = clean,
                description = "Valore iniziale predefinito assegnato alla variabile all'inizio di ogni esecuzione del ciclo, prima che la macchina a stati valuti le condizioni ambientali ed energetiche.",
                quandoCompare = "Valore iniziale prima della logica AC.",
                effetto = "Valore transitorio; normalmente sostituito.",
                category = AcReasonCategory.UNKNOWN
            )

            clean == "SISTEMA_DISABILITATO_DA_UTENTE" -> AcReasonInfo(
                code = clean,
                description = "Generato quando l'interruttore generale di abilitazione (AI_climate_enabling) viene impostato su false.",
                quandoCompare = "AI_climate_enabling = false.",
                effetto = "Prima disabilitazione: forza OFF, registra ultimo_spegnimento.",
                category = AcReasonCategory.WARNING
            )

            clean == "WATCHDOG_FALSO_OFF" -> AcReasonInfo(
                code = clean,
                description = "Attivato se lo stato logico del sistema risulta OFF ma il sensore di potenza rileva un consumo dell'AC (ac_power) superiore a 30W dopo il tempo minimo di spegnimento.",
                quandoCompare = "Stato logico OFF ma AC assorbe >30 W da oltre 3 min dallo spegnimento.",
                effetto = "Invia OFF immediato e bypassa routine normale.",
                category = AcReasonCategory.WARNING
            )

            clean.startsWith("WATCHDOG_TENTATIVO_RIAVVIO") -> AcReasonInfo(
                code = clean,
                description = "Generato quando il sistema è acceso ma il consumo misurato dell'AC è inferiore a 10W per oltre 6 minuti, indicando un mancato avvio.",
                quandoCompare = "AC acceso ma assorbe <10 W oltre 6 min dall'avvio (tentativi <3).",
                effetto = "Forza stato logico OFF per consentire nuovo tentativo.",
                category = AcReasonCategory.WARNING
            )

            clean == "WATCHDOG_SOSPENSIONE_SISTEMA_GUASTO" -> AcReasonInfo(
                code = clean,
                description = "Attivato quando il watchdog fallisce per 3 volte consecutive nel tentativo di riavviare l'unità, portando alla disabilitazione automatica della IA per sicurezza.",
                quandoCompare = "Ulteriore fallimento dopo 3 tentativi watchdog.",
                effetto = "Disabilita AI climatica, azzera tentativi, forza OFF.",
                category = AcReasonCategory.CRITICAL
            )

            clean == "LOGICA_INVERNALE_ATTIVA" -> AcReasonInfo(
                code = clean,
                description = "Impostato stabilmente durante i mesi invernali per gestire il riscaldamento.",
                quandoCompare = "Normale elaborazione in stagione INVERNO.",
                effetto = "Gestione riscaldamento invernale.",
                category = AcReasonCategory.NORMAL
            )

            clean.startsWith("PEAK_SHAVING_ATTIVO") -> AcReasonInfo(
                code = clean,
                description = "Generato in estate, nella fascia serale, se la potenza di scarica della batteria supera i 2500W, imponendo un blocco di protezione.",
                quandoCompare = "Estate, sera dinamica, scarica batteria > 2500 W.",
                effetto = "Blocco spegnimento emergenza attivo; se AC acceso OFF immediato.",
                category = AcReasonCategory.CRITICAL
            )

            clean.startsWith("SOPRAVVIVENZA_NOTTE_ATTIVA") -> AcReasonInfo(
                code = clean,
                description = "Generato in estate, nella fascia serale, quando il cuscinetto energetico stimato in batteria è inferiore alla riserva minima richiesta per coprire la notte.",
                quandoCompare = "Estate, sera dinamica, cuscinetto energetico stimato insufficiente.",
                effetto = "Blocca nuove accensioni; se AC acceso OFF immediato.",
                category = AcReasonCategory.CRITICAL
            )

            clean == "BLOCCO_TIMER_ANTICICLO_OFF" -> AcReasonInfo(
                code = clean,
                description = "Impostato in estate, in stato OFF, se il timer di protezione anti-ciclo di accensione (minMancantiAccensione) è ancora attivo.",
                quandoCompare = "Estate, AC OFF e tempo minimo dall'ultimo spegnimento non ancora trascorso.",
                effetto = "Impedisce qualsiasi nuova accensione nel ciclo.",
                category = AcReasonCategory.WARNING
            )

            clean == "BLOCCO_NOTTE_BATTERIA_BASSA" -> AcReasonInfo(
                code = clean,
                description = "Generato in estate, durante la fascia notturna, se il livello di carica della batteria (soc) è inferiore o uguale al minimo logico stabilito.",
                quandoCompare = "Estate, notte, AC OFF e SOC <= soglia minima.",
                effetto = "Blocca NIGHT_DRY.",
                category = AcReasonCategory.CRITICAL
            )

            clean == "COMFORT_NOTTURNO_RAGGIUNTO" -> AcReasonInfo(
                code = clean,
                description = "Attivato di notte quando l'Humidex non supera la soglia di attivazione prevista.",
                quandoCompare = "Estate, notte, AC OFF, Humidex sotto soglia di riaccensione.",
                effetto = "Nessuna accensione.",
                category = AcReasonCategory.NORMAL
            )

            clean == "BLOCCO_NOTTE_PAUSA_45M" -> AcReasonInfo(
                code = clean,
                description = "Generato di notte se è trascorso meno di un intervallo minimo di 45 minuti dall'ultimo spegnimento notturno dell'unità.",
                quandoCompare = "Estate, notte; pausa specifica NIGHT_DRY non terminata (45 min).",
                effetto = "Blocca NIGHT_DRY.",
                category = AcReasonCategory.WARNING
            )

            clean == "PRONTO_ACCENSIONE_NIGHT_DRY" -> AcReasonInfo(
                code = clean,
                description = "Condizione in cui tutte le verifiche notturne (batteria, isteresi, timer) sono superate ed il sistema è pronto ad avviare la deumidificazione.",
                quandoCompare = "Notte: SOC e Humidex adeguati, pausa notturna terminata.",
                effetto = "Segnala condizioni notturne favorevoli; segue NIGHT_DRY.",
                category = AcReasonCategory.NORMAL
            )

            clean == "BLOCCO_GRAZIA_VINCOLO_SOLARE" -> AcReasonInfo(
                code = clean,
                description = "Attivato nel periodo di grazia diurno se la modalità solare restrittiva è attiva ma il surplus fotovoltaico è inferiore a 1000W.",
                quandoCompare = "Periodo grazia con vincolo solare ma fotovoltaico insufficiente.",
                effetto = "Blocca accensione.",
                category = AcReasonCategory.WARNING
            )

            clean == "BLOCCO_DIURNO_BATTERIA" -> AcReasonInfo(
                code = clean,
                description = "Generato di giorno se la batteria è sotto il limite minimo e l'Humidex interno non ha raggiunto la soglia di emergenza assoluta.",
                quandoCompare = "Giorno, AC OFF, SOC sotto minimo, Humidex non emergenziale.",
                effetto = "Blocca accensione.",
                category = AcReasonCategory.CRITICAL
            )

            clean == "ATTESA_SURPLUS_SOLARE" -> AcReasonInfo(
                code = clean,
                description = "Attivato di giorno quando il surplus virtuale fotovoltaico non è sufficiente (inferiore o uguale a 150W).",
                quandoCompare = "Giorno, surplus virtuale insufficiente e nessun override/emergenza.",
                effetto = "Attende maggior surplus FV.",
                category = AcReasonCategory.WARNING
            )

            clean == "COMFORT_DIURNO_RAGGIUNTO" -> AcReasonInfo(
                code = clean,
                description = "Condizione diurna in cui l'Humidex si mantiene al di sotto della soglia di comfort impostata.",
                quandoCompare = "Giorno, Humidex sotto soglia attivazione.",
                effetto = "Nessuna accensione.",
                category = AcReasonCategory.NORMAL
            )

            clean == "PRONTO_ACCENSIONE_COOLING" || clean == "PRONTO_ACCENSIONE_OVERRIDE_SOLARE" -> AcReasonInfo(
                code = clean,
                description = "Segnalano che le condizioni di surplus o di superamento dell'Humidex diurno consentono l'avvio del raffrescamento.",
                quandoCompare = "Condizioni favorevoli all'accensione diurna.",
                effetto = "Diagnostica di autorizzazione; segue COOLING_ON.",
                category = AcReasonCategory.NORMAL
            )

            clean == "ESECUZIONE_ACCENSIONE_DIURNA" || clean == "ESECUZIONE_ACCENSIONE_NOTTURNA" -> AcReasonInfo(
                code = clean,
                description = "Registrati nell'istante esatto in cui l'algoritmo invia il comando a infrarossi per attivare rispettivamente il raffrescamento diurno o la deumidificazione notturna.",
                quandoCompare = "Tutte le condizioni finali di accensione soddisfatte.",
                effetto = "Stato -> COOLING_ON / NIGHT_DRY; invia comando IR.",
                category = AcReasonCategory.NORMAL
            )

            clean == "MANTENIMENTO_COOLING_ON" -> AcReasonInfo(
                code = clean,
                description = "Indica che il condizionatore è acceso in modalità raffrescamento diurno e il sistema sta confermando il corretto mantenimento dei parametri di comfort e surplus energetico.",
                quandoCompare = "Estate, AC già non OFF e nessun blocco emergenziale.",
                effetto = "Mantiene funzionamento corrente in raffrescamento.",
                category = AcReasonCategory.NORMAL
            )

            clean == "MANTENIMENTO_NIGHT_DRY" -> AcReasonInfo(
                code = clean,
                description = "Segnala la regolare prosecuzione del ciclo di deumidificazione notturna, con condizioni di umidità e batteria stabili.",
                quandoCompare = "Estate, notte, deumidificazione regolare in corso.",
                effetto = "Mantiene funzionamento corrente in NIGHT_DRY.",
                category = AcReasonCategory.NORMAL
            )

            clean == "MANTENIMENTO_STANDBY_INVERTER" -> AcReasonInfo(
                code = clean,
                description = "Compare quando l'unità è in una fase di pausa/standby energetico gestita dall'inverter, ma il sistema mantiene attiva la supervisione logica in attesa di una ripresa o di uno spegnimento definitivo.",
                quandoCompare = "Unità in pausa/standby energetico inverter.",
                effetto = "Mantiene attiva la supervisione logica in attesa di ripresa o spegnimento.",
                category = AcReasonCategory.NORMAL
            )

            clean.startsWith("MANTENIMENTO_") -> AcReasonInfo(
                code = clean,
                description = "Generati dinamicamente durante i cicli di regime in cui il sistema conferma la regolare prosecuzione dello stato attivo corrente.",
                quandoCompare = "Ciclo di regime attivo.",
                effetto = "Conferma regolare prosecuzione dello stato attivo.",
                category = AcReasonCategory.NORMAL
            )

            clean == "COMFORT_RAGGIUNTO_ATTESA_TIMER" -> AcReasonInfo(
                code = clean,
                description = "Attivato quando il comfort climatico è raggiunto ma l'unità deve rimanere accesa per rispettare il tempo minimo obbligatorio di funzionamento (MIN_RUN_TIME).",
                quandoCompare = "Comfort raggiunto ma MIN_RUN_TIME non ancora terminato.",
                effetto = "Richiede standby ma attende completamento timer obbligatorio.",
                category = AcReasonCategory.NORMAL
            )

            clean == "STANDBY_COMFORT_RAGGIUNTO" -> AcReasonInfo(
                code = clean,
                description = "Generato quando il comfort è raggiunto e il timer minimo è scaduto, permettendo il passaggio dell'unità in stato di standby.",
                quandoCompare = "Comfort raggiunto e MIN_RUN_TIME scaduto.",
                effetto = "Richiede standby energetico.",
                category = AcReasonCategory.NORMAL
            )

            clean == "DEFICIT_RILEVATO_AVVIO_TIMER" -> AcReasonInfo(
                code = clean,
                description = "Attivato quando il bilancio energetico passa in deficit rispetto alla soglia e viene avviato il conteggio del tempo di tolleranza.",
                quandoCompare = "Surplus scende sotto limite deficit e timer non era attivo.",
                effetto = "Avvia timer deficit. Nessuna transizione immediata.",
                category = AcReasonCategory.WARNING
            )

            clean == "TOLLERANZA_DEFICIT_IN_CORSO" -> AcReasonInfo(
                code = clean,
                description = "Segnala che la casa si trova in deficit energetico e il timer di tolleranza (deficit_tolerance_time) è attivo ma non è ancora scaduto.",
                quandoCompare = "Deficit in corso ma entro il tempo di tolleranza.",
                effetto = "Mantiene funzionamento corrente in attesa del timer.",
                category = AcReasonCategory.WARNING
            )

            clean == "STANDBY_DEFICIT_SOLARE" -> AcReasonInfo(
                code = clean,
                description = "Generato quando il timer di tolleranza al deficit scade e il sistema sceglie di dirottare l'unità in standby per preservare la batteria.",
                quandoCompare = "Timer tolleranza deficit scaduto.",
                effetto = "Richiede passaggio in standby per preservare la batteria.",
                category = AcReasonCategory.WARNING
            )

            clean == "RIPRESA_COOLING_DA_STANDBY" -> AcReasonInfo(
                code = clean,
                description = "Attivato quando l'unità si trovava in standby inverter e l'Humidex torna a salire oltre la soglia, facendo ripartire il compressore.",
                quandoCompare = "Standby Inverter e Humidex torna >= soglia.",
                effetto = "Riattiva raffrescamento (stato -> COOLING_ON).",
                category = AcReasonCategory.NORMAL
            )

            clean == "BATTERIA_SCARICA_ATTESA_TIMER" -> AcReasonInfo(
                code = clean,
                description = "Generato in modalità notturna quando la batteria si scarica sotto il limite ma si attende il completamento del timer di spegnimento obbligatorio.",
                quandoCompare = "Notte, SOC <= minimo ma MIN_RUN_TIME in corso.",
                effetto = "Richiede OFF ma attende completamento timer.",
                category = AcReasonCategory.WARNING
            )

            clean == "ESECUZIONE_SPEGNIMENTO" -> AcReasonInfo(
                code = clean,
                description = "Registrato nell'istante in cui la logica impone e invia il comando effettivo di spegnimento dell'aria condizionata.",
                quandoCompare = "Richiesta spegnimento autorizzata e timer terminato.",
                effetto = "Stato -> OFF; registra spegnimento; invia comando IR OFF.",
                category = AcReasonCategory.NORMAL
            )

            clean == "ESECUZIONE_STANDBY_INVERTER" -> AcReasonInfo(
                code = clean,
                description = "Registrato nell'istante in cui l'unità viene comandata in modalità standby energetico.",
                quandoCompare = "Standby richiesto e timer MIN_RUN_TIME terminato.",
                effetto = "Stato -> STANDBY_INVERTER; registra spegnimento.",
                category = AcReasonCategory.NORMAL
            )

            else -> AcReasonInfo(
                code = rawReason,
                description = rawReason,
                category = AcReasonCategory.UNKNOWN
            )
        }

        if (aiData == null) return baseInfo

        val elec = aiData.metricheElettriche
        val logica = aiData.logicaControllo
        val env = aiData.metricheAmbientali

        val metricsList = mutableListOf<AcReasonMetric>()
        val locale = Locale.getDefault()

        val soglieStr = when {
            clean.startsWith("WATCHDOG_FALSO") -> "Consumo AC (${elec.consumoAcW.toInt()} W) > 30 W"
            clean.startsWith("WATCHDOG_TENTATIVO") -> "Consumo AC (${elec.consumoAcW.toInt()} W) < 10 W"
            clean.startsWith("WATCHDOG_SOSPENSIONE") -> "Tentativi watchdog >= 3"
            clean.startsWith("PEAK_SHAVING") -> "Scarica Batteria (${elec.batteryDischargeW.toInt()} W) > 2500 W"
            clean.startsWith("SOPRAVVIVENZA") -> "Cuscinetto (${"%.1f".format(locale, logica.cuscinettoSicurezzaKwh)} kWh) < Richiesto (${"%.1f".format(locale, logica.cuscinettoRichiestoKwh)} kWh)"
            clean == "BLOCCO_TIMER_ANTICICLO_OFF" -> "Timer Anti-ciclo (${logica.tempoMancanteAnticicloMinuti} min rimanenti)"
            clean.startsWith("BLOCCO_NOTTE_BATTERIA") || clean == "BLOCCO_DIURNO_BATTERIA" -> "SOC Batteria (${elec.powerwallSocPercent.toInt()}%) <= SOC Minimo (${logica.socMinimoApplied.toInt()}%)"
            clean == "BLOCCO_GRAZIA_VINCOLO_SOLARE" -> "Surplus Fotovoltaico (${elec.surplusW.toInt()} W) < 1000 W"
            clean == "ATTESA_SURPLUS_SOLARE" -> "Surplus Fotovoltaico (${elec.surplusW.toInt()} W) <= 150 W"
            clean == "COMFORT_DIURNO_RAGGIUNTO" -> "Humidex (${"%.1f".format(locale, env.humidexLiving)}) < Soglia (${"%.1f".format(locale, logica.sogliaAttivazioneApplicata)})"
            clean == "COMFORT_NOTTURNO_RAGGIUNTO" -> "Humidex (${"%.1f".format(locale, env.humidexLiving)}) < Target"
            clean.contains("DEFICIT") || clean.contains("TOLLERANZA") -> "Tolleranza Deficit Impostata: ${logica.tempoMancanteAnticicloMinuti} min"
            clean == "COMFORT_RAGGIUNTO_ATTESA_TIMER" -> "Tempo Minimo ON (MIN_RUN_TIME) in corso"
            else -> ""
        }

        when {
            clean.startsWith("WATCHDOG") -> {
                metricsList.add(AcReasonMetric("Consumo AC", "${elec.consumoAcW.toInt()} W"))
                metricsList.add(AcReasonMetric("Surplus Solare", "${elec.surplusW.toInt()} W"))
            }

            clean.contains("SOLARE") || clean.contains("DEFICIT") || clean.contains("TOLLERANZA") -> {
                metricsList.add(AcReasonMetric("Surplus Solare", "${elec.surplusW.toInt()} W"))
                metricsList.add(AcReasonMetric("SOC Batteria", "${elec.powerwallSocPercent.toInt()}%"))
            }

            clean.contains("BATTERIA") || clean.contains("PEAK_SHAVING") || clean.contains("SOPRAVVIVENZA") -> {
                metricsList.add(AcReasonMetric("SOC Batteria", "${elec.powerwallSocPercent.toInt()}%"))
                metricsList.add(AcReasonMetric("SOC Minimo", "${logica.socMinimoApplied.toInt()}%"))
                metricsList.add(AcReasonMetric("Potenza Batteria", "${elec.batteryPowerW.toInt()} W"))
            }

            clean.contains("HUMIDEX") || clean.contains("COOLING") || clean.contains("NIGHT_DRY") || clean.contains("COMFORT") || clean.contains("PRONTO") -> {
                metricsList.add(AcReasonMetric("Humidex Int.", "%.1f".format(locale, env.humidexLiving)))
                metricsList.add(AcReasonMetric("Soglia Humidex", "%.1f".format(locale, logica.sogliaAttivazioneApplicata)))
                metricsList.add(AcReasonMetric("Surplus Solare", "${elec.surplusW.toInt()} W"))
            }

            else -> {
                metricsList.add(AcReasonMetric("Surplus Solare", "${elec.surplusW.toInt()} W"))
                metricsList.add(AcReasonMetric("SOC Batteria", "${elec.powerwallSocPercent.toInt()}%"))
            }
        }

        return baseInfo.copy(soglieCostanti = soglieStr, metrics = metricsList)
    }
}
