# ROUTER-FLOOR-PUMP.1 — abilitazione pompa pavimento sul broker di `.15`

Base: ROUTER-FIREPLACE-RETAIN.1 (`v122`).

- **Problema (analisi del 16/10/2026):** l'interruttore dell'app «pompa pavimento abilitata»
  passava dal router a `zara/domotics/pompapavimento/abilitata` sul broker del `.20`, mentre la
  logica della pompa sul `.15` (tab «Sheet 1») ascolta il proprio broker: il comando non arrivava
  mai e l'app mostrava solo l'eco del proprio comando. Stesso difetto della modalità del
  caminetto (ROUTER-FIREPLACE-MODE).
- **Correzione:** la voce `heating.floor_pump.enabled` usa `broker: "emon"` e `leg_retain: true`
  (il `.15` ritrova il valore dopo un riavvio; lo salva anche nel context `file`). Lo stato
  «in funzione» (`zara/domotics/floorpumpstate`) arrivava già dal `.15`.
- Chiave `router_initialized_v122` → `v123`. L'installazione non invia comandi.

Logica del `.15`, invariata: con la pompa abilitata si accende con puffer alto > 35 °C e si
spegne sotto 30 °C (un cambio al minuto al massimo), comando a `/esp8266/floorPump/switch1/enable`;
«disabilita» spegne subito. **Abilitare con il puffer sopra 35 °C avvia la pompa.**

Test: `node --test test.js` (5 casi).
