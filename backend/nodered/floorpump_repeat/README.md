# FLOORPUMP-REPEAT.1 — `.15`: la pompa del pavimento ripete l'ultimo comando nella fascia 30–35 °C

Host: **`.15` (emonpi)**, progetto Node-RED `emonpi`, tab «Sheet 1», function `1572e937.c23a67`.

- **Perché:** la logica della pompa invia `1` con puffer alto > 35 °C e `0` sotto 30 °C, e nella
  fascia 30–35 °C non invia nulla. Il firmware `floorpump_v2` spegne la pompa dopo 15 minuti senza
  comandi (scelta dell'utente del 16/10/2026): nella fascia la pompa si fermerebbe a 34 °C invece
  che a 30 °C.
- **Modifica:** nella fascia la function ripete l'ultimo comando (memoria del nodo); con la pompa
  disabilitata registra `0`. Soglie, ritmo (un messaggio al minuto al massimo) e variabili usate
  dalla logica del caminetto restano invariati.
- Da applicare **prima** di caricare `floorpump_v2`; innocua anche con il firmware attuale.

Deploy sul `.15` con l'Admin API locale: serve un token in `~/.node-red/.admin-token.json`
(creato dall'utente con la password dell'editor, scade dopo 7 giorni). Poi `deploy.py prepare`,
`apply BACKUP`, `rollback BACKUP`.

Applicata il 16/10/2026 alle 13:21 (backup sul `.15`: `/home/pi/AI_backups/nodered/20261016T112048Z-before-floorpump-repeat`).

Test: `node --test test.js` (5 casi).
