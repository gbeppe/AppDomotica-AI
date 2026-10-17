# Preriscaldo del puffer con il caminetto (fase P) — diario di lavoro

Stato: **tappa 0 in corso** (dal 17/10/2026). Questo documento traccia scopo,
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
| 0 — Dati | Storico EmonCMS dell'inverno scorso: modulazione e fiamma caldaia, puffer alto/basso, caminetto (stato, livello, modalità), esterno, solare termico. Ricavare: °C/h che il caminetto aggiunge al puffer; minuti di caldaia oltre il 20 %; confronto giorni con e senza caminetto anticipato | nullo (sola lettura) | in corso |
| 1 — Avviso | Nel tab «Avvisi» (`house_alerts`): «caldaia oltre il 20 % da 10 minuti» con puffer, stato del caminetto e consiglio; massimo 1 ogni 2 ore; escluso il prelievo di acqua sanitaria | nullo | da fare |
| 2 — Consiglio del giorno | Ogni mattina (profilo Ridotto o Pieno): ora di avvio = prima fascia del timer − tempo per portare il puffer a ~40 °C (velocità appresa), corretta per il solare termico; notifica e «applica il consiglio» ad AI Smart con conferma | basso | da fare |
| 3 — Automatico | Selettore «Preriscaldo puffer: Manuale / Consiglio / Automatico» nella scheda Clima; l'automatico imposta ogni giorno orari e modalità del caminetto e notifica; rapporto settimanale di efficienza; ritorno a Manuale con un tocco | medio | da decidere dopo la tappa 2 |

**Termocaminetto:** Palazzetti **Idro 45** (2015), pompa di circolazione a 3 velocità usata da anni
alla **2**. Il caminetto non comunica il proprio stato: la scheda ESP `.22` simula solo i tasti del
pannello, quindi la soglia di avvio della pompa non si legge né si cambia da remoto.

Idee successive (fuori da questa fase): abbassare la mandata della caldaia via OpenTherm per più
condensazione; con la futura pompa di calore, preriscaldo del puffer con l'eccesso fotovoltaico.

## 5. Avanzamento

| Data | Cosa | Esito |
|---|---|---|
| 17/10/2026 | Idea discussa e approvata a tappe; verificata la soglia della pompa (35/30 °C, non 37); verificato che la caldaia è spenta da marzo | documento creato |
| 17/10/2026 | Tappa 0: inventario dei feed EmonCMS (sotto) e lettura di un giorno d'inverno campione (14/01/2026) | storico disponibile da 9 inverni; prime osservazioni in §7 |
| 17/10/2026 | Tappa 0: analisi dello stato «90 W» del caminetto su tutto l'inverno 2025-26 | pompa del caminetto probabilmente attiva a fiamma spenta con puffer ≥ 34 °C; puffer che si raffredda circa il 50 % più in fretta (§7) |
| 17/10/2026 | Tappa 0: lavoro della caldaia dopo ogni partenza della pompa del pavimento (3 inverni, 991 partenze) | con puffer ≥ 38 °C la caldaia lavora circa la metà nelle 2 ore successive (§7) |

## 6. Modifiche fatte (codice, Node-RED, app, dispositivi)

| Data | Dove | Modifica | Commit / backup |
|---|---|---|---|
| — | — | nessuna finora | — |

## 7. Misure

Per ogni misura: periodo, sorgente (feed EmonCMS / topic), metodo, risultato.

Feed EmonCMS sul `.15` (PHPFina in `/var/opt/emoncms/phpfina/`, lettura in sola lettura):

| Feed | Nome | Passo | Dati |
|---|---|---|---|
| 181 | caldaia modulation (%) | 30 s | 31/05/2017 → oggi (0 da marzo 2026) |
| 179 | caldaia flame (0/1) | 30 s | 31/05/2017 → oggi |
| 180 | caldaia water temp (mandata) | 30 s | 30/05/2017 → 16/03/2026 |
| 42 / 41 | Temp Puffer alto / basso | 60 s | 21/04/2016 → oggi |
| 147 | focolarePower (emontx3, W; negativo = assorbimento) | 30 s | 13/03/2017 → oggi |
| 195 | focolarePower (Node focolare) | 60 s | 10/09/2017 → 16/10/2026 |
| 137 | floorHeatingPumpPower (W; negativo = in funzione) | 10 s | 07/03/2017 → oggi |
| 43 / 40 | velocità pompa solare / temperatura pannelli | 60 s | 21/04/2016 → oggi |
| 48 | extTemp | 120 s | 22/04/2016 → oggi (buchi: assente il 14/01/2026) |

