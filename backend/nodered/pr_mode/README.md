# PR-MODE.1 — Modalità e prontezza del Predictive Reserve visibili all'app

Data: 10 ottobre 2026. Fase 2c, punto 1. Decisione dell'operatore: esporre in
sola lettura `operating_mode` e `ready`.

## Perché

L'app mostrava solo `control_enabled`. L'effetto reale del toggle dipende però da
`pr_operating_mode` (impostabile solo da inject nell'editor) e dalla prontezza del
modello (`ready` = champion caricato e KNN pronto):

| control | mode | ready | Avvio notturno OFF → NIGHT_DRY |
|---|---|---|---|
| OFF | qualsiasi | qualsiasi | logica legacy, PR ignorato |
| ON | SHADOW | qualsiasi | **sempre negato** (`PR_NOT_AUTHORIZED`) |
| ON | ACTIVE | false | **sempre negato** (`PR_NOT_READY`) |
| ON | ACTIVE | true | solo `eco26dry`, decisione < 120 s, energia favorevole |

Stato letto dal contesto del flow il 10/10/2026: mode `ACTIVE`, control `false`.

## Modifica (2 nodi, nessun nodo del tab PR toccato)

- **PR Control State Adapter** (`c67ee62775ea71f8`): oltre a
  `casa/clima/stat/predictive_reserve_control_enabled` pubblica, retained QoS 1,
  `casa/clima/stat/predictive_reserve_operating_mode` (`SHADOW`/`ACTIVE`) e
  `casa/clima/stat/predictive_reserve_ready` (booleano), letti dallo stato che
  `PR Operational State` già invia. Valori non validi non vengono pubblicati.
- **Digital Twin Router** (`5a7c1d360710ca21`, base: ROUTER-DEDUP.1): due voci di
  sola lettura, senza `int_cmd`:
  `zara/interface/predictive_reserve/operating_mode/stat` e
  `zara/interface/predictive_reserve/ready/stat`. Guardia di inizializzazione
  `router_initialized_v117` → `v118` per ricostruire le tabelle di routing
  memorizzate nel contesto del nodo (altrimenti le nuove voci verrebbero ignorate).

## File

`build.py` (base: runtime live rev `2e138ebb…`), `update.json`, `rollback.json`,
`manifest.json`, `test.js` (6 test, `node --test test.js`), `deploy.py`.

## Deploy (su `.20`)

```sh
python3 deploy.py prepare             # verifica i 2 nodi, backup completo
python3 deploy.py apply <BACKUP_DIR>  # deploy 'nodes' + verifica
python3 deploy.py bootstrap           # opzionale: preme "PR Operational State Bootstrap"
python3 deploy.py rollback <BACKUP_DIR>
```

`bootstrap` ripubblica subito lo stato (come all'avvio di Node-RED): non cambia
modalità né toggle, ricalcola il gate con gli stessi valori. Senza bootstrap i
nuovi topic compaiono alla prossima emissione di PR Operational State.
