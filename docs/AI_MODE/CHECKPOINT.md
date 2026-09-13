# Checkpoint — 12 settembre 2026

## Preparazione controlli Clima e Impianti — 13 settembre 2026

Catalogati e rappresentati in Smart tutti i 23 controlli già presenti nelle
schede dashboard Clima e Impianti: 9 e 14 rispettivamente. La nuova schermata
legge esclusivamente i topic `/stat`; non riceve `MqttManager`, non contiene
callback operative e lascia sconosciuti gli stati non ricevuti. Strategia,
copertura e requisiti per una futura attivazione in
[`PREPARAZIONE_CONTROLLI_SMART.md`](PREPARAZIONE_CONTROLLI_SMART.md).

## Primo incremento climatizzazione — 13 settembre 2026

Implementato il percorso read-only dal registry MQTT alla risposta deterministica
per stato, modalità, setpoint e motivo registrato del condizionatore. Il gateway
seleziona il tool chiuso `current_air_conditioner`; un successivo «perché?» usa
un contesto limitato a dominio e focus, senza cronologia libera. Dettagli,
contratti, replica e rollback in
[`SMART_CLIMATIZZAZIONE.md`](SMART_CLIMATIZZAZIONE.md).
La release `.20` `20260913-climate-reason`, revisione `ef1de8b`, ha superato 69
test su Python 3.9.2. Le due domande reali via TLS e i record MQTT osservati sono
in [`VERIFICA_CLIMATIZZAZIONE_2026-09-13.md`](VERIFICA_CLIMATIZZAZIONE_2026-09-13.md).

## Diagnostica stack Smart — 13 settembre 2026

Implementata l'icona di stato con scheda gerarchica e health autenticato per
percorso TLS, `.20`, Digital Twin, gateway, Groq, EmonCMS `.15` e log Node-RED.
Il controllo ogni 60 secondi e i log sanitizzati sono attivi solo mentre la
schermata Smart è `STARTED`. Architettura e replica in
[`DIAGNOSTICA_STACK_SMART.md`](DIAGNOSTICA_STACK_SMART.md).
La release backend `20260913-stack-health`, revisione `f2d50d8`, è attiva su
`.20`; verifica reale e rollback in
[`VERIFICA_DIAGNOSTICA_STACK_2026-09-13.md`](VERIFICA_DIAGNOSTICA_STACK_2026-09-13.md).

## Estremi energetici giornalieri — 13 settembre 2026

Il commit `16e47fa` aggiunge il tool deterministico `energy_daily_extreme` per
massimo/minimo giornaliero fino a 366 giorni. La release
`/opt/domopi-house-ai/releases/20260913-daily-extreme` è attiva su `.20`; 63 test
passano su Python 3.9.2. La domanda reale sui sei mesi è riuscita tramite TLS,
Groq ed EmonCMS: 165 giorni completi confrontati, 19 esclusi, massimo eleggibile
11 settembre 2026 con 3,75 kWh. Vedere
[`VERIFICA_ESTREMI_GIORNALIERI_2026-09-13.md`](VERIFICA_ESTREMI_GIORNALIERI_2026-09-13.md)
per limiti, replica e rollback.

Decisione successiva: EmonCMS resta utilizzabile anche nei giorni incompleti. La
risposta espone sia il massimo fra giorni al 100%, sia il massimo dell'energia
osservata includendo i parziali con la relativa copertura dati. Non chiama la
copertura “certezza” e segnala quando il massimo assoluto non è dimostrabile.
Questa modalità è attiva su `.20` nella release
`20260913-partial-extremes`, revisione `30ddc13`; la prova TLS reale è riuscita.

## Dove riprendere

Worktree: `/home/giuseppe/.cache/Google/AndroidStudio2026.1.4/aia/agents/DomoPiAndroidApp-ai-mode`
Branch: `feature/ai-home-assistant`; base precedente: `78a720f`.
L'utente ha autorizzato esplicitamente il commit di tutte le modifiche al progetto
e la prosecuzione il 12 settembre. Nessun push o deployment richiesto.

## Stato salvato

Schermata AI smart, stato MQTT con provenienza, consultazione deterministica
luci/living/ACS, dettatura e sintesi, test unitari e strumentali, documentazione.
Dettagli in [avanzamento](AVANZAMENTO_STATO_VOCE.md).
Le verifiche precedenti documentano 32 test Android e 12 backend passati;
non sono stati rieseguiti per il commit di checkpoint.

