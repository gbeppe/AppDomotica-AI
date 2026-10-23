# TASMOTA-TIMERS-OFF.1 — `.15`: vecchio timer del termostato del bagno disattivato

Host **`.15`**, tab «TASMOTAs», nodo `timerswitch` `7e69383.2658ac8` (`bathroom_schedule`).

- Il vecchio timer mandava **11 °C** direttamente alla Tasmota del bagno (`cmnd/tasmota_F294D7/TEMPTARGETSET`,
  saltando il router) alle 04:00 e alle 06:00. Con il timer di HEATING-PROFILE.1 attivo sul `.20` avrebbe
  interrotto la fascia del bagno 05:05–07:00 (profilo Pieno, giorni feriali).
- Il timer del soggiorno (`living_schedule`) era già disattivato; ora lo sono entrambi. Nessun nodo tolto:
  il rollback riattiva il nodo.
- Prerequisito per passare il timer del `.20` ad active (`docs/AI_MODE/PROFILO_RISCALDAMENTO.md`).

Deploy con l'Admin API locale del `.15` (token in `~/.node-red/.admin-token.json`, creato dall'utente, 7 giorni):
`deploy.py prepare`, `apply BACKUP`, `rollback BACKUP`. Test: `node --test test.js`.
