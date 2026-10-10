# Estensione riscontro RM: libreria e lampada lettura

Base letta via SSH da .20, hash in manifest. Il percorso TV send_invoked è stato
validato dall'utente. Estensione distribuita il 22 settembre 2026 e verificata dall’utente in ON/OFF
per libreria e lettura fino allo stadio software send_invoked. Preserva i nodi TV,
i payload legacy, il filtro RBE e i collegamenti operativi. Non modifica Android.

## Perimetro e scelta

Libreria e lettura usano un RM seguito da una Function che pubblica on/off.
Riutilizziamo il publisher semantico della TV, senza aggiungere nodi. Soggiorno
ha due RM e un ritardo; prolunga ha una scelta condizionale: non sono inclusi.
Le nuove luci Android non sono dichiarate validate da questa patch.

La provenienza utente viaggia sul messaggio, con lo stesso command_id. Il record
send_invoked attesta l'output software successivo a sendData, non acknowledgment
hardware, né transizione fisica. I messaggi legacy senza provenienza non diventano
utente o automazione per inferenza. L'attribuzione delle automazioni richiederà
l'identificazione dei loro ingressi specifici; non è completata qui.

## Aggiornamento manuale

Salvare prima gli export dei cinque nodi sotto elencati. Non importare update.json
come nuovi nodi: contiene sostituzioni con ID originali. Nessun nuovo publisher.

1. Function 17 `ea8307d48995ca93`: sostituire tutto il codice con
   [function17.js](function17.js), mantenendo 24 uscite e tutti i collegamenti.
2. Aggiornare le quattro Function successive agli RM, usando gli ID per
   riconoscerle (possono non avere nome):

| Function | File | ID |
|---|---|---|
| Libreria ON | [codice ON](lights.libreria.power.on.js) | `f7bc8ba8.270b6` |
| Libreria OFF | [codice OFF](lights.libreria.power.off.js) | `18386049.c296a8` |
| Lettura ON | [codice ON](lights.reading.power.on.js) | `a082b270d39049dc` |
| Lettura OFF | [codice OFF](lights.reading.power.off.js) | `1d425ade7f5a82ae` |

3. Portare ciascuna di queste quattro Function da una a due uscite. Conservare
   la prima uscita e tutti i suoi collegamenti (incluso eventuale debug).
   Collegare la seconda al publisher `TV semantic receipt only`, ID
   `aa764cb4ce617210`, già installato. Il nome TV non limita i topic dinamici.
4. Deploy dei cinque nodi modificati. Non cambiare Router, RM o nodi TV.

## Verifica e rollback

Alternare ON/OFF da dashboard Android e leggere:

- `zara/interface/semantic/v1/rm_receipts/lights.libreria.power/stat`
- `zara/interface/semantic/v1/rm_receipts/lights.reading.power/stat`

Attesi actor utente, requested_state coerente, stage send_invoked e command_id
uguale al corrispondente dispatched_commands. Verificare anche TV e stati della
dashboard. Retained significa ultimo riscontro disponibile, non stato fresco.
Un comando scartato dall'RBE non aggiorna il riscontro. Nessun output RM non
significa automaticamente errore hardware; timeout semantici non implementati.

Rollback: ripristinare le cinque Function da rollback.json, rimuovere solo le
quattro nuove connessioni e riportare i report a una uscita. Function 17 resta
a 24 uscite; publisher e percorso TV restano installati. Fare deploy.

