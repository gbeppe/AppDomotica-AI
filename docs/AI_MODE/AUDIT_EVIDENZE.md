# Audit delle evidenze

Data: 11 settembre 2026. Analisi di file forniti e interrogazioni EmonCMS in sola lettura. Nessun test con attuazione fisica, deploy o modifica degli archivi.

## Sorgenti esaminate

File originali in `/home/giuseppe/Downloads` (non copiati nel repository):

- `flows - 2026-09-11T113811.470.json`: singolo Digital Twin Router.
- `flows - 2026-09-11T114204.037.json`: 650 nodi; associato a .15 dal contesto fornito e dalla struttura.
- `flows - 2026-09-11T114137.082.json`: 1.316 nodi; associato a .20.
- `clima_controllo.log`, `apprendimento_storico.log`, `predictive_reserve_shadow.log`, `zara_previsione_kwh_openmeteo.log`.

Android: `MainDashboard.kt`, `MqttManager.kt`, `AiManagedData.kt`, `AcReasonMapper.kt`, `EnergyRepository.kt`, `EnergyDetailScreen.kt`, navigazione e contratto MQTT.

## Inventario EmonCMS

L'endpoint locale `/feed/list.json?meta=1` ha restituito 256 feed. L'autenticazione non è riportata qui. Il catalogo include ultimo valore e metadati; i campioni sono stati richiesti separatamente tramite `/feed/data.json`.

| Feed | Significato | Intervallo restituito nei metadati |
|---|---|---|
| 185 | VMC speed, unità dichiarata `%` | 60 s |
| 198 | ACstatus | 10 s |
| 303–304 | Potenza casa e SOC Powerwall | 30 s |
| 353 | Potenza AC | Circa 29,7 s; non assumere passo fisso nativo |
| 362 | Energia AC in kWh | 30 s |
| 412 | Humidex Living | 10 s |

Esistono anche feed per caldaia, pompa pavimento, termocaminetto, solare termico, tariffe e altri impianti. Copertura decennale e continuità non sono verificate per ciascun feed. Le codifiche storiche VMC e AC e i passaggi fra sensori restano da validare.

## Log disponibili

| File | Record JSON | Estremi osservati, Europe/Rome |
|---|---:|---|
| clima_controllo.log | 4.640 | 18 luglio 16:47 – 10 settembre 04:02, 2026 |
| apprendimento_storico.log | 1.996 | 17 giugno 03:01 – 11 settembre 11:37, 2026 |
| predictive_reserve_shadow.log | 100 | 5 settembre 23:43 – 6 settembre 01:20, 2026 |
| zara_previsione_kwh_openmeteo.log | 117 | Date previste 18 giugno – 10 settembre 2026 |

Il log climatico include una riga di reset manuale e 11 eventi senza `motivo_ac`. La prima motivazione AC compare il 18 luglio alle 23:44. Gli estremi non attestano continuità.

Tutti i 100 record Predictive Reserve forniti dichiarano `SHADOW` e `NONE_SHADOW_ONLY`: non attribuire loro autorità sugli attuatori in quel periodo. Il log previsioni non contiene un timestamp di elaborazione; più revisioni della stessa data non sono temporalmente attribuibili con certezza.

## Percorsi e logica

- Funzione climatica collegata: `61992c8879c11f66`, 1.164 righe nella proprietà `func`. Tre omonime sono scollegate; non costituiscono una cronologia provata.
- Lo stato composito viene pubblicato sul broker .20 come `casa/clima/stato_completo`; il Router espone i topic pubblici Android.
- Il logger eventi va al file `/home/pi/AI_climate/clima_controllo.log` e a un'uscita MQTT configurata su .15 con topic `zara/domotics/clima_log_eventi`.
- La telemetria va a MariaDB .20, database `domotica`, tabella `telemetria_clima`, attraverso un limitatore di un messaggio ogni 10 minuti con scarto degli altri.
- Sono presenti query verso `storico_clima`. Non è stata interrogata MariaDB né verificata l'effettiva popolazione delle tabelle.
- Predictive Reserve può partecipare al nuovo avvio notturno mediante richiesta, risposta e nodo separato `AI Climate NIGHT_DRY Commit`. I commenti di sviluppo non bastano a stabilire lo stato operativo: nell'esportazione l'uscita MQTT comandi non risulta disabilitata.

