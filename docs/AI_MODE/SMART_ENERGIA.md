# Modalità smart energia — implementazione del 12 settembre 2026

Il dominio energia dispone del percorso Android → API autenticata → pianificatore
HTTP configurabile → strumenti deterministici → risposta italiana comune a testo
e voce. Non usa corrispondenze con frasi predefinite per scegliere gli strumenti.
È implementato il gateway Groq; la valutazione reale indica
`openai/gpt-oss-120b` come prima scelta e mantiene `openai/gpt-oss-20b` come
alternativa economica. La chiave provider privata è stata verificata su `.20`.
Backend e gateway sono installati persistentemente su `.20`; l'app li raggiunge
in TLS privato tramite Tailscale Serve.

## Funzioni implementate

- Cinque letture del Digital Twin: FV, consumo casa, scambio rete, scambio
  batteria, livello Powerwall. Segni rete/batteria spiegati come prelievo,
  immissione, carica e scarica. Zero, invalido e mai ricevuto rimangono distinti.
- Sette metriche EmonCMS: produzione, consumo, prelievo, immissione, carica,
  scarica e SOC medio ponderato nel tempo. Periodi Europe/Rome con fine esclusa,
  buchi espliciti e nessun conteggio di campioni futuri. Sono stime da campioni,
  non misure fiscali. Oggi o periodi in corso restano parziali.
- Domande composte, fino a sei operazioni. Confronto deterministico della stessa
  metrica fra due periodi: differenza e variazione relativa, solo con copertura
  completa. La base zero rende indefinita la variazione relativa; per SOC la
  differenza è in punti percentuali. Nessuna normalizzazione implicita per durata.
- Ricerca deterministica del giorno con massimo o minimo totale per una metrica
  energetica in un intervallo fino a 366 giorni. Sono confrontati soltanto giorni
  di calendario `Europe/Rome` con copertura completa; quelli incompleti vengono
  contati ed esclusi. A parità viene scelto il primo giorno cronologico.
- Quattro log Node-RED, con estrazione giornaliera e file/riga. Testo con ultimo
  dato restituito: previsione FV, medie orarie/EMA, riserva e budget SHADOW,
  motivo AC registrato. I record grezzi sono nelle evidenze API; Android mostra
  provenienza e limiti, non una tabella completa di ogni campo.
- Risposte parziali conservate se una sorgente fallisce; autenticazione,
  indisponibilità, chiarimento, modello non configurato e risposta non valida
  gestiti esplicitamente. I chiarimenti si risolvono modificando la domanda:
  non è una conversazione con memoria implicita dei turni.
- Trascrizione italiana modificabile e invio esplicito; sintesi italiana offline
  se disponibile. Le risposte lunghe sono suddivise senza alterarne il testo.
- Ritorno alla dashboard classica e al rapporto giornaliero precedente.

## Provenienza e limiti

La risposta corrente è sempre qualificata come ultimo dato ricevuto: l'ora
Android non è il timestamp fisico del sensore. Anche a connessione attiva,
retained non prova freschezza. La risposta a una domanda è una fotografia della
richiesta con data di generazione; non si aggiorna automaticamente. Il riepilogo
iniziale segue invece lo stato MQTT osservato.

I log non danno copertura continua o conferma fisica. Lo SHADOW letto termina
al 6 settembre, non rappresenta lo stato corrente del 12 settembre. Le previsioni
usano il giorno previsto, senza inventare un'ora di elaborazione. La risposta
sui log espone fatti selezionati, non una spiegazione causale generata di tutte
le decisioni. Copie reali e hash sono sintetizzati in
[evidence/2026-09-12/energy-log-audit.json](evidence/2026-09-12/energy-log-audit.json).
Gli originali su `.20` sono stati esclusivamente letti.

## Configurazione necessaria per l'uso reale

