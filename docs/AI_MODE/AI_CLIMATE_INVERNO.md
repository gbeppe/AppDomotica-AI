# AI Climate d'inverno: il condizionatore come pompa di calore

Progetto aperto il 27/10/2026 su richiesta dell'utente: rendere più intelligente l'uso del
condizionatore in riscaldamento (comandi `heat*`) con fotovoltaico e Powerwall, man mano che fa più
freddo. Questo documento raccoglie l'analisi della logica attuale, le osservazioni dal vivo e le
migliorie da fare, una alla volta, con il via libera dell'utente.

Fonte della logica: Node-RED `.20`, scheda «AI climate», function `61992c8879c11f66` «Machine Learning
domotico locale ad alta precisione» (1 421 righe; parte invernale alle righe ~354–379). Registro:
`/home/pi/AI_climate/clima_controllo.log` sul `.20` (un evento JSON per ogni cambio di stato, dal
13/09/2026).

## 1. Logica attuale (audit del 27/10/2026, sola lettura)

La stagione dipende solo dal mese: da ottobre ad aprile `INVERNO`.

| Stato | Si accende quando | Si spegne quando | Comando |
|---|---|---|---|
| `HEAT_DIURNO` (08–22) | spento, `surplus` > 80 % del consumo medio storico del giorno (oggi 800 W → 640 W) **e** temperatura < 19,0 °C | è notte, **oppure** `surplus` < −150 W, **oppure** temperatura ≥ 19,5 °C | `eco19heat` |
| `HEAT_SICUREZZA_NOTTE` (22–08) | temperatura < 17,0 °C **e** carica della batteria oltre il minimo notturno (25/40/60 % secondo la previsione solare e i consumi) | è giorno, **oppure** temperatura ≥ 17,5 °C, **oppure** carica ≤ minimo | `eco17heat` |

Comuni a tutte le stagioni:
- acceso almeno 15 minuti, spento almeno 20 (`user_min_run_time`, `user_min_off_time`);
- controllo di guardia: se è «acceso» ma assorbe meno di 10 W per oltre 6 minuti, lo considera spento e
  riprova (3 tentativi, poi spegne AI Climate e lo segnala);
- priorità manuale dall'app (AI-CLIMATE-AC-OVERRIDE.1, fino a 6 ore).

Dati usati: temperatura = sensore `emonth5` (`emon/emonth5/temperature_calibrated`); `surplus` =
produzione − consumo istantanei del Powerwall; carica della batteria; previsione solare di domani;
consumi medi storici. Non usati d'inverno: temperatura esterna, previsione della temperatura, umidità,
stato del caminetto, del puffer, della pompa del pavimento, profilo di riscaldamento, presenza in casa.

## 2. Osservazioni dal vivo

### 27/10/2026, 08:05 — acceso senza bisogno (segnalato dall'utente)
- Evento nel registro: `OFF → HEAT_DIURNO`, comando `eco19heat`, motivo `COMFORT_INVERNALE_BASE`.
- Condizioni: temperatura **18,9 °C** (soglia 19,0: 0,1 °C sotto), esterno 7,7 °C, sole a 11,6°;
  produzione 1 571 W, consumo 443 W, `surplus` 1 128 W.
- Ma quel `surplus` stava **caricando la batteria** (1 110 W, carica 54 %, verso la rete solo 24 W) in una
  giornata con previsione solare bassa (10 kWh, batteria prevista solo al 68 %).
- Il condizionatore ha ricevuto il comando ma **il compressore non è partito**: assorbe ~25 W (unità interna
  in attesa), il consumo della casa è rimasto 330–420 W per tutta la mattina. Il suo termostato misura già
  ~19 °C; il sensore `emonth5` segna 18,9 °C fermo fino alle 10:35.
- Conseguenza: lo stato resta `HEAT_DIURNO` tutto il giorno (si spegne solo a 19,5 °C, che il condizionatore
  stesso non raggiunge perché regola a 19), con ~25 W di attesa (~0,3 kWh al giorno) e uno stato fuorviante
  nell'app. Per l'utente in casa non fa freddo.

## 3. Problemi trovati

