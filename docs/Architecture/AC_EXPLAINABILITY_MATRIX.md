# DomoticsAI --- AC Explainability Matrix

**Destinazione repository:**
`docs/Architecture/AC_EXPLAINABILITY_MATRIX.md`\
**Versione:** 1.2\
**Data:** 2026-09-22\
**Baseline:** AC Explainability WP1 → HUMIDEX B3.3C/D\
**Stato:** baseline documentale consolidata e allineata alle specifiche Humidex B2/B3 finali

## 1. Scopo

Questo documento definisce il contratto semantico tra **Climate
Controller Node-RED → Digital Twin Router → MQTT → Android** per la
spiegazione dello stato del climatizzatore.

Principio vincolante: **il backend è l'autorità decisionale; Android è
consumer della decisione**. L'app presenta `motivo_logica`, diagnostica
e metriche pubblicate, senza ricostruire l'algoritmo Node-RED.

La UI deve rispondere a tre domande:

1.  **Perché siamo qui?**
2.  **Quali parametri spiegano lo stato corrente?**
3.  **Cosa deve cambiare perché lo stato possa evolvere?**

------------------------------------------------------------------------

# 2. Matrice `motivo_logica / stato → spiegazione Android`

  -----------------------------------------------------------------------------------------------------------------------------------------------
  `motivo_logica` / stato                   Perché siamo qui   Parametri da     Cosa deve cambiare         **Disponibilità attuale**
                                                               mostrare
  ----------------------------------------- ------------------ ---------------- -------------------------- --------------------------------------
  **`BLOCCO_TIMER_ANTICICLO_OFF`**          AC appena spento:  tempo OFF        timer → 0                  **✅ COMPLETO** ---
                                            riaccensione       residuo, durata                             `timer_residuo_minuti`,
                                            protetta           `MIN_OFF`, stato                            `timer_durata_minuti`,
                                                               OFF                                         `timer_tipo=MIN_OFF` disponibili E2E

  **`BLOCCO_DIURNO_TEMPERATURA_BASSA`**     Temperatura        temperatura,     temperatura ≥ soglia avvio **✅ DATI DISPONIBILI** ---
                                            insufficiente per  soglia avvio                                `soglia_temperatura_avvio_cooling_c`
                                            avviare cooling    cooling, Humidex                            ora esposta

  **`ATTESA_SOGLIA_HUMIDEX`**               Non esiste ancora  Humidex, soglia  Humidex ≥ soglia con       **✅ COMPLETO lato dati** --- Humidex,
                                            richiesta di       attivazione,     temperatura sufficiente    `soglia_attivazione_applicata` e
                                            raffrescamento     temperatura,                                soglia temperatura disponibili
                                                               soglia
                                                               temperatura
                                                               avvio

  **`BLOCCO_GRAZIA_VINCOLO_SOLARE`**        Grace mode         surplus,         surplus sufficiente oppure **⚠️ PARZIALE** --- surplus
                                            richiede           stato/modalità   uscita dal vincolo         disponibile; soglia decisionale e
                                            disponibilità      vincolo solare,                             stato completo grace/solo-solare non
                                            solare             Humidex                                     integralmente esposti. Android non
                                                                                                           deve hardcodare 1000 W

  **`BLOCCO_DIURNO_BATTERIA`**              Batteria sotto la  SOC, SOC minimo  SOC recupera oppure        **⚠️ QUASI COMPLETO** --- SOC,
                                            soglia dinamica    applicato,       interviene                 `soc_minimo_applied`, Humidex e
                                                               Humidex,         override/emergenza         previsione disponibili; soglia Humidex
                                                               condizione                                  emergenza non esplicitamente
                                                               emergenza,                                  pubblicata
                                                               previsione
                                                               ricarica

  **`ATTESA_SURPLUS_SOLARE`**               Richiesta comfort  surplus reale,   surplus virtuale           **⚠️ PARZIALE** --- surplus reale e
                                            presente ma        surplus          sufficiente oppure         SOC disponibili; surplus virtuale e
                                            energia            virtuale, soglia override                   relativa soglia non pubblicati.
                                            insufficiente      richiesta, SOC                              Android non duplica 150 W

  **`PRONTO_ACCENSIONE_COOLING`**           Tutti i gate di    temperatura,     transizione → `COOLING_ON` **✅ QUASI COMPLETO / sufficiente alla
                                            avvio sono         soglia                                      spiegazione** --- principali evidenze
                                            soddisfatti        temperatura,                                pubblicate; Android non ricostruisce
                                                               Humidex, soglia,                            tutti i gate
                                                               SOC, minimo SOC,
                                                               surplus

  **`PRONTO_ACCENSIONE_OVERRIDE_SOLARE`**   Solare forte       SOC, minimo SOC, transizione → `COOLING_ON` **⚠️ PARZIALE** --- SOC/minimo,
                                            permette avvio     surplus,                                    surplus reale e previsione
                                            nonostante SOC     eventuale                                   disponibili; non esposti surplus
                                                               surplus                                     virtuale/soglie interne override
                                                               virtuale,
                                                               previsione
                                                               ricarica

  **`COMFORT_TEMP_BASSA_ATTESA_TIMER`**     Temperatura sotto  temperatura,     timer → 0 mantenendo le    **✅ COMPLETO lato dati** ---
                                            soglia comfort e   soglia OFF       condizioni                 `soglia_temperatura_off_comfort_c` e
                                            Humidex            comfort,                                    diagnostica `MIN_RUN` disponibili
                                            compatibile, ma    Humidex, soglia
                                            MIN_RUN non        attivazione,
                                            terminato          timer ON
                                                               residuo/durata

  **`SPEGNIMENTO_COMFORT_TEMP_BASSA`**      Condizioni per OFF temperatura,     → OFF                      **✅ COMPLETO lato dati** --- soglia
                                            completo raggiunte soglia OFF                                  OFF comfort pubblicata
                                                               comfort,
                                                               Humidex, soglia
                                                               attivazione

  **`COMFORT_RAGGIUNTO_ATTESA_TIMER`**      Humidex ha         Humidex, soglia  timer → 0 → standby        **✅ COMPLETO** --- soglie
                                            raggiunto comfort  spegnimento,                                comfort/isteresi + diagnostica timer
                                            ma MIN_RUN         isteresi, timer                             disponibili
                                            protegge il        ON
                                            compressore        residuo/durata

  **`STANDBY_COMFORT_RAGGIUNTO`**           Humidex ≤ soglia   Humidex, soglia  → `STANDBY_INVERTER`       **✅ COMPLETO**
                                            spegnimento        spegnimento,
                                                               isteresi

  **`DEFICIT_RILEVATO_AVVIO_TIMER` /        Cooling mantenuto  surplus, durata  recupero surplus oppure    **✅ COMPLETO per la diagnostica
  `TOLLERANZA_DEFICIT_IN_CORSO`**           durante deficit    tolleranza,      residuo → 0                prevista** ---
                                            temporaneo         tempo deficit                               `deficit_tolleranza_minuti` +
                                                               residuo, SOC                                `deficit_residuo_minuti` disponibili
                                                                                                           E2E

  **`BLOCCO_NOTTE_BATTERIA_BASSA`**         Batteria           SOC, SOC minimo  SOC > minimo               **✅ COMPLETO lato evidenze** ---
  insufficiente per notte, Humidex camera, soglia attivazione                                        mapper NIGHT usa Bedroom

  **`COMFORT_NOTTURNO_RAGGIUNTO`**          Humidex camera non Humidex camera,  backend rileva nuovamente  **✅ COMPLETO PER ANDROID** --- B3.2:
                                            richiede NIGHT_DRY Margine NIGHT_DRY, le condizioni di avvio     `Humidex camera`, `Margine NIGHT_DRY`,
                                                               Soglia NIGHT_DRY                            `Soglia NIGHT_DRY`; nessun calcolo
                                                                                                           aritmetico locale

  **`BLOCCO_NOTTE_PAUSA_45M`**              Precedente ciclo   timer pausa      timer pausa → 0            **✅ COMPLETO** ---
                                            NIGHT_DRY          residuo, Humidex                            `pausa_notturna_residuo_minuti`
                                            terminato troppo   camera, SOC                                 disponibile E2E; testato anche con 23
                                            recentemente                                                   min

  **`PRONTO_ACCENSIONE_NIGHT_DRY`**         Gate notte         Humidex camera,  backend autorizza/avvia    **✅ COMPLETO PER LA PRESENTATION**
                                            soddisfatti        Margine NIGHT_DRY, NIGHT_DRY                --- soglia effettiva e margine
                                                               Soglia NIGHT_DRY,                           restituiti direttamente dal
                                                               SOC                                         backend

  **`PR_PRESTART_PENDING`**                 Condizioni         stato PR/gate,   PR autorizza o nega la     **⚠️ REASON DISPONIBILE; dettaglio PR
                                            NIGHT_DRY          reason, evidenze richiesta                  separato** --- Android spiega l'attesa
                                            raggiunte ma PR    NIGHT_DRY                                   ma non ricostruisce l'arbitraggio
                                            deve arbitrare
                                            l'autorità

  **`BLOCCO_PR_REQUEST_INVALID`**           Richiesta PR non   reason PR /      nuova richiesta valida     **⚠️ REASON DISPONIBILE** ---
                                            valida             diagnostica                                 dettaglio completo errore PR fuori
                                                               disponibile                                 dalla diagnostica AC

  **`BLOCCO_PR_GATE_INVALID`**              Gate PR non        stato/reason PR  gate torna valido          **⚠️ REASON DISPONIBILE** ---
                                            valido/mancante                                                fail-closed backend; Android non
                                                                                                           ricostruisce il gate

  **`ESECUZIONE_ACCENSIONE_DIURNA`**        Avvio cooling      temperatura,     → `COOLING_ON`             **✅ COMPLETO PER LA PRESENTATION**
                                            effettivamente     Humidex, soglia,
                                            comandato          SOC, surplus

  **`ESECUZIONE_ACCENSIONE_NOTTURNA`**      Avvio NIGHT_DRY    Humidex camera,  → `NIGHT_DRY`              **✅ COMPLETO PER LA PRESENTATION**
                                            effettivamente     Margine NIGHT_DRY,                          --- evidenze autoritative fornite
                                            comandato          Soglia NIGHT_DRY,                           dal backend
                                                               SOC

  **`MANTENIMENTO_COOLING_ON`**             Cooling diurno     Humidex, soglia, una condizione backend     **✅ QUASI COMPLETO** --- principali
                                            regolarmente in    temperatura,     richiede standby/OFF       evidenze disponibili
                                            funzione           surplus, SOC

  **`MANTENIMENTO_NIGHT_DRY` / NIGHT_DRY    Deumidificazione   Humidex camera,  SOC insufficiente / fine   **✅ QUASI COMPLETO** --- evidenze
  attivo**                                  notturna in corso  SOC              fascia / altra decisione   principali disponibili; nessun
                                                                                backend → OFF              Humidex exit threshold locale

  **`MANTENIMENTO_STANDBY_INVERTER`**       Inverter in        Humidex, soglia, Humidex richiede ripresa   **✅ QUASI COMPLETO**
                                            standby con        stato,           oppure backend decide OFF
                                            supervisione       condizioni
                                            attiva             energetiche

  **`MANTENIMENTO_*`**                      Regolare           evidenze         decisione backend          **✅ FALLBACK DI FAMIGLIA** --- prefix
                                            prosecuzione di    disponibili per  successiva                 gestito dal mapper
                                            uno stato attivo   lo stato
                                            non specializzato

  **`BATTERIA_SCARICA_ATTESA_TIMER`**       Durante NIGHT_DRY  SOC, minimo SOC, timer → 0 → OFF            **✅ COMPLETO** --- metriche NIGHT +
                                            la batteria è      timer MIN_RUN                               `timer_residuo_minuti`
                                            scesa, ma MIN_RUN  residuo
                                            impedisce OFF
                                            immediato

  **`RIPRESA_COOLING_DA_STANDBY`**          Humidex torna a    Humidex, soglia  → `COOLING_ON`             **✅ COMPLETO lato evidenze**
                                            richiedere         attivazione
                                            raffrescamento

  **`ESECUZIONE_SPEGNIMENTO`**              Una richiesta di   stato OFF, causa transizione già avvenuta   **🟡 IMPLEMENTATO E2E, verifica
                                            OFF ha superato    della                                       runtime causa non-null pendente** ---
                                            gate/timer         transizione                                 `causa_transizione` esiste e clear
                                                                                                           retained verificato

  **`ESECUZIONE_STANDBY_INVERTER`**         Una richiesta      stato standby,   transizione già avvenuta   **🟡 IMPLEMENTATO E2E, verifica
                                            standby ha         causa della                                 runtime causa non-null pendente**
                                            superato MIN_RUN   transizione

  **`LOGICA_INVERNALE_ATTIVA` /             Inverno, richiesta temperatura,     condizioni di              **⚠️ PARZIALE / NON COPERTO dalla
  HEAT_DIURNO**                             riscaldamento      soglie inverno,  mantenimento/spegnimento   diagnostica AC estiva** --- soglie
                                            diurno secondo     surplus, consumo inverno                    specifiche 19/19.5 e gate energetici
                                            temperatura ed     storico                                     inverno non esplicitamente esposti
                                            energia                                                        dalla nuova diagnostica

  **`LOGICA_INVERNALE_ATTIVA` /             Riscaldamento di   temperatura,     condizioni di uscita dalla **⚠️ PARZIALE / NON COPERTO** ---
  HEAT_SICUREZZA_NOTTE**                    sicurezza notturno soglie inverno,  sicurezza                  SOC/minimo disponibili; soglie 17/17.5
                                                               SOC, minimo SOC                             non fanno parte dell'attuale contratto
                                                                                                           AC Explainability

  **codice sconosciuto**                    Reason backend     codice           aggiornamento mapper solo  **✅ FALLBACK SICURO** --- categoria
                                            nuovo/non ancora   originale +      se serve una semantica     `UNKNOWN`, nessun crash
                                            mappato            metriche         dedicata
                                                               generiche
                                                               disponibili

  **reason vuoto**                          Nessun motivo      nessuna evidenza arrivo di un reason valido **✅ FALLBACK SICURO** ---
                                            valido ricevuto    inventata                                   `Stato Non Disponibile`, `code=N/D`
  -----------------------------------------------------------------------------------------------------------------------------------------------

