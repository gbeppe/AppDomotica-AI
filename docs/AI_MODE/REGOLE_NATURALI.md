# Regole naturali (fase H) — progetto

Stato: **progetto approvato** (decisioni dell'utente del 16/10/2026, sotto). Nessun codice scritto. Riprende la
fase H di [`PIANO_DI_LAVORO.md`](PIANO_DI_LAVORO.md), il punto 8 di
[`ANALISI_ARCHITETTURA.md`](ANALISI_ARCHITETTURA.md) e il vincolo 4 di [`README.md`](README.md):
le regole si conservano ed eseguono nel backend, indipendenti da Android e dal modello generativo.

Fino ad allora AI Smart rifiuta le richieste condizionali (release `20261016-rules-guard`).

## Obiettivo

Dire a voce una regola «quando… allora…» e farla eseguire alla casa, anche con il telefono spento:

- «quando il puffer alto arriva a 42 gradi spegni il caminetto»
- «se fuori scende sotto 5 gradi metti il bagno a 21»
- «quando la batteria supera il 90 per cento accendi la luce del portico» *(esempio di azione)*
- «alle 22 di ogni sera spegni tutte le luci»

Il modello **traduce** la frase in una regola strutturata; il backend la **verifica**; l'utente la
**conferma** dopo averla sentita rileggere; un tab Node-RED del `.20` la **esegue**.

## Una regola

```json
{
  "id": "r-20261016-1",
  "text": "quando il puffer alto supera 42 °C spegni il caminetto, una volta",
  "trigger": {"kind": "threshold", "metric": "puffer_top", "op": "above", "value": 42.0},
  "conditions": [],
  "action": {"tool": "set_fireplace_power", "state": "off"},
  "lifetime": {"kind": "once"},
  "cooldown_min": 30,
  "enabled": true,
  "created_at": "2026-10-16T18:40:00+02:00"
}
```

| Parte | Valori ammessi (MVP) |
|---|---|
| `trigger` | **soglia**: una misura dell'elenco, `above` / `below`, valore nei limiti della misura; **orario**: `HH:MM` e giorni della settimana |
| `conditions` | al massimo 2, stesse forme del trigger (per esempio «solo se il caminetto è in Manuale», «solo dalle 8 alle 20») |
| `action` | **uno** dei comandi che AI Smart sa già fare, con gli stessi parametri chiusi e le stesse regole |
| `lifetime` | `once` (si disattiva dopo il primo intervento; **predefinito** se non detto), `until` (fino a una data), `always` |
| `cooldown_min` | attesa minima fra due interventi della stessa regola (predefinito 30 minuti) |

### Misure ammesse come trigger (tutte già sul broker del `.20`)

| Misura | Topic `zara/interface/…` | Limiti | Dato vecchio dopo |
|---|---|---|---|
| Puffer alto / basso | `heating/puffer/{top,bottom}_temperature/stat` | 10–95 °C | 10 min |
| Temperatura esterna | `env/outdoor/temperature/stat` | −20–45 °C | 15 min |
| Stanze (soggiorno, bagno, camera, cameretta) | `climate/thermostat_*/current_temperature/stat`, `env/*/temperature/stat` | 5–35 °C | 15 min |
| Batteria | `energy/battery/soc/stat` | 0–100 % | 5 min |
| Fotovoltaico, consumo casa | `energy/solar/power/stat`, `energy/home/consumption/stat` | 0–15 kW | 5 min |
| Orario | — | `HH:MM`, giorni | — |

### Azioni ammesse (MVP)

Luci e scene; setpoint dei termostati (16–22 °C); caminetto: modalità, orari, potenza
automatica, livello e acceso/spento **con le stesse condizioni di oggi** (acceso/spento e livello
solo in Manuale); profilo di riscaldamento (dopo l'attivazione del timer).
**Escluse**: OpenSprinkler e irrigazione (regola 5 di `CLAUDE.md`), piscina, garage,
condizionatore, caldaia, Predictive Reserve.

## Come si comporta

- **Sul passaggio, non sul livello**: una soglia scatta quando la misura la *attraversa*
  (da sotto a sopra per `above`). Se la condizione è **già vera quando la regola nasce, interviene
  subito una volta** (decisione dell'utente), poi segue il riarmo.
- **Riarmo con isteresi**: dopo un intervento la regola si riarma solo quando la misura torna
  indietro di un margine (0,5 °C, 2 punti di batteria, 200 W) e dopo il `cooldown`.
- **Dati vecchi o mancanti**: nessun intervento; la regola resta in attesa e lo registra.
- **Riavvio del `.20`**: stato di ogni regola (armata, intervenuta, ultimo intervento) salvato su
  file; dopo il riavvio non si ripete un intervento già fatto.
- **Al massimo 10 regole attive**; l'undicesima è rifiutata con l'invito a cancellarne una.
- **Prova di un giorno per ogni regola nuova**: per 24 ore registra (e notifica) cosa farebbe senza
  inviare comandi, poi diventa attiva da sola; si può attivare prima a voce o dall'app.
- **Più regole sullo stesso dispositivo** entro il cooldown: vince la prima, la seconda è
  registrata come conflitto.
- **Protezioni**: una regola non scavalca la logica del `.15` (caminetto nelle modalità automatiche,
  pompa pavimento), né le conferme di sicurezza di Node-RED; se il comando non è ammesso in quel
  momento (caminetto non in Manuale) non lo invia e lo registra.
- **Registro**: `/home/pi/AI_climate/rules-AAAA-MM-GG.jsonl` (creazione, armamento, intervento con
  i valori letti, comando inviato, rifiuto, conflitto, scadenza).

## Conferma

- **Alla creazione**, sempre: AI Smart rilegge la regola in parole semplici («Creo questa regola:
  quando il puffer alto supera 42 °C spengo il caminetto, una volta. Confermi?») e la salva solo
  dopo «confermo» o il tasto. Vale per tutte le regole, non solo per il riscaldamento.
- **All'intervento**, nessuna conferma (l'utente può non esserci): la conferma data alla creazione
  vale come autorizzazione. Per questo l'elenco delle azioni è chiuso e ogni regola scade o si
  esaurisce.

## Architettura (stesso schema dei comandi di oggi)

1. **`house_ai`**: nuovi strumenti `create_rule`, `list_rules`, `delete_rule`, `pause_rule`;
   il modello produce la regola, il backend la valida (misure, limiti, azioni, durata) e prepara
   la frase di conferma. Nessun comando parte dal backend.
2. **App**: dopo la conferma pubblica la regola validata su `zara/interface/rules/create/cmd`
   (come oggi pubblica i comandi).
3. **Node-RED `.20`, nuovo tab «Regole»**: rivalida la regola con le stesse liste (difesa in
   profondità), la salva, ascolta le misure, esegue le azioni tramite i topic `…/cmd`
   dell'interfaccia (router), pubblica l'elenco retained su `zara/interface/rules/list/stat` e
   scrive il registro.
4. **Lettura**: «che regole ci sono?», «cosa hanno fatto le regole oggi?» (dal registro),
   «cancella la regola del caminetto», «sospendi le regole».
5. **App, fase successiva**: elenco delle regole con interruttore, cancellazione e ultimo intervento.

## Fasi

| Fase | Contenuto |
|---|---|
| H1 | Tab «Regole» sul `.20` con trigger a soglia e a orario, una azione, durata, cooldown, registro; regole create a mano per provarlo, in **prova** (registrano cosa farebbero) |
| H2 | Strumenti `house_ai` + conferma vocale + pubblicazione dall'app; lettura e cancellazione a voce |
| H3 | Elenco nell'app; condizioni aggiuntive |
| G (in parallelo a H2) | **Notifiche sul telefono** «la regola X è intervenuta / sarebbe intervenuta (in prova)»: trasporto da progettare (notifica dall'app collegata al broker oppure servizio di notifica push) |

## Decisioni dell'utente (16/10/2026)

| # | Domanda | Decisione |
|---|---|---|
| 1 | Durata predefinita | **una volta** (`once`) |
| 2 | Condizione già vera alla creazione | **interviene subito** una volta |
| 3 | Intervento senza conferma (autorizzato alla creazione) | **sì, per tutte le azioni ammesse** |
| 4 | Massimo regole attive | **10** |
| 5 | Misure e azioni ammesse | **elenco approvato** |
| 6 | Prova | **ogni regola nuova in prova per un giorno** |
| 7 | Avvisi | **notifica sul telefono** a ogni intervento |
