# Preriscaldo del puffer con il caminetto (fase P) — diario di lavoro

Stato: **idea approvata, tappa 0 da iniziare** (17/10/2026). Questo documento traccia scopo,
avanzamento, modifiche, misure e la decisione finale sull'automatismo. Si aggiorna a ogni passo,
nello stesso commit delle modifiche (regola 8 di `CLAUDE.md`).

## 1. L'idea (dall'esperienza dell'utente)

Il riscaldamento a pavimento usa l'acqua del puffer: la pompa si abilita quando il **puffer alto
supera 35 °C** (si ferma sotto 30 °C; logica del `.15`, `backend/nodered/floorpump_repeat3/pump.js`).
Se all'inizio delle fasce del riscaldamento (oggi dal tardo pomeriggio) il puffer è freddo, la
caldaia a gas deve scaldare una grande massa d'acqua: **fiamma alta, fuori condensazione, molto gas**.

Da anni l'utente accende prima il **caminetto in modalità Integrazione Caldaia, circa dalle 12 alle
15**: il puffer arriva a circa **40 °C**, poi la caldaia deve solo mantenere la temperatura,
**modula bassa (0–20 %), lavora in condensazione e consuma poco**.

Richieste dell'utente:

1. una **notifica** quando la caldaia non sta modulando bassa (fiamma oltre il 20 %);
2. poter **cambiare orari di avvio/arresto del caminetto** (già possibile da AI Smart con conferma);
3. meglio ancora: il **sistema calcola e imposta da solo** orari del caminetto (ed eventualmente le
   fasce del timer), con la possibilità di **verificarne l'efficienza** e di **tornare alla
   gestione manuale** di oggi se l'automatismo non va bene.

## 2. Scopo e criteri di successo

- **Scopo:** meno gas a parità di comfort, sfruttando caminetto (e solare termico) per portare il
  puffer in temperatura prima che parta il riscaldamento.
- **Misure di successo** (da confermare con i dati della tappa 0):
  - minuti di caldaia con modulazione **oltre il 20 %** per giorno di riscaldamento: in calo;
  - puffer alto **≥ 38 °C** all'inizio della prima fascia del timer;
  - ore di caminetto (pellet) non superiori alla gestione manuale a parità di risultato;
  - nessun peggioramento del comfort (temperature delle stanze nelle fasce).
- **Gas reale:** da verificare se esiste un contatore leggibile; in mancanza si stima dai minuti di
  modulazione (adatto al confronto fra giorni, non ai metri cubi).

## 3. Vincoli

- La **logica del caminetto sul `.15` non si modifica** (decisione dell'utente). Si usano solo i
  comandi che AI Smart usa già: modalità, orari di avvio/arresto, potenza automatica.
- **Manuale sempre disponibile:** in Manuale il sistema non tocca caminetto né fasce.
- Comandi sugli impianti di riscaldamento **solo con conferma** (regola 10), salvo la modalità
  Automatico, autorizzata una volta all'attivazione e con notifica a ogni cambio (come le regole).
- Limiti dell'automatico: finestra di accensione decisa dall'utente (proposta 10:00–17:00); mai se il
  puffer alto è già sopra 38 °C; mai con profilo di riscaldamento **Spento**.
- Le **fasce del timer non si toccano** nelle prime tappe: l'automatico adatta il caminetto alle
  fasce, non il contrario.
- **Prerequisito:** la caldaia (Fondital Itaca KRB 24) è spenta da marzo 2026 e il gateway OpenTherm
  chiede CH=0 / DHW=0. Fiamma e modulazione arrivano al `.20` da `emon/OTGcaldaia/flame` e
  `emon/OTGcaldaia/modulation` solo a caldaia accesa (piano HANDOFF §7, punto caldaia).

## 4. Tappe

| Tappa | Contenuto | Rischio | Stato |
|---|---|---|---|
| 0 — Dati | Storico EmonCMS dell'inverno scorso: modulazione e fiamma caldaia, puffer alto/basso, caminetto (stato, livello, modalità), esterno, solare termico. Ricavare: °C/h che il caminetto aggiunge al puffer; minuti di caldaia oltre il 20 %; confronto giorni con e senza caminetto anticipato | nullo (sola lettura) | da iniziare |
| 1 — Avviso | Nel tab «Avvisi» (`house_alerts`): «caldaia oltre il 20 % da 10 minuti» con puffer, stato del caminetto e consiglio; massimo 1 ogni 2 ore; escluso il prelievo di acqua sanitaria | nullo | da fare |
| 2 — Consiglio del giorno | Ogni mattina (profilo Ridotto o Pieno): ora di avvio = prima fascia del timer − tempo per portare il puffer a ~40 °C (velocità appresa), corretta per il solare termico; notifica e «applica il consiglio» ad AI Smart con conferma | basso | da fare |
| 3 — Automatico | Selettore «Preriscaldo puffer: Manuale / Consiglio / Automatico» nella scheda Clima; l'automatico imposta ogni giorno orari e modalità del caminetto e notifica; rapporto settimanale di efficienza; ritorno a Manuale con un tocco | medio | da decidere dopo la tappa 2 |

Idee successive (fuori da questa fase): abbassare la mandata della caldaia via OpenTherm per più
condensazione; con la futura pompa di calore, preriscaldo del puffer con l'eccesso fotovoltaico.

## 5. Avanzamento

| Data | Cosa | Esito |
|---|---|---|
| 17/10/2026 | Idea discussa e approvata a tappe; verificata la soglia della pompa (35/30 °C, non 37); verificato che la caldaia è spenta da marzo | documento creato |

## 6. Modifiche fatte (codice, Node-RED, app, dispositivi)

| Data | Dove | Modifica | Commit / backup |
|---|---|---|---|
| — | — | nessuna finora | — |

## 7. Misure

Per ogni misura: periodo, sorgente (feed EmonCMS / topic), metodo, risultato.

| Data | Misura | Periodo e sorgente | Risultato |
|---|---|---|---|
| — | — | — | — |

## 8. Decisione sull'automatismo

Da compilare alla fine della tappa 2 (consiglio) e della tappa 3 (automatico): dati a confronto,
parere dell'utente, decisione (**accettato / accettato con modifiche / respinto → Manuale**) e data.

| Data | Tappa | Decisione | Motivazione |
|---|---|---|---|
| — | — | — | — |