Condizioni lette nella funzione attuale: free cooling VMC con temperatura esterna oltre 1,5 °C inferiore a quella interna; limite notturno VMC; antirimbalzo di cinque minuti; timer AC; tolleranza del deficit; protezione serale oltre 2.500 W di scarica. Sono regole del codice esportato, non prova della versione attiva in tutte le date storiche.

## Limiti della spiegabilità attuale

1. Codici finali come `ESECUZIONE_SPEGNIMENTO` possono sostituire cause più specifiche.
2. Il cambio del solo motivo VMC non è tra le condizioni esplicite che fanno scattare il logger.
3. Il commit notturno PR è separato dal log della richiesta pendente.
4. Mancano versione di strategia/configurazione e identificativo comune di esecuzione nei log climatici esaminati.
5. SOC filtrato e fallback possono differire dai campioni hardware; servono i valori realmente usati.
6. La fascia notturna del controllo è 22–08; quella del calcolo delle medie è 22–05. Non uniformarle nella ricostruzione.
7. Il parser storico Android converte alcuni valori non numerici in zero: non riutilizzare questo comportamento per i calcoli analitici.

## Caso verificato: 6 settembre 2026

Interrogate le serie 353, 304, 306 e 303 per la giornata, con intervallo richiesto di 30 secondi: 2.880 campioni restituiti per feed. Potenza AC: 2.874 valori numerici finiti, sei mancanti esclusi dai riepiloghi. Gli altri tre feed: 2.880 valori numerici finiti.

Filtro esplorativo SOC: valore fuori 0–100 oppure salto superiore a tre punti rispetto a entrambi i vicini con differenza fra i vicini inferiore a un punto. Nessun outlier individuato con questo criterio. Non è una certificazione di assenza di ogni possibile glitch né una soglia definitiva per la produzione.

### Pausa notturna

- 00:20–00:26: AC circa 443–453 W.
- 00:26:31: comando OFF del watchdog; la serie AC scende fino a zero nel campione etichettato 00:27. La risoluzione di interrogazione non permette di attribuire precisione al secondo all'effetto fisico.
- 00:53:14: il record coerente (riga 4261 del log fornito) riporta `COMFORT_NOTTURNO_RAGGIUNTO`, Humidex camera 27,4, SOC 52,74%, soglia SOC 35%.
- Il record precedente presenta previsione 12 kWh/data N/A e temperatura camera zero: escluso dalla spiegazione ordinaria come transitorio, secondo l'indicazione dell'utente. L'origine da riavvio non è provata.
- 00:50–01:00: tutti i 20 campioni AC interrogati sono zero.
- 02:06:19: avvio NIGHT_DRY nel log; 02:10–02:15: AC circa 433–457 W.

Conclusione: mantenimento della pausa alle 00:53 documentato come comfort raggiunto e fisicamente coerente. Il primo OFF delle 00:23 presenta contesto transitorio: non attribuirgli una causa ordinaria certa.

### Spegnimento serale

- Riga 4293: alle 19:43:54, `PEAK_SHAVING_ATTIVO (Scarica: 3190W)`, comando OFF, SOC circa 84,8%.
- Prima: AC circa 490 W e consumo casa circa 947 W.
- Nei campioni attorno all'evento: consumo casa circa 3.137 W; batteria 3.190 W.
- Dopo: AC circa 38 W, poi zero; scarica batteria ancora circa 2.600 W nei campioni successivi.

Conclusione: decisione di limitazione della potenza di scarica documentata, con calo AC confermato. Il carico elevato persiste ed è confermato da più misure: non viene escluso come glitch isolato. Nessuna identificazione dell'altro carico domestico è stata eseguita.

## Da verificare

- Copertura e mapping temporale completo dei feed.
- Codifica delle velocità VMC archiviate e riscontro dei comandi.
- Disponibilità delle tabelle MariaDB e dei log PR di esecuzione attiva, se necessari.
- Valori correnti dei parametri e date effettive delle versioni.
- Preferenze di riepilogo, costo stimato e futura esecuzione delle regole.