1. Rendere disponibili al processo backend URL/chiave di lettura EmonCMS,
   token applicativo di almeno 24 caratteri, URL/token di un gateway pianificatore.
   [config.env.example](../../backend/house_ai/config.env.example) contiene soltanto
   segnaposto. Non copiarli come credenziali reali e non committare segreti.
2. Il gateway deve implementare il protocollo di `planner.py`: riceve domanda,
   data, timezone e catalogo; restituisce `{"plan":{"operations":[...]}}` oppure
   `{"plan":{"clarification":"..."}}`. `HOUSE_AI_PLANNER_URL` non è una URL API
   arbitraria di OpenAI/Ollama/altro provider: serve un adattatore conforme al
   modello scelto. Il gateway Groq incluso e il deployment attivo sono descritti
   in [GATEWAY_MODELLO.md](GATEWAY_MODELLO.md).
3. Impostare `HOUSE_AI_LOG_ROOT` solo su una cartella accessibile al backend,
   con i quattro file autorizzati. Non viene effettuata sincronizzazione SSH
   automatica. `HOUSE_AI_CLIMATE_LOG` resta separato per il rapporto precedente.
4. Dal modulo `backend/house_ai`, avvio manuale `python3 -B server.py`.
   Il bind predefinito è localhost. Per accesso remoto usare un endpoint TLS
   raggiungibile. Su `.20` viene usato Tailscale Serve, senza esposizione pubblica.
5. Nell'app: AI smart → indirizzo backend e token → domanda → Chiedi.
   Il token resta in memoria della schermata; uscendo o ricreandola va reinserito.

Nessun tool pubblica MQTT, scrive log, esegue shell o controlla dispositivi.
Validazione dell'intero piano prima delle letture, limiti campioni/operazioni,
cache soltanto per richiesta, massimo 40 s per avviare letture storiche aggiuntive
(più completamento della lettura in corso). Timeout Android 120 s. Le esclusioni
storiche API riportano conteggio e primi 100 dettagli; i log hanno limiti
32 MiB/file, 128 KiB/riga, 50 record e 160 kB grezzi/operazione. Symlink e file
non regolari vengono rifiutati. L'accesso HTTP rimane un servizio prototipo, non
un'infrastruttura pubblica multiutente.

## Verifiche riproducibili

Dal modulo `backend/house_ai`:

```sh
python3 -B -m unittest discover -s tests -v
python3 -B tools/evaluate_planner.py
```

Il secondo comando richiede gateway configurato e testa 16 domande sintetiche
senza accesso a sorgenti domestiche. La suite è una regressione scritta durante
lo sviluppo, non una valutazione linguistica indipendente; è stata eseguita
contro modelli reali il 13 settembre con gli esiti documentati. Servono anche
domande indipendenti dell'utente per
verificare ambiguità, scelta di periodo/strumento e richieste non supportate.

Da radice Android, con SDK/JDK compatibili:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

I test strumentali ordinari sono `AiSmartScreenTest` e `HouseAiRepositoryTest`.
`EnergyNavigationTest` va eseguito solo in emulatore con rete domestica isolata,
passando l'argomento strumentazione `houseAiIsolated=true`: avvia MainActivity,
quindi il normale tentativo di connessione MQTT viene bloccato dall'isolamento.
Non impostare quell'argomento su un telefono o un emulatore con rete domestica.

Risultati dettagliati in [VERIFICA_SMART_ENERGIA.md](VERIFICA_SMART_ENERGIA.md).
Restano fuori dalla prova: modello reale, accesso remoto end-to-end, ricezione
MQTT fisica su telefono e riconoscimento/riproduzione acustica su dispositivo.

## Compatibilità del backend con Raspberry `.20`

Verifica del 12 settembre 2026: il backend è compatibile con Python 3.9 dopo
una modifica dell'annotazione di `service.report`, da `Path | None` a
`Optional[Path]`. Nessuna modifica ai calcoli o al protocollo HTTP.

- Python 3.9.25 Linux x86_64, interprete isolato nella cache del worktree:
  prima della correzione due errori di importazione impedivano di caricare
  tutte le suite; dopo la correzione **51 test superati**.