| # | Problema | Effetto |
|---|---|---|
| P1 | Soglia di accensione 19,0 °C senza margine né conferma nel tempo; una lettura a 18,9 basta | accensioni per pochi decimi, come stamattina |
| P2 | Il `surplus` comprende la potenza che carica la batteria | d'inverno il condizionatore «ruba» al Powerwall l'energia per la sera; con poco sole la batteria non si riempie e la sera si preleva dalla rete |
| P3 | Spegnimento a 19,5 °C mentre il condizionatore regola a 19 °C | resta «acceso» per ore senza scaldare; lo stato dell'app non corrisponde al vero |
| P4 | Un solo sensore di temperatura (`emonth5`), non si sa se rappresenta la casa; la temperatura della camera arriva 0 (memoria non su file, persa ai riavvii) | decisioni su un punto solo |
| P5 | Ignora caminetto, puffer, pompa del pavimento e profilo di riscaldamento | può scaldare con l'aria quando il caminetto sta già scaldando la casa |
| P6 | Ignora temperatura esterna e previsione: con sole pieno la casa si scalda da sola nel pomeriggio | accensioni al mattino che il sole renderebbe inutili |
| P7 | Stagione per mese (ottobre = inverno) | in una giornata mite di ottobre si comporta come a gennaio |
| P8 | Il registro salva solo i cambi di stato: niente durata reale del compressore, energia, temperatura raggiunta | impossibile valutare se un'accensione è servita |
| P9 | `ultimo_spegnimento` fermo al 17/09 | da verificare: forse il passaggio a spento in alcuni rami non lo aggiorna (influenza il tempo minimo di spento) |

## 4. Migliorie proposte (da decidere e fare una alla volta)

Ogni passo: modifica della function con test offline, prima in **osservazione** quando possibile
(calcola e registra la decisione senza mandare comandi), poi attiva con il via libera dell'utente.

