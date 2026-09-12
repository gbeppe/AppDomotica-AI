# Primo modulo di consultazione

Prototipo Python 3.10+ con sola libreria standard e dati timezone Europe/Rome.
Eseguibile localmente; non installa servizi e non contiene connessioni MQTT o comandi ai dispositivi.
Python è usato per questo adattatore verificabile, senza vincolare il resto del backend.

Dal percorso `backend/house_ai`:

```sh
python3 cli.py events --log /percorso/clima_controllo.log --day 2026-09-06
python3 cli.py catalog
python3 -B -m unittest discover -s tests -v
```

Il catalogo richiede `EMONCMS_URL` e `EMONCMS_API_KEY` nell'ambiente. La URL può
includere `/emoncms` se necessario. Non inserire la chiave nel repository o negli
argomenti del comando. Usare una credenziale di sola lettura. Nessuna chiamata al
modello generativo viene eseguita.

L'estrazione restituisce JSON su stdout, con riferimenti a file/riga, esclusioni e
limiti. Gli originali non vengono modificati. Il filtro dei transitori riconosce
la combinazione previsione N/A e temperatura camera zero; non considera da sola
una previsione mancante prova di un'anomalia. Esclude duplicati esatti, senza
confondere eventi diversi avvenuti nello stesso secondo.

Implementati nel prototipo: rapporto giornaliero, integrazione AC con copertura,
filtro dei picchi SOC isolati, medie AC prima/dopo gli eventi, API HTTP e schermata
Android «Chiedi alla casa». La validazione integrata con le sorgenti reali resta
da completare. Non ancora implementati: catalogo semantico completo, modello
generativo, domande libere e riepiloghi spontanei.
Lo schema evidence v1 è provvisorio. Nessuna conferma fisica viene dedotta da un
semplice comando nel log. Il parser è destinato ai file JSONL climatici forniti,
non ai log SHADOW o ad altri formati.

## API locale

Avvio manuale: `python3 -B server.py` dalla cartella di questo modulo.
Richiede `EMONCMS_URL`, `EMONCMS_API_KEY` e `HOUSE_AI_TOKEN` (almeno 24
caratteri) nell'ambiente; `HOUSE_AI_CLIMATE_LOG` è il percorso facoltativo
del log JSONL. In sua assenza il rapporto dichiara il limite esplicitamente.
Il bind predefinito è `127.0.0.1:8765`; non viene installato alcun servizio.

Entrambi gli endpoint richiedono `Authorization: Bearer <token>`:

- `GET /v1/health`: stato del servizio in consultazione.
- `GET /v1/report?day=2026-09-06`: schema `house_ai.daily_report.v1`,
  intervallo Europe/Rome, serie con timestamp in millisecondi, metriche,
  eventi, esclusioni e limiti. I campioni mancanti restano null.

La schermata Android si apre con il pulsante AI della dashboard e richiede
indirizzo, token e giorno. Il token resta in memoria. L'accesso remoto richiede
un endpoint raggiungibile con TLS; collocazione e deployment restano da definire.

## Stato corrente (base offline)

`current_catalog.json` e `current_state.py` definiscono il catalogo provvisorio e
l’interprete delle osservazioni pubbliche. Non avviano connessioni né espongono
nuovi endpoint. Conteggi riferiti agli stati dichiarati, con copertura parziale
e freschezza ignota. Evidenze: `docs/AI_MODE/MAPPING_STATO_CORRENTE.md` dalla radice
del progetto. I test si eseguono dalla cartella di questo modulo come sopra.

## Storico energia — primo incremento

`energy_history.py` calcola `grid_import_kwh` e `soc_mean_percent`, con copertura
temporale e stato complete/partial/insufficient_data. Date locali Europe/Rome,
fine esclusa, massimo 366 giorni. Media SOC ponderata nel tempo; integrazione
del solo prelievo con attraversamento dello zero interpolato linearmente.
Nessuna estrapolazione attraverso buchi o valori invalidi. Valori SOC fuori
0–100 esclusi; picchi dentro tale intervallo non filtrati senza ulteriori evidenze.

CLI dalla directory `backend/house_ai`, con `EMONCMS_URL` e `EMONCMS_API_KEY`
configurati; verificare metadati/unità prima di usare il mapping statico:

```sh
python3 -B cli.py energy --start 2026-09-01 --end 2026-09-08 --metric soc_mean_percent --feed-id 304 --unit %
```

Per `grid_import_kwh` occorrono `--unit W` e `--import-sign 1` o `-1`,
secondo la convenzione verificata del feed. Il programma rifiuta segno assente:
non considera il mapping statico una verifica fisica. Nessuna nuova credenziale
è incorporata nel codice. Richieste suddivise in blocchi sotto 10000 campioni,
risoluzione CLI 30 secondi, senza medie mensili preventive. La risoluzione resta
un limite: non ricostruisce variazioni fra campioni né garantisce misura fiscale.

`relative_period` distingue mese precedente, settimana precedente e ultimi
7/30 giorni completi (oggi escluso); non interpreta ancora frasi libere.
Questo incremento è disponibile in Python/CLI, non nella API HTTP o scheda Android.

## Assistente dinamico del dominio energia

`assistant.py` non riconosce frasi cablate: passa il testo a un pianificatore e
fa eseguire a `energy_tools.py` soltanto un piano JSON validato contro
`energy_sources.json`. Sono disponibili prelievo/immissione, consumo casa,
produzione FV, carica/scarica batteria e SOC medio. Richieste composte possono
produrre fino a sei operazioni.

Configurazione facoltativa del gateway del pianificatore:

```sh
export HOUSE_AI_PLANNER_URL=https://planner.example/v1/plan
export HOUSE_AI_PLANNER_TOKEN=...
```

`POST /v1/assistant/query`, corpo `{"question":"..."}`, usa l'autenticazione
Bearer del servizio. Il gateway riceve istruzione, domanda, data, timezone e
catalogo tool e restituisce `{"plan":{"operations":[...]}}` oppure
`{"plan":{"clarification":"..."}}`. Nessuna credenziale EmonCMS viene inviata.
Il provider concreto resta da scegliere e non è incluso nel deployment.

La verifica reale del 12 settembre ha promosso i due mapping in
`energy_sources.json`. È disponibile anche `GET /v1/energy/query?q=...`, con la
stessa autenticazione Bearer degli altri endpoint. L'interprete accetta per ora
prelievo rete e SOC medio con «mese scorso», «settimana scorsa», «ultimi 30
giorni» e «ultimi 7 giorni». «Ultimo mese/settimana» produce una richiesta di
chiarimento tra calendario e finestra mobile. Il risultato include sempre il
periodo assoluto, la copertura e le limitazioni analitiche.

## Log Node-RED aggiuntivi

`HOUSE_AI_LOG_ROOT` abilita il tool `backend_log_day` nell'assistente per i
quattro log allowlist di `.20`. Deve indicare una cartella accessibile al
processo backend; nessuna connessione SSH automatica. Fonte/giorno sono
validati, lettura e risposta limitate, provenienza file/riga preservata.
Dettagli e limiti: `docs/AI_MODE/FONTI_LOG_NODERED.md` dalla radice repository.