## Ripresa successiva al checkpoint `27c89c2`

Corretto il selettore del test «Chiedi», usando la semantica aggregata del
pulsante. Build `:app:assembleDebug :app:assembleDebugAndroidTest` riuscita.
Rieseguiti i quattro test UI su emulatore isolato: **4/4 passati**, 27,887 s.
Ispezionati cinque screenshot in tema chiaro/scuro: riepilogo, provenienza,
risposta composta, disconnessione e dati mancanti. Testi leggibili e controlli
coerenti nelle porzioni visualizzate; le schermate sono scorrevoli.
Evidenze salvate in [evidence/2026-09-12](evidence/2026-09-12/).
I valori sono fixture dei test, non letture domestiche correnti.

## Prossime attività

1. Verificare navigazione completa e voce su telefono; i test appena passati
   coprono contenuto isolato e callback simulati, non riconoscimento e
   riproduzione acustica reali né navigazione MainActivity con MQTT reale.
2. Restano da concordare backend/modello/accesso remoto e da risolvere
   freschezza sorgente, entità mancanti e mapping storico VMC/AC.

## Riproduzione locale e confini

Gli script locali `.validation/run-gradle.sh` e `.validation/run-ui.sh`
utilizzano bwrap con rete isolata, SDK `/home/giuseppe/Android/Sdk` e JDK 25
già presente. Sono ausili locali, con cache, APK e screenshot ignorati da Git;
restano sul disco e non vengono eliminati. Codice, test e stato di avanzamento
sono versionati. Nessuna credenziale o chiave locale va aggiunta al commit.

Preservare contratto `zara/interface`, Digital Twin Router e worktree principale.
La modalità AI resta di sola lettura; nessuna autorizzazione a deployment sui
Raspberry, comandi dispositivi o attivazione di regole.

## Requisito aggiunto: storico energia

La prosecuzione deve includere [domande complesse sui flussi energetici](DOMANDE_ENERGIA.md):
prelievo rete nel mese, media carica Tesla nella settimana e interrogazioni
composte. Prima dell'implementazione validare serie, unità/segni, periodi e
significato della percentuale richiesta. Requisito documentato, non implementato.

## Incremento storico energia implementato — 12 settembre 2026

Aggiunti `backend/house_ai/energy_history.py`, comando CLI `energy` e sette test.
Calcoli prelievo rete e SOC medio temporale, copertura esplicita, gestione
di null/invalidi/duplicati conflittuali, limiti e blocchi per richieste mensili.
Periodi Europe/Rome con fine esclusa e cambio d'ora; nomi distinti per periodi
di calendario e ultimi giorni completi. Unità e segno import devono essere
forniti esplicitamente sulla base di una verifica della sorgente.

Verifica locale iniziale: **19 test backend passati**, sintassi Python e diff Git validi.
Prova sintetica: 1 kW costante nel mese di agosto produce 744 kWh, copertura 100%.
Non è un risultato domestico reale.

Passo successivo completato: mapping e campioni reali verificati in sola lettura,
con esiti in [VALIDAZIONE_STORICO_ENERGIA.md](VALIDAZIONE_STORICO_ENERGIA.md).
Aggiunti catalogo sorgenti, interprete italiano e endpoint protetto. Totale:
26 test backend passati. Prossimo passo: collegare l'endpoint a UI e voce Android.
Nessun collegamento Android incluso in questo incremento.
Codice, documentazione e validazione sono salvati nel commit `d0bce5e`.

## Interprete dinamico energia

Implementata la base model-agnostic: pianificatore HTTP JSON, piano validato,
sette metriche energetiche e endpoint `POST /v1/assistant/query`. Le frasi non
sono mappate nel codice; i calcoli restano deterministici. 34 test backend
passati e tutte le metriche verificate in sola lettura sul 6 settembre. Dettagli
in [ARCHITETTURA_INTERPRETE_DINAMICO.md](ARCHITETTURA_INTERPRETE_DINAMICO.md).
Restano scelta/configurazione del modello, valutazione linguistica indipendente
e collegamento della UI/voce Android.

## Fase energia Digital Twin e Android