------------------------------------------------------------------------

# 3. Matrice Diagnostica AC

Percorso JSON: `logica_controllo.diagnostica_ac`.

  ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  Campo JSON                        Topic MQTT Digital Twin                                                               Tipo        Significato       Semantica `null` Uso Android                      **Disponibilità
                                                                                                                          Android                       / `0`                                             attuale**
  --------------------------------- ------------------------------------------------------------------------------------- ----------- ----------------- ---------------- -------------------------------- --------------------
  `timer_tipo`                      `zara/interface/logica_controllo/diagnostica_ac/timer_tipo/stat`                      `String?`   Tipo timer        `null` = non     `MIN_OFF` con AC OFF, `MIN_RUN`  **✅ DISPONIBILE
                                                                                                                                      associato allo    ricevuto;        negli altri stati; da solo non   E2E** --- prodotto
                                                                                                                                      stato AC corrente stringa = valore indica blocco                    dal Controller,
                                                                                                                                                        backend                                           esposto dal Digital
                                                                                                                                                                                                          Twin, ricevuto da
                                                                                                                                                                                                          Android; verificato
                                                                                                                                                                                                          nel payload reale
                                                                                                                                                                                                          con `MIN_OFF`

  `timer_residuo_minuti`            `zara/interface/logica_controllo/diagnostica_ac/timer_residuo_minuti/stat`            `Int?`      Minuti residui    `null` = non     Alimenta `remainingTimerMinutes` **✅ DISPONIBILE
                                                                                                                                      del timer         ricevuto; `0` =  per i reason di attesa           E2E** --- verificato
                                                                                                                                      MIN_OFF/MIN_RUN   scaduto/non                                       runtime con `0`; UI
                                                                                                                                                        bloccante; `>0`                                   non genera falso
                                                                                                                                                        = residuo reale                                   timer

  `timer_durata_minuti`             `zara/interface/logica_controllo/diagnostica_ac/timer_durata_minuti/stat`             `Int?`      Durata            `null` = non     Dettaglio tecnico                **✅ DISPONIBILE
                                                                                                                                      configurata timer ricevuto; valore MIN_OFF/MIN_RUN                  E2E** --- verificato
                                                                                                                                      corrente          = durata backend                                  nel payload reale
                                                                                                                                                                                                          con `20` min

  `deficit_tolleranza_minuti`       `zara/interface/logica_controllo/diagnostica_ac/deficit_tolleranza_minuti/stat`       `Int?`      Durata            `null` = non     Contestualizza                   **✅ DISPONIBILE
                                                                                                                                      configurata       ricevuto         `DEFICIT_RILEVATO_AVVIO_TIMER` / E2E** --- verificato
                                                                                                                                      tolleranza                         `TOLLERANZA_DEFICIT_IN_CORSO`    nel payload reale
                                                                                                                                      deficit                                                             con `30` min

  `deficit_residuo_minuti`          `zara/interface/logica_controllo/diagnostica_ac/deficit_residuo_minuti/stat`          `Int?`      Minuti mancanti   `null` = non     Per                              **✅ DISPONIBILE
                                                                                                                                      alla fine della   ricevuto; `0` =  `TOLLERANZA_DEFICIT_IN_CORSO`    E2E** --- aggiunto a
                                                                                                                                      tolleranza        nessun residuo;  alimenta `remainingTimerMinutes` Controller/Router;
                                                                                                                                      deficit           `>0` =                                            runtime `0`; test
                                                                                                                                                        tolleranza                                        Android anche con
                                                                                                                                                        attiva                                            `17`

  `pausa_notturna_residuo_minuti`   `zara/interface/logica_controllo/diagnostica_ac/pausa_notturna_residuo_minuti/stat`   `Int?`      Minuti residui    `null` = non     `BLOCCO_NOTTE_PAUSA_45M` →       **✅ DISPONIBILE
                                                                                                                                      pausa NIGHT_DRY   ricevuto; `0` =  `remainingTimerMinutes`          E2E** --- aggiunto a
                                                                                                                                                        nessuna pausa;                                    Controller/Router;
                                                                                                                                                        `>0` = pausa                                      runtime `0`; test
                                                                                                                                                        attiva                                            Android anche con
                                                                                                                                                                                                          `23`

  `causa_transizione`               `zara/interface/logica_controllo/diagnostica_ac/causa_transizione/stat`               `String?`   Reason che ha     `null`/payload   Spiega soprattutto               **🟡 DISPONIBILE E2E
                                                                                                                                      causato la        vuoto = nessuna  `ESECUZIONE_SPEGNIMENTO` e       / VALIDAZIONE
                                                                                                                                      transizione       causa corrente;  `ESECUZIONE_STANDBY_INVERTER`;   TRANSIZIONE REALE
                                                                                                                                      effettiva AC      stringa = causa  mapper stateless                 PENDENTE** ---
                                                                                                                                                        corrente                                          produzione, routing,
                                                                                                                                                                                                          retained-clear e
                                                                                                                                                                                                          parsing verificati;
                                                                                                                                                                                                          resta da osservare
                                                                                                                                                                                                          una transizione
                                                                                                                                                                                                          reale con causa
                                                                                                                                                                                                          non-null
  ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