Sei test isolati passati (quattro della patch TV, due dell'estensione): ID,
ON/OFF, payload legacy, assenza di attribuzione senza provenienza, wiring.
RM simulato nei test isolati; deploy e riscontri runtime sono documentati sotto.

```sh
node --test backend/nodered/semantic_bridge/rm_extension/test.js backend/nodered/semantic_bridge/rm_receipt/test.js
```

## Deploy eseguito il 22 settembre 2026

Autorizzato dall'utente, eseguito tramite Admin API v2 su TLS verificato e token
CLI locale (non copiato nel repository né mostrato). Prima del POST è stato
verificato il runtime, non soltanto il file progetto. Confrontati i cinque nodi
con la base; conservati tutti gli altri nodi e campi del runtime corrente.

Backup completo su .20:
`/home/pi/AI_backups/nodered/20260922T112456Z-before-rm-extension`.
Include runtime-before.json, runtime-planned.json, runtime-after.json,
flow su disco e file credenziali cifrate presente. Directory privata.

Deploy di tipo nodes con controllo rev: stop dei nodi modificati alle 13:25:07
CEST, avvio completato alle 13:25:08. Nessun riavvio del servizio.
Revisione restituita:
`5c2d77d1f09dd553267de300e9ee10fa886e71a610c47cb370b080f6d0611de0`.
GET successivo conferma che tutto il runtime coincide con la configurazione
prevista; Node-RED active, nessun errore nel filtro dei log della finestra osservata.
Sei test isolati passati prima del deploy. Nessun comando ON/OFF inviato
dall’agente. Successivamente l’utente ha fornito i quattro riscontri software
ON/OFF riportati sotto; la conferma fisica resta esclusa.

Il file deploy.py versiona la procedura prepare/apply e usa rev per rifiutare
modifiche concorrenti. Non stampare né versionare .cli-config.json o backup completi.
Per rollback manuale valgono le istruzioni sopra; un ripristino API deve usare
la revisione CORRENTE e sostituire solo i cinque nodi, dopo verifica che nessuno
li abbia modificati nel frattempo. Non ripubblicare alla cieca l'intero vecchio
runtime: cancellerebbe eventuali modifiche successive.

API di riferimento: https://nodered.org/docs/api/admin/methods/post/flows/


## Prove runtime ON/OFF concluse — 22 settembre 2026

Fonte: quattro payload JSON incollati dall'utente in chat dopo le prove dalla
dashboard. Questi risultati sono evidenze fornite dall'utente, non una nuova
lettura autonoma del broker. Tutti riportano `schema: domopi.semantic_command.v1`,
`actor: utente`, `origin: app_interface`, `attribution: interface_contract`,
`stage: send_invoked` ed `evidence: rm_output_after_sendData`.

| Entità | Richiesta | command_id | observed_at_ms | Intervallo dal timestamp nell'ID |
|---|---|---|---|---|
| lights.reading.power | ON (`true`) | `5a7c1d360710ca21:1790076533001:2` | 1790076533037 | 36 ms |
| lights.libreria.power | OFF (`false`) | `5a7c1d360710ca21:1790076536325:3` | 1790076536370 | 45 ms |
| lights.reading.power | OFF (`false`) | `5a7c1d360710ca21:1790076637686:4` | 1790076637723 | 37 ms |
| lights.libreria.power | ON (`true`) | `5a7c1d360710ca21:1790076639234:5` | 1790076639642 | 408 ms |

Esito: copertura ON e OFF completata per entrambe le entità fino all'uscita
software RM, con identificativo del comando presente nel riscontro. Gli
intervalli sono differenze fra timestamp software; non sono tempi di risposta
fisica della lampada né benchmark delle prestazioni.

In tutti i record `execution_confirmed`, `physical_confirmation` e
`publisher_authenticated` sono false. Quindi non sono dimostrati ricezione IR,
stato fisico, autenticazione individuale del publisher o causalità delle
transizioni MQTT legacy. I payload forniti sono i riscontri finali: non costituiscono
una cattura completa indipendente di tutte le fasi con lo stesso ID.

Il record TV ON `5a7c1d360710ca21:1789511265177:14`, fornito insieme al primo
record lettura, ha un timestamp precedente e non viene contato come una nuova
prova TV del 22 settembre o come prova libreria. La validazione TV precedente
resta separata.

Prossimo passo, non eseguito in questo aggiornamento documentale: mappare gli
ingressi della libreria per distinguere utente e automazione con provenienza
esplicita. Attribuzione delle automazioni e consumo dei nuovi record in Smart
non sono dichiarati completati da queste prove.

## Mappatura delle origini (punto 2)

Disponibile la [mappa pilota della libreria](../source_mapping/LIBRERIA.md):
app, UI Node-RED, Alexa, scheduler, regola TV LG e scene. Sono esplicitati i punti
in cui preservare la provenienza e i casi ancora ambigui. Analisi read-only,
nessuna nuova attribuzione o modifica del runtime implementata in questo passo.

## Provenienza: incremento distribuito

Il 22 settembre è stato distribuito il [primo incremento delle origini libreria](../libreria_origins/README.md):
UI Node-RED e Alexa diretti come utente, regola LG come automazione. Runtime
verificato e backup completo; prove fisiche/utenti dei nuovi ingressi da svolgere.
Scheduler e scene restano fuori da questo incremento.
