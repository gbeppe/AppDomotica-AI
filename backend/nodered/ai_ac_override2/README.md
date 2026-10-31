# AI-CLIMATE-AC-OVERRIDE.2 — priorità manuale anche con AI Climate spento, opzione «solo sole»

Strumenti 1 e 2 della prova invernale ([`docs/AI_MODE/AI_CLIMATE_INVERNO.md`](../../../docs/AI_MODE/AI_CLIMATE_INVERNO.md),
sezione 8), decisioni dell'utente del 31/10/2026.

1. **Priorità manuale con AI Climate disabilitato.** Prima il blocco della priorità stava nel ramo «abilitato», e
   con l'interruttore spento non aveva effetto. Ora quel ramo gira anche se c'è una priorità valida o uno stato
   `MANUALE_*` da chiudere; la logica automatica resta saltata (`!bypassRoutine && aiAbilitata`). L'interruttore ferma solo
   l'automatico: una priorità in corso continua fino alla scadenza, «Spegni» e «Auto» la chiudono prima. VMC al minimo.
2. **`"solar_only": true`** nel comando (`zara/interface/climate/ac/override/cmd`, non vale per `off`; riportato in
   `…/override/stat`): con più di 100 W da batteria o rete per 15 minuti di fila la priorità si chiude (motivo
   `SOLO_SOLE_CHIUDE_MANUALE`, `closed_reason` `SOLO_SOLE_PRELIEVO_15_MIN`, avviso ntfy, allarme `SOLAR_ONLY_CLOSED` nello stato per
   l'app); il condizionatore si spegne col tempo minimo del compressore. Il conteggio riparte se il prelievo cessa. I
   comandi senza opzione non cambiano. Il prelievo può dipendere anche da altri carichi della casa.

**Limite:** la protezione estiva della batteria si calcola nella logica automatica: con AI Climate disabilitato non chiude la
priorità. Modificati solo la funzione di AI Climate e «Condizionatore priorità manuale»; router invariato.

```sh
python3 build.py <flows-export.json> && TZ=Europe/Rome node --test test.js     # 5 casi
python3 deploy.py prepare | apply BACKUP | rollback BACKUP
```