## 3.1 Soglie correlate pubblicate fuori da `diagnostica_ac`

  ------------------------------------------------------------------------------------------------------------------------------------------
  Campo JSON                             Topic MQTT                                                                  Disponibilità / uso
  -------------------------------------- --------------------------------------------------------------------------- -----------------------
  `soglia_attivazione_applicata`         `zara/interface/logica_controllo/soglia_attivazione_applicata/stat`         **✅ E2E** --- Humidex
                                                                                                                     di attivazione
                                                                                                                     effettivo applicato
                                                                                                                     dal Controller

  `soglia_spegnimento_comfort`           `zara/interface/logica_controllo/soglia_spegnimento_comfort/stat`           **✅ E2E** --- Humidex
                                                                                                                     comfort calcolato dal
                                                                                                                     Controller

  `ampiezza_isteresi_humidex`            `zara/interface/logica_controllo/ampiezza_isteresi_humidex/stat`            **✅ E2E** ---
                                                                                                                     Isteresi comfort
                                                                                                                     Humidex (parametro
                                                                                                                     configurabile RW)

  `night_dry_humidex_margin`             `zara/interface/logica_controllo/night_dry_humidex_margin/stat`             **✅ E2E** --- Margine
                                                                                                                     di attivazione
                                                                                                                     NIGHT_DRY (parametro
                                                                                                                     configurabile RW)

  `night_dry_humidex_threshold`          `zara/interface/logica_controllo/night_dry_humidex_threshold/stat`          **✅ E2E** --- Soglia
                                                                                                                     NIGHT_DRY calcolata
                                                                                                                     dal Controller

  `soglia_temperatura_avvio_cooling_c`   `zara/interface/logica_controllo/soglia_temperatura_avvio_cooling_c/stat`   **✅ DISPONIBILE** ---
                                                                                                                     soglia avvio cooling
                                                                                                                     pubblicata dal backend

  `soglia_temperatura_off_comfort_c`     `zara/interface/logica_controllo/soglia_temperatura_off_comfort_c/stat`     **✅ DISPONIBILE** ---
                                                                                                                     soglia OFF comfort
                                                                                                                     pubblicata dal backend

  `soc_minimo_applied`                   `zara/interface/logica_controllo/soc_minimo_applied/stat`                   **✅ E2E** --- evidenza
                                                                                                                     reason batteria

  `blocco_emergenza_attivo`              `zara/interface/logica_controllo/blocco_emergenza_attivo/stat`              **✅ E2E** --- stato
                                                                                                                     sicurezza/energia
                                                                                                                     backend
  ------------------------------------------------------------------------------------------------------------------------------------------

