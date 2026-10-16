# FLOORPUMP-REPEAT.2 — `.15`: la pompa non si blocca senza l'ora di spegnimento del caminetto

Host **`.15`**, function `1572e937.c23a67` (base FLOORPUMP-REPEAT.1).

- **Problema (prova del 16/10/2026, 14:22):** con la pompa abilitata la function andava in errore
  a ogni lettura del puffer (`TypeError: Cannot read property 'split' of undefined`) e non inviava
  nulla all'ESP. Spezza l'ora di spegnimento del caminetto (`palazzettiOraSpegnimento`), memorizzata
  solo mentre la pompa è abilitata: dopo un riavvio del `.15` con la pompa disabilitata non esiste.
  L'orario calcolato non è usato (il codice che lo usava è commentato).
- **Modifica:** una riga; senza l'ora di spegnimento la function prosegue.
- Applicata il 16/10/2026 alle 14:25 (backup sul `.15`:
  `/home/pi/AI_backups/nodered/20261016T122530Z-before-floorpump-repeat2`); alle 14:25:35 primo
  comando `1` all'ESP.

Prossimo passo proposto: FLOORPUMP-REPEAT.3, togliere dalla function ogni riferimento agli orari
del caminetto (la decisione dipende solo da abilitazione e puffer).

Test: `node --test test.js` (4 casi).
