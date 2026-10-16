# HEATING-PROFILE.2 — piano con il prossimo cambio e correzioni dell'utente (per l'app)

Base: HEATING-PROFILE.1 (shadow). Sostituisce la function «profilo e timer» con
[`../heating_profile/heating.js`](../heating_profile/heating.js), fonte unica:

- `zara/interface/heating/timer/plan/stat`: per termostato anche `min`, `max` e `next`
  (`day_offset`, `at`, `inside`, `value`), il prossimo cambio di fascia entro una settimana;
- nuovo `zara/interface/heating/profile/learning/stat` (retained): ultime 10 correzioni di setpoint
  dell'utente, la più recente prima, e `learning_active: false` (apprendimento non ancora attivo).

Decisioni, eventi e modalità shadow invariati. Test: `TZ=Europe/Rome node --test ../heating_profile/test.js`.
