# RULES-ENGINE.3 — comandi a tempo (fase T1)

Progetto: [`docs/AI_MODE/COMANDI_A_TEMPO.md`](../../../docs/AI_MODE/COMANDI_A_TEMPO.md).

- Motore delle regole (nodo `RULES-ENGINE.1/logic`): nuovo sorgente di
  [`../rules_engine/rules.js`](../rules_engine/rules.js) — trigger `at` (orario con data, da 15 minuti
  fa a 48 ore avanti, sempre «una volta»), `start_active` solo con `at`, comando in ritardo di oltre
  15 minuti chiuso come saltato, regole `at` chiuse tolte dall'elenco dopo 24 ore.
- Notifiche (nodo `RULES-ENGINE.2/format`): [`../rules_notify/ntfy.js`](../rules_notify/ntfy.js) —
  «Comando programmato … Attivo subito.» per i comandi a tempo.
- Cambia solo il codice dei due nodi; fili e altri nodi invariati.

```sh
python3 build.py <flows-export.json> && node --test test.js
TZ=Europe/Rome node --test ../rules_engine/test.js && node --test ../rules_notify/test.js
python3 deploy.py prepare | apply BACKUP | rollback BACKUP
```