Aggiunto stato energetico corrente con provenienza, nuovo tool corrente nel
catalogo del pianificatore e invio dello snapshot al backend. La schermata AI
smart usa lo stesso testo digitato o trascritto per l'endpoint dinamico e mostra
una risposta utilizzabile dalla sintesi vocale. Dettagli e limiti in
[FASE_ENERGIA_DIGITAL_TWIN_ANDROID.md](FASE_ENERGIA_DIGITAL_TWIN_ANDROID.md).

## Persistenza dei lavori

Tutto ciò che serve a sviluppo e ripresa deve essere nel repository e
committato; `.validation/` e le altre cache devono restare sacrificabili. Il
repository Git comune è fuori dal worktree temporaneo, in
`/home/giuseppe/AndroidStudioProjects/DomoPiAndroidApp/.git`. Regole complete in
[POLITICA_FILE_E_RIPRESA.md](POLITICA_FILE_E_RIPRESA.md).

## Fonti Node-RED aggiuntive

Verificati i quattro log reali su `.20` in sola lettura; aggiunto tool backend
per lettura giornaliera con provenienza e limiti. Vedere
[FONTI_LOG_NODERED.md](FONTI_LOG_NODERED.md) per configurazione, campionamento,
copertura implementata e lavoro restante. Test backend: 41 superati.

## Completamento implementazione del dominio energia

Percorso Android/repository HTTP/evidenze completato per i cinque stati correnti,
sette metriche storiche, confronti omogenei e sintesi deterministiche dei quattro
log. Riepilogo dedicato energia, provenienza della risposta, gestione errori e
stessa risposta per testo/voce, inclusa suddivisione per sintesi lunga.
Vedere [SMART_ENERGIA.md](SMART_ENERGIA.md): è il punto di ripresa aggiornato.

Il pianificatore concreto non è configurato; all'utente è stata chiesta la scelta
locale/cloud/gateway esistente, senza richiedere segreti. Non dichiarare operativa
la comprensione linguistica reale, non confondere test con planner simulato e
validazione su modello. Nessun deployment o modifica del runtime domestico.

Validazione conclusiva di questo incremento: 51 test Python, 36 unitari Android,
9 test UI/repository e 1 navigazione MainActivity, tutti superati. Quattro
screenshot ispezionati e ricevute versionate in
[VERIFICA_SMART_ENERGIA.md](VERIFICA_SMART_ENERGIA.md). APK debug generato.

## Compatibilità Python 3.9 su `.20`

Corretto il solo tipo opzionale in `service.report`. Suite completa: 51 test
superati su Python 3.9.25 locale. Su `.20` (Python 3.9.2/aarch64), sorgenti
compilati e moduli importati in memoria, timezone Europe/Rome verificata.
Nessuna installazione o modifica del runtime domestico. Dettagli e limiti in
[SMART_ENERGIA.md](SMART_ENERGIA.md#compatibilità-del-backend-con-raspberry-20).
Il gateway del modello era ancora da realizzare a questo checkpoint storico.

Aggiornamento 13 settembre: gateway Groq provato con chiave reale su `.20`;
calendario relativo ora calcolato dal gateway e chiave leggibile da file privato.
60 test Python superati. Le prove reali confermano confronto EmonCMS e lettura
dei quattro log; hanno anche rilevato limiti linguistici documentati. Aggiunti
test Android live opt-in e template systemd, non installati. Guida completa:
[GATEWAY_MODELLO.md](GATEWAY_MODELLO.md). Restano deployment persistente, TLS e
prova completa su telefono prima della promozione.

## Gateway Groq — stato corrente del 13 settembre 2026

Implementato `model_gateway.py`: JSON Schema strict, prompt di consultazione,
validazione locale dei piani, autenticazione separata, errori sanitizzati, nessun
retry automatico e nessuna pubblicazione MQTT. Default promosso a
`openai/gpt-oss-120b` dopo il confronto reale col 20B. 60 test Python superati;
inferenze reali, confronto EmonCMS e quattro log verificati da `.20`. Riprendere
da [GATEWAY_MODELLO.md](GATEWAY_MODELLO.md). Deployment persistente e TLS
Tailscale completati successivamente; vedere
[DEPLOYMENT_20_TLS_2026-09-13.md](DEPLOYMENT_20_TLS_2026-09-13.md).
