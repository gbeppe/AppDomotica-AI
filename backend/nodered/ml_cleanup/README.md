# AI-CLIMATE-CLEANUP.1 — Copie scollegate del "Machine Learning domotico"

Data: 11 ottobre 2026. Scheda Node-RED "AI climate".

Nella scheda c'erano 7 nodi "Machine Learning domotico locale ad alta precisione" (36–60 KB
di codice ciascuno). Solo `61992c8879c11f66`, alimentato dallo switch dei sensori, è
collegato e lavora; le altre 6 copie non avevano né ingressi né uscite (avanzi di versioni
precedenti). La patch rimuove le 6 copie; nessun nodo modificato o aggiunto.

Decisioni dell'utente nella stessa analisi: scheda "Amazon Echo" lasciata (Alexa non usata),
nodi `tasmota_F294D7` / `shelly1-98F4ABF294D7` lasciati, 55 nodi disattivati lasciati come
promemoria.

File: `build.py`, `update.json`, `rollback.json` (le 6 copie, per il ripristino),
`manifest.json`, `test.js`, `deploy.py` (`prepare`, `apply BACKUP`, `rollback BACKUP`).
