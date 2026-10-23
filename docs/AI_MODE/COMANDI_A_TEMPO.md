# Comandi a tempo («fino alle», «dalle», «dalle… alle…») — progetto

Stato: **T1 attiva** dal 18/10/2026 (RULES-ENGINE.3 + house_ai `20261018-timed-commands`); **T2 (VMC)** in Node-RED dal 21/10/2026 (AI-CLIMATE-VMC-OVERRIDE.1, RULES-ENGINE.4), backend `20261021-budget` e app da rilasciare; T3–T4 da fare. Richiesta dell'utente dopo la prova del 18/10 («accendi il
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
| T2 | VMC | priorità manuale con scadenza rispettata da AI Climate (`AI-CLIMATE-VMC-OVERRIDE.1`), azioni VMC nel motore (`RULES-ENGINE.4`), AI Smart `vmc_command`, pulsanti dell'app = priorità di 2 ore | Node-RED live 21/10; backend e app in rilascio |
| T3 | piscina (pompa, skimmer, luce acqua, luce bordo) | protezioni per la pompa di filtrazione da decidere | **rimandata** (utente, 23/10) |
| T4 | condizionatore | priorità manuale con scadenza (spento o acceso in caldo/raffrescamento/deumidifica) rispettata da AI Climate; vedi sotto | Node-RED live 23/10 (AI-CLIMATE-AC-OVERRIDE.1, RULES-ENGINE.5); backend `20261023-ac`; app: riquadro «Condizionatore a tempo» (23/10); prova dal vivo da fare |
| (opzione) | termostati | «metti il bagno a 21 fino alle 22» = setpoint ora + ripristino del precedente | da valutare |
| — | irrigazione / OpenSprinkler | **mai** (regola 5 di `CLAUDE.md`) | — |

## T4 — condizionatore (decisioni dell'utente del 23/10/2026)

Oggi il condizionatore lo decide solo **AI Climate** (stati `OFF`, `COOLING_ON`, `NIGHT_DRY`,
`STANDBY_INVERTER`, d'inverno `HEAT_DIURNO`/`HEAT_SICUREZZA_NOTTE`), con tempi minimi del compressore
(15 min acceso, 15 spento), batteria minima, protezioni d'estate (scarica oltre 2,5 kW, riserva
notturna) e un vigilante che rispegne un condizionatore acceso «da fuori» dopo 3 minuti.
L'interruttore generale `AI_climate_enabling` spegne anche la VMC al minimo: non va bene per T4.

Decisioni: **1a** spegni e accendi; **2a** vince la protezione della batteria (chiude la priorità e
avvisa); **3a** tempi del compressore rispettati anche dai comandi manuali; **durata massima 6 ore**;
sempre con conferma.

| Frase | Effetto |
|---|---|
| «spegni il condizionatore fino alle 18» | spento; AI Climate non lo riaccende fino alle 18 |
| «accendi il condizionatore in caldo a 20 fino alle 22» | caldo 20 °C (codici `eco16heat`…`eco22heat`) |
| «accendi il condizionatore in raffrescamento a 24» | 24 °C (codici `22`…`28`); senza ora: 2 ore |
| «metti il condizionatore in deumidifica fino alle 23» | deumidifica (codici `23dry`…`25dry`) |
| «rimetti il condizionatore in automatico» | chiude la priorità; AI Climate riprende |

Componenti, sullo schema della VMC:

1. **Node-RED `AI-CLIMATE-AC-OVERRIDE.1`**: comando `zara/interface/climate/ac/override/cmd` =
   `{"mode": "off|heat|cool|dry", "setpoint": n, "until": "<ISO con fuso>"}` (al più 6 ore avanti) o
   `"auto"`; funzione «Condizionatore priorità manuale» (contesto `file`, stato retained
   `…/override/stat`, scadenza controllata ogni 60 s); blocco nella funzione di AI Climate: impone lo
   stato manuale (`MANUALE_SPENTO`/`MANUALE_CALDO`/…) e il codice IR, attende i tempi del compressore
   (motivo `MANUALE_IN_ATTESA_COMPRESSORE`), lascia lavorare il vigilante; se scatta una protezione della
   batteria chiude la priorità (motivo `PROTEZIONE_BATTERIA_CHIUDE_MANUALE`, avviso su ntfy). Alla
   scadenza di un'accensione il condizionatore si spegne e AI Climate decide da capo (rispettando i 15
   minuti di spento). VMC e Predictive Reserve invariati.
