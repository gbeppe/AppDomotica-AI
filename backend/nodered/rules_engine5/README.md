# RULES-ENGINE.5 — azioni del condizionatore per i comandi a tempo (fase T4)

Progetto: [`docs/AI_MODE/COMANDI_A_TEMPO.md`](../../../docs/AI_MODE/COMANDI_A_TEMPO.md).

- Nuove azioni nel motore ([`../rules_engine/rules.js`](../rules_engine/rules.js)): `set_ac_override`
  (`mode` off/heat/cool/dry, `setpoint` per heat 16–22, cool 22–28, dry 23–25, `until`; pubblica
  `zara/interface/climate/ac/override/cmd`, gestito da [`../ai_ac_override`](../ai_ac_override/README.md))
  e `set_ac_auto` (torna ad AI Climate).
- Al momento dell'esecuzione la fine deve essere nel futuro e al più 6 ore dopo (decisione dell'utente),
  altrimenti la regola si chiude con il motivo («comando del condizionatore oltre 6 ore»).
- Cambia solo il codice del nodo del motore; richiede AI-CLIMATE-AC-OVERRIDE.1.

```sh
python3 build.py <flows-export.json> && node --test test.js
TZ=Europe/Rome node --test ../rules_engine/test.js      # 16 casi
python3 deploy.py prepare | apply BACKUP | rollback BACKUP
```