------------------------------------------------------------------------

# 4. Regole semantiche vincolanti

1.  **Backend autorevole.** Android non decide accensione, spegnimento,
    standby, NIGHT_DRY o autorità Predictive Reserve.
2.  **`null != 0`.** `null` = dato non ricevuto/sconosciuto; `0` =
    valore realmente pubblicato.
3.  **Timer solo se pertinente.** `timer_tipo` non implica un blocco. La
    StatusCard mostra il residuo quando `remainingTimerMinutes > 0` ed è
    coerente con il reason.
4.  **MIN_OFF / MIN_RUN.** AC OFF → `MIN_OFF`; AC non OFF → `MIN_RUN`.
    La semantica operativa deriva comunque da reason + residuo.
5.  **`causa_transizione` è transitoria.** Il retained viene cancellato
    con payload vuoto; `MqttManager` converte subito il vuoto in `null`.
    `AcReasonMapper` è stateless.
6.  **Humidex notturno = Bedroom.** I reason NIGHT_DRY usano
    `humidexBedroom`, non `humidexLiving`.
7.  **Android performs no calculation.** Android non esegue calcoli di isteresi, margini o soglie finali (es. no `+0.5`, no `-2.5`, no `activation - hysteresis`). Il Controller pubblica `night_dry_humidex_margin`, `night_dry_humidex_threshold` e `ampiezza_isteresi_humidex`. Android si limita a visualizzare tali valori.
8.  **Semantica B3.2 NIGHT_DRY.** Per `COMFORT_NOTTURNO_RAGGIUNTO`, le metriche esposte sono `Humidex camera`, `Margine NIGHT_DRY`, `Soglia NIGHT_DRY`.
9.  **Niente soglie decisionali duplicate.** Se una soglia non arriva
    dal Digital Twin, Android usa una descrizione semantica e non una
    costante copiata da Node-RED.
