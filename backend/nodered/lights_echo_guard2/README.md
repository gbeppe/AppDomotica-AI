# LIGHTS-ECHO-GUARD.2 — un solo invio radio anche dalla dashboard

Nodi modificati: "function 17" (tab Digital Twin Bridge) e le 18 funzioni che, dopo le catene
Broadlink, pubblicano lo stato su `zara/domotics/lights/<luce>` (shelflamp, livinglamp, tvlamp,
readinglight, prolunga, poolpump, poolskimmer, poolfloor, poollight; acceso e spento).

- **Problema (23/10/2026):** un tocco sulla luce TV nella dashboard Node-RED mandava «3on» due
  volte (09:39:16 e 09:39:18). La catena pubblica lo stato sullo stesso topic che "function 17"
  ascolta; LIGHTS-ECHO-GUARD.1 riconosceva come eco solo i comandi smistati da "function 17", quindi
  l'eco di un comando partito dalla dashboard (o da Alexa, o da una programmazione) veniva rispedito.
- **Correzione:** ogni funzione che pubblica lo stato annuncia l'eco nel registro globale
  `lightsPendingEchoes` (topic, valore, ora); "function 17" consuma un annuncio corrispondente degli
  ultimi 10 s **prima** del suo anti-loop e aggiorna la memoria dell'anti-loop. I comandi del router
  (`_semantic_command`) non sono mai presi per eco. Tolta la contabilità della .1.
- Limite: un comando MQTT esterno identico a uno stato appena pubblicato (entro 10 s) è preso per eco;
  lo stato è comunque già quello.
- Non tocca il registro del Digital Twin Router: nessun incremento di `router_initialized_vNNN`.

Generata da `build.py <export runtime>`. Test: `node --test test.js` (8 casi). Deploy: `deploy.py prepare`,
poi `apply BACKUP`, `rollback BACKUP`.
