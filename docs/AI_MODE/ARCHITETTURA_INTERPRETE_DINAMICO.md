# Interprete dinamico — architettura iniziale del dominio energia

Decisione dell'utente del 12 settembre 2026: domande scritte e trascrizioni
vocali non devono essere riconosciute attraverso frasi cablate durante lo
sviluppo. Il primo dominio completo usato per validare l'architettura è energia.

## Flusso implementato

```text
testo o trascrizione
        ↓
pianificatore dinamico configurabile
        ↓ piano JSON
validatore chiuso sul catalogo energia
        ↓ operazioni ammesse
strumenti deterministici → EmonCMS sola lettura
        ↓ risultati, copertura, fonti e limiti
risposta comune per schermo e sintesi vocale
```

Il pianificatore riceve la domanda originale, la data corrente, Europe/Rome e
il catalogo degli strumenti. Non riceve credenziali EmonCMS. Può formulare fino
a sei operazioni per richieste composte o confronti, oppure chiedere un
chiarimento. La comprensione linguistica appartiene al pianificatore: il codice
non contiene sinonimi o modelli di frase per selezionare le nuove operazioni.

Il piano non viene eseguito liberamente. `energy_tools.py` accetta soltanto lo
schema esatto, lo strumento `energy_metric`, metriche presenti nel catalogo e
date assolute. Campi extra, tool o metriche inventati, ID duplicati e piani
troppo grandi vengono rifiutati prima dell'accesso ai dati. Non esistono tool
shell, SQL, MQTT o comando dispositivi.

## Metriche disponibili nel primo catalogo

- prelievo e immissione dalla rete;
- consumo della casa e produzione fotovoltaica;
- energia caricata e scaricata dalla Powerwall;
- SOC medio temporale.

Le sei metriche in kWh integrano la parte fisicamente pertinente della potenza,
senza annullare flussi opposti. Il mapping dei feed 303–307 e i segni sono
coerenti con il bilancio Tesla verificato. Il 6 settembre 2026 tutte le metriche
hanno copertura 100%; i risultati della fotografia sono registrati nella
[validazione](VALIDAZIONE_STORICO_ENERGIA.md), non usati come costanti.

## Interfaccia del pianificatore

`planner.py` implementa un adattatore HTTP JSON neutro rispetto al fornitore.
Il backend invia una richiesta strutturata a `HOUSE_AI_PLANNER_URL`, autenticata
con `HOUSE_AI_PLANNER_TOKEN`; si aspetta `{ "plan": { ... } }`. URL e token
restano nell'ambiente del backend. Redirect vietati, risposta massima 256 KiB e
timeout 30 secondi. Nessun fornitore o modello è ancora scelto o distribuito.

L'endpoint applicativo è `POST /v1/assistant/query` con corpo JSON contenente
solo `question`; usa il token del servizio `house_ai`. Se il pianificatore non è
configurato, risponde esplicitamente `not_configured`. Il precedente endpoint
`GET /v1/energy/query` basato su frasi limitate resta temporaneamente per
compatibilità di test, ma non è l'interprete previsto per il prodotto.

## Stato della validazione

Passano 34 test backend. Sono coperti frase arbitraria delegata, richieste
composte, chiarimento, planner assente, piani ostili o fuori schema, tutte le
direzioni energetiche, buchi, cambio d'ora, limiti HTTP e autenticazione.

Validazione live in sola lettura del 6 settembre 2026:

| Metrica | Risultato | Copertura |
|---|---:|---:|
| Prelievo rete | 0,07314 kWh | 100% |
| Immissione rete | 19,02642 kWh | 100% |
| Consumo casa | 12,93421 kWh | 100% |
| Produzione FV | 34,19455 kWh | 100% |
| Carica batteria | 10,99991 kWh | 100% |
| Scarica batteria | 8,73375 kWh | 100% |
| SOC medio | 65,91530% | 100% |

Questa validazione prova strumenti e mapping, non la qualità semantica di un
modello reale. Prossimi criteri: scegliere/configurare un pianificatore,
costruire un insieme di domande italiane indipendente dallo sviluppo, misurare
correttezza di tool/periodo e richieste di chiarimento, quindi collegare Android
e verificare che testo e voce producano la stessa richiesta.
