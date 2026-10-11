# ROUTER-CLEANUP.2 — Ricostruzione delle tabelle del router

Data: 11 ottobre 2026. Completa ROUTER-CLEANUP.1.

Il Digital Twin Router costruisce le tabelle comando→legacy e legacy→stato una sola
volta e le tiene nel context del nodo; le ricostruisce solo quando cambia la chiave
`router_initialized_vNNN`. ROUTER-CLEANUP.1 non l'aveva cambiata: dopo il deploy il
router continuava a usare le regole precedenti (prova reale ore 16:19:42:
`abilitata 1` → `enabled/stat false`). Questa patch porta la chiave da `v118` a `v119`.

Regola per le patch future al registro del router: incrementare sempre la chiave.

File: `build.py`, `update.json`, `rollback.json`, `manifest.json`, `test.js` (3 test,
riproducono il context già inizializzato), `deploy.py`.
