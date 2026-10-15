# LIGHTS-ECHO-GUARD.1 — niente ping-pong accendi/spegni sulle luci IR

Nodo modificato: "function 17" (tab Digital Twin Bridge), che ascolta `zara/domotics/lights/+` e
smista acceso/spento verso le catene Broadlink.

- **Problema (15/10/2026, 21:27:58):** spegnendo le luci una dopo l'altra dall'app, con un doppio
  tocco su Soggiorno Main, la lampada del soggiorno ha alternato accendi/spegni per oltre 2 minuti
  (85 invii IR), fermata solo spegnendo l'emettitore. Ogni catena IR (primo codice, 5 s, secondo
  codice) pubblica lo stato finale sullo stesso topic che la function ascolta: la function prendeva
  l'eco per un comando nuovo. Il suo anti-loop scartava solo un valore uguale al precedente, quindi
  con un «on» e un «off» in volo entro ~6 s le due eco si rilanciavano a vicenda.
- **Correzione:** ogni comando smistato è ricordato per 10 s come eco attesa; un messaggio senza
  `_semantic_command` (arrivato via MQTT, non dal router) uguale a un'eco attesa viene consumato e
  non rismistato. I comandi dell'app passano sempre. Ultimo comando vince.
- Limite noto: un comando via MQTT (dashboard, altre automazioni) identico a un comando smistato
  meno di 10 s prima, e arrivato prima della sua eco, viene preso per l'eco.
- Non tocca il registro del Digital Twin Router: nessun incremento di `router_initialized_vNNN`.

Applicata il 15/10/2026 alle 23:29 (backup `/home/pi/AI_backups/nodered/20261015T212857Z-before-lights-echo-guard`).

Test: `node --test test.js` (7 casi). Deploy: `deploy.py prepare`, poi `apply BACKUP`, `rollback BACKUP`.
