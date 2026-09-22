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
    val userTitle: String = "",
    val userExplanation: String = "",
    val description: String = "",
    val quandoCompare: String = "",
    val effetto: String = "",
    val soglieCostanti: String = "",
    val category: AcReasonCategory,
    val remainingTimerMinutes: Int? = null,
    val metrics: List<AcReasonMetric> = emptyList()
)

object AcReasonMapper {

    fun getAcReasonInfo(rawReason: String, aiData: AiManagedData? = null): AcReasonInfo {
        val clean = rawReason.trim().uppercase()
        val diag = aiData?.logicaControllo?.diagnosticaAc
        val causa = diag?.causaTransizione?.trim()?.uppercase()?.ifEmpty { null }

        if (clean.isEmpty()) {
            return AcReasonInfo(
                code = "N/D",
                userTitle = "Stato Non Disponibile",
                userExplanation = "Nessun motivo di logica comunicato dal sistema.",
                description = "Nessun motivo di logica comunicato dal sistema.",
                category = AcReasonCategory.UNKNOWN
            )
        }

        val baseInfo = when {
            clean == "ANALISI_IN_CORSO" -> AcReasonInfo(
                code = clean,
                userTitle = "Analisi in corso",
                userExplanation = "Valutazione condizioni ambientali ed energetiche...",
                description = "Valore iniziale predefinito prima che la macchina a stati valuti le condizioni ambientali ed energetiche.",
                quandoCompare = "Valore iniziale prima della logica AC.",
                effetto = "Valore transitorio; normalmente sostituito.",
                category = AcReasonCategory.UNKNOWN
            )

            clean == "SISTEMA_DISABILITATO_DA_UTENTE" -> AcReasonInfo(
                code = clean,
                userTitle = "Sistema disabilitato da utente",
                userExplanation = "L'interruttore generale AI_climate_enabling è disattivato.",
                description = "Generato quando l'interruttore generale di abilitazione viene impostato su false.",
                quandoCompare = "AI_climate_enabling = false.",
                effetto = "Disabilitazione generale; forza OFF.",
                category = AcReasonCategory.WARNING
            )

            clean == "WATCHDOG_FALSO_OFF" -> AcReasonInfo(
                code = clean,
                userTitle = "Anomalia Watchdog Falso OFF",
                userExplanation = "Rilevato assorbimento AC fisicamente attivo mentre la logica è in OFF.",
                description = "Attivato se lo stato logico è OFF ma il sensore rileva consumi AC.",
                quandoCompare = "Stato logico OFF ma AC in assorbimento.",
                effetto = "Invia OFF immediato di sicurezza.",
                category = AcReasonCategory.WARNING
            )

            clean.startsWith("WATCHDOG_TENTATIVO_RIAVVIO") -> AcReasonInfo(
                code = clean,
                userTitle = "Tentativo riavvio watchdog",
                userExplanation = "Unità accesa ma nessun assorbimento elettrico rilevato.",
                description = "Generato quando l'AC è acceso ma consuma <10W per oltre 6 minuti.",
                quandoCompare = "AC acceso ma assorbe <10 W (tentativi <3).",
                effetto = "Forza OFF temporaneo per nuovo tentativo.",
                category = AcReasonCategory.WARNING
            )

            clean == "WATCHDOG_SOSPENSIONE_SISTEMA_GUASTO" -> AcReasonInfo(
                code = clean,
                userTitle = "Sospensione sistema per guasto",
                userExplanation = "Watchdog fallito per 3 tentativi consecutivi di riavvio.",
                description = "Attivato dopo 3 fallimenti consecutivi del watchdog per sicurezza.",
                quandoCompare = "Ulteriore fallimento dopo 3 tentativi watchdog.",
                effetto = "Disabilita IA climatica e forza OFF.",
                category = AcReasonCategory.CRITICAL
            )

            clean == "LOGICA_INVERNALE_ATTIVA" -> AcReasonInfo(
                code = clean,
                userTitle = "Logica invernale attiva",
                userExplanation = "Gestione del riscaldamento nei mesi invernali.",
                description = "Impostato stabilmente durante i mesi invernali per gestire il riscaldamento.",
                quandoCompare = "Normale elaborazione in stagione INVERNO.",
                effetto = "Gestione riscaldamento invernale.",
                category = AcReasonCategory.NORMAL
            )

            clean.startsWith("PEAK_SHAVING_ATTIVO") -> AcReasonInfo(
                code = clean,
                userTitle = "Protezione picco scarica batteria",
                userExplanation = "Protezione da elevata potenza di scarica batteria attiva.",
                description = "Generato in estate se la scarica batteria supera il limite di picco.",
                quandoCompare = "Estate, sera dinamica, elevata scarica batteria.",
                effetto = "Blocco spegnimento emergenza attivo.",
                category = AcReasonCategory.CRITICAL
            )

            clean.startsWith("SOPRAVVIVENZA_NOTTE_ATTIVA") -> AcReasonInfo(
                code = clean,
                userTitle = "Sopravvivenza notturna attiva",
                userExplanation = "Riserva di carica in batteria insufficiente per coprire la notte.",
                description = "Generato quando il cuscinetto energetico in batteria è inferiore al minimo notturno.",
                quandoCompare = "Estate, sera dinamica, riserva energetica insufficiente.",
                effetto = "Blocca nuove accensioni; forza OFF.",
                category = AcReasonCategory.CRITICAL
            )

            clean == "BLOCCO_TIMER_ANTICICLO_OFF" -> {
                val remaining = diag?.timerResiduoMinuti?.takeIf { it > 0 }
                AcReasonInfo(
                    code = clean,
                    userTitle = "Riavvio temporaneamente bloccato",
                    userExplanation = "Attesa del tempo minimo di spegnimento prima di un nuovo avvio.",
                    description = "Impostato se il timer di protezione anti-ciclo di accensione (minMancantiAccensione) è ancora attivo.",
                    quandoCompare = "Estate, AC OFF e tempo minimo dall'ultimo spegnimento in corso.",
                    effetto = "Impedisce nuova accensione nel ciclo.",
                    category = AcReasonCategory.WARNING,
                    remainingTimerMinutes = remaining
                )
            }

            clean == "BLOCCO_NOTTE_BATTERIA_BASSA" -> AcReasonInfo(
                code = clean,
                userTitle = "Batteria notturna insufficiente",
                userExplanation = "Livello batteria pari o inferiore al minimo logico notturno.",
                description = "Generato in fascia notturna se il livello di carica della batteria è <= al minimo.",
                quandoCompare = "Estate, notte, AC OFF e SOC <= soglia minima.",
                effetto = "Blocca deumidificazione notturna NIGHT_DRY.",
                category = AcReasonCategory.CRITICAL
            )

            clean == "COMFORT_NOTTURNO_RAGGIUNTO" -> AcReasonInfo(
                code = clean,
                userTitle = "Comfort notturno raggiunto",
                userExplanation = "Humidex camera dentro la zona di comfort notturna.",
                description = "Attivato di notte quando l'Humidex camera non supera la soglia di attivazione.",
                quandoCompare = "Estate, notte, AC OFF, Humidex camera sotto soglia di riaccensione.",
                effetto = "Nessuna accensione necessaria.",
                category = AcReasonCategory.NORMAL
            )

            clean == "BLOCCO_NOTTE_PAUSA_45M" -> {
                val remaining = diag?.pausaNotturnaResiduoMinuti?.takeIf { it > 0 }
                AcReasonInfo(
                    code = clean,
                    userTitle = "Pausa notturna in corso",
                    userExplanation = "Nuova accensione temporaneamente sospesa dalla pausa notturna obbligatoria.",
                    description = "Generato di notte se è trascorso meno dell'intervallo minimo di 45 minuti dall'ultimo spegnimento.",
                    quandoCompare = "Estate, notte; pausa specifica NIGHT_DRY non terminata (45 min).",
                    effetto = "Blocca riaccensione NIGHT_DRY.",
                    category = AcReasonCategory.WARNING,
                    remainingTimerMinutes = remaining
                )
            }

            clean == "PRONTO_ACCENSIONE_NIGHT_DRY" -> AcReasonInfo(
                code = clean,
                userTitle = "Pronto per deumidificazione notturna",
                userExplanation = "Tutte le verifiche notturne superate: pronto all'avvio.",
                description = "Condizione in cui batteria, Humidex camera e timer notturni consentono l'avvio.",
                quandoCompare = "Notte: SOC e Humidex camera adeguati, pausa notturna terminata.",
                effetto = "Autorizza deumidificazione NIGHT_DRY.",
                category = AcReasonCategory.NORMAL
            )

            clean == "BLOCCO_GRAZIA_VINCOLO_SOLARE" -> AcReasonInfo(
                code = clean,
                userTitle = "Vincolo solare in corso",
                userExplanation = "Modalità solare restrittiva attiva e surplus fotovoltaico non sufficiente.",
                description = "Attivato nel periodo di grazia diurno se il surplus fotovoltaico è insufficiente.",
                quandoCompare = "Periodo grazia con vincolo solare ma fotovoltaico insufficiente.",
                effetto = "Blocca accensione.",
                category = AcReasonCategory.WARNING
            )

            clean == "BLOCCO_DIURNO_BATTERIA" -> AcReasonInfo(
                code = clean,
                userTitle = "Batteria diurna insufficiente",
                userExplanation = "Batteria sotto il limite minimo e Humidex non emergenziale.",
                description = "Generato di giorno se la batteria è sotto il limite minimo e l'Humidex non ha raggiunto l'emergenza.",
                quandoCompare = "Giorno, AC OFF, SOC sotto minimo, Humidex non emergenziale.",
                effetto = "Blocca accensione.",
                category = AcReasonCategory.CRITICAL
            )

            clean == "BLOCCO_DIURNO_TEMPERATURA_BASSA" -> AcReasonInfo(
                code = clean,
                userTitle = "Temperatura diurna bassa",
                userExplanation = "Temperatura ambiente al di sotto della soglia di avvio cooling.",
                description = "Generato quando la temperatura interna si mantiene bassa e non richiede raffrescamento.",
                quandoCompare = "Giorno, temperatura interna sotto soglia avvio.",
                effetto = "Blocca accensione raffrescamento.",
                category = AcReasonCategory.NORMAL
            )

            clean == "ATTESA_SOGLIA_HUMIDEX" -> AcReasonInfo(
                code = clean,
                userTitle = "In attesa soglia Humidex",
                userExplanation = "Humidex interno al di sotto della soglia di attivazione.",
                description = "Impostato quando le condizioni climatiche interne sono entro i valori di tolleranza.",
                quandoCompare = "Giorno, Humidex interno sotto la soglia di attivazione.",
                effetto = "Nessuna accensione richiesta.",
                category = AcReasonCategory.NORMAL
            )

            clean == "ATTESA_SURPLUS_SOLARE" -> AcReasonInfo(
                code = clean,
                userTitle = "In attesa di surplus solare",
                userExplanation = "Surplus fotovoltaico insufficiente per l'avvio.",
                description = "Attivato di giorno quando il surplus fotovoltaico non è sufficiente ad alimentare l'AC.",
                quandoCompare = "Giorno, surplus virtuale insufficiente e nessun override.",
                effetto = "Attende maggior produzione solare.",
                category = AcReasonCategory.WARNING
            )

            clean == "COMFORT_DIURNO_RAGGIUNTO" -> AcReasonInfo(
                code = clean,
                userTitle = "Comfort diurno raggiunto",
                userExplanation = "Humidex si mantiene sotto la soglia di attivazione.",
                description = "Condizione diurna in cui l'Humidex si mantiene al di sotto della soglia di comfort.",
                quandoCompare = "Giorno, Humidex sotto soglia attivazione.",
                effetto = "Nessuna accensione.",
                category = AcReasonCategory.NORMAL
            )

            clean == "PRONTO_ACCENSIONE_COOLING" || clean == "PRONTO_ACCENSIONE_OVERRIDE_SOLARE" -> AcReasonInfo(
                code = clean,
                userTitle = "Pronto per raffrescamento",
                userExplanation = "Condizioni di surplus solare o Humidex favorevoli all'avvio.",
                description = "Segnala che le condizioni di surplus o superamento Humidex consentono l'avvio.",
                quandoCompare = "Condizioni favorevoli all'accensione diurna.",
                effetto = "Autorizza raffrescamento; segue COOLING_ON.",
                category = AcReasonCategory.NORMAL
            )

            clean == "ESECUZIONE_ACCENSIONE_DIURNA" || clean == "ESECUZIONE_ACCENSIONE_NOTTURNA" -> AcReasonInfo(
                code = clean,
                userTitle = if (clean.contains("NOTTURNA")) "Avvio deumidificazione notturna" else "Avvio raffrescamento diurno",
                userExplanation = "Tutte le condizioni soddisfatte: invio comando di accensione.",
                description = "Registrato nell'istante in cui viene inviato il comando IR di accensione.",
                quandoCompare = "Tutte le condizioni finali di accensione soddisfatte.",
                effetto = "Attiva unità e passa in COOLING_ON / NIGHT_DRY.",
                category = AcReasonCategory.NORMAL
            )

            clean == "MANTENIMENTO_COOLING_ON" -> AcReasonInfo(
                code = clean,
                userTitle = "Raffrescamento diurno in corso",
                userExplanation = "Regolare mantenimento dei parametri di comfort e produzione solare.",
                description = "Indica che il condizionatore è acceso in raffrescamento diurno regolare.",
                quandoCompare = "Estate, AC in funzione e nessun blocco.",
                effetto = "Mantiene funzionamento corrente.",
                category = AcReasonCategory.NORMAL
            )

            clean == "MANTENIMENTO_NIGHT_DRY" -> AcReasonInfo(
                code = clean,
                userTitle = "Deumidificazione notturna in corso",
                userExplanation = "Regolare proseguimento del ciclo notturno con batteria stabile.",
                description = "Segnala la regolare prosecuzione del ciclo di deumidificazione notturna.",
                quandoCompare = "Estate, notte, deumidificazione regolare.",
                effetto = "Mantiene funzionamento corrente.",
                category = AcReasonCategory.NORMAL
            )

            clean == "MANTENIMENTO_STANDBY_INVERTER" -> AcReasonInfo(
                code = clean,
                userTitle = "Standby energetico inverter",
                userExplanation = "Pausa gestita dall'inverter; supervisione logica attiva.",
                description = "Unità in pausa energetica gestita dall'inverter in attesa di ripresa o spegnimento.",
                quandoCompare = "Pausa energetica inverter.",
                effetto = "Mantiene supervisione logica attiva.",
                category = AcReasonCategory.NORMAL
            )

            clean.startsWith("MANTENIMENTO_") -> AcReasonInfo(
                code = clean,
                userTitle = "Funzionamento di regime regolare",
                userExplanation = "Regolare prosecuzione dello stato attivo corrente.",
                description = "Generato durante i cicli di regime in cui il sistema conferma lo stato attivo.",
                quandoCompare = "Ciclo di regime attivo.",
                effetto = "Conferma prosecuzione stato attivo.",
                category = AcReasonCategory.NORMAL
            )

            clean == "COMFORT_TEMP_BASSA_ATTESA_TIMER" -> {
                val remaining = diag?.timerResiduoMinuti?.takeIf { it > 0 }
                AcReasonInfo(
                    code = clean,
                    userTitle = "Comfort per temperatura - attesa timer",
                    userExplanation = "Temperatura interna sotto la soglia di comfort.\nSpegnimento in attesa del completamento del tempo minimo di funzionamento.",
                    description = "Comfort per temperatura raggiunto ma attesa completamento timer minimo.",
                    quandoCompare = "Comfort per temperatura raggiunto e timer in corso.",
                    effetto = "Attende completamento timer prima dello spegnimento.",
                    category = AcReasonCategory.NORMAL,
                    remainingTimerMinutes = remaining
                )
            }

            clean == "SPEGNIMENTO_COMFORT_TEMP_BASSA" -> AcReasonInfo(
                code = clean,
                userTitle = "Spegnimento per comfort temperatura",
                userExplanation = "Temperatura interna scesa sotto la soglia di comfort.",
                description = "Generato quando la temperatura interna raggiunge la soglia di disattivazione comfort.",
                quandoCompare = "Temperatura interna <= soglia disattivazione comfort.",
                effetto = "Invia comando di spegnimento per comfort raggiunto.",
                category = AcReasonCategory.NORMAL
            )

            clean == "COMFORT_RAGGIUNTO_ATTESA_TIMER" -> {
                val remaining = diag?.timerResiduoMinuti?.takeIf { it > 0 }
                AcReasonInfo(
                    code = clean,
                    userTitle = "Comfort raggiunto",
                    userExplanation = "Comfort raggiunto; attesa del tempo minimo di funzionamento prima dello spegnimento o standby.",
                    description = "Comfort raggiunto ma attesa completamento del tempo minimo obbligatorio di funzionamento (MIN_RUN_TIME).",
                    quandoCompare = "Comfort raggiunto ma MIN_RUN_TIME in corso.",
                    effetto = "Attende timer obbligatorio prima dello spegnimento/standby.",
                    category = AcReasonCategory.NORMAL,
                    remainingTimerMinutes = remaining
                )
            }

            clean == "STANDBY_COMFORT_RAGGIUNTO" -> AcReasonInfo(
                code = clean,
                userTitle = "Standby per comfort raggiunto",
                userExplanation = "Comfort raggiunto e timer minimo di funzionamento completato.",
                description = "Generato quando il comfort è raggiunto e il timer minimo è scaduto.",
                quandoCompare = "Comfort raggiunto e MIN_RUN_TIME scaduto.",
                effetto = "Passaggio in standby energetico.",
                category = AcReasonCategory.NORMAL
            )

            clean == "DEFICIT_RILEVATO_AVVIO_TIMER" -> AcReasonInfo(
                code = clean,
                userTitle = "Deficit energetico rilevato",
                userExplanation = "Bilancio energetico in deficit: avviato il timer di tolleranza.",
                description = "Attivato quando il bilancio energetico passa in deficit e parte il conteggio di tolleranza.",
                quandoCompare = "Surplus sotto limite e timer non ancora attivo.",
                effetto = "Avvia timer tolleranza deficit.",
                category = AcReasonCategory.WARNING,
                remainingTimerMinutes = null
            )

            clean == "TOLLERANZA_DEFICIT_IN_CORSO" -> {
                val remaining = diag?.deficitResiduoMinuti?.takeIf { it > 0 }
                AcReasonInfo(
                    code = clean,
                    userTitle = "Deficit energetico temporaneamente tollerato",
                    userExplanation = "Deficit energetico in corso ma ancora entro il tempo di tolleranza.",
                    description = "Segnala che la casa è in deficit energetico e il timer di tolleranza è in corso.",
                    quandoCompare = "Deficit in corso entro il tempo di tolleranza.",
                    effetto = "Mantiene funzionamento in attesa del timer.",
                    category = AcReasonCategory.WARNING,
                    remainingTimerMinutes = remaining
                )
            }

            clean == "STANDBY_DEFICIT_SOLARE" -> AcReasonInfo(
                code = clean,
                userTitle = "Standby per deficit solare",
                userExplanation = "Timer di tolleranza al deficit scaduto: passaggio in standby.",
                description = "Generato quando scade la tolleranza al deficit energetico.",
                quandoCompare = "Timer tolleranza deficit scaduto.",
                effetto = "Passaggio in standby per preservare la batteria.",
                category = AcReasonCategory.WARNING
            )

            clean == "RIPRESA_COOLING_DA_STANDBY" -> AcReasonInfo(
                code = clean,
                userTitle = "Ripresa raffrescamento da standby",
                userExplanation = "Humidex di nuovo sopra la soglia: riavvio del compressore.",
                description = "Attivato quando dallo standby inverter l'Humidex torna a salire.",
                quandoCompare = "Standby Inverter e Humidex >= soglia.",
                effetto = "Riattiva raffrescamento.",
                category = AcReasonCategory.NORMAL
            )

            clean == "BATTERIA_SCARICA_ATTESA_TIMER" -> {
                val remaining = diag?.timerResiduoMinuti?.takeIf { it > 0 }
                AcReasonInfo(
                    code = clean,
                    userTitle = "Batteria scarica - in attesa timer",
                    userExplanation = "Batteria sotto il limite minimo; attesa del tempo minimo di funzionamento prima dello spegnimento.",
                    description = "Generato quando la batteria scende sotto il minimo ma si attende il timer minimo di funzionamento.",
                    quandoCompare = "Notte, SOC <= minimo ma MIN_RUN_TIME in corso.",
                    effetto = "Attende timer prima dello spegnimento.",
                    category = AcReasonCategory.WARNING,
                    remainingTimerMinutes = remaining
                )
            }

            clean == "ESECUZIONE_SPEGNIMENTO" -> {
                val explanation = when {
                    causa == "SPEGNIMENTO_COMFORT_TEMP_BASSA" || causa?.contains("COMFORT") == true -> "Comfort raggiunto"
                    causa != null -> causa.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                    else -> "Comando di spegnimento eseguito"
                }
                AcReasonInfo(
                    code = clean,
                    userTitle = "Condizionatore spento",
                    userExplanation = explanation,
                    description = "Registrato nell'istante in cui la logica invia il comando effettivo di spegnimento.",
                    quandoCompare = "Richiesta spegnimento autorizzata e timer terminato.",
                    effetto = "Stato -> OFF; invia comando IR OFF.",
                    category = AcReasonCategory.NORMAL
                )
            }

            clean == "ESECUZIONE_STANDBY_INVERTER" -> {
                val explanation = when {
                    causa?.contains("COMFORT") == true -> "Comfort raggiunto"
                    causa != null -> causa.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                    else -> "Unità comandata in modalità standby energetico"
                }
                AcReasonInfo(
                    code = clean,
                    userTitle = "Standby Inverter",
                    userExplanation = explanation,
                    description = "Registrato nell'istante in cui l'unità viene comandata in standby energetico.",
                    quandoCompare = "Standby richiesto e timer MIN_RUN_TIME terminato.",
                    effetto = "Stato -> STANDBY_INVERTER.",
                    category = AcReasonCategory.NORMAL
                )
            }

            clean == "PR_PRESTART_PENDING" -> AcReasonInfo(
                code = clean,
                userTitle = "Riserva predittiva in preparazione",
                userExplanation = "Pre-avvio riserva predittiva pendente.",
                description = "Condizione di pre-avvio per la riserva energetica predittiva.",
                quandoCompare = "Programmazione riserva predittiva attiva.",
                effetto = "In attesa della finestra di esecuzione riserva.",
                category = AcReasonCategory.NORMAL
            )

            clean == "BLOCCO_PR_REQUEST_INVALID" || clean == "BLOCCO_PR_GATE_INVALID" -> AcReasonInfo(
                code = clean,
                userTitle = "Riserva predittiva non valida",
                userExplanation = "Richiesta o condizioni gate per riserva predittiva non valide.",
                description = "Blocco generato quando la richiesta o il gate della riserva predittiva sono non validi.",
                quandoCompare = "Parametri o gate riserva predittiva non conformi.",
                effetto = "Blocca avvio riserva predittiva.",
                category = AcReasonCategory.WARNING
            )

            else -> AcReasonInfo(
                code = rawReason,
                userTitle = rawReason.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                userExplanation = "Motivo di logica: $rawReason",
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

        val isNightReason = clean == "COMFORT_NOTTURNO_RAGGIUNTO" ||
                clean == "PRONTO_ACCENSIONE_NIGHT_DRY" ||
                clean == "MANTENIMENTO_NIGHT_DRY" ||
                clean == "BLOCCO_NOTTE_PAUSA_45M" ||
                clean == "ESECUZIONE_ACCENSIONE_NOTTURNA" ||
                clean == "BLOCCO_NOTTE_BATTERIA_BASSA" ||
                clean == "BATTERIA_SCARICA_ATTESA_TIMER"

        val soglieStr = when {
            clean.startsWith("WATCHDOG_FALSO") -> "Rilevato assorbimento AC non previsto"
            clean.startsWith("WATCHDOG_TENTATIVO") -> "Nessun assorbimento AC rilevato"
            clean.startsWith("WATCHDOG_SOSPENSIONE") -> "Sospensione sistema per guasti ripetuti"
            clean.startsWith("PEAK_SHAVING") -> "Protezione da elevata potenza di scarica batteria"
            clean.startsWith("SOPRAVVIVENZA") -> "Cuscinetto (${"%.1f".format(locale, logica.cuscinettoSicurezzaKwh)} kWh) < Richiesto (${"%.1f".format(locale, logica.cuscinettoRichiestoKwh)} kWh)"
            clean == "BLOCCO_TIMER_ANTICICLO_OFF" -> ""
            clean.startsWith("BLOCCO_NOTTE_BATTERIA") || clean == "BLOCCO_DIURNO_BATTERIA" -> "SOC Batteria (${elec.powerwallSocPercent.toInt()}%) <= SOC Minimo (${logica.socMinimoApplied.toInt()}%)"
            clean == "BLOCCO_GRAZIA_VINCOLO_SOLARE" -> "Surplus fotovoltaico non sufficiente per vincolo solare"
            clean == "ATTESA_SURPLUS_SOLARE" -> "Surplus fotovoltaico insufficiente"
            clean == "COMFORT_DIURNO_RAGGIUNTO" -> "Humidex (${"%.1f".format(locale, env.humidexLiving)}) < Soglia configurata (${"%.1f".format(locale, logica.sogliaAttivazioneApplicata)})"
            clean == "COMFORT_NOTTURNO_RAGGIUNTO" -> ""
            clean == "BLOCCO_NOTTE_PAUSA_45M" -> ""
            clean.contains("DEFICIT") || clean.contains("TOLLERANZA") -> ""
            clean == "COMFORT_RAGGIUNTO_ATTESA_TIMER" -> ""
            else -> ""
        }

        val isTempComfortReason = clean == "COMFORT_TEMP_BASSA_ATTESA_TIMER" || clean == "SPEGNIMENTO_COMFORT_TEMP_BASSA"
        val isDayHumidexReason = clean == "ATTESA_SOGLIA_HUMIDEX" || clean == "COMFORT_DIURNO_RAGGIUNTO"
        val isBatteryBlockReason = clean == "BLOCCO_DIURNO_BATTERIA" || clean == "BLOCCO_NOTTE_BATTERIA_BASSA"
        val isAntiCycleReason = clean == "BLOCCO_TIMER_ANTICICLO_OFF"
        val isComfortWaitingTimerReason = clean == "COMFORT_RAGGIUNTO_ATTESA_TIMER"
        val isBatteryWaitingTimerReason = clean == "BATTERIA_SCARICA_ATTESA_TIMER"
        val isNightPauseReason = clean == "BLOCCO_NOTTE_PAUSA_45M"
        val isNightComfortReached = clean == "COMFORT_NOTTURNO_RAGGIUNTO"
        val isCoolingStandbyMaintenance = clean == "MANTENIMENTO_STANDBY_INVERTER"
        val isCoolingMaintenance = clean == "MANTENIMENTO_COOLING_ON"
        val isStandbyComfortReached = clean == "STANDBY_COMFORT_RAGGIUNTO"
        val isMaintenanceNightDry = clean == "MANTENIMENTO_NIGHT_DRY"

        when {
            clean.startsWith("WATCHDOG") -> {
                metricsList.add(AcReasonMetric("Consumo AC", "${elec.consumoAcW.toInt()} W"))
                metricsList.add(AcReasonMetric("Surplus Solare", "${elec.surplusW.toInt()} W"))
            }

            isAntiCycleReason -> {
                diag?.timerTipo?.takeIf { it.isNotBlank() }?.let {
                    metricsList.add(AcReasonMetric("Timer", it))
                }
                diag?.timerDurataMinuti?.takeIf { it > 0 }?.let {
                    metricsList.add(AcReasonMetric("Durata minima", "$it min"))
                }
            }

            isTempComfortReason -> {
                metricsList.add(AcReasonMetric("Temperatura Int.", "%.1f °C".format(locale, env.temperaturaC)))
                logica.sogliaTemperaturaOffComfortC?.let { soglia ->
                    metricsList.add(AcReasonMetric("Soglia Comfort", "%.1f °C".format(locale, soglia)))
                }
            }

            isDayHumidexReason -> {
                metricsList.add(AcReasonMetric("Humidex zona giorno", "%.1f".format(locale, env.humidexLiving)))
                metricsList.add(AcReasonMetric("Humidex di attivazione", "%.1f".format(locale, logica.sogliaAttivazioneApplicata)))
            }

            clean.contains("SOLARE") || clean.contains("DEFICIT") || clean.contains("TOLLERANZA") -> {
                metricsList.add(AcReasonMetric("Surplus Solare", "${elec.surplusW.toInt()} W"))
                metricsList.add(AcReasonMetric("SOC Batteria", "${elec.powerwallSocPercent.toInt()}%"))
                if (clean == "DEFICIT_RILEVATO_AVVIO_TIMER" || clean == "TOLLERANZA_DEFICIT_IN_CORSO") {
                    diag?.deficitTolleranzaMinuti?.takeIf { it > 0 }?.let {
                        metricsList.add(AcReasonMetric("Tolleranza", "$it min"))
                    }
                }
            }

            isBatteryBlockReason -> {
                metricsList.add(AcReasonMetric("SOC Batteria", "${elec.powerwallSocPercent.toInt()}%"))
                metricsList.add(AcReasonMetric("SOC Minimo", "${logica.socMinimoApplied.toInt()}%"))
                if (clean == "BLOCCO_NOTTE_BATTERIA_BASSA") {
                    metricsList.add(AcReasonMetric("Humidex camera", "%.1f".format(locale, env.humidexBedroom)))
                } else {
                    metricsList.add(AcReasonMetric("Humidex zona giorno", "%.1f".format(locale, env.humidexLiving)))
                }
            }

            isComfortWaitingTimerReason -> {
                metricsList.add(AcReasonMetric("Humidex zona giorno", "%.1f".format(locale, env.humidexLiving)))
                logica.sogliaSpegnimentoComfort.let { soglia ->
                    metricsList.add(AcReasonMetric("Humidex comfort", "%.1f".format(locale, soglia)))
                }
                diag?.timerTipo?.takeIf { it.isNotBlank() }?.let {
                    metricsList.add(AcReasonMetric("Timer", it))
                }
                diag?.timerDurataMinuti?.takeIf { it > 0 }?.let {
                    metricsList.add(AcReasonMetric("Durata minima", "$it min"))
                }
            }

            isStandbyComfortReached -> {
                metricsList.add(AcReasonMetric("Humidex zona giorno", "%.1f".format(locale, env.humidexLiving)))
                logica.sogliaSpegnimentoComfort.let { soglia ->
                    metricsList.add(AcReasonMetric("Humidex comfort", "%.1f".format(locale, soglia)))
                }
            }

            isCoolingMaintenance -> {
                metricsList.add(AcReasonMetric("Humidex zona giorno", "%.1f".format(locale, env.humidexLiving)))
                logica.sogliaSpegnimentoComfort.let { soglia ->
                    metricsList.add(AcReasonMetric("Humidex comfort", "%.1f".format(locale, soglia)))
                }
                metricsList.add(AcReasonMetric("Surplus Solare", "${elec.surplusW.toInt()} W"))
            }

            isBatteryWaitingTimerReason -> {
                metricsList.add(AcReasonMetric("SOC Batteria", "${elec.powerwallSocPercent.toInt()}%"))
                metricsList.add(AcReasonMetric("SOC Minimo", "${logica.socMinimoApplied.toInt()}%"))
                diag?.timerTipo?.takeIf { it.isNotBlank() }?.let {
                    metricsList.add(AcReasonMetric("Timer", it))
                }
                diag?.timerDurataMinuti?.takeIf { it > 0 }?.let {
                    metricsList.add(AcReasonMetric("Durata minima", "$it min"))
                }
            }

            isNightPauseReason -> {
                metricsList.add(AcReasonMetric("Humidex camera", "%.1f".format(locale, env.humidexBedroom)))
                metricsList.add(AcReasonMetric("Humidex impostato", "%.1f".format(locale, logica.sogliaAttivazioneApplicata)))
            }

            isNightComfortReached -> {
                metricsList.add(AcReasonMetric("Humidex camera", "%.1f".format(locale, env.humidexBedroom)))
                metricsList.add(AcReasonMetric("Margine NIGHT_DRY", "%.1f".format(locale, logica.nightDryHumidexMargin)))
                metricsList.add(AcReasonMetric("Soglia NIGHT_DRY", "%.1f".format(locale, logica.nightDryHumidexThreshold)))
            }

            clean == "PRONTO_ACCENSIONE_NIGHT_DRY" -> {
                metricsList.add(AcReasonMetric("Humidex camera", "%.1f".format(locale, env.humidexBedroom)))
                metricsList.add(AcReasonMetric("Margine NIGHT_DRY", "%.1f".format(locale, logica.nightDryHumidexMargin)))
                metricsList.add(AcReasonMetric("Soglia NIGHT_DRY", "%.1f".format(locale, logica.nightDryHumidexThreshold)))
                metricsList.add(AcReasonMetric("SOC Batteria", "${elec.powerwallSocPercent.toInt()}%"))
            }

            isMaintenanceNightDry -> {
                metricsList.add(AcReasonMetric("Humidex camera", "%.1f".format(locale, env.humidexBedroom)))
                metricsList.add(AcReasonMetric("SOC Batteria", "${elec.powerwallSocPercent.toInt()}%"))
            }

            isCoolingStandbyMaintenance -> {
                metricsList.add(AcReasonMetric("Humidex zona giorno", "%.1f".format(locale, env.humidexLiving)))
                metricsList.add(AcReasonMetric("Humidex di attivazione", "%.1f".format(locale, logica.sogliaAttivazioneApplicata)))
                metricsList.add(AcReasonMetric("Surplus Solare", "${elec.surplusW.toInt()} W"))
            }

            clean.contains("BATTERIA") || clean.contains("PEAK_SHAVING") || clean.contains("SOPRAVVIVENZA") -> {
                metricsList.add(AcReasonMetric("SOC Batteria", "${elec.powerwallSocPercent.toInt()}%"))
                metricsList.add(AcReasonMetric("SOC Minimo", "${logica.socMinimoApplied.toInt()}%"))
                metricsList.add(AcReasonMetric("Potenza Batteria", "${elec.batteryPowerW.toInt()} W"))
            }

            !isTempComfortReason && !isDayHumidexReason && !isBatteryBlockReason && !isAntiCycleReason && !isComfortWaitingTimerReason && !isBatteryWaitingTimerReason && !isNightPauseReason && !isNightComfortReached && clean != "PRONTO_ACCENSIONE_NIGHT_DRY" && !isCoolingStandbyMaintenance && !isCoolingMaintenance && !isStandbyComfortReached && !isMaintenanceNightDry && (clean.contains("HUMIDEX") || clean.contains("COOLING") || clean.contains("NIGHT_DRY") || clean.contains("COMFORT") || clean.contains("PRONTO")) -> {
                if (isNightReason) {
                    metricsList.add(AcReasonMetric("Humidex camera", "%.1f".format(locale, env.humidexBedroom)))
                    metricsList.add(AcReasonMetric("Humidex impostato", "%.1f".format(locale, logica.sogliaAttivazioneApplicata)))
                    metricsList.add(AcReasonMetric("SOC Batteria", "${elec.powerwallSocPercent.toInt()}%"))
                } else {
                    metricsList.add(AcReasonMetric("Humidex zona giorno", "%.1f".format(locale, env.humidexLiving)))
                    metricsList.add(AcReasonMetric("Humidex di attivazione", "%.1f".format(locale, logica.sogliaAttivazioneApplicata)))
                    metricsList.add(AcReasonMetric("Surplus Solare", "${elec.surplusW.toInt()} W"))
                }
            }

            else -> {
                metricsList.add(AcReasonMetric("Surplus Solare", "${elec.surplusW.toInt()} W"))
                metricsList.add(AcReasonMetric("SOC Batteria", "${elec.powerwallSocPercent.toInt()}%"))
            }
        }

        return baseInfo.copy(soglieCostanti = soglieStr, metrics = metricsList)
    }
}
