# AI-CLIMATE-AC-STATE.1 — stato del condizionatore per l'app

Richiesta dell'utente dopo la prova dal vivo di T4 (30/10/2026): il condizionatore si comanda solo a
infrarossi e non ha display, quindi non si vede cosa sta facendo. Formato e quattro scelte concordati con
l'utente lo stesso giorno ([`docs/AI_MODE/COMANDI_A_TEMPO.md`](../../../docs/AI_MODE/COMANDI_A_TEMPO.md)).

Topic retained `zara/interface/climate/ac/state/stat`, schema `domopi.ac_state.v1`, scritto dalla funzione di
AI Climate ([`state.js`](state.js)):

| Campo | Contenuto |
|---|---|
| `ai_enabled` | interruttore di AI Climate |
| `current` | `mode` (`off`, `heat`, `cool`, `dry`, `standby`), `setpoint`, ultimo comando IR `code`, `since`, `by` (`user`, `ai`, `watchdog`) |
| `confirm` | riscontro dal consumo `power_w`: `off` (sotto 30 W), `stopping` (spento ma ancora sopra 30 W), `starting` (acceso, sotto 200 W: ventola), `running` (da 200 W: compressore), `no_power` (acceso, sotto 10 W dopo 6 minuti) |
| `target` | stato richiesto e trattenuto da una salvaguardia: `mode`, `setpoint`, `by` (`user` con `until`, oppure `ai`); `null` senza transizione |
| `guard` | salvaguardia in corso: `type` `min_run` o `min_off` (tempi del compressore), `until` (ora esatta di fine, per il conto alla rovescia), `minutes` |
| `alarms` | `ON_NO_POWER`, `WATCHDOG_RESTART` (per 30 minuti), `BATTERY_CLOSED_MANUAL` (per 60 minuti), `WATCHDOG_SUSPENDED` (finché AI Climate non viene riabilitato) |
| `reason` | codice tecnico di AI Climate (solo per «dettagli tecnici») |
| `context` | `surplus_w`, `soc`, `temp`, `humidex`, `vmc` |
| `ts` | ora della pubblicazione |

Solo codici: i testi sono dell'app. Pubblicato quando cambia stato, riscontro, richiesta, salvaguardia,
allarmi o motivo; consumo (oltre 50 W) e valori di casa al più ogni 30 s.

`target` delle decisioni automatiche (scelta 4): la funzione ricorda la richiesta trattenuta da un tempo
del compressore in cinque punti (caldo diurno e notturno, spegnimento invernale, raffrescamento diurno,
spegnimento o standby estivo). Non coperta la deumidifica notturna (il comando lo sceglie il Predictive
Reserve). Con una priorità manuale attiva vale solo la sua richiesta.

Le decisioni, i tempi e i comandi IR di AI Climate **non cambiano**: la patch aggiunge due variabili, sei
righe `else`, il blocco di riepilogo e una settima uscita collegata a un nodo «mqtt out» retained
(«stato condizionatore (app)»). Router invariato.

```sh
python3 build.py <flows-export.json> && TZ=Europe/Rome node --test test.js     # 7 casi
python3 deploy.py prepare | apply BACKUP | rollback BACKUP
```
