# ROUTER-HEARTBEAT.1 — Heartbeat dei valori energetici sotto soglia

Data: 10 ottobre 2026. Prerequisito della fase 3.4 (flussi energia in stile WallBox).

Il Router applica a FV, casa, rete e batteria una soglia di 15 W (SOC 0,5%):
le letture che cambiano meno non vengono pubblicate e, con ROUTER-DEDUP.1, non
passano nemmeno dall'heartbeat. Di notte il FV fermo a 0 W non generava messaggi
per ore e l'app non poteva distinguere "fermo" da "dati assenti".

Modifica (solo `emitState()` del Digital Twin Router, base runtime `d698fbda`):
se una lettura **energetica** (`zara/interface/energy/…`) arriva ma resta sotto
soglia, l'ultimo valore pubblicato viene ripubblicato al massimo ogni 60 s
(retained, QoS 1). Se la sorgente tace non si pubblica nulla: dopo 3 minuti
l'app mostra il valore come non aggiornato. Nessun effetto sugli altri domini.

File: `build.py`, `update.json`, `rollback.json`, `manifest.json`, `test.js`
(6 test, `node --test test.js`), `deploy.py` (`prepare`, `apply BACKUP`,
`rollback BACKUP`, da eseguire su `.20`).
