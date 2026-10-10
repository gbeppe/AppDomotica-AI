# Estensione della provenienza alle altre luci

> Aggiornamento: la [catalogazione dashboard_only_v2](../actor_policy/README.md)
> sostituisce le precedenti classificazioni: dashboard utente, Alexa e tutte le
> altre sorgenti automazione. Le descrizioni precedenti restano storiche dove divergono.


Distribuita su .20 il 22 settembre 2026. Non cambia Android né le modifiche
utente alla dashboard. Copertura determinata dal percorso, non dalla sola
presenza del dispositivo nel catalogo. Nessuna conferma fisica viene inventata.

## Copertura effettiva

| Luce | App individuale | UI Node-RED / Alexa diretti | Scene con provenienza | Automazioni |
|---|---|---|---|---|
| Libreria | RM send_invoked, precedente patch | RM, precedente patch | RM, precedente patch | LG marcata; schedex non attribuito |
| TV | RM send_invoked | RM send_invoked | RM send_invoked dove il ramo è raggiunto | schedex non attribuito |
| Lettura | RM send_invoked | RM send_invoked | RM send_invoked dove il ramo è raggiunto | schedex non attribuito |
| Soggiorno | RM send_invoked | RM send_invoked | RM send_invoked dove il ramo è raggiunto | schedex non attribuito |
| Piscina acqua e pedana | routed nel Router; provenienza persa sul tratto MQTT legacy | RM send_invoked | RM send_invoked dove il ramo è raggiunto | schedex non attribuito |
| Lavanderia, portico ingresso, ingresso servizio | routed nel Router | publisher_input | publisher_input dove la scena raggiunge il publisher | motion_rule sul ramo verificato, publisher_input |

Prolunga/Allarme resta esclusa: classificazione ancora pending nel catalogo.
HiFi/camera non fanno più parte della lista luci corrente della dashboard.
SSH .15 rifiutato: l'inventario di eventuali publisher locali .15 non è completo.
È stato richiesto export aggiornato o ripristino accesso; non si presume assenza
di altri comandi. Gli eventi privi di provenienza non diventano automazione.

## Architettura

- Sedici marcatori per sorgenti dirette UI/Alexa già identificate. UI passthru=false
  verificato. Payload, topic e fan-out originali conservati.
- Sei marcatori sugli archi di azione delle regole movimento Shelly: dopo filtro
  ON e dopo il timer OFF. Origine motion_rule, autore automazione. Il codice
  stoptimer installato invia il msg originale sulla prima uscita collegata;
  il payload numerico configurato appartiene alla seconda uscita non collegata.
- Dieci adattatori RM specifici per entità/stato e tre davanti ai publisher Shelly.
  Consumano provenienza diretta o scena, rispettando tutte le condizioni a monte.
- Due publisher semantici home, nei tab in cui servono; sei report RM aggiunti
  e quattro esistenti estesi. Nessun nodo RM, comando IR o topic legacy cambiato.
- Function 17 conserva la provenienza anche sul percorso interno soggiorno.
  Il soggiorno attraversa due RM in sequenza: il report è dopo il secondo.
  È un riscontro software della sequenza, non la prova fisica di due lampade.

I metadati interni sono eliminati prima del report legacy/publisher Shelly.
command_id segue il comando e le scene producono ID specifici per entità/stato.
actor e origin indicano il ramo logico, non l'identità autenticata del publisher.
execution_confirmed e physical_confirmation restano false.

## Topic da verificare

Broadlink: `zara/interface/semantic/v1/rm_receipts/{entity_id}/stat`, stage
send_invoked. Entità: lights.living.power, lights.tv.power, lights.reading.power,
lights.libreria.power, pool.water.power, pool.deck.power.

Shelly da UI/Alexa/regole .20:
`zara/interface/semantic/v1/publisher_inputs/{entity_id}/stat`, stage
publisher_input. Entità: lights.laundry.power, lights.porch_entrance.power,
lights.service_entrance.power. Attesta consegna al ramo mqtt out, non pubblicazione
confermata dal broker né esecuzione Shelly. Il normale feedback relay non contiene
command_id e non viene correlato per sola vicinanza temporale.

I comandi individuali app che attraversano MQTT mantengono il precedente
`routed_commands` con stage routed. Questa versione NON certifica il loro
riscontro finale con autore noto. Non alterare il percorso operativo per
aggirare tale limite e non confondere i record dei diversi stadi.

## Deploy, backup e verifica

Backup completo runtime e file progetto/credenziali cifrate disponibili su .20:
`/home/pi/AI_backups/nodered/20260922T114958Z-before-all-lights`.
59 nodi modificati (molti solo nei fili), 37 aggiunti. ID nel manifest.
Admin API v2 TLS verificato, revision check, deploy nodes alle 13:50:10 CEST.
Revisione finale `e90596260dc10898d9e2b22f4654a67d5819173e58bff4da73ab01e8d3cda07f`.
GET post-deploy identico al piano completo, servizio active, nessun errore
nel filtro dei log della finestra osservata. Nessun riavvio completo.
Potenziale perdita dei messaggi pendenti nei timer/delay modificati comunicata
prima del deploy. Nessun comando fisico di collaudo inviato dall'agente.

20 test isolati complessivi passati (5 nuovi): conservazione di tutti i fili
operativi attraversando i marker, payload e fan-out, sorgenti utente/movimento,
riscontri per ogni ramo RM, evidenza Shelly senza falsa conferma, compatibilità
con le precedenti patch. Node RM simulati: non sostituisce i test sul campo.
Durante il normale utilizzo raccogliere nuovi ID, requested_state e origin per
ON/OFF e per ciascun canale; non usare retained vecchi come nuova prova.

## Replica e rollback

build.py richiede export completo aggiornato; verifica ID e assunzioni di base.
update.json contiene sostituzioni: non importare come duplicati. deploy.py
prepare/apply salva runtime e revisioni, rifiuta drift e collisioni dei nuovi ID.
Prima di ogni nuova applicazione generare e controllare un piano aggiornato.

Rollback: confrontare il runtime corrente, ripristinare solo i modified_ids con
i nodi di rollback.json, eliminare soltanto gli added_ids del manifest. Usare
revisione corrente e deploy nodes. Non ripubblicare alla cieca tutto il backup
se ci sono modifiche successive. Mantenere precedenti patch libreria, TLS e
configurazioni MQTT; non cancellare context o topic dei dispositivi.

## Lavoro residuo

Provenienza programmata/manuale interna schedex; ricognizione runtime .15;
classificazione Prolunga/Allarme; riscontro causale oltre i confini MQTT senza
command_id; lettura dei record da Smart (punto 4). Questi limiti non sono risolti
dalla copertura delle origini .20 qui distribuita.

## Consumo nelle risposte Smart

L'app e House AI consumano le quattro famiglie di record con la policy corrente
`dashboard_only_v2`. Architettura, contratto, rollout e limiti sono descritti in
[Integrazione semantica luci](../../../../docs/AI_MODE/INTEGRAZIONE_SEMANTICA_LUCI.md).
Questa integrazione non cambia il flow e non invia comandi di collaudo.
