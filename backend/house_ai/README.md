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
