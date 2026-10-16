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
| App test | I test unitari Android non hanno `org.json` reale né `com.sun.net.httpserver`: usare `kotlinx.serialization`/regex e un mini server su socket. |
| OpenSprinkler | L'API `/co?mqtt=` vuole il JSON **senza** graffe esterne, altrimenti salva un valore malformato che rompe `/jc` e `/ja`. OpenSprinkler pubblica su MQTT ma non accetta comandi MQTT. |
| Tasmota | Le credenziali MQTT si cambiano dalla pagina web locale (`/cm?cmnd=Backlog MqttUser …; MqttPassword …`), così la password non passa sul broker. |
| Mosquitto | Con un `password_file` attivo, un client che invia utente senza password viene rifiutato anche se l'anonimo è ammesso. Ricaricare (`systemctl reload`) non chiude le sessioni aperte. |
| Backend `.20` | Python **3.9**: `HTTPError.close()` fallisce se l'errore non ha corpo; provare i test su `.20` prima del commit. |
| Groq | Piano gratuito ~8000 token/min ≈ 2 domande al minuto; distanziare le prove di ~35 s. |
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