2. **`RULES-ENGINE.5`**: azioni `set_ac_override` e `set_ac_auto` nel motore delle regole.
3. **House AI**: strumento `ac_command` (`mode`, `setpoint`, `from`, `to`), regola con conferma, testi
   («accendo il condizionatore in caldo a 20 °C fino alle 22, poi torna l'automatico»), guida utente.
4. **App** (fatto il 23/10/2026): riquadro «Condizionatore a tempo» nella schermata AI Climate, sotto
   «Stato operativo» (`AcOverride.kt`, `AcOverrideCard.kt`): priorità in corso («caldo 20 °C fino alle
   22:00»), pulsanti **Auto**, Spegni, Caldo 20°, Fresco 24°, Deumidifica (2 ore, **con conferma**),
   chiusura per protezione della batteria in rosso (`closed_reason` di `override/stat`).

## T2 — VMC (decisioni dell'utente del 18/10/2026)

La velocità la decide **AI Climate** (1–3 per umidità, limite notturno, antirimbalzo di 5 minuti) e la
reinvia solo quando cambia il suo calcolo: una velocità messa a mano veniva sovrascritta al primo
cambio. Quindi:

- **Priorità manuale con scadenza** (`backend/nodered/ai_vmc_override`): comando
  `zara/interface/ventilation/vmc/override/cmd` `{"speed", "until"}` o `"auto"`; finché dura, AI
  Climate usa quella velocità (motivo `MANUALE_FINO_ALLE_HH:MM`), **anche sopra limite notturno e
  anticondensa** (decisione 3: vince la scelta dell'utente; AI Smart avvisa se l'umidità è ≥ 65 %);
  poi riprende da solo. Stato retained `…/override/stat`.
- **Frasi:** «metti la VMC a 3» = **velocità secca** (decisione 2: AI Climate può cambiarla);
  «fino alle 20» = priorità fino alle 20; «dalle 18 alle 20» = alle 18 priorità fino alle 20;
  «dalle 18» senza fine = **2 ore** (decisione 1); «rimetti la VMC in automatico» / «riattiva AI
  Climate per la VMC» = fine della priorità (richiesta dell'utente).
- **Pulsanti 1–4 dell'app** = priorità manuale di 2 ore (decisione 4), più il tasto «Auto».
- Motore (`RULES-ENGINE.4`): azioni `set_vmc_speed`, `set_vmc_override`, `set_vmc_auto`.

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
| 23/10/2026 | T3 rimandata dall'utente. T4 progettata: decisioni 1a, 2a, 3a, durata massima 6 ore; analisi di AI Climate (stati, vigilante, codici IR del dizionario) |
| 23/10/2026 15:50–15:54 | AI-CLIMATE-AC-OVERRIDE.1 (test 9/9) e RULES-ENGINE.5 (16/16) live; backend `ac_command` (208 test); prova dal vivo rimandata dall'utente |
| 23/10/2026 | Passo 4: riquadro dell'app con stato, Auto e pulsanti di 2 ore con conferma |
| 18/10/2026 21:17 | RULES-ENGINE.3 live sul `.20` (backup `20261018T191739Z-before-rules-engine3`); house_ai `20261018-timed-commands` (commit `b3c2897`); prove reali delle frasi senza comandi |
| 18/10/2026 22:53 | **Prova dal telefono riuscita**: «alle 22:56 accendo la luce della libreria» creata 22:53:38 (notifica «Comando programmato»), eseguita 22:56:13 (`lights/libreria/power/cmd` true, notifica «Regola intervenuta»), regola chiusa |
| 21/10/2026 23:00 | AI-CLIMATE-VMC-OVERRIDE.1 e RULES-ENGINE.4 live sul `.20` (backup `20261021T205755Z-before-ai-vmc-override`, `20261021T210015Z-before-rules-engine4`); house_ai `20261018-vmc`: «metti la VMC a 3» e «rimetti la VMC in automatico» corretti, le frasi con orario fallivano per il limite al minuto di Groq (413; ~7700 token a domanda) → `20261021-budget` |
