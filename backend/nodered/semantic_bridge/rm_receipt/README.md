# Strumentazione Broadlink — luce TV

Patch preparata sul flow progetto letto via SSH in sola lettura da `.20`.
Hash base in manifest.json. Non distribuita. Autorizzata dall'utente anche nel
tratto fuori Digital Twin Bridge. Modifiche Android dell'utente preservate.

## Architettura ed evidenza

Function 17 conserva `_semantic_command` solo sui rami TV ON/OFF. RM conserva
le proprietà del messaggio e modifica il payload; le due funzioni dopo RM
producono un riscontro separato e rimuovono la metainformazione prima del
publisher legacy. Topic e valori on/off legacy restano invariati. Non cambiano
RBE, comandi IR, configurazioni RM o strategie.

Verificato il codice installato in
`/home/pi/.node-red/node_modules/node-red-contrib-broadlink-control/nodes/RMNode.js`:
nel ramo send/catalogo, sendData viene chiamato a riga 169; payload.status=OK a
175; node.send(msg) a 179. Non attende un acknowledgment della lampada.
Perciò stage=send_invoked, execution_confirmed=false, physical_confirmation=false.
Lo stesso command_id collega richiesta, dispatch e riscontro software, non
prova lo stato fisico né attribuisce retroattivamente i report MQTT scalari.
Se RM non produce output non si inventa esito: timeout/errori non sono ancora
pubblicati come eventi semantici. Eventuali duplicati mantengono lo stesso event_id.

Copertura: sola luce TV comandata dal percorso app con provenienza Router.
Alexa, dashboard Node-RED e automazioni legacy restano senza attribuzione in
questo incremento. Le nuove luci Android non sono alterate o dichiarate validate.

## Installazione manuale

Esportare i tre nodi modificati prima di procedere. Non creare copie di nodi
operativi. Il file update.json contiene sostituzioni con ID originali per audit,
non è da importare come nuovi nodi.

1. In function 17 (`ea8307d48995ca93`) sostituire tutto il codice con
   [function17.js](function17.js), compreso il return finale. Restano 24 uscite
   e tutti i collegamenti attuali.
2. Nel tab contenente i nodi RM TV importare **solo**
   [publisher-only.json](publisher-only.json), nel tab corrente. È un nuovo
   mqtt out `TV semantic receipt only`, broker home .20, topic vuoto, QoS/retain
   da msg. Riutilizzare la configurazione broker esistente.
3. La funzione dopo RM TV ON, ID `8f921a5e.1766f`, riceve
   [report-on.js](report-on.js). Portare le uscite da 1 a 2. Conservare la prima
   verso `7ef048a0.7bf07`; collegare la seconda al nuovo publisher.
4. La funzione dopo RM TV OFF, ID `ed8c7e3e.e2c32`, riceve
   [report-off.js](report-off.js). Stessi collegamenti: prima originale, seconda
   al publisher nuovo.
5. Deploy dei nodi modificati. Router e Smart Semantic Observer non cambiano.

## Verifica

Dalla dashboard Android alternare OFF e ON della TV. Sottoscrivere sul broker .20:
`zara/interface/semantic/v1/rm_receipts/lights.tv.power/stat`.
Atteso actor=utente, requested_state coerente, stage=send_invoked, command_id
uguale al relativo dispatched_commands. Stato dashboard e topic legacy devono
continuare come prima. Un comando scartato dall'RBE non produce un nuovo receipt.

Se dispatched arriva e receipt no, verificare metainformazione in ingresso RM,
output RM e seconde uscite delle due funzioni. Non interpretare l'assenza come
prova di mancata accensione. Il retained rappresenta l'ultimo riscontro software,
non lo stato corrente; controllare ID e orario durante le prove.

## Rollback e test

Ripristinare le tre funzioni da rollback.json, riportare i due report a una
uscita, rimuovere i nuovi fili e il solo publisher `aa764cb4ce617210`; deploy.
Non cancellare context o topic dei dispositivi. I retained semantici possono
restare e non attestano attività successiva al rollback.

Quattro test isolati passati: ON/OFF con ID condiviso, payload legacy,
RBE/altri dispositivi, evidenze mancanti e wiring. La risposta RM è simulata;
non sono prove hardware o runtime. Nessun deploy o comando ai dispositivi eseguito.

```sh
node --test backend/nodered/semantic_bridge/rm_receipt/test.js
python3 backend/nodered/semantic_bridge/rm_receipt/build.py /percorso/flow-base.json
```

## Evoluzione

L'utente ha validato su runtime TV ON con stage send_invoked. Disponibile la
[successiva estensione a libreria e lettura](../rm_extension/README.md), distribuita il 22 settembre 2026 e verificata dall’utente in ON/OFF; non amplia la conferma a stato fisico o automazioni.


Aggiornamento documentale: le quattro prove ON/OFF libreria/lettura sono
registrate con command_id, tempi e limiti nel
[verbale dell’estensione](../rm_extension/README.md#prove-runtime-onoff-concluse--22-settembre-2026).
La verifica è del riscontro software RM, non dello stato fisico delle lampade.
