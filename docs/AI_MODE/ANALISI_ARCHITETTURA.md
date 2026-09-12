# Analisi e architettura proposta

## Obiettivo

Aggiungere ad Android una modalità AI che permetta di interrogare la casa, consultare spiegazioni documentate e ricevere riepiloghi spontanei, sia nella scheda dedicata sia vocalmente. L’app deve accettare anche richieste vocali complesse sullo stato della casa. In una fase successiva permettere la preparazione e l'attivazione di regole in linguaggio naturale.

## Sistema esistente

- Raspberry `192.168.1.15`: EmonCMS, Node-RED e broker MQTT; acquisizione e funzionalità domotiche aggiunte dall'utente.
- Raspberry `192.168.1.20`: Node-RED e broker MQTT; gestione ambientale, climatizzazione, Digital Twin Router e Predictive Reserve.
- Android: Compose, stato corrente tramite `MqttManager`, storico tramite `EnergyRepository`.
- Contratto di riferimento: [RUNTIME_MQTT_CONTRACT.md](../Architecture/RUNTIME_MQTT_CONTRACT.md).
- I feed storici e le capacità di comando sono cataloghi diversi. Un feed non implica che esista un comando pubblico equivalente.

Le esportazioni esaminate sono fotografie del sistema, non una verifica del deployment in tempo reale né della cronologia delle versioni.

## Prima versione

### Interrogazioni

- Stato corrente, consumi AC, confronti tra periodi.
- Intervalli SOC sotto soglia e incroci FV/AC.
- Transizioni VMC, dopo la validazione della codifica dei feed storici.
- Spiegazioni degli eventi per cui esistono motivazioni registrate.

Ogni risultato esplicita periodo e fuso orario, sorgenti, copertura, esclusioni e natura della conclusione: osservazione, motivo registrato, ricostruzione o previsione.

### Interazione vocale — requisito confermato dall'utente

La voce è parte del progetto di consultazione, insieme alla scheda dedicata:
lettura vocale dello stato della casa e delle risposte, acquisizione di domande
in italiano e gestione di più richieste nella stessa frase.

Casi richiesti:

- «Quante luci sono accese?» — contare le entità fisiche validate, senza duplicare
  alias o gruppi; dichiarare gli stati sconosciuti o non aggiornati.
- «Mi dici le temperature della stanza living e dell'acqua sanitaria ACS?» —
  riconoscere entrambe le misure, recuperarle e fornire una risposta unica con
  nomi comprensibili e unità. Il mapping di living e ACS deve derivare dalle
  sorgenti reali; non si assegnano qui topic o feed non verificati.

Flusso previsto: domanda vocale → trascrizione visibile → interpretazione delle
richieste → letture e calcoli verificabili → risposta nella scheda e sintesi
vocale. Testo e voce devono comunicare gli stessi valori, la stessa freschezza
e gli stessi limiti; la voce può riassumere i dettagli disponibili nella scheda.
Se una delle misure manca, rispondere alla parte disponibile e dichiarare quella
mancante. Se un nome identifica più sensori, chiedere quale intende l'utente.

La scheda espone gli stati di ascolto, elaborazione e riproduzione, consente di
interrompere ascolto e lettura e resta utilizzabile senza voce. Modalità di avvio
del microfono, eventuale parola di attivazione, letture spontanee, comportamento
in background e scelta dei servizi di riconoscimento/sintesi restano da definire.
Questa nota non sceglie un fornitore né introduce ascolto continuo.

Criteri di accettazione:

- Entrambi gli esempi funzionano in italiano, anche come domande vocali.
- La richiesta composta produce una risposta per ogni misura riconosciuta.
- Luce sconosciuta non significa spenta; temperatura assente non significa zero.
- Risposta vocale e scheda coincidono nei fatti e distinguono dati correnti e obsoleti.
- Trascrizione errata, microfono non disponibile o riconoscimento fallito permettono
  correzione o nuovo tentativo senza perdere l'accesso alla scheda.
- La consultazione vocale non pubblica comandi agli attuatori.

Requisito registrato l'11 settembre 2026. Per l’implementazione del primo sottoinsieme e lo stato delle verifiche al 12 settembre, vedere [avanzamento stato e voce](AVANZAMENTO_STATO_VOCE.md).

### Riepilogo spontaneo

Esempio illustrativo, non stato attuale: «Giuseppe, risultano accese 3 luci. La caldaia è in funzione al 12% e la Powerwall è al 35%. Con la produzione e i consumi previsti, domani potrebbe raggiungere il 100%.»

