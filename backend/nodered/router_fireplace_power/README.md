# ROUTER-FIREPLACE-MODE.2 — caminetto manuale dall'app

Base: ROUTER-FIREPLACE-MODE.1 (`v120`). Applicata il 14/10/2026 (backup
`/home/pi/AI_backups/nodered/20261014T131008Z-before-router-fireplace-power`).

- **Pulsante acceso/spento:** il router scriveva `1`/`0` su `zara/domotics/palazzetti/acceso` del
  broker di `.20`, che è un topic di **stato** (lo scrive `.15` dopo un'accensione): il pulsante non
  comandava nulla. Ora invia il comando manuale di `.15`, `alexa/caminetto` = `on`/`off` (broker
  `emon`). Lo stato resta letto da `.../acceso`, accettando anche `on` minuscolo come lo scrive `.15`.
- **Modalità "Manuale" → `manuale`:** la logica `MAIN` di `.15` non esegue automatismi per una
  modalità diversa da `inverno`/`conCaldaia`/`sanitaria` ma accetta acceso/spento manuali (che
  invece ignora con `disattivato`). L'app abilita il pulsante solo in "Manuale".
- Chiave `router_initialized_v120` → `v121`.

Comportamento di `.15` da ricordare: un "on" manuale accende con livello 3, poi "on" dopo 30 s;
viene ignorato se il caminetto è già acceso dall'automatismo o se `.15` non ha avuto risposta
dalla scheda nell'ultimo controllo (ogni 15 minuti).

Test: `node --test test.js` (6 casi).
