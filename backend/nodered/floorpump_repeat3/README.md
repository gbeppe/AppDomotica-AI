# FLOORPUMP-REPEAT.3 — `.15`: funzione della pompa senza riferimenti agli orari del caminetto

Host **`.15`**, function `1572e937.c23a67` (base FLOORPUMP-REPEAT.2), riscritta da `pump.js`.

- La decisione non cambia: pompa abilitata + puffer alto (accesa sopra 35 °C, spenta sotto 30 °C,
  ultimo comando ripetuto in mezzo). I test confrontano la nuova funzione con la precedente su una
  sequenza mista, con pompa abilitata e disabilitata.
- Tolti gli orari del caminetto e il calcolo dell'orario di stop (usati solo da codice commentato;
  nessun altro nodo li legge).
- La modalità del caminetto resta memorizzata **solo con la pompa abilitata**, come prima: la
  leggono i vecchi timer dei termostati (tab TASMOTAs), che altrimenti si risveglierebbero con
  valori vecchi in conflitto con HEATING-PROFILE.1.
- Applicata il 16/10/2026 alle 17:31 (backup sul `.15`:
  `/home/pi/AI_backups/nodered/20261016T153106Z-before-floorpump-repeat3`).

Test: `node --test test.js` (4 casi).