- Raspberry `pi@192.168.1.20`, Python **3.9.2**, **aarch64**: compilazione in
  memoria di tutti i 26 file Python e importazione dei 13 moduli principali
  riuscite. Verificati gli offset invernale/estivo di `Europe/Rome`.
  SSL disponibile: OpenSSL 1.1.1w. Nessuna connessione a provider verificata.
- Prova remota tramite `ssh` e `python3 -B -`, sorgenti forniti su stdin con
  caricatore in memoria: nessun file installato, servizio avviato o Python di
  sistema modificato. La suite completa è stata eseguita localmente su 3.9.25,
  non sul Raspberry; la prova su 3.9.2 riguarda compilazione/import e timezone.

Il backend usa soltanto la libreria standard e richiede i dati timezone di
sistema, presenti su `.20`. Non serve sostituire Python per questo incremento.
La compatibilità funzionale non costituisce una verifica degli aggiornamenti
security del sistema né una prova di prestazioni sotto carico.

La collocazione candidata è `.20` per backend leggero e accesso locale ai log,
con `.15` come sorgente EmonCMS. La RAM comunicata per `.20` è 1,8 GiB totali e
circa 1,0 GiB disponibili: questa verifica non qualifica l'esecuzione locale di
un LLM. Il gateway è ora implementato; restano configurazione privata,
accesso alle sorgenti e prestazioni dell'intero percorso prima del deployment.

Per ripetere la regressione, dalla cartella `backend/house_ai`, usando un
interprete Python 3.9 disponibile:

```sh
python3.9 -B -m unittest discover -s tests -q
```

## Gateway implementato — 13 settembre 2026

[model_gateway.py](../../backend/house_ai/model_gateway.py) adatta il protocollo
DomoPi a Groq, richiede JSON Schema strict e valida nuovamente ogni piano.
Configurazione, riproduzione delle prove e limiti in
[GATEWAY_MODELLO.md](GATEWAY_MODELLO.md).

**63 test superati** nell'ultima regressione, inclusa la catena API backend → gateway HTTP →
adattatore Groq con risposta provider simulata → strumenti → risposta italiana.
Su `.20`, Python 3.9.2: compilazione di 28 file e import di 14 moduli in memoria
riusciti; HTTPS verso Groq verificato, risposta 401 senza credenziali con
User-Agent applicativo. Sono poi riuscite inferenze autentiche con la chiave
privata e letture reali di Digital Twin, EmonCMS e log. La suite linguistica ha
evidenziato errori e guidato le correzioni al prompt/calendario; i risultati e
la procedura di replica sono in [GATEWAY_MODELLO.md](GATEWAY_MODELLO.md).
Il 13 settembre backend e gateway sono stati installati come servizi persistenti
su `.20` e il backend è esposto in TLS solo nella tailnet tramite Tailscale Serve.
Dettagli e rollback in [DEPLOYMENT_20_TLS_2026-09-13.md](DEPLOYMENT_20_TLS_2026-09-13.md).

Il test Android opt-in ha poi superato il percorso repository Android → backend
e gateway temporanei su `.20` → Groq 120B → Digital Twin, EmonCMS `.15` e log
OpenMeteo. Sono riusciti anche il confronto EmonCMS e la lettura aggregata dei
quattro log. Evidenza completa in
[VERIFICA_GATEWAY_REALE_2026-09-13.md](VERIFICA_GATEWAY_REALE_2026-09-13.md).

La release `16e47fa` aggiunge la ricerca del massimo/minimo giornaliero ed è
stata verificata su `.20` con Python 3.9.2, EmonCMS reale e lo stesso endpoint
TLS usato dall'app. La prova della domanda sui sei mesi è documentata in
[VERIFICA_ESTREMI_GIORNALIERI_2026-09-13.md](VERIFICA_ESTREMI_GIORNALIERI_2026-09-13.md).
