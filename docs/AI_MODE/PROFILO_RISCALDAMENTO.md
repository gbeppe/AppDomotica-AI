# Profilo di riscaldamento e timer dei termostati sul `.20` (progetto)

Stato: patch `HEATING-PROFILE.1` pronta in `backend/nodered/heating_profile/` (16/10/2026), da
applicare in shadow. Profilo Ridotto: solo fasce serali (scelta dell'utente).

## Perché

I timer dei termostati sul `.15` (tab «TASMOTAs», flussi attivi in
`~/.node-red/projects/emonpi/flows_emonpi.json`) non funzionano:

- leggono minimo e massimo dal broker del `.15`, mentre app e router li pubblicano sul `.20`;
- scelgono le fasce in base alla modalità del caminetto, una variabile aggiornata solo con la
  pompa del pavimento abilitata (oggi disabilitata) e tenuta solo in memoria: le funzioni dei
  timer scartano ogni comando;
- la modalità del caminetto dice chi produce il calore, non quanto ne serve: «Integrazione
  Caldaia» significa freddo, non meno riscaldamento.

## Il profilo

Un parametro unico per la casa (la **stagione**), indipendente dal caminetto; **ogni stanza ha le
sue fasce** per ciascun profilo (decisione dell'utente del 16/10/2026: profilo unico, fasce per
stanza, niente profili separati per stanza).

| Profilo | Fasce soggiorno | Fasce bagno |
|---|---|---|
| **Spento** | nessuna (sempre minimo) | nessuna (sempre minimo) |
| **Ridotto** | 17:00–21:00 tutti i giorni | 17:00–21:00 tutti i giorni |
| **Pieno** | 12:00–20:00 tutti i giorni | 17:00–22:00 tutti i giorni; 05:05–07:00 lun–ven; 07:30–09:30 sab–dom |

Dentro una fascia il timer imposta il **massimo** del termostato, fuori il **minimo** (valori
già impostabili dall'app: `zara/interface/climate/thermostat_{living,bath}/{max,min}_temperature`).

Modalità del profilo: **Automatico** (predefinito) oppure forzato **Spento / Ridotto / Pieno**.

### Automatico

Media della temperatura esterna (`zara/interface/env/outdoor/temperature/stat`) sulle ultime
72 ore, da medie orarie salvate su file. Soglie ricavate da EmonCMS (stagioni 2017/18–2024/25,
pompa pavimento e caldaia contro la media esterna di 3 giorni):

| Media 3 giorni | Giorni con riscaldamento | Giorni con caldaia | Profilo |
|---|---|---|---|
| < 10 °C | 80–100% | 64–100% | Pieno |
| 10–16 °C | 9–62% | 2–48% | Ridotto |
| > 16 °C | 0–6% | ~0% | Spento |

Isteresi di 1 °C: Ridotto → Pieno sotto 10 °C, Pieno → Ridotto sopra 11 °C; Ridotto → Spento
sopra 16 °C, Spento → Ridotto sotto 15 °C. Con meno di 48 ore di dati il profilo resta quello
precedente (all'avvio: Ridotto).

## Quando il timer scrive i setpoint

Solo a un evento, mai di continuo, così un setpoint impostato a mano o a voce resta valido fino
al prossimo evento:

- inizio o fine di una fascia;
- cambio di profilo (automatico o forzato);
- cambio di minimo o massimo di quel termostato.

Il comando passa dal topic dell'interfaccia
`zara/interface/climate/thermostat_X/target_temperature/cmd` (router → Tasmota), come l'app.

## Prova (shadow) e attivazione

`mode`: **shadow** (predefinito) o **active**. In shadow il timer calcola e registra cosa
farebbe, pubblica il piano su `zara/interface/heating/timer/plan/stat`, ma non invia setpoint.
Dopo il confronto (almeno un giorno) si passa ad active con una conferma.

I timer del `.15` oggi non inviano nulla; prima di passare ad active vanno comunque disattivati
(con l'utente), per evitare un doppio controllo se in futuro la pompa del pavimento venisse
abilitata.

## Topic (nuovo tab Node-RED «Profilo riscaldamento», nessuna modifica al router)

| Topic | Contenuto |
|---|---|
| `zara/interface/heating/profile/mode/cmd` | `auto`, `spento`, `ridotto`, `pieno` |
| `zara/interface/heating/profile/stat` (retained) | JSON: profilo effettivo, modalità, media 72 h, ore di dati, soglie, motivo |
| `zara/interface/heating/timer/mode/stat` (retained) | `shadow` / `active` |
| `zara/interface/heating/timer/plan/stat` (retained) | JSON: per termostato fascia attiva, valore voluto, prossimo evento |

## Registro per l'apprendimento

File giornaliero JSONL in `/home/pi/AI_climate/heating-YYYY-MM-DD.jsonl` (stessa radice dei log
che `house_ai` già legge): cambi di profilo con la media esterna, decisioni del timer, e
**correzioni dell'utente** (setpoint cambiato fuori da un evento del timer, profilo forzato).

## Nell'app (scheda Clima, 16/10/2026)

Progetto delle schermate: https://claude.ai/artifact/2rAG5monYc7gEsBdtKt4xr (privato).

- Riquadro «Profilo riscaldamento» sopra i termostati: profilo in esecuzione (colore per profilo),
  «Automatico» o «Scelto da te», «In prova» finché il timer è in shadow, motivo (media esterna o
  ore di dati mancanti) e prossimo cambio di fascia.
- Tocco sul riquadro o su ⓘ: foglio dal basso con la scelta del profilo (Automatico, Spento,
  Ridotto, Pieno) e **Conferma**, che pubblica `zara/interface/heating/profile/mode/cmd`; da lì la
  legenda: fasce per stanza, come decide l'automatico, ultime correzioni registrate.
- In ogni termostato: «Chiede calore» / «Non chiede calore» e la riga del timer (fascia, valore,
  prossimo cambio).
- Dati: `heating/profile/stat`, `heating/timer/plan/stat`, `heating/profile/learning/stat`
  (HEATING-PROFILE.2).

## Fasi successive

1. ~~App: profilo nella scheda Clima, con selettore~~ (fatto il 16/10/2026).
2. AI Smart: «com'è il profilo di riscaldamento?» e «metti il riscaldamento in ridotto», con
   conferma (regola 10).
3. Apprendimento: ricalcolo notturno delle soglie dai dati e spostamento di mezzo grado dopo
   correzioni ripetute dell'utente, sempre registrato e spiegabile.
