# ROUTER-FIREPLACE-RETAIN.1 — impostazioni caminetto retained sul broker di .15

Base: ROUTER-FIREPLACE-MODE.2 (`v121`). Non ancora applicata.

- **Problema (test del 14/10/2026):** il router inoltrava modalità, orari e potenza automatica a
  `.15` senza retain (nodo "Legacy Cmd (Emon Broker)" con retain `false`). `.15` usava i nuovi
  valori, ma il broker di `.15` conservava quelli vecchi (17:00/19:00). Quando il Node-RED di `.15`
  rilegge i topic retained (riavvio o nuova sottoscrizione) torna ai valori vecchi, mentre l'app
  mostra ancora i nuovi.
- **Correzione:** le voci `fireplace.main.mode`, `start_time`, `stop_time` e `auto_power` hanno
  `leg_retain: true` e solo queste rotte impostano `msg.retain`. Il nodo emon passa da retain
  `false` a vuoto, perché in Node-RED 5 l'impostazione del nodo prevale su `msg.retain`. Acceso,
  spento e livello (`alexa/caminetto`) restano comandi singoli, mai retained.
- Chiave `router_initialized_v121` → `v122`.

Dopo l'applicazione i valori retained di `.15` si aggiornano al primo nuovo invio dall'app
(modalità, orari, potenza automatica).

Test: `node --test test.js` (6 casi).
