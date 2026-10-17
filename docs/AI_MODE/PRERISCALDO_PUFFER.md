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

### Interventi sulla dispersione attraverso il caminetto spento

Dalla tappa 0 (§7): a fiamma spenta la pompa del caminetto gira quando il puffer è caldo (~1.110 ore
nell'inverno 2025-26) e il puffer si raffredda circa il 50 % più in fretta (≈ 22 m³ di gas e ≈ 95
kWh elettrici a stagione). Ordine deciso dall'utente (17/10/2026): **prima la soglia della pompa,
solo dopo un'attenta valutazione lo Shelly sulla linea**.

| # | Intervento | Come | Rischi e protezioni | Stato |
|---|---|---|---|---|
| D1 | **Soglia di avvio della pompa** del Palazzetti Multifire 45 Idro (2015) | dal menu installatore/tecnico del pannello: leggere e annotare il valore attuale (dai dati sembra ~34–36 °C); chiedere al centro assistenza Palazzetti (con il numero di serie) se e fino a quanto alzarlo (ipotesi 45–50 °C); velocità della pompa lasciata a **2** | la soglia protegge anche il caminetto (surriscaldamento, condensa; valvola anticondensa): solo valori approvati dall'assistenza. Nessun hardware, nessun taglio di corrente | **da studiare** (utente: menu e foto; poi assistenza) |
| D2 | **Shelly sulla linea dedicata** del caminetto (es. Shelly Pro 1PM su guida DIN, con misura di potenza), comandato da Node-RED `.20` | taglio notturno solo se: caminetto spento da ≥ 2–3 h, Shelly < 115 W (solo standby o pompa), nessuna accensione prevista, fascia decisa dall'utente (es. 23–06); corrente ridata a fine fascia e ≥ 15 min prima di ogni avvio | **la scheda ESP `.22` è sotto la stessa alimentazione**: un comando durante il taglio verrebbe perso e la scheda ne presumerebbe l'effetto (retained `/command` riallinea lo stato senza premere tasti) → durante il taglio **prima ridare corrente, attendere la scheda online, poi inoltrare** (oppure rifiutare il comando con messaggio). Mai tagliare a caminetto caldo (smaltimento del calore residuo). Avvisi: taglio voluto / corrente ridata; scheda non online entro 5 min = avviso vero. Una notte in prova, poi una con l'utente. Ritorno indietro: Shelly sempre acceso | **solo dopo D1** e valutazione |

**Dal manuale d'uso** (Palazzetti MULTIFIRE 45 IDRO, cod. 004775620, rev. 12/2016, fornito
dall'utente il 17/10/2026):

- caminetto a **pellet e legna**; la pompa (gruppo idraulico, componente 11) è comandata dalla
  scheda elettronica (uscita POMPA) in base alla **SONDA CALDAIA** (acqua nel corpo del caminetto);
- la **soglia di avvio della pompa non è nei menu utente** (MENU SET LAVORO, MENU TIMER, MENU SET
  STUFA: orologio, lingua, tipo menu, start ext., funz. totale, ore residue, auto on/off): è un
  parametro tecnico del **CAT** (Centro Assistenza Tecnica) → D1 si fa con l'assistenza;
- in lavoro si imposta solo **SET T. ACQUA 60–85 °C** (temperatura dell'acqua che il caminetto
  mantiene modulando la potenza);
- sicurezze: termostati a riarmo **85 °C e 92 °C**; **valvola di scarico termico a 94 °C** con
  serpentina alimentata dall'acqua di rete, che raffredda il caminetto in caso di black-out
  (arresto della pompa). Utile per D2 (protezione in caso di taglio con caminetto caldo), ma da non
  usare come funzionamento normale: scarica acqua di rete e interviene a temperatura di sicurezza;
- la scheda ha una porta **RS 232** e gli ingressi **START EXT.** e **GSM FIRE CONTROL**: possibile
  strada futura per leggere lo stato vero del caminetto (temperatura acqua, acceso/spento) invece
  di presumerlo dai tasti premuti. Da verificare, fuori da questa fase.

Secondo manuale fornito (Palazzetti **ECOFIRE IDRO** con pannello EASY TOUCH, cod. 004772531,
07/2013): **non è il modello di casa** (pannello a icone touch; il Multifire 45 ha il display LCD con
i 6 tasti che la scheda ESP simula). Anche lì la pompa non è regolabile dall'utente: il menu
installatore sceglie solo la configurazione dell'impianto (1–4) e il menu di servizio è del CAT.