10. **Fallback forward-compatible.** Reason nuovi/non mappati non
    causano crash e restano visibili come `UNKNOWN`.

------------------------------------------------------------------------

# 5. Contratto UI

La StatusCard usa `AcReasonInfo` come sorgente presentation:

-   `userTitle`: spiegazione principale;
-   `userExplanation`: dettaglio leggibile;
-   `quandoCompare`: contesto;
-   `effetto`: conseguenza operativa;
-   `remainingTimerMinutes`: mostrato solo se positivo;
-   `metrics`: evidenze rilevanti;
-   `code`: informazione tecnica secondaria/debug.

Il Composable non deve introdurre condizioni sui singoli reason per
ricostruire la logica backend. `SystemDetailsCard` può mostrare
diagnostica tecnica senza trasformarla in decisione.

------------------------------------------------------------------------

# 6. Baseline di verifica consolidata (WP3.3A-J & Humidex B2/B3)

Casi verificati:
- WP3.3A-J PASS
- HUMIDEX B2 CLOSED PASS
- HUMIDEX B3 CLOSED PASS
- Android unit tests: 91 PASS / 0 FAIL / 0 SKIP
- `assembleDebug` SUCCESS

Runtime Humidex E2E verificato:
- `2.5 / 0.5` → `2.6 / 0.6` → `2.5 / 0.5` PASS.
- Con soglia di attivazione applicata = `33`:
  - Isteresi `2.6` → Humidex comfort restituito dal backend = `30.4`
  - Margine NIGHT_DRY `0.6` → Soglia NIGHT_DRY restituita dal backend = `33.6`
