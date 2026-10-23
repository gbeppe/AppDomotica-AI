# Decisioni dell'utente e lezioni apprese

Registro delle scelte prese con l'utente (con data e motivo) e degli errori o scoperte
tecniche da non ripetere. Complementa [`HANDOFF.md`](HANDOFF.md).

## Decisioni dell'utente

| Data | Decisione | Motivo / note |
|---|---|---|
| 10/10 | Ordine di lavoro: git al sicuro → correggere ciò che è rotto → sviluppo; priorità app e AI Smart; Predictive Reserve solo se impattato | indicato dall'utente |
| 10/10 | Termostati (3.2): lettura e setpoint 16–22 °C a passi di 0,5, esecuzione immediata; slider HVAC invia solo al rilascio | min/max dei termostati sono i setpoint del timer di `.15` (max in fascia, min fuori fascia) |
| 11/10 | Date nelle risposte sempre come "gg/mm alle hh:mm" | lettura vocale naturale |
| 11/10 | Scene luci tv/sleep/all_on/all_off e "prolunga" (presa RF Broadlink) in AI Smart, esecuzione immediata | |
| 11/10 | Scheda AI Smart con pulsante "Parla" (nessun ascolto automatico); lettura automatica solo per domande vocali; tutti i comandi immediati | |
| 11/10 | Risposte brevi ("Ok, accendo la lampada TV."); il dettaglio tecnico va nella diagnostica | stanze: living, camera da letto, cameretta, bagno servizio, locale tecnico, esterno |
| 11/10 | Tailscale necessario **solo fuori casa** | realizzato con variante B (`house-ai-lan` + record Pi-hole) |
| 11/10 | Fase 3.5: riepilogo all'apertura solo energia e temperature, **solo scritto**; avvisi solo casa scollegata e batteria (100% / <20%) in banner su ogni schermata, solo scritti; "Com'è andata oggi" come pulsante | |
| 11/10 | Pompa pavimento: si comanda dall'app (gestita da `.15`) | serviva solo il ritorno di stato |
| 11/10 | **Rimuovere** lampada HiFi e pompa di calore (dispositivi non più presenti) | ROUTER-CLEANUP.1 |
| 11/10 | **Tenere** le altre voci morte del router (modulazione caldaia, humidex esterno, `climate/ai_enabling`) | potrebbero tornare |
| 11/10 | **Tenere** la scheda Node-RED "Amazon Echo" (Alexa non usata), i nodi `tasmota_F294D7`/`shelly1-98F4ABF294D7`, i 55 nodi disattivati (promemoria) | `F294D7` si è rivelato il termostato del bagno (.68), ancora attivo |
| 11/10 | Irrigazione: durata massima 60 min per zona, scheda nel carosello, interruttore controller nell'app | |
| 11/10 | **Non abilitare mai il controller OpenSprinkler** (resta `en=0`) | richiesta esplicita |
| 11/10 | Registrare su EmonCMS i tempi d'irrigazione per calcoli futuri sui consumi d'acqua; i feed li collega l'utente dall'interfaccia | portate per zona da fornire in futuro |
| 11/10 | Robot (Ecovacs, MOVA) per ultimi, priorità bassissima | |
| 11/10 | Grafica interattiva del giardino per l'irrigazione: idea per il futuro | |
| 14/10 | **Tenere la chiave EmonCMS di sola lettura** pur se pubblicata nella storia GitHub | usata da molti sistemi; EmonCMS non più esposto su internet |
| 14/10 | Port forwarding del router **disattivato**, DMZ e UPnP spenti | l'accesso remoto passa da Tailscale |
| 14/10 | Bridge Mosquitto verso il QNAP **rimosso** | il broker sul QNAP non esiste più |
| 14/10 | Caminetto: **riscrittura completa** dello sketch ESP8266 (`palazzetti_v2`), stessi topic; caricata e verificata | la versione di aprile bloccava e rieseguiva il comando retained |
| 14/10 | Caminetto: niente "azzeramento" dal minimo né strada più corta | il pannello premendo "meno" a 1 riparte da 6 |
| 14/10 | Firmware delle schede **nel repository pubblico** (`firmware/`), senza `secrets.h`; **si lavora direttamente lì** (worktree di `feature/ui-topbar`, aperta in Arduino IDE dal percorso reale); anche gli altri sketch della domotica (VMC, pompa pavimento, …) vi verranno spostati | un solo posto per il progetto |
| 14/10 | Password Wi-Fi e `emonpi` (broker `.15`) stampate per errore in sessione: **rischio accettato**, non cambiate | |
| 14/10 | Caminetto: modalità **"Manuale"** = `manuale` su `.15` (nessun automatismo, solo acceso/spento a mano); il **pulsante acceso/spento dell'app è attivo solo in "Manuale"** e invia `alexa/caminetto` on/off | prima il pulsante scriveva su un topic di stato e non comandava nulla (ROUTER-FIREPLACE-MODE.2) |
| 14/10 | Caminetto: accensione **a livello 3** e tabella della potenza automatica di `.15` in `inverno` (<38 °C → 6, <40 → 5, <42 → 4, <45 → 3, **≥45 → 5**) **volute**, da non "correggere"; non modificare la logica del caminetto su `.15` | con pompa pavimento accesa e freddo il puffer alto si scarica in fretta: la fiamma deve restare alta, altrimenti il caminetto entra in un ciclo acceso/spento |
| 14/10 | Caldaia: quando sarà comandabile da Node-RED, progettare isteresi e tempi minimi di acceso/spento per evitare lo stesso ciclo acceso/spento | |
| 14/10 | AI Smart legge l'impianto di riscaldamento (caminetto, puffer, pompa pavimento, caldaia, solare termico) **solo in lettura**; il termostato si descrive come "chiede / non chiede calore" | "riscaldamento spento" era fuorviante con il caminetto acceso |
| 14/10 | Broker MQTT: **un solo utente** (`domotica`) per tutti i client, anonimo chiuso, `domopi` eliminato | semplicità |
| 15/10 | AI Smart energia: riepilogo e «energia?» con produzione, batteria (livello e carica/scarica), **consumo** e rete (**anche l'immissione**); «dettaglio energia» aggiunge **solo i flussi attivi, in italiano**; fatto prima dei test sul caminetto | flussi stimati, non misurati |
| 15/10 | **Guida utente di AI Smart** (`docs/GUIDA_UTENTE_AI_SMART.md`): come si attiva, cosa fa, domande e risposte; **aggiornata a ogni modifica** delle funzioni di AI Smart (regola 9 di `CLAUDE.md`) | |
| 15/10 | AI Smart comanda il caminetto: modalità, orari e potenza automatica **subito**; acceso/spento **solo in Manuale** (fuori da Manuale risponde di passare prima a Manuale, opzione A) e **solo dopo la conferma** nell'app; **livello di potenza escluso** | il caminetto è un apparecchio a combustione |
| 15/10 | Livello di potenza del caminetto da AI Smart **solo in Manuale** e a caminetto acceso, subito e senza conferma; nelle altre modalità vale la potenza automatica attiva/disattiva | decisione dell'utente; verificato 15/10 che in Manuale .15 non cambia il livello (rimasto a 3 dalle 16:57 alle 17:23 con potenza automatica attiva, nessun comando di livello) |
| 15/10 | Conferma di acceso/spento del caminetto anche **a voce**: dopo un comando vocale il microfono si apre a lettura finita; vale solo la parola singola «confermo»/«conferma» («sì» escluso), riconosciuta nell'app e mai inviata al backend; tasti sempre disponibili; validità 60 s | scelta dell'utente: «sì» è troppo facile da sentire per sbaglio (TV, altre persone) |
| 15/10 | Luci IR: un nodo che ascolta lo stesso topic su cui le sue catene pubblicano lo stato deve riconoscere la propria eco; l'anti-loop "scarta se uguale al precedente" non basta con due comandi opposti in volo (LIGHTS-ECHO-GUARD.1) | incidente 15/10 21:27: doppio tocco su Soggiorno Main, 85 invii IR alternati in 2 minuti, fermati spegnendo l'emettitore |
| 16/10 | **Tutti i comandi di AI Smart sul riscaldamento chiedono conferma** (caminetto: modalità, orari, potenza automatica, livello, acceso/spento; termostati; in futuro pompa pavimento e caldaia), una sola per richiesta, con la domanda in fondo alla risposta e il campo `confirmation` (prompt, done) nella risposta del backend; con Manuale nella stessa conferma l'app attende 3 s prima di acceso/livello. Luci e scene immediate. Regola 10 di CLAUDE.md | decisione dell'utente del 15/10 sera: impianti a combustione e riscaldamento |
| 16/10 | Conferma a voce: il testo letto prima di aprire il microfono **non deve contenere la parola di conferma** (finiva con «…tocca Conferma.») e il microfono si apre 1 s dopo la lettura; un test lo verifica | prova del 16/10 09:16: «metti il bagno a 21» inviato senza conferma dell'utente, quasi certamente per l'eco della voce del telefono |
| 16/10 | Firmware: spostando le credenziali in `secrets.h` **ignorare i blocchi commentati** e verificare rete e broker contro lo sketch in uso prima di caricare; prima di un OTA controllare la **porta** (l'IDE ricorda l'ultima, era il caminetto) | `floorpump_v2` caricato con la rete commentata `ASUS_2.4`: scheda irraggiungibile, recuperata rinominando per qualche minuto l'access point TL-WA850RE |
| 16/10 | Scheda Clima: lo stato dei termostati diventa **«Chiede calore»** (fiamma arancione) / **«Non chiede calore»** (fiamma grigia), con le stesse parole di AI Smart, al posto del simbolo ⏻ rosso/grigio; da fare insieme al profilo di riscaldamento | il rosso sul simbolo di accensione era letto come «spento»/allarme (prova del 16/10) |
| 16/10 | Scheda Clima, caminetto: tasto acceso/spento con **fiamma arancione + «Acceso»** quando è acceso e **⏻ grigio + «Spento»** quando è spento (prima ⏻ rosso) | stessa lettura sbagliata del rosso come «spento» |
| 16/10 | Richieste di **regole automatiche** in AI Smart: finché la fase H (regole naturali, `PIANO_DI_LAVORO.md`) non è fatta, il backend scarta ogni comando pianificato da una richiesta condizionale e risponde che le regole non sono disponibili; il prompt chiede di non ripeterne valori | prova del 16/10: «…quando il puffer raggiunge i 42 gradi» riformulato con 40 e poi trasformato in uno spegnimento immediato, fermato solo dalla conferma |
| 17/10 | Fase H2: AI Smart crea, elenca e cancella le regole con `create_rule`/`list_rules`/`delete_rule` (parametri chiusi, stesse liste del motore); ogni creazione o cancellazione si conferma come i comandi del riscaldamento; l'app pubblica su `zara/interface/rules/{create,delete}/cmd` e invia ad AI Smart l'elenco ricevuto dal motore | progetto `REGOLE_NATURALI.md` |
| 17/10 | Fase G: notifiche con **ntfy self-hosted sul `.20`** (scelta dell'utente; niente servizi esterni né Google), stesso indirizzo dentro e fuori casa come AI Smart (Pi-hole + certificato in casa, Tailscale fuori); versione fissata vecchia di settimane, non l'ultima appena uscita | `backend/ntfy/README.md` |
| 17/10 | Avvisi della casa (scelta dell'utente: tutti e sei) su `domopi-casa`: una volta al passaggio, stessa notifica non prima di 2 ore, rientro solo se l'avviso era partito; soglie dei sensori fissate dopo aver misurato la frequenza reale (bagno circa ogni 3 minuti) | `backend/nodered/house_alerts/README.md` |
| 17/10 | Lezione: un client MQTT con **nome casuale e sessione persistente** lascia una sessione orfana a ogni avvio (362 sul `.20`, Mosquitto 146 MB). Nome casuale ⇒ sessione pulita; sul broker sempre `persistent_client_expiration` | `HANDOFF.md` §7 |
| 18/10 | Motore delle regole **attivato** dall'utente (in generale), dopo aver cancellato le due regole di prova (puffer > 42 → spegni caminetto; luci alle 22 ogni giorno) | `REGOLE_NATURALI.md` |
| 18/10 | Lezione: sul `.15` un caminetto acceso a mano non si spegne con la fascia oraria (in Manuale gli orari non contano): AI Smart non deve far credere il contrario | `HANDOFF.md` §7 |
| 18/10 | **Comandi a tempo** per tutti i dispositivi comandabili (ordine T1 luci/scene/caminetto, T2 VMC con velocità, T3 piscina, T4 condizionatore tramite sospensione o forzatura di AI Climate); ogni orario futuro è una regola «una volta» con data, **attiva subito** senza giorno di prova | `AI_MODE/COMANDI_A_TEMPO.md` |

## Lezioni tecniche

| Tema | Lezione |
|---|---|
| Digital Twin Router | Le tabelle di traduzione sono in cache nel context del nodo e si ricostruiscono solo se cambia la chiave `router_initialized_vNNN`: incrementarla in ogni patch al registro (ROUTER-CLEANUP.2). I test offline devono simulare un context già inizializzato dal codice vecchio. |
| Router e `.15` | Ogni voce del router ha il suo broker (`home` = `.20`, `emon` = `.15`): la logica di riscaldamento gira su `.15` e ascolta solo il suo broker. Il router legge invece gli stati `zara/domotics/#` da entrambi, quindi un valore retained sbagliato su uno dei due falsa lo stato. Verificare sempre su entrambi i broker che il comando arrivi a destinazione. |
| Node-RED MQTT | I nodi `mqtt in` in auto-detect trasformano `"1"` nel numero `1`: le regole di stato devono accettare entrambe le forme. |
| Node-RED deploy | Il flow live è quello del progetto (`~/.node-red/projects/domopi/`); `~/.node-red/flows_domopi.json` è una copia vecchia di giugno. |
| App MQTT | Ogni nuovo dominio di topic va aggiunto a `MqttManager.subscribeToUnifiedTopics` (l'irrigazione all'inizio risultava "Stato non ricevuto"). |
| App MQTT | I comandi d'irrigazione non vanno mai accodati offline (partirebbero più tardi a orari inattesi). |
| App MQTT | Cambiando credenziali, `connect()` deve forzare la riconnessione (prima restava la sessione vecchia). |
| App telecamere | La WebView di sistema non mostra un flusso MJPEG aperto come pagina: usare il lettore nativo (`TinyCamClient`). |
| App campi password | Molte tastiere nascondono gli appunti nei campi password: serve un pulsante "Incolla" esplicito. |
| AI Smart | Una funzione nuova del backend va provata **dal telefono**, non solo sul backend: il profilo di riscaldamento (17/10) dava «Schema evidenza non riconosciuto» perché l'app non leggeva `heating_profile.v1` e il test di contratto non guardava quel file (23/10). Ogni modulo che produce risultati va nell'elenco di `AssistantSchemaContractTest`. |
| Energia | I kWh dei periodi vengono dai **contatori cumulativi** scelti dall'utente nell'app EmonCMS MySolarBattery (23/10): integrare la potenza Tesla e i buchi di dati perdevano energia. Consumo casa: Tesla 312, come l'app dopo il cambio di `use_kwh` (37 → 312): emontx3 HomeReading differisce dal Powerwall in modo **variabile** (−5…+19 % al giorno a inizio ottobre, poi −34 % il 21/10 e +81 % il 22/10), non di un 12 % fisso come avevo detto confrontando una sola settimana: confrontare giorno per giorno prima di concludere. Il feed 313 `site_instant_power_KWh` è il saldo netto con la rete, non il prelievo: prelievo/immissione dalla potenza Tesla 305 integrata (i contatori emontx3 26/29 vengono dalla pinza CT1 dell'emonTx, invertita dal 19/10). Il «rete → casa» dell'app è `import_direct = use − battery_discharge − solar_direct` (`mysolarpvbattery.js`, `derive_flows`, verificato sul sorgente GitHub e sulla copia del .15): con `use` da emontx3 e il resto da Tesla dava 11,6 kWh contro 3,8 misurati. |
| MQTT | Prima di chiudere un broker agli anonimi, guardare il log delle connessioni (sul `.15` serviva `log_type notice`, temporaneo) e riavviare: ogni client si ricollega e mostra il suo utente. Leggere i log ruotati **ordinati per data** (`zcat` li dà dal più recente). Un «Socket error on client <unknown>» subito dopo «New connection» può essere un test TCP di raggiungibilità (salute impianto) o MQTT 5 verso Mosquitto 1.5 (`.15` accetta solo 3.1.1), non per forza una password sbagliata. Un nome utente con uno spazio finale viene rifiutato. |
| Luci radio | Un codice Broadlink RF con troppe ripetizioni incorporate (secondo valore del codice in `/home/pi/broadlink/SharedData/jsonIrCode`) può essere letto dalla presa come due pressioni: acceso → spento. La luce TV (3on, 21 ripetizioni) si accendeva e rispegneva; con 5 ripetizioni resta accesa (23/10). Il campo «fix» del nodo RM ripete l'intero codice, cioè allunga la trasmissione invece di accorciarla. Prima di concludere su Node-RED, verificare il file di flow attivo: con i progetti è `~/.node-red/projects/<nome>/`, non `~/.node-red/flows_<host>.json`. |
| emonTx | Il segno della potenza dell'emonTx dipende dall'**adattatore di tensione AC-AC**: staccato/ricollegato girato (o sostituito) il 19/10, tutte le pinze misurano al contrario (CT1 rete, power3 pompa pavimento, power4 caminetto). Dopo interventi su alimentatori e prese, confrontare CT1 con la rete del Powerwall (stesso segno sopra 150 W). In EmonCMS i feed «emontx3» sono in parte copie del Powerwall (GridPower 22 = 305, SolarPower 32 = 307): leggere i processi degli input prima di fidarsi del nome. |
| App test | I test unitari Android non hanno `org.json` reale né `com.sun.net.httpserver`: usare `kotlinx.serialization`/regex e un mini server su socket. |
| OpenSprinkler | L'API `/co?mqtt=` vuole il JSON **senza** graffe esterne, altrimenti salva un valore malformato che rompe `/jc` e `/ja`. OpenSprinkler pubblica su MQTT ma non accetta comandi MQTT. |
| Tasmota | Le credenziali MQTT si cambiano dalla pagina web locale (`/cm?cmnd=Backlog MqttUser …; MqttPassword …`), così la password non passa sul broker. |
| Mosquitto | Con un `password_file` attivo, un client che invia utente senza password viene rifiutato anche se l'anonimo è ammesso. Ricaricare (`systemctl reload`) non chiude le sessioni aperte. |
| Backend `.20` | Python **3.9**: `HTTPError.close()` fallisce se l'errore non ha corpo; provare i test su `.20` prima del commit. |
| Groq | Piano gratuito ~8000 token/min ≈ 2 domande al minuto; distanziare le prove di ~35 s. **Una domanda usa ~7700 token** di sola richiesta (istruzioni, catalogo, schema; misurati il 21/10 con la VMC) + ~300 di risposta: consuma quasi tutto il minuto. A minuto esaurito Groq risponde **413** («Request too large… Requested N», dove N è il consumo recente), non 429: il gateway lo tratta come «troppe domande, riprova tra 60 s» (`20261021-budget`). Il 21/10 avevo stimato ~6000 token dai caratteri e ridotto `max_completion_tokens` a 1536: **inutile** (misurare con `usage`, non stimare). Log `plan tokens …` nel journal di `house-ai-gateway`. Rimedi: snellire istruzioni, catalogo e schema sotto ~4000 token, o Groq Dev Tier. **22/10:** c'è anche un limite di **200.000 token al giorno** (finestra mobile; 429 «tokens per day (TPD)»), esaurito dalle prove dell'agente nella notte: il telefono resta bloccato anche lui. Le prove vanno contate sul budget giornaliero (una suite completa ≈ 41 × 2000 ≈ 80.000 token col prompt per argomento, ≈ 330.000 col vecchio: impossibile in un giorno). Il gateway nasconde il corpo dell'errore (`from None`): per diagnosticare leggere il messaggio, non solo il codice. Soluzione adottata: istruzioni e strumenti per argomento (`20261022-topics`). |
| Previsione FV | Il flow falliva in silenzio e solo alle 23:00: ora ritenta fino alle 05:30 e registra gli errori (SOLAR-FORECAST.1). |
| Router / Tailscale | Dopo un riavvio del router (IP pubblico dinamico) `tailscaled` su `.20` può restare scollegato dal control plane: `sudo systemctl restart tailscaled`. |
| Telefono | Una volta la rete mobile con Tailscale acceso non funzionava: si è risolto riavviando il telefono. |
| Rete | Il redirect 80→8980 su `.20` (vecchia emulazione Alexa) dirottava il traffico Tailscale verso EmonCMS: rimosso il 10/10 (`docs/NETWORK/IPTABLES_ALEXA_REDIRECT_REMOVAL.md`). |
| Firmware caminetto | `/esp8266/palazzetti/command` è retained sul broker di `.15`: a ogni riconnessione la scheda lo riceve di nuovo. La versione di aprile lo rieseguiva (pressioni non volute, livello sfasato); la v2 lo usa solo per riallinearsi. Il livello è presunto: il caminetto non comunica nulla (`emon/focolare/powerraw` = livello richiesto). |
| Node-RED MQTT | Nel nodo `mqtt out` di Node-RED 5 l'impostazione Retain del nodo, se è `true` **o `false`**, prevale su `msg.retain`: per decidere per singolo messaggio va lasciata vuota. Il router inviava così orari e modalità del caminetto a `.15` senza retain (ROUTER-FIREPLACE-RETAIN.1). |
| Caminetto | `.15` rilegge i topic del caminetto ogni 30 min (xx:09:59, xx:39:59) e controlla la finestra oraria ogni minuto: l'accensione e lo spegnimento da orario arrivano entro pochi secondi (test del 14/10: `on` 16:30:42, `off` 16:55:12). Il controllo automatico della potenza parte 5 min dopo l'accensione e si ripete ogni 5 min. |
| Arduino IDE | Uno sketch aperto tramite collegamento simbolico compare in sola lettura (lucchetto): l'IDE risolve il percorso reale e lo considera fuori dalla cartella aperta. Aprire sempre dal percorso reale. |
| Arduino IDE | Su Ubuntu 26.04 l'IDE 2 non disegnava testi di editor, bottoni e menu (arduino-ide#2957): non era GPU, Wayland, tema GTK, nome del font né font mancanti. Risolto lanciandolo con un fontconfig ristretto (`~/.config/arduino-fontconfig`, icona di menu "Arduino IDE 2.3.10"); dettagli in `firmware/README.md`. |
| Sicurezza | Nella prima sessione sono state stampate per errore alcune credenziali (password del bridge QNAP, vecchie credenziali `domopi`, chiave EmonCMS; il 14/10 password Wi-Fi ed `emonpi` leggendo uno sketch): tutte ora non più valide o con rischio accettato. Leggere i file di configurazione filtrando le righe con segreti. |
| Alimentazione dei Raspberry | Dal 17/10 15:35 il `.20` (Pi 4) ha avuto 492 cadute di tensione (~13/h; nessuna da aprile) e il 18/10 23:15 si è riavviato all'inizio di un deploy (nulla applicato). Risolto il 19/10 con un alimentatore nuovo; `get_throttled` 0x0. Un alimentatore dedicato per ogni Pi (5 V 3–4 A); **niente caricatori multiporta** (rinegoziano e tolgono corrente a tutte le porte, unico punto di guasto); in alternativa un alimentatore Mean Well 5 V da guida DIN. Prima di un deploy controllare `vcgencmd get_throttled`. Lo spegnimento brusco del 18/10 23:15 ha lasciato **vuoti (0 byte)** tutti i file scritti nei 10 minuti prima (copie delle patch in `~/AI_patches` e il backup appena creato), con file system integro: dopo un'interruzione di corrente ricontrollare gli ultimi file scritti; dopo una copia sugli host fare `sync`. |
| Avvio di Mosquitto | Il listener 1884 è legato a `192.168.1.20`: se Mosquitto parte prima del DHCP fallisce («Cannot assign requested address») e systemd si arrende (21/10, dopo lo spegnimento). Drop-in `/etc/systemd/system/mosquitto.service.d/wait-network.conf`: `After/Wants=network-online.target`, `StartLimitIntervalSec=0`, `RestartSec=5`. |
| Persistenza di Mosquitto | Con `persistence false` i retained (modalità, orari, profilo, regole) si perdono a ogni riavvio del broker. Lasciarla `true`; se `mosquitto.db` cresce troppo (54 MB, 146 MB di memoria il 17/10) spostarlo nel backup e riavviare (21/10: memoria da 146 a 7 MB). |
| Router ASUS | Il 21/10 il `.15` non era raggiungibile dal PC in Wi‑Fi (ARP incompleto, in entrambi i versi) ma sì dal `.20` via cavo, senza blocchi nel router: risolto **riavviando il router**. Dopo **ogni** riavvio del router controllare Tailscale sul `.20` (`tailscale status`): il 14/10 e il 21/10 era offline e si è ripreso con `sudo systemctl restart tailscaled`. Progetto per accorgersene da soli: [`AI_MODE/SALUTE_IMPIANTO.md`](AI_MODE/SALUTE_IMPIANTO.md). |

## Fatti utili sull'impianto

- Timer dei termostati su `.15` (light-scheduler "SOLO caminetto"/"Con Caldaia" secondo
  `palazzettiModalita`): setpoint = max in fascia, min fuori fascia; riscrive al cambio fascia, al
  cambio di min/max e ogni 8 h. La modalità vacanza su `.20` forza 17 °C ogni 2 h; gli scheduler
  termostati di `.20` sono disattivati.
- Scene luci (Scene Dispatcher Node-RED): tv (lampada TV + soggiorno accesi, libreria e lampadario
  TV spenti), sleep (tutto spento dopo 2 min), all_on (niente in vacanza; con ECO solo lettura e
  TV), all_off (tutto, ~35 s). "Prolunga" = presa RF Broadlink ("luce allarme"/"luci natale").
- Topbar LOCALE/REMOTO, icona build e comando di rilascio sono portati dal progetto WallBox
  (`/home/giuseppe/AndroidStudioProjects/new2/WallBox`, branch `develop`).
- Grafica energia della Home ispirata all'artifact dell'utente "WallBox Home multi-sorgente";
  tutte le icone modificabili tranne l'immagine della Tesla Powerwall.

## Predictive Reserve

Fatti runtime verificati il 10/10 su un export live (da riverificare prima di lavorarci):

- Vive solo in Node-RED `.20`, scheda "AI Climate - Predictive Reserve"; la catena è: controller
  clima (`61992c88…`, uscita 6) → link call (timeout 2 s) → SHADOW Engine → Final SHADOW Decision →
  Arbitration Snapshot → Shared Arbitration Gate → Pre-start Result → "AI Climate NIGHT_DRY Commit"
  → broadlink → `zara/domotics/AC/command`.
- Due interruttori indipendenti: `pr_control_enabled` (dall'app via Router →
  `casa/clima/cmnd/predictive_reserve_control_enabled`) e `pr_operating_mode` SHADOW/ACTIVE
  (solo da un inject in Node-RED; l'app lo legge da PR-MODE.1). Controllo ON + SHADOW = avvio
  notturno NIGHT_DRY sempre negato. Solo eco26dry è qualificato.
- Nessun nodo scrive più `predictive_reserve_shadow.log` dal 06/09 (House AI lo dichiara archivio).
- Il controller usa la previsione FV (`previsione_kwh_domani`) per il SOC minimo notturno.