Raccolta non ufficiale dei codici dei menu nascosti Palazzetti (da forum, in possesso
dell'utente, **non nel repository**): nessuna informazione sul circolatore per nessun modello.
Eventuale passo prima del CAT: **solo lettura** del menu Installazione a caminetto spento e freddo,
foto di ogni schermata, nessuna conferma o modifica; **non** entrare nei menu Fabbrica, Banca dati
(ripristina le impostazioni di fabbrica) e Test uscite (attiva direttamente coclea, resistenza,
ventilatore, pompa).

**Tabella dei parametri «WATER PUMP»** (estratto fornito dall'utente il 17/10/2026; fonte e
modello di riferimento da confermare, due colonne di valori):

| Parametro | Descrizione | Valori | Intervallo |
|---|---|---|---|
| PAR67 | T media mandata/ritorno per passaggio da anti-condensa a lavoro pompa | 50° / 50° | 30–70 |
| **PAR68** | **T media mandata/ritorno per start pompa** | **35° / 40°** | 30–70 |
| PAR69 | deltaT mandata/ritorno massimo per modulazione pompa | 9° / 7° | 0–15 |

**Coerente con i dati:** la pompa a caminetto spento parte con il puffer intorno a 34–36 °C (§7) →
probabile **PAR68 = 35**. Con la pompa in moto l'acqua del caminetto resta alla temperatura del
puffer, quindi la media mandata/ritorno resta sopra 35 °C e la pompa non si ferma: è il ciclo
osservato. **Ipotesi da proporre al CAT: PAR68 = 45** (dentro l'intervallo e sotto PAR67 = 50).
Stima dai dati dell'inverno 2025-26: con la pompa attiva solo sopra 45 °C le ore a caminetto
spento scenderebbero da ~1.110 a ~100 (≈ −90 %). Prima: leggere sul caminetto il valore reale di
PAR68 (e PAR67) senza modificarlo.

Domande per il CAT (D1): nome e valore attuale del parametro di attivazione/arresto del
circolatore; valore massimo consentito con impianto a puffer (ipotesi 45–50 °C); effetti su
anticondensa e protezione del corpo caldaia; se esiste un'isteresi separata per l'arresto.

Misura del risultato di D1 o D2: ore nello stato «90 W» e raffreddamento del puffer in finestre
quiete, prima e dopo, con lo stesso metodo di §7.

Idee successive (fuori da questa fase): abbassare la mandata della caldaia via OpenTherm per più
condensazione; con la futura pompa di calore, preriscaldo del puffer con l'eccesso fotovoltaico.

## 5. Avanzamento

| Data | Cosa | Esito |
|---|---|---|
| 17/10/2026 | Idea discussa e approvata a tappe; verificata la soglia della pompa (35/30 °C, non 37); verificato che la caldaia è spenta da marzo | documento creato |
| 17/10/2026 | Tappa 0: inventario dei feed EmonCMS (sotto) e lettura di un giorno d'inverno campione (14/01/2026) | storico disponibile da 9 inverni; prime osservazioni in §7 |
| 17/10/2026 | Tappa 0: analisi dello stato «90 W» del caminetto su tutto l'inverno 2025-26 | pompa del caminetto probabilmente attiva a fiamma spenta con puffer ≥ 34 °C; puffer che si raffredda circa il 50 % più in fretta (§7) |
| 17/10/2026 | Tappa 0: lavoro della caldaia dopo ogni partenza della pompa del pavimento (3 inverni, 991 partenze) | con puffer ≥ 38 °C la caldaia lavora circa la metà nelle 2 ore successive (§7) |
| 17/10/2026 | Decisione dell'utente sulla dispersione: prima la soglia della pompa del caminetto (D1), poi, dopo attenta valutazione, lo Shelly sulla linea (D2) | §4, interventi sulla dispersione |
| 17/10/2026 | Letto il manuale d'uso del Multifire 45 Idro | soglia della pompa solo tra i parametri tecnici del CAT; valvola di scarico termico a 94 °C per i black-out; porta RS 232 sulla scheda (§4) |
| 17/10/2026 | Letto il manuale ECOFIRE IDRO (Easy Touch) | non è il modello di casa; conferma che il circolatore non è regolabile dall'utente |
| 17/10/2026 | Letta la raccolta non ufficiale dei codici dei menu nascosti | nessun parametro del circolatore; possibile lettura del menu Installazione senza modifiche prima del CAT (§4) |
| 17/10/2026 | Tabella dei parametri «WATER PUMP» (PAR67–69) | PAR68 «T media mand/rit per start pompa» 35 °C coerente con i dati; ipotesi PAR68 = 45 da verificare sul caminetto e con il CAT (§4) |

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
