# ROUTER-CLEANUP.1 — Correzioni e pulizia del Digital Twin Bridge

Data: 11 ottobre 2026. Esito della verifica della scheda "Digital Twin Bridge".

Modifiche (22 nodi modificati, 7 rimossi, nessuno aggiunto):

1. **Stati booleani da sorgenti "1"/"0"** — il nodo MQTT del router legge "1" come numero
   (auto-detect) e 17 regole `stat_trans_func` accettavano solo il testo "1": dopo un
   comando dall'app la pompa pavimento risultava `false` (prova reale del 11/10 ore 16:03).
   Le 17 regole accettano ora anche il numero 1. Le sorgenti che scrivono `true`/`false`
   (impostazioni) non cambiano.
2. **Dispositivi non più presenti** (conferma dell'utente): rimosse dal registro del router
   le voci `lights.hifi.power` e `heating.heat_pump.{enabled,solar_divert,target_temperature}`
   (sostituite da un commento); rimossa la lampada HiFi dalla mappa dell'osservatore
   semantico passivo e i suoi 4 nodi di ingresso (`stat/tasmota_86AD14/POWER`, LWT Offline).
3. **Nodi inutili**: vecchia copia scollegata del "Digital Twin Router" (`d6fd86d9…`), due
   ingressi MQTT disattivati e duplicati (`zara/interface/#`, `cmnd/#`).
4. **Nomi**: le 20 uscite verso "broadlink lights" chiamate tutte "Trigger Shelf Lamp
   ON/OFF" prendono il nome dell'azione collegata (es. "→ pool pump on").

Non toccati (decisione dell'utente o da analizzare a parte): modulazione caldaia,
humidex esterno, `climate/ai_enabling`, doppio percorso WP-C2 dei dati clima,
anti-ripetizione della "function 17".

File: `build.py`, `update.json`, `rollback.json`, `manifest.json`, `test.js` (6 test,
`node --test test.js`), `deploy.py` (`prepare`, `apply BACKUP`, `rollback BACKUP`, da
eseguire su `.20`; il rollback ripristina anche i nodi rimossi).