| Fase | Contenuto | Risolve |
|---|---|---|
| W1 | **Registro più ricco**: ogni 5–10 minuti mentre è acceso, potenza assorbita, temperatura interna ed esterna, carica e flusso della batteria; a ogni spegnimento energia consumata e gradi guadagnati | P8 |
| W2 | **Stato vero**: se dopo l'accensione il compressore non parte (assorbimento in attesa ~25 W) perché la stanza è già alla temperatura impostata, tornare `OFF` (o `STANDBY_INVERTER`) e spegnere l'unità; spegnimento a «temperatura impostata − 0,2 °C» invece che +0,5 | P3, P9 |
| W3 | **Soglie con isteresi e conferma**: accendere sotto 18,5 °C per almeno 10 minuti di fila (valori da decidere con l'utente); soglie modificabili dall'app | P1 |
| W4 | **Energia giusta d'inverno**: usare come disponibile solo l'energia che andrebbe in rete, più quella che la batteria non riuscirebbe comunque a immagazzinare (carica prevista a fine giornata dalla previsione solare); priorità alla batteria nelle giornate con poco sole | P2 |
| W5 | **Sole e meteo**: con previsione di sole pieno nelle ore successive e temperatura esterna in salita, rimandare l'accensione del mattino; stagione decisa anche dalla temperatura esterna media, non solo dal mese | P6, P7 |
| W6 | **Integrazione col riscaldamento di casa**: non accendere (o alzare la soglia) se il caminetto è acceso, il puffer è caldo o la pompa del pavimento sta già lavorando; seguire il profilo di riscaldamento | P5 |
| W7 | **Sensori**: decidere quali sensori rappresentano le stanze servite dal condizionatore; memoria su file anche per camera ed esterno | P4 |
| W8 | **Notte**: rivedere la protezione notturna (17 °C, carica minima) quando arriveranno le notti fredde; valutare il preriscaldo nel pomeriggio con il sole invece che di notte con la batteria | — |

Ordine suggerito: W1 + W2 subito (danno i dati e tolgono lo stato falso), poi W3 e W4 prima delle
giornate davvero fredde, W5–W8 man mano.

## 5. Risposte dell'utente (27/10/2026)
- La climatizzazione usa tre ambienti: **living, camera, bagno**. `emonth5` misura il **living**.
- Comfort per ora: **18,0 °C di giorno e di notte, 19,0 °C di sera**.
- **Caminetto e condizionatore non sono legati** (nessun blocco reciproco).
- Nelle giornate con poco sole la priorità è la **batteria** (carica per i carichi della casa).
- Ancora da decidere: vacanza e riscaldamento ad aria.

## 5-bis. Studio preliminare (richiesto dall'utente il 27/10)
Prima di ottimizzare l'algoritmo, capire **se** il condizionatore d'inverno è utile: dati EmonCMS degli inverni
da maggio 2023 (impianto FV portato a 6 kW e Powerwall), modelli dei flussi energetici della casa (caldaia a gas,
caminetto a pellet, solare termico, FV, batteria, carichi), stima di quanto gas e pellet si potrebbero risparmiare
usando il condizionatore con il FV in più e la batteria lasciata carica per la casa, e con quale comfort.
Se lo studio dice che conviene, si ottimizza l'algoritmo (fasi W). Risultati nella sezione 7.

## 7. Studio: il condizionatore d'inverno conviene? (in corso)

### 7.1 Impianto (dall'utente, 27/10)
- Caldaia a gas Fondital Itaca KRB 24 (24 kW) e caminetto a pellet Palazzetti scaldano il **puffer** (800 l);
  il puffer alto alimenta il **pavimento radiante** (orari dei termostati) e l'acqua calda sanitaria. In inverno
  la caldaia è in solo riscaldamento a 40 °C, senza timer: parte quando il puffer scende. Solare termico quasi
  nullo d'inverno.
- Condizionatore Hitachi RAD-25RPE / RAC-25NPE (classe 2,5 kW; in caldo ~3,4 kW): scalda **l'aria del living**,
  fuori dal circuito del puffer.
- Prezzi: gas 1 €/Smc, pellet 6 €/15 kg; energia comprata ~0,22–0,26 €/kWh variabile (PUN + perdite, contributi,
  oneri, accise, IVA; dalle bollette Edison); energia venduta (GSE) da confermare, per ora 0,10 €/kWh.

### 7.2 Dati
- EmonCMS dal 01/05/2023, medie orarie di 36 feed (caldaia 179/181, caminetto 147, puffer 41/42, pompa del
  pavimento 137, living 242, camera 189, bagno 246/388, termostati 234–241, condizionatore 263/362, Powerwall
  303–307). Strumenti e dati di lavoro nello scratchpad della sessione, non nel repository.
- **Temperatura esterna da Open-Meteo** (archivio orario): i sensori di casa segnano in media +1 °C e
  nell'inverno 2025-26 non hanno registrato.
- **Gas reale dalle bollette** (contatore 950 → 1 465 mc, 03/2024–05/2026): inverno 2024-25 (nov–mar) **221 Smc**,
  2025-26 (ott–mar) **~264 Smc**; 2023-24 non coperto (bollette da marzo 2024). Estate: 0 Smc (acqua calda dal
  solare termico).
- Luce comprata d'inverno: 45–224 kWh al mese (FV e batteria coprono quasi tutto).

Pagina con dati e grafici per ora, giorno, mese e anno: [`studio_inverno/index.html`](studio_inverno/index.html)
(«La casa d'inverno»; rigenerabile con gli script della stessa cartella).

### 7.3 Risultati preliminari

| Inverno (nov–mar) | Gradi giorno (18 °C) | Gas reale | Caldaia, ore di fiamma | Caminetto, ore | FV prodotto / immesso | Condizionatore |
|---|---|---|---|---|---|---|
| 2023-24 | 1 652 | — | 825 | 172 | 2 028 / 432 kWh | 135 kWh |
| 2024-25 | 1 755 | 221 Smc | 591 | 295 | 2 200 / 468 kWh | ~10 kWh (dati quasi assenti) |
| 2025-26 | 1 671 | ~264 Smc | 845 | 331 | 2 082 / 471 kWh | 46 kWh |

- La stima del calore della caldaia da fiamma e modulazione (5–24 kW) supera le bollette del 44–52 %: va corretta
  di ~0,66. Calore reale della caldaia ~2 200–2 700 kWh a inverno.
- **Tetto del calore dal condizionatore con energia non usata** (esterno < 15 °C, living < 20,5 °C, 8–20, max
  1 kW elettrico, rendimento 2–4,5 secondo la temperatura esterna, stima prudente da scheda):
  - dal FV che andava in rete: **580–800 kWh di calore** a inverno (57–79 Smc, 40–80 € netti secondo il prezzo
    di vendita);
  - dalla batteria che il mattino dopo era ancora sopra il 20 %: **950–1 380 kWh di calore** (93–136 Smc), da
    verificare: usarla la sera può far comprare energia la notte.
  - Il solo FV non usato equivale a circa **un quarto/un terzo del gas reale**.
- Regressione giornaliera caldaia ~ gradi + ore di caminetto non affidabile (R² 0,33): il caminetto si accende nei
  giorni più freddi e i due effetti si confondono.

### 7.4 Costo del calore (inverno 2025-26, dati dell'utente)
- Pellet: **55 sacchi** da 15 kg a inverno (825 kg, 330 €); energia venduta in **scambio sul posto, ~180 € l'anno**
  per ~2 250–2 400 kWh immessi → ~0,075 €/kWh.

| Fonte | Costo a inverno | Calore | Costo del calore |
|---|---|---|---|
| Gas (caldaia, rendimento ~0,95) | ~264 € | ~2 680 kWh | **~0,098 €/kWh** |
| Pellet (4,8 kWh/kg, rendimento ~0,88) | 330 € + ~39 € di elettricità del caminetto (161 kWh) | ~3 490 kWh | **~0,106 €/kWh** (~0,11 con le perdite in più del puffer) |
| Condizionatore con FV che andava in rete | — | — | **~0,023 €/kWh** (rendimento 3,2) |
| Condizionatore con luce comprata | — | — | ~0,075 €/kWh sopra 5 °C esterni, ~0,09 col freddo |

- Gas e pellet costano quasi uguale; riscaldamento totale ~630 € a inverno.
- **Conclusione provvisoria:** il condizionatore conviene solo «opportunistico»: FV in più (580–800 kWh di
  calore, ~45–60 € a inverno) e batteria che resterebbe comunque carica (fino a ~90–130 € in più, solo se non fa
  comprare luce la notte); mai con luce comprata. Ordine di grandezza **50–150 € su ~630 (8–25 %)**.

### 7.5 Cosa ha fatto davvero il condizionatore (inverni 2023-24 e 2025-26)
- **Ora per ora** (176 ore con il condizionatore acceso e caminetto spento, confrontate con ore dello stesso mese,
  stessa fascia oraria, esterno entro ±1,5 °C): ha funzionato soprattutto alle **12–14 di giornate miti** (13,5 °C
  esterni), 1,06 kWh el./ora. La caldaia in quelle ore lavorava già poco: 0,35 → 0,10 kWh di calore all'ora, cioè
  **0,23 kWh di gas risparmiati per kWh elettrico**. Il living saliva di +0,20 °C/ora contro +0,05 (circa +0,3 °C).
- **Giorno per giorno** (26 giorni con condizionatore > 1 kWh e caminetto spento, contro giorni con esterno entro
  ±1 °C): 5,0 kWh el./giorno, caldaia **−1,8 kWh di calore**, pompa del pavimento −0,3 ore → **0,36 kWh di gas per kWh
  elettrico**.
- **Perché così poco:** la caldaia lavora per tenere il puffer a 40 °C per il pavimento negli orari dei termostati
  (pompa del pavimento: picchi alle 4–5, 13–15 e 17); il condizionatore a mezzogiorno scalda l'aria del living, non
  riduce le perdite del puffer né il lavoro del pavimento, e alza soprattutto la temperatura (comfort).
- **In euro:** 0,36 × 0,098 ≈ **0,035 € di gas per kWh elettrico**, meno di quanto vale lo stesso kWh venduto in
  scambio sul posto (~0,075 €). **Usato come finora, il condizionatore d'inverno ha dato comfort, non risparmio.**

### 7.6 Conclusione dello studio
- Il condizionatore produce calore a basso costo (rendimento ~3) **ma** quel calore fa risparmiare gas solo se
  **sostituisce il lavoro del pavimento nel living**: altrimenti diventa solo qualche decimo di grado in più.
- Strategia proposta («coordinata»): nelle ore di FV in più (e batteria che resterebbe piena) il condizionatore
  scalda il living **e** la temperatura impostata del termostato del living scende di ~1 °C nello stesso periodo,
  così il pavimento del living non chiede calore; fuori da quelle ore tutto torna come prima. Massimo risparmio
  atteso: il calore dal FV non usato (580–800 kWh, ~45–60 € a inverno) più l'eventuale batteria disponibile.
- Senza questo coordinamento il condizionatore d'inverno non conviene economicamente (va bene solo per il comfort
  in giornate miti), e l'algoritmo attuale andrebbe almeno corretto per non usarlo inutilmente (W2–W4).
- Verifica sul campo proposta: in alcune giornate di sole di novembre–dicembre, prova coordinata contro giornate
  simili senza; misura di caldaia, pompa del pavimento e temperature.
- **Decisione dell'utente (27/10/2026): prova sul campo** della strategia coordinata; se i numeri confermano, si
  ottimizza l'algoritmo in quella direzione.

### 7.7 Da fare
- Quota del calore della casa che il condizionatore può sostituire (scalda solo il living): analisi delle ore
  in cui ha funzionato (2023-24, 135 kWh) e del suo effetto sulla temperatura del living e sulla caldaia.
- Rendimento reale del condizionatore col freddo dai consumi misurati (362) e dalla temperatura del living.
- Simulazione ora per ora con la batteria lasciata carica per la casa, poi la conclusione: conviene o no.

## 8. Protocollo della prova sul campo (concordato con l'utente il 30/10/2026)

**Obiettivo:** misurare se il condizionatore che scalda il living con il FV in più, **con il termostato del living
1 °C più basso nello stesso periodo**, riduce il lavoro della caldaia. **Riuscita** se ogni kWh elettrico del
condizionatore fa risparmiare almeno **0,77 kWh di calore della caldaia** (0,075 €/kWh di scambio sul posto diviso
0,098 €/kWh del calore del gas); senza coordinamento lo studio ha trovato 0,23–0,36.

**Quando:** la caldaia a gas si accende l'**8 dicembre** (utente), prima solo in caso di freddo improvviso. Senza
caldaia non c'è niente da misurare: la prova parte con la caldaia accesa e prosegue a gennaio–febbraio finché ci sono
almeno **5 giorni di prova e 5 di confronto**. Prima di allora si preparano gli strumenti.

**Giorni:** di prova con sole previsto (FV previsto del giorno ≥ 12 kWh, soglia da tarare a dicembre, alle 8:30) ed esterno
5–15 °C (alle 10:30); di confronto con tempo simile senza strategia; alternati. Caminetto spento nella
finestra (un giorno con il caminetto acceso si scarta).

**Nei giorni di prova:** 10:30–15:00 condizionatore in caldo 20 °C «solo sole» e termostato del living 1 °C sotto il piano dei
timer; dopo, tutto come prima. Automatico da subito (31/10), con l'utente che controlla gli avvisi la prima settimana; collaudo prima
dell'8/12, senza valore come misura.

**Strumenti da preparare prima dell'8/12:**
1. priorità manuale del condizionatore valida **anche con AI Climate disabilitato**: AI-CLIMATE-AC-OVERRIDE.2
   ([`../../backend/nodered/ai_ac_override2/`](../../backend/nodered/ai_ac_override2/README.md), 31/10);
2. **interruzione** con l'opzione «solo sole»: più di 100 W da batteria o rete per 15 minuti di fila (stessa patch);
   pulsante «Caldo 20° FV» nell'app per i giorni a mano;
3. scheda Node-RED «Prova invernale» ([`../../backend/nodered/winter_test/`](../../backend/nodered/winter_test/README.md), WINTER-TEST.1,
   31/10): sceglie i giorni, alterna prova e confronto, condizionatore e termostato, ripristino alle 15:00; spenta finché non si attiva;
4. **riepilogo** in sola lettura (`report.py`, stessa cartella): calore della caldaia (fiamma e modulazione), ore della pompa del
   pavimento, temperature di living, camera, bagno ed esterna (Open-Meteo), energia del condizionatore e da dove
   viene (FV, batteria, rete), batteria al tramonto e il mattino dopo, ore del caminetto; a fine prova confronto e
   verdetto.

## 6. Diario

| Data | Fatto |
|---|---|
| 27/10/2026 | Progetto aperto. Audit della logica e dell'accensione delle 08:05 (sezioni 1–3). Nessuna modifica. Risposte dell'utente (sezione 5); deciso lo studio preliminare sui dati (5-bis). Studio sui 3 inverni (sezione 7): il condizionatore usato finora ha dato comfort, non risparmio; scelta la prova sul campo della strategia coordinata. |
| 31/10/2026 | Strumenti 3 e 4: scheda WINTER-TEST.1 installata spenta e `report.py`. Collaudo 12:48–13:18 riuscito con AI Climate disabilitato: acceso, termostato 16,5 °C, spento e 17,5 °C alla fine. «Solo sole» provato solo nei test. Attivazione con la caldaia, 8/12. |
| 31/10/2026 | Strumenti 1 e 2: AI-CLIMATE-AC-OVERRIDE.2 e pulsante «Caldo 20° FV» (decisioni dell'utente). |
| 30/10/2026 | Protocollo della prova concordato (sezione 8); partenza con la caldaia, accesa l'8/12 salvo freddo improvviso; strumenti da preparare prima. |