- All'apertura: sintesi dello stato disponibile.
- Ad app aperta: aggiornamento per variazioni rilevanti, con deduplicazione e intervallo minimo configurabile.
- App chiusa: notifiche facoltative in una fase separata, dopo la scelta del trasporto e delle preferenze.
- Letture e previsioni vengono presentate separatamente. La previsione include orizzonte, istante di elaborazione e condizioni.
- Dati non aggiornati non vengono trattati come OFF o zero. Il conteggio luci richiede un insieme validato di entità fisiche, evitando alias e duplicati.
- Un riepilogo non genera un comando ai dispositivi.

## Componenti proposti

1. **Adattatori delle sorgenti:** EmonCMS, eventi climatici, apprendimento, previsioni, stato pubblico MQTT; MariaDB se la verifica ne conferma l'utilità.
2. **Catalogo semantico:** significato dei feed, unità, segni, codifica degli stati, provenienza, validità temporale, sostituzioni dei sensori e capacità pubbliche disponibili.
3. **Qualità e calcoli:** selezione delle serie, esclusioni tracciabili, integrazione della potenza, differenze di contatori, conteggio transizioni e incrocio di intervalli.
4. **Ricostruzione degli eventi:** associazione tra motivazione, condizioni, comando proposto e riscontro fisico.
5. **Servizio conversazionale:** interpreta la domanda e richiama operazioni definite; riceve risultati numerici calcolati dal backend.
6. **Servizio dei riepiloghi:** decide quando comunicare e costruisce una sintesi. Frasi predefinite sono sufficienti per gli stati semplici; chiamate al modello solo quando utili.
7. **Interfaccia Android:** schermata dedicata, testo, indicatori, tabelle, grafici, dettaglio delle evidenze, acquisizione delle domande vocali e lettura delle risposte. Riconoscimento e sintesi vocale restano separati dalle letture e dai calcoli delle sorgenti.
8. **Motore di regole futuro:** validazione, simulazione, persistenza e attivazione esplicita; traduzione verso le sole capacità pubbliche ammesse.

Proposta di trasporto per il nuovo servizio: API HTTPS separata. È un'aggiunta da concordare; non sostituisce il traffico MQTT attuale. Ambiente, linguaggio, hosting e fornitore del modello sono ancora da scegliere. Credenziali del modello nel backend.

Il modello non riceve dieci anni di campioni: il backend seleziona periodi e risoluzioni, calcola risultati e fornisce evidenze limitate alla richiesta. Testi nei log e nomi dei dispositivi sono dati, non istruzioni da eseguire. Nessun SQL, shell o flow arbitrario generato viene eseguito.

## Contratto concettuale dei risultati

Un risultato dovrebbe includere:

- identificativo richiesta, periodo assoluto e `Europe/Rome`;
- istante di generazione e freschezza delle sorgenti;
- testo, indicatori numerici e serie per grafici/tabelle;
- riferimenti a feed/eventi e metodo di calcolo;
- campioni attesi/disponibili, intervalli esclusi e motivazioni;
- distinzione tra osservato, documentato, ricostruito e previsto.

Lo schema API definitivo sarà progettato dopo la validazione dei casi iniziali.

## Qualità dei dati

Politica richiesta dall'utente: eliminare dalle analisi ordinarie anomalie sporadiche verosimilmente legate a sviluppo, riavvii o glitch hardware. Le esclusioni operano su viste di analisi; non sugli archivi originali.

Un candidato glitch viene valutato per isolamento temporale, ritorno al livello precedente, plausibilità fisica e confronto con altre misure. Una variazione persistente e confermata da più sorgenti non viene scartata solo perché elevata.

I transitori con valori di ripiego, come temperatura camera zero e previsione `N/A`, possono essere esclusi dalla ricostruzione ordinaria. Se lasciano un vuoto causale, la risposta lo dichiara; non inventa una spiegazione alternativa. L'origine da riavvio rimane un'ipotesi finché non confermata.

I contatori richiedono controllo di reset e unità; la potenza richiede integrazione temporale con gestione dei buchi. Un campione nullo resta mancante. Il costo stimato richiede tariffa e criterio concordati. La stima di costo AC non coincide automaticamente con il costo del prelievo dalla rete.

## Registro decisionale futuro

Riutilizzare prima i log esistenti. Le integrazioni eventualmente necessarie riguardano:

- versione effettiva di strategia/configurazione e periodo di attivazione;
- identificativo della valutazione e correlazione richiesta/risposta/commit;
- valori realmente utilizzati, freschezza e uso di fallback;
- causa specifica e cause concorrenti, prima della sostituzione con un codice generico;
- timer, soglie effettive e stato interno rilevante;
- separazione fra intenzione, comando emesso e riscontro fisico.

Le regole future devono chiarire durata, ripristino, priorità rispetto alla strategia e alle protezioni, gestione di riavvii e duplicati. La disponibilità di presenza, contatti finestra o altri trigger va validata prima di renderli attivabili.
