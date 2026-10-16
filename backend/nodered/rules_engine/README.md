# RULES-ENGINE.1 — motore delle regole naturali (fase H1)

Progetto: [`docs/AI_MODE/REGOLE_NATURALI.md`](../../../docs/AI_MODE/REGOLE_NATURALI.md). Nuovo tab
Node-RED «Regole (DomoPi)» sul `.20`, nessun nodo esistente modificato, router non toccato.

- `rules.js` (function «motore regole»): trigger a soglia (puffer, esterna, stanze, batteria, FV,
  consumo) o a orario con giorni; al massimo 2 condizioni (soglia, finestra oraria, modalità del
  caminetto); una azione ammessa (luci, scene, termostati 16–22 °C, caminetto con i limiti di AI
  Smart, profilo di riscaldamento); durata `once` (predefinita) / `until` / `always`; attesa minima
  (30 min); riarmo con margine; dati vecchi = nessun intervento; massimo 10 regole.
- Ogni regola nuova è **in prova 24 h** (annuncia `would_fire`, non invia e non consuma `once`); poi
  si attiva da sola (o con `activate`). Se la condizione è già vera, interviene subito.
- Il **motore parte in shadow**: nessun comando finché non si pubblica `active` su
  `zara/interface/rules/engine/cmd`.
- Comandi: `zara/interface/rules/{create,delete,activate,pause,resume,engine}/cmd` (create: JSON
  della regola; gli altri: id della regola). Stato: `zara/interface/rules/list/stat` (retained),
  `zara/interface/rules/last_event/stat` (retained), eventi su `zara/interface/rules/events` (per le
  notifiche, fase G). Registro: `/home/pi/AI_climate/rules-AAAA-MM-GG.jsonl`.
- Stato delle regole nel context store `file` (sopravvive ai riavvii; nessun intervento ripetuto).

Test: `TZ=Europe/Rome node --test test.js` (11 casi). Deploy: `deploy.py prepare`, poi
`apply BACKUP`, `rollback BACKUP` (rimuove il tab).