- Ripristino default:
  - Isteresi `2.5` → Humidex comfort = `30.5`
  - Margine NIGHT_DRY `0.5` → Soglia NIGHT_DRY = `33.5`

Le semantiche di presentazione NIGHT_DRY introdotte in B2/B3 hanno superato e sostituito la vecchia rappresentazione basata su soglie di base o calcoli lato client.

------------------------------------------------------------------------

# 7. Limiti e follow-up separati

-   `causa_transizione`: infrastruttura E2E implementata; manca solo
    l'osservazione runtime di una transizione reale con causa non-null.
-   Surplus virtuale e alcune soglie interne di grace/override non sono
    pubblicati: Android non deve duplicarli.
-   La soglia NIGHT_DRY finale **è calcolata dal Controller**, pubblicata attraverso il Digital Twin e visualizzata da Android.
-   Il ramo legacy `+1.0 / SOC > 75` resta presente nello snapshot del Controller ma non è esposto né configurabile da Android, in quanto l'audit causale ha stabilito che non governa l'effettiva esecuzione di `NIGHT_DRY`.
-   `timer_bloccante` non esiste nella baseline.
-   La logica invernale è solo parzialmente coperta dall'attuale
    contratto AC Explainability; soglie 17/17.5 e 19/19.5 non sono parte
    della nuova diagnostica.
