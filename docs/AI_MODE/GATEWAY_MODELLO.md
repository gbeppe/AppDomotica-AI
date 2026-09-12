# Gateway del modello — 13 settembre 2026

Implementato in `backend/house_ai/model_gateway.py`, Python 3.9 e sola libreria
standard. Default: Groq `openai/gpt-oss-20b`, endpoint HTTPS fisso
`https://api.groq.com/openai/v1/chat/completions`. La modalità JSON Schema strict
è documentata per questo modello nelle [fonti ufficiali Groq](https://console.groq.com/docs/structured-outputs),
consultate il 13 settembre 2026. La disponibilità gratuita dipende dalle quote
dell'account; il gateway non gestisce abbonamenti o attivazioni a pagamento.

## Percorso e confini

App → backend `/v1/assistant/query` → gateway localhost `/v1/plan` → Groq →
piano validato → strumenti backend → risposta italiana deterministica → app.

Il gateway riceve il protocollo `house_ai.planner_request.v1` e restituisce
`{"plan":{"operations":[...],"clarification":null}}`, oppure operations vuoto
e clarification testuale. Il catalogo ricevuto deve corrispondere a quello
versionato. L'istruzione inviata dal chiamante non viene usata come prompt:
il gateway possiede le regole di sistema. La domanda rimane dato non fidato.

Al provider vengono inviati domanda, data, timezone e catalogo. Non vengono
inoltrati snapshot Digital Twin, record dei log, serie EmonCMS o credenziali
domestiche. Una domanda può comunque contenere informazioni personali scritte
dall'utente: il percorso Groq è cloud. La chiave provider resta nel processo gateway.

JSON Schema chiuso sui quattro strumenti; il validatore locale controlla anche
metriche, date, budget campioni, massimo sei operazioni e riferimenti dei confronti.
Un piano invalido o una risposta troncata/refused produce 502; nessun fallback
che inventi una risposta o esegua azioni. Timeout provider 20 s, nessun retry
automatico, risposta provider massima 256 KiB. Ingresso gateway massimo 32 KiB,
1000 caratteri per domanda, token di almeno 24 caratteri all'avvio.
Il servizio è seriale e adatto alla prova domestica; prestazioni concorrenti non validate.

## Configurazione privata e avvio manuale

Usare `backend/house_ai/config.env.example` come elenco di variabili; sostituire
privatamente i segnaposto. Non inserire chiavi in Git, chat, argomenti shell o screenshot.
I due processi possono ricevere ambienti separati:

- Gateway: `GROQ_API_KEY`, `HOUSE_AI_PLANNER_TOKEN`, facoltativi `HOUSE_AI_MODEL`
  e `HOUSE_AI_GATEWAY_PORT` (8766). La porta ascolta solo su `127.0.0.1`.
- Backend: `EMONCMS_URL`, `EMONCMS_API_KEY` di lettura, `HOUSE_AI_TOKEN`,
  `HOUSE_AI_PLANNER_URL=http://127.0.0.1:8766/v1/plan`, stesso
  `HOUSE_AI_PLANNER_TOKEN`, percorsi log e impostazioni bind/porta esistenti.

Dalla cartella `backend/house_ai`, in due terminali con i rispettivi ambienti:

```sh
python3 -B model_gateway.py
```

```sh
python3 -B server.py
```

Non servono pacchetti pip. Nessun servizio systemd è incluso o installato.
L'accesso Android remoto richiede il consueto endpoint backend TLS raggiungibile;
il gateway resta sullo stesso host del backend. `.20` è il candidato backend,
con log locali in `/home/pi/AI_climate`; `.15` resta sorgente EmonCMS.

## Evidenza ottenuta

- 58 test superati su Python 3.9.25 locale, comando dalla cartella del modulo:
  `python3.9 -B -m unittest discover -s tests -q`.
- Sette nuovi test coprono richiesta al provider e separazione credenziali,
  output malformati/troncati, errori sanitizzati senza retry, schema/catalogo,
  autenticazione, errore 502 senza letture e catena HTTP con tre fonti sintetiche.
- Nella catena HTTP: FV corrente 1234 W, prelievo storico 24 kWh e previsione
  da file temporaneo 18 kWh ritornano nel testo. Backend e gateway sono server
  reali su porte loopback effimere; risposta Groq e client storico sono simulati.
  Non è una valutazione della correttezza linguistica del modello.
- `.20`, Python 3.9.2/aarch64: tutti i 28 file compilati e i 14 moduli principali
  importati in memoria via SSH/stdin, bytecode disabilitato, senza installare file.
- Da `.20`, TLS verificato verso `/openai/v1/models`: con User-Agent predefinito
  urllib risposta 403; con `DomoPi-HouseAI/1.0` risposta JSON 401 Invalid API Key
  (nessuna chiave inviata). Il gateway include quel User-Agent. Questo prova
  raggiungibilità TLS, non autenticazione o disponibilità del modello.

## Prova reale ancora da eseguire

Non è presente `GROQ_API_KEY` nell'ambiente di lavoro. È stato chiesto all'utente
di indicare il nome della variabile o il percorso privato dove è configurata,
senza inviare il segreto. Non cercare chiavi indiscriminatamente nei file domestici.

Dopo configurazione, eseguire dal modulo:

```sh
python3 -B tools/evaluate_planner.py
```

La suite contiene 16 domande italiane sintetiche e non legge sorgenti domestiche.
Poi verificare attraverso `/v1/assistant/query` almeno stato corrente con snapshot
reale dell'app, un giorno storico, un confronto e ciascuna delle quattro fonti log.
Confrontare piano, periodo, unità, provenienza e copertura con gli strumenti diretti;
non interpretare risposta HTTP 200 o copertura parziale come prova di tutti i dati.
Verificare anche richieste ambigue/non supportate e indisponibilità del provider.

Restano aperti inferenza reale, autenticazione provider, percorso Android/TLS,
accesso integrato alle sorgenti e prestazioni sul Raspberry. Nessun deployment,
modifica Node-RED o comando ai dispositivi eseguito durante questo incremento.
