# Comandi a tempo («fino alle», «dalle», «dalle… alle…») — progetto

Stato: **T1 attiva** dal 18/10/2026 (RULES-ENGINE.3 + house_ai `20261018-timed-commands`); T2–T4 da fare. Richiesta dell'utente dopo la prova del 18/10 («accendi il
caminetto fino alle 19:00» confermato, caminetto rimasto acceso alle 19: un'accensione manuale non
è mai spenta dalla fascia oraria del `.15`).

## Cosa si potrà dire

| Frase | Cosa succede, con **una sola conferma** |
|---|---|
| «accendi X **fino alle** 19» | accende subito + alle 19:00 spegne |
| «spegni X fino alle 7» | spegne subito + alle 7:00 riaccende |
| «accendi X **dalle** 18» | alle 18:00 accende |
| «accendi X **dalle** 18 **alle** 20» | alle 18:00 accende, alle 20:00 spegne; se le 18 sono passate e le 20 no, accende subito |
| VMC: «metti la velocità 2 fino alle / dalle / dalle… alle…» | velocità chiesta, poi ripristino della precedente (T2) |

- L'orario è **il prossimo**: «fino alle 2» detto alle 23 = domani alle 2:00. Massimo 48 ore avanti.
- Ogni parte futura è una **regola «una volta» con orario e data** nel motore delle regole del `.20`,
  **attiva subito, senza giorno di prova** (decisione dell'utente del 18/10/2026: l'ha appena
  confermata e scade dopo un intervento). Dopo l'intervento la regola si chiude; le regole chiuse
  si tolgono dall'elenco dopo 24 ore.
- Notifica su `domopi-regole` a ogni intervento; «che regole ci sono?» le elenca; «cancella la
  regola del caminetto» le annulla.
- Se quando scatta il comando non è ammesso (caminetto non più in Manuale) non lo invia e lo
  notifica come «bloccata».

## Fasi

| Fase | Dispositivi | Note | Stato |
|---|---|---|---|
| **T1** | luci, scene, caminetto | motore: trigger `at` (orario con data), regole `start_active`; AI Smart: strumento `schedule_command`; caminetto solo in Manuale | **attiva 18/10** |
| T2 | VMC | nuovo comando della velocità (`ventilation/vmc/speed`); «metti la velocità X fino/dalle/dalle… alle…»; ripristino della velocità precedente | da fare |
| T3 | piscina (pompa, skimmer, luce acqua, luce bordo) | protezioni per la pompa di filtrazione da decidere | da fare |
| T4 | condizionatore | comando a tempo = **sospendere o forzare AI Climate** fino all'ora indicata (decisione dell'utente del 18/10/2026); oggi il condizionatore è solo in lettura | da progettare |
| (opzione) | termostati | «metti il bagno a 21 fino alle 22» = setpoint ora + ripristino del precedente | da valutare |
| — | irrigazione / OpenSprinkler | **mai** (regola 5 di `CLAUDE.md`) | — |

## T1 — dettagli

**Motore (`RULES-ENGINE.3`, `backend/nodered/rules_engine/rules.js`):**

- trigger `{"kind": "at", "at": "<ISO con fuso>"}`: valido da 15 minuti prima di adesso a 48 ore
  avanti; scatta quando l'ora è raggiunta; se Node-RED era fermo e il ritardo supera 15 minuti la
  regola si chiude come «saltata» e lo notifica; durata sempre «una volta»;
- `"start_active": true` ammesso **solo** con trigger `at`: la regola nasce attiva;
- regole `at` chiuse da più di 24 ore tolte dall'elenco;
- notifica «Regola creata» con «attiva subito» invece di «in prova fino alle…».

**AI Smart (`house_ai`):**

- strumento `schedule_command` (azione, destinazione, valore, orario `HH:MM`): una regola `at`
  con l'orario del prossimo `HH:MM` in Europe/Rome;
- in una richiesta con orari, l'accensione o lo spegnimento immediato di luci e scene diventa una
  regola «adesso» (così tutto passa dalla stessa conferma); il caminetto usa il comando immediato di
  oggi (modalità → 3 s → accensione);
- «dalle T1 alle T2» con T1 già passata e T2 futura: la prima parte diventa «adesso»;
- caminetto: comandi a tempo solo se il caminetto è o viene messo in Manuale;
- frase di conferma unica, per esempio «Accendo il caminetto e alle 19:00 (oggi) spengo il
  caminetto. Confermi?».

**App:** nessuna modifica: pubblica già le regole della risposta dopo la conferma.

## Avanzamento

| Data | Cosa |
|---|---|
| 18/10/2026 | Progetto scritto con le decisioni dell'utente (ordine T1–T4, VMC con velocità, condizionatore via AI Climate, regole a tempo attive subito) |
| 18/10/2026 21:17 | RULES-ENGINE.3 live sul `.20` (backup `20261018T191739Z-before-rules-engine3`); house_ai `20261018-timed-commands` (commit `b3c2897`); prove reali delle frasi senza comandi |
| 18/10/2026 22:53 | **Prova dal telefono riuscita**: «alle 22:56 accendo la luce della libreria» creata 22:53:38 (notifica «Comando programmato»), eseguita 22:56:13 (`lights/libreria/power/cmd` true, notifica «Regola intervenuta»), regola chiusa |