| Data | Misura | Periodo e sorgente | Risultato |
|---|---|---|---|
| 17/10/2026 | Giorno campione, campioni ogni 30 min | 14/01/2026, feed 181, 179, 42, 41, 147, 195, 137, 43, 48 | La caldaia **mantiene il puffer alto a ~35–38 °C tutto il giorno** con modulazione 10–14 %. La modulazione **sale a 22–34 % solo quando parte la pompa del pavimento** (04:30–06:30: puffer basso da 38 a 26 °C, caldaia 27–34 %). Con il **caminetto 13:00–15:00** il puffer sale a **42–43 °C** e alla richiesta delle 17:30 la pompa lavora **con la caldaia a 0 %**: conferma qualitativa dell'idea. Da chiarire con l'utente il significato dei feed 147 (≈ −90 W anche di notte) e 195 (40/80). |
| 17/10/2026 | Stato «≈ 90 W» del circuito del caminetto (feed 147 fra −115 e −65 W, caminetto non acceso) | 01/10/2025–31/03/2026, feed 147, 195, 42 | **Non tutte le notti**: notti 00–05 con lo stato per oltre metà del tempo: ott 0/30, nov 3/28, dic 24/31, gen 16/31, feb 15/28, mar 1/31. **Dipende dalla temperatura del puffer alto**: quasi mai sotto 34 °C (≤ 0,5 % dei minuti), 16 % a 34–36, 31 % a 36–40, 53–78 % a 40–50 °C. Totale ~1.110 ore nella stagione. Compatibile con l'ipotesi dell'utente: **la pompa del caminetto circola quando sente l'acqua calda**, anche a fiamma spenta. |
| 17/10/2026 | Raffreddamento del puffer con lo stato «90 W» acceso o spento | 01/11/2025–28/02/2026, finestre di 30 min «quiete» (caldaia spenta, pompa pavimento e solare ferme, caminetto spento), puffer alto 35–42 °C | Calo mediano **0,62 °C/h con «90 W» acceso** contro **0,41 °C/h spento** (1.126 e 1.186 finestre, puffer medio 37,4 e 37,1 °C): circa **+50 % di dispersione**, probabilmente attraverso il corpo e la canna fumaria del caminetto freddo. Indicativo: lo stato acceso è più frequente nei mesi freddi. Con il **puffer da 800 litri** (dato dell'utente): 0,21 °C/h × 800 l × 1,163 Wh/(l·°C) ≈ **195 W di calore perso**; su ~1.110 ore ≈ **215 kWh termici a stagione ≈ 22 m³ di gas**, più ≈ **95 kWh elettrici** (85 W oltre lo standby). |
| 17/10/2026 | Confronto giorno per giorno con e senza caminetto anticipato | 15/10/2025–15/03/2026, 109 giorni con pompa pavimento ≥ 30 min; esterno da OSMER (feed 337: il feed 48 non ha dati quell'inverno) | **Non conclusivo**: l'utente ha acceso il caminetto in anticipo (avvio 9–16, ≥ 60 min) in **97 giorni su 109**; solo 3 giorni senza fuoco e 9 con fuoco in altri orari. Serve il metodo per partenza della pompa (riga sotto). |
| 17/10/2026 | **Lavoro della caldaia nelle 2 ore dopo ogni partenza della pompa del pavimento, secondo il calore nel puffer** | inverni 2023-24, 2024-25, 2025-26 (nov–feb): 991 partenze (pompa ferma ≥ 30 min, poi in funzione); solo le 716 senza caminetto acceso nelle 2 ore; puffer = media alto/basso alla partenza; feed 137, 42, 41, 181, 179, 147 | Puffer **32–35 °C** (170 partenze): fiamma **72 min**, oltre il 20 % **31 min**, modulazione·minuti **1.448**. **35–38 °C** (335): 71 / 21 / 1.194. **38–41 °C** (80): **34 / 10 / 573**. 41–44 °C (67): 33 / 23 / 843. **44–47 °C** (49): **11 / 6 / 398**. Con il puffer **sopra 38 °C alla partenza** la caldaia lavora circa **la metà** (−50/−60 %) e fa meno di un terzo dei minuti a fiamma alta rispetto a 32–35 °C: **conferma quantitativa dell'idea**. Limiti: temperatura esterna non controllata (puffer caldo più frequente nei giorni miti o di sole); la fascia 41–44 °C è anomala, da approfondire. |

## 8. Decisione sull'automatismo

Da compilare alla fine della tappa 2 (consiglio) e della tappa 3 (automatico): dati a confronto,
parere dell'utente, decisione (**accettato / accettato con modifiche / respinto → Manuale**) e data.

| Data | Tappa | Decisione | Motivazione |
|---|---|---|---|
| — | — | — | — |
