# ROUTER-FIREPLACE-MODE.1 — modalità caminetto dall'app

**Problema (14/10/2026):** il menu "Modalità" del caminetto nell'app invia l'etichetta
("Acqua Sanitaria", "Riscaldamento", …). Il Digital Twin Router la inoltrava senza tradurla e sul
broker di `.20` (`home`), mentre la logica del caminetto gira su Node-RED di `.15`, ascolta il
broker di `.15` e accetta solo `inverno`, `conCaldaia`, `sanitaria`, `disattivato`. Una modalità
scelta dall'app non arrivava mai al caminetto (verificato sul broker: `.20` riceveva
"Acqua Sanitaria", `.15` restava `disattivato`).

**Correzione:** voce `fireplace.main.mode` del router con broker `emon` (`.15`), traduzione
etichetta → valore (`cmd_trans`) e valore → etichetta per lo stato; chiave di inizializzazione
`router_initialized_v119` → `v120` (le tabelle del router sono in cache nel context).
L'app non mostra più "Manuale" (a `.15` non esiste: l'accensione manuale è il comando acceso/spento).

| App | `.15` (`zara/domotics/palazzetti/modalita`) |
|---|---|
| Riscaldamento | `inverno` |
| Integrazione Caldaia | `conCaldaia` |
| Acqua Sanitaria | `sanitaria` |
| Disattivato | `disattivato` |

Dopo l'applicazione va cancellato il valore retained rimasto sul broker di `.20`
(`zara/domotics/palazzetti/modalita` = "Acqua Sanitaria"), perché il router legge lo stato da
entrambi i broker.

File: `build.py` (genera da un export dei flow), `test.js` (`node --test test.js`, 5 casi, compreso
il context già inizializzato dal codice precedente), `deploy.py` (`prepare`, `apply BACKUP`,
`rollback BACKUP` su `.20`).
