# SOLAR-FORECAST.1 — Previsione FV affidabile

Data: 11 ottobre 2026. Scheda Node-RED "AI climate" su `.20`.

La previsione del fotovoltaico (Open-Meteo → "function 22" → variabili di flusso
`previsione_kwh_domani` / `previsione_data_domani` → controller "Machine Learning
domotico" e MQTT `zara/interface/logica_controllo/previsione_solare_*`) veniva
calcolata solo alle 23:00 e falliva senza lasciare traccia: nessuna previsione per
l'11/10 e per vari giorni di settembre/ottobre, con il controller che usava il valore
del giorno prima per `socMinimoLogico`.

Modifica (due nodi, nessun nodo aggiunto):

- inject `6916979d5a52f89e`: `00 23 * * *` → `*/30 23,0-5 * * *` (23:00 e ogni 30 min
  fino alle 05:30); resta il lancio al deploy/avvio;
- function `e987e367138f30ef`:
  - giorno obiettivo in ora italiana (prima di mezzogiorno oggi, poi domani), non UTC;
  - se il giorno obiettivo ha già una previsione calcolata nelle ultime 7 h non fa nulla;
  - ogni fallimento è registrato nel log di Node-RED (`node.warn`/`node.error`) con stato
    HTTP e motivo; le variabili restano invariate e si ritenta al giro successivo;
  - risposta illeggibile: resta il fallback storico di 12 kWh nella variabile, senza
    riga nel log delle previsioni e senza aggiornare la data;
  - giornata incompleta (meno di 24 ore): nessuna previsione inventata (prima: 12 kWh
    scritti nel log come previsione);
  - la riga del log aggiunge `elaborata_alle` (ISO UTC).

Il controller e il Predictive Reserve leggono le stesse variabili: nessuna modifica.

File: `function22.js` (corpo nuovo), `build.py`, `update.json`, `rollback.json`,
`manifest.json`, `test.js` (9 test, `node --test test.js`), `deploy.py` (`prepare`,
`apply BACKUP`, `rollback BACKUP`, da eseguire su `.20`).
