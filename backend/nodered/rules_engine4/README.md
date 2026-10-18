# RULES-ENGINE.4 — azioni VMC per i comandi a tempo (fase T2)

Progetto: [`docs/AI_MODE/COMANDI_A_TEMPO.md`](../../../docs/AI_MODE/COMANDI_A_TEMPO.md).

- Nuove azioni nel motore ([`../rules_engine/rules.js`](../rules_engine/rules.js)): `set_vmc_speed`
  (velocità secca 1–4), `set_vmc_override` (priorità manuale fino a un momento, gestita da
  [`../ai_vmc_override`](../ai_vmc_override/README.md)), `set_vmc_auto` (torna ad AI Climate).
- Cambia solo il codice del nodo del motore; richiede AI-CLIMATE-VMC-OVERRIDE.1.

```sh
python3 build.py <flows-export.json> && node --test test.js
TZ=Europe/Rome node --test ../rules_engine/test.js
python3 deploy.py prepare | apply BACKUP | rollback BACKUP
```
