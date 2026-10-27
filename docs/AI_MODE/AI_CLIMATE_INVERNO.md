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

## 6. Diario

| Data | Fatto |
|---|---|
| 27/10/2026 | Progetto aperto. Audit della logica e dell'accensione delle 08:05 (sezioni 1–3). Nessuna modifica. Risposte dell'utente (sezione 5); deciso lo studio preliminare sui dati (5-bis). |
