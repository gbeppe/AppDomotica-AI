# HEATING-PROFILE.1 — profilo di riscaldamento e timer dei termostati (shadow)

Progetto: [`docs/AI_MODE/PROFILO_RISCALDAMENTO.md`](../../../docs/AI_MODE/PROFILO_RISCALDAMENTO.md).

Nuovo tab Node-RED «Profilo riscaldamento (DomoPi)» sul `.20`, nessun nodo esistente modificato,
router non toccato (nessun incremento di `router_initialized_vNNN`).

- `heating.js` (function «profilo e timer»): profilo Spento / Ridotto / Pieno, automatico dalla
  media esterna delle ultime 72 ore (Pieno < 10 °C, uscita > 11 °C; Spento > 16 °C, uscita
  < 15 °C; servono 48 ore di dati) oppure forzato. In fascia il termostato riceve il massimo,
  fuori il minimo, solo a un evento (inizio/fine fascia, cambio profilo, cambio min/max).
- Ingressi: `zara/interface/env/outdoor/temperature/stat`, `zara/interface/climate/+/+/stat`,
  `zara/interface/heating/{profile,timer}/mode/cmd`, un tick al minuto.
- Uscite: setpoint su `zara/interface/climate/thermostat_X/target_temperature/cmd` **solo in
  active**; stato retained su `zara/interface/heating/{profile/stat,profile/mode/stat,timer/mode/stat,timer/plan/stat}`;
  registro `/home/pi/AI_climate/heating-YYYY-MM-DD.jsonl` (decisioni, cambi di profilo,
  setpoint cambiati dall'utente).
- Stato persistente nel context store `file`. Parte in **shadow**: per passare ad active,
  dopo il confronto e con conferma, pubblicare `active` su `zara/interface/heating/timer/mode/cmd`.

Applicata in shadow il 16/10/2026 alle 12:44 (backup `/home/pi/AI_backups/nodered/20261016T104325Z-before-heating-profile`).

Test: `TZ=Europe/Rome node --test test.js` (9 casi). Deploy: `deploy.py prepare`, poi
`apply BACKUP`, `rollback BACKUP` (rimuove il tab).