-   L'anomalia della data della previsione solare appartiene a un
    workstream separato.

------------------------------------------------------------------------

# 8. Humidex B2/B3 Final Architecture & Contract

## Configurable Humidex Inputs
- **Humidex impostato**: Existing backend-owned setting (`ai/night_humidex_threshold/cmd` / `stat`). Distinguishes configured value from applied activation threshold.
- **Humidex emergenza**: Existing backend-owned emergency setting (`ai/morning_humidex_emergency/cmd` / `stat`).
- **Isteresi comfort Humidex**:
  - CMD: `zara/interface/ai/humidex_comfort_hysteresis/cmd`
  - Authoritative applied state: `zara/interface/logica_controllo/ampiezza_isteresi_humidex/stat`
  - Default: `2.5`
- **Margine di attivazione NIGHT_DRY**:
  - CMD: `zara/interface/ai/night_dry_humidex_margin/cmd`
  - Authoritative applied state: `zara/interface/logica_controllo/night_dry_humidex_margin/stat`
  - Default: `0.5`

## Backend-Calculated Values
- **Humidex di attivazione**: `zara/interface/logica_controllo/soglia_attivazione_applicata/stat`
- **Humidex comfort**: `zara/interface/logica_controllo/soglia_spegnimento_comfort/stat`
- **Soglia NIGHT_DRY**: `zara/interface/logica_controllo/night_dry_humidex_threshold/stat`

