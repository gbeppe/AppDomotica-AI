# Modalità smart energia — implementazione del 12 settembre 2026

Il dominio energia dispone del percorso Android → API autenticata → pianificatore
HTTP configurabile → strumenti deterministici → risposta italiana comune a testo
e voce. Non usa corrispondenze con frasi predefinite per scegliere gli strumenti.
Il modello concreto e il suo gateway non sono stati scelti/configurati: pertanto
l'implementazione è verificata con pianificatori simulati, non con comprensione
linguistica reale o deployment domestico.

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
   modello scelto. Nessun gateway provider-specific è incluso o distribuito.
3. Impostare `HOUSE_AI_LOG_ROOT` solo su una cartella accessibile al backend,
   con i quattro file autorizzati. Non viene effettuata sincronizzazione SSH
   automatica. `HOUSE_AI_CLIMATE_LOG` resta separato per il rapporto precedente.
4. Dal modulo `backend/house_ai`, avvio manuale `python3 -B server.py`.
   Il bind predefinito è localhost. Per accesso remoto usare un endpoint TLS
   raggiungibile: nessun reverse proxy o servizio è stato installato qui.
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
lo sviluppo, non una valutazione linguistica indipendente; non è stata eseguita
contro un modello reale. Servono anche domande indipendenti dell'utente per
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
