# ROUTER-DEDUP.1 — Stati Digital Twin pubblicati solo se cambiano

Data: 10 ottobre 2026. Decisione dell'operatore: opzione A dell'analisi del
traffico MQTT (fase 2c).

## Problema misurato (sola lettura, broker `.20`)

- Traffico `zara/interface/#`: circa 20 msg/s; **509 messaggi su 794 (64%)** in
  40 s erano ripetizioni identiche dello stesso topic entro 0,5 s.
- Causa: il controllore clima `61992c8879c11f66` viene eseguito a ogni messaggio
  dei suoi 13 ingressi (5 topic Powerwall quasi simultanei, humidex, temperature,
  potenza AC…) e ripubblica ogni volta `casa/clima/stato_completo`; il Router lo
  scompone in circa 45 topic. Esempio osservato: 6 pubblicazioni in 70 ms.
- Il Router è corretto (1 ingresso → 1 blocco); non è un loop.

## Modifica

Solo la Function `Digital Twin Router` (`5a7c1d360710ca21`), solo `emitState()`:
un topic `zara/interface/.../stat` viene pubblicato se il valore cambia, oppure
come heartbeat se l'ultimo invio identico ha 60 s o più. Stato in
`context.lastPublishedStates` (memoria del nodo; dopo un riavvio si ripubblica
tutto alla prima occorrenza). `lastSensorValues` e le soglie esistenti
(`stat_trans_func`, es. FV 15 W) restano invariate. Wiring, uscite, comandi,
scene, evidenze semantiche e retained/QoS invariati.

Non toccati: controllore clima, Predictive Reserve (legge
`casa/clima/stato_completo` direttamente), app Android, backend House AI.

## Effetto atteso

Riduzione del traffico verso l'app di circa l'80%; registro "Diagnosi" senza
doppioni. `received_at_ms` lato app al massimo vecchio di 60 s per valori
invariati (nessun consumer usa soglie di freschezza sull'ora di ricezione:
verificato su Android e `backend/house_ai`).

## File

| File | Ruolo |
|---|---|
| `build.py` | Genera update/rollback/manifest da un export dei flow; fallisce se la base cambia |
| `update.json` / `rollback.json` | Router dopo / prima (base: export live del 10/10/2026) |
| `manifest.json` | ID, heartbeat, SHA-256 della funzione prima/dopo |
| `test.js` | 7 test offline (`node --test test.js`) |
| `deploy.py` | Da eseguire su `.20`: `prepare`, `apply BACKUP`, `rollback BACKUP` |

## Deploy (operatore, su `.20`)

```sh
cd <copia di questa cartella su .20>
python3 deploy.py prepare            # verifica runtime = rollback.json, backup completo
python3 deploy.py apply <BACKUP_DIR> # deploy 'nodes' del solo Router + verifica
```

Rollback: `python3 deploy.py rollback <BACKUP_DIR>` (verifica che il Router sia
quello patchato e ripristina la funzione originale). Il backup contiene anche i
file di progetto Node-RED.

## Verifica dopo il deploy

Ripetere la misura in sola lettura (40 s su `zara/interface/#`): ripetizioni
identiche entro 0,5 s ≈ 0; app → Diagnosi: registro senza doppioni, picco basso.