## Architectural Rule
**Controller calculates; Android displays.**
Android MUST NOT derive or calculate locally:
- `activation - hysteresis`
- `activation + NIGHT_DRY margin`
- `+0.5`
- `-2.5`
- `+1.0`
The UI displays backend-returned values directly.

## Important Mode Semantics
The applied activation threshold may differ from the configured "Humidex impostato" (e.g. during grace/emergency behavior). Do NOT document `Humidex comfort = target - 2.5` as a universal formula. The Controller alters effective activation/comfort semantics according to operating mode.

## NIGHT_DRY Semantics
- **COMFORT_NOTTURNO_RAGGIUNTO**: Displays `Humidex camera`, backend `Margine NIGHT_DRY`, backend final `Soglia NIGHT_DRY`.
- **PRONTO_ACCENSIONE_NIGHT_DRY**: Displays `Humidex camera`, backend `Margine NIGHT_DRY`, backend final `Soglia NIGHT_DRY`, and `SOC Batteria` where applicable.
- **MANTENIMENTO_NIGHT_DRY**: Displays `Humidex camera` and `SOC Batteria`. **NO** Humidex exit threshold is displayed because the Controller currently has no explicit Humidex maintenance/exit comparator for active NIGHT_DRY.

## Legacy +1.0 Branch
The Controller snapshot still contains `soc > 75 && humidex >= sogliaAttivazioneLogica + 1.0` inside the night assignment to `transizionePermessa`. The causal audit established that the actual `NIGHT_DRY` execution branch does not consume `transizionePermessa`. Therefore:
- `+1.0` is not exposed to Android
- It is not user-configurable
- Android does not display or derive it

------------------------------------------------------------------------

# 9. Node-RED Production Versioning Reference

Le versioni di produzione autorevoli delle Function-node Node-RED sono tracciate nel repository sotto:
`backend/nodered/production/`

File inclusi:
- [README.md](../../backend/nodered/production/README.md)
- [`climate-controller.function.json`](../../backend/nodered/production/climate/climate-controller.function.json) (Node ID: `61992c8879c11f66` — *Machine Learning domotico locale ad alta precisione*)
- [`digital-twin-router.function.json`](../../backend/nodered/production/climate/digital-twin-router.function.json) (Node ID: `5a7c1d360710ca21` — *Digital Twin Router*)
- [`climate-settings-consumer.function.json`](../../backend/nodered/production/climate/climate-settings-consumer.function.json) (Node ID: `18cd61655da131ad` — *Gestore Impostazioni UI & Android*)

**Nota di riferimento**:
- Snapshot date: `2026-09-22`
- Source: Production Node-RED instance `.20`
- Nature of exports: Selected Function-node exports (**NOT** a complete flow backup; surrounding MQTT nodes, broker config nodes and complete wiring are not necessarily included).
