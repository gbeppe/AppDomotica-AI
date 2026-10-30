# AI-CLIMATE-AC-OVERRIDE.1 — priorità manuale del condizionatore con scadenza (fase T4)

Progetto e decisioni dell'utente (23/10/2026): [`docs/AI_MODE/COMANDI_A_TEMPO.md`](../../../docs/AI_MODE/COMANDI_A_TEMPO.md#t4--condizionatore-decisioni-dellutente-del-23102026).

- Comando `zara/interface/climate/ac/override/cmd` = `{"mode": "off|heat|cool|dry", "setpoint": n,
  "until": "<ISO con fuso>"}` (al più **6 ore** avanti) oppure `"auto"`. Temperature: caldo 16–22 °C
  (predefinita 20), raffrescamento 22–28 (24), deumidifica 23–25 (24): i codici del dizionario IR
  (`eco20heat`, `24`, `24dry`, `OFF`).
- Nuova funzione «Condizionatore priorità manuale» ([`override.js`](override.js)) nel tab «AI climate»:
  salva la priorità nel contesto `file`, pubblica lo stato retained `zara/interface/climate/ac/override/stat`
  (`domopi.ac_override.v1`), la chiude alla scadenza (controllo ogni 60 s) o con `"auto"`, e se una
  protezione della batteria l'ha chiusa manda un avviso su ntfy (`domopi-casa`) tramite un nuovo nodo
  «link in» nel tab «Avvisi (DomoPi)» collegato alla funzione «notifica ntfy» esistente.
- Funzione di AI Climate, due blocchi:
  - **A** prima della decisione del condizionatore: ricorda stato e orari del giro; finita una priorità,
    chiude lo stato manuale (spento, dopo il tempo minimo di marcia: impostazione dell'app, 25 minuti al 30/10) prima della logica AI;
  - **B** dopo la decisione: finché dura la priorità annulla la decisione AI del giro e applica stato e
    codice IR manuali con i tempi del compressore (`MANUALE_IN_ATTESA_COMPRESSORE_<n>_MIN`); una protezione
    della batteria (d'estate: scarica oltre 2,5 kW o riserva notturna) la chiude invece
    (`PROTEZIONE_BATTERIA_CHIUDE_MANUALE`) e la decisione AI resta.
- Stati nuovi in `condizionatore_stato`: `MANUALE_CALDO`, `MANUALE_RAFFRESCAMENTO`, `MANUALE_DEUMIDIFICA`.
  Durante la priorità non parte la richiesta di preavvio al Predictive Reserve. Con AI Climate
  disattivato dall'utente (`AI_climate_enabling` false) la priorità non ha effetto.
- VMC, uscite e fili di AI Climate invariati; router invariato.

```sh
python3 build.py <flows-export.json> && TZ=Europe/Rome node --test test.js     # 9 casi
python3 deploy.py prepare | apply BACKUP | rollback BACKUP
```
