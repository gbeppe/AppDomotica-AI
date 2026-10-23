# HANDOFF — punto di partenza per una nuova chat o un nuovo agente

Aggiornato al **14 ottobre 2026**. Leggere questo file per primo; poi
[`DECISIONI_E_LEZIONI.md`](DECISIONI_E_LEZIONI.md) e
[`SICUREZZA_RETE_E_CREDENZIALI.md`](SICUREZZA_RETE_E_CREDENZIALI.md).

Il progetto governa una **casa reale**: ogni modifica a Node-RED, MQTT, backend o
dispositivi ha effetti fisici (luci, piscina, irrigazione, clima, caldaia).

## 1. Regole di lavoro con l'utente (Giuseppe)

1. **Lingua**: italiano.
2. **Prima l'analisi**: verifiche in sola lettura sono sempre benvenute; modifiche ai file del
   repository solo dopo aver concordato i prossimi passi.
3. **Conferma esplicita prima di ogni commit, push, deploy** (backend `.20`, Node-RED,
   Mosquitto, router, dispositivi) e prima di installare l'app. Un'approvazione vale per la
   sequenza proposta, non per le successive.
4. **Mai stampare segreti** (token, password, chiavi API) nell'output: leggerli da file e
   passarli direttamente ai comandi. Il repository GitHub è **pubblico**.
5. **Mai attivare il controller OpenSprinkler** (`en=0`) né cambiarne lo stato di propria
   iniziativa (richiesta esplicita dell'utente).
6. Ogni incremento coerente: test, commit con documentazione aggiornata nello stesso commit
   (vedi [`AI_MODE/POLITICA_FILE_E_RIPRESA.md`](AI_MODE/POLITICA_FILE_E_RIPRESA.md)).
7. Proporre il lavoro nell'ordine concordato (sezione 7) e segnalare impatti sul Predictive
   Reserve di qualunque modifica app/AI.

## 2. Repository, branch e worktree

| Cosa | Dove |
|---|---|
| Repository | `github.com/gbeppe/AppDomotica-AI` (pubblico) |
| Checkout `main` | `/home/giuseppe/AndroidStudioProjects/DomoPiAndroidApp` |
| **Linea attiva** | branch **`feature/ui-topbar`** nella worktree `~/.cache/Google/AndroidStudio2026.1.4/aia/agents/DomoPiAndroidApp-ai-mode` |
| Altri branch | `feature/ai-home-assistant` (storico AI, fuso), `feature/ts-mig-1-subnet-routing` (fuso in ui-topbar) |

Il 14/10/2026, dopo le prove sul campo (slider caminetto e barra in alto), `main` è stato
portato per avanzamento rapido su `feature/ui-topbar` (`e71ccef`). Si continua a lavorare su
`feature/ui-topbar` e si riporta `main` in pari (`git merge --ff-only`) dopo le verifiche sul
campo; niente commit diretti su `main`.

Contenuto: app Android (Kotlin/Compose, `app/`), backend House AI (Python 3.9 stdlib,
`backend/house_ai/`), patch Node-RED versionate (`backend/nodered/<patch>/`), documentazione
(`docs/`), tool di rilascio (`tools/`).

## 3. Host e dispositivi

| IP | Cosa | Note |
|---|---|---|
| `192.168.1.1` | Router ASUS | port forwarding OFF, DMZ off, UPnP off dal 14/10; IP pubblico dinamico |
| `192.168.1.20` | **Raspberry "domopi"** | Node-RED (projects mode, flow live in `~/.node-red/projects/domopi/flows_domopi.json`), Mosquitto (1883 con utente `domotica`, anonimi ammessi dal 19/10 da richiudere; 1884 sola lettura c1321; drop-in di avvio dopo la rete dal 21/10), Pi-hole (DNS di casa), Apache, backend House AI, Tailscale (subnet router `192.168.1.0/24`, Serve). Grafana **disinstallata** il 15/10 (leggeva ~7 MB/s dalla SD: attesa I/O al 95%, luci IR in ritardo di 4-5 s); restano configurazione e `/var/lib/grafana` (543 MB). Memoria scarsa (1,9 GB, swap 100 MB pieno): evitare `journalctl -f` lunghi |
| `192.168.1.15` | emonPi / EmonCMS | broker MQTT "emon" con autenticazione, Node-RED proprio in **modalità progetti** (flussi attivi `~/.node-red/projects/emonpi/flows_emonpi.json`; `~/.node-red/flows_emonpi.json` è vecchio), EmonCMS `/emoncms` (dati su `/var/opt/emoncms`). SSH dal PC `.40` con la chiave `~/.ssh/id_ed25519_codex_domopi`, utente `pi`, dal 16/10. I timer dei termostati (tab TASMOTAs) **non inviano nulla**: leggono min/max e «pompa pavimento abilitata» dal broker del `.15` mentre app e router li scrivono sul `.20`, e la modalità del caminetto vi si aggiorna solo con la pompa abilitata. 16/10: `apt-get clean`, `/` da 110 MB a 974 MB liberi |
| `192.168.1.40` | PC di sviluppo (Ubuntu) | build e rilascio app, SSH senza password verso `pi@192.168.1.20` |
| `192.168.1.121` | Telefono Realme GT (RMX2202) | app installata via adb wireless |
| `192.168.1.4` | NAS QNAP | solo il container registry del progetto WallBox; il broker MQTT non c'è più |
| `192.168.1.22` | ESP8266 caminetto Palazzetti (OTA `palazzetti`) | firmware `palazzetti_v2` dal 14/10, broker `.15`: [`firmware/palazzetti/`](../firmware/palazzetti/README.md) |
| `192.168.1.21` | ESP8266 pompa pavimento (OTA `floorPump`) | `floorpump_v2` dal 16/10, broker `.15`, rete `TP-Link_SalaTecnica` (TL-WA850RE `192.168.1.236`, access point): [`firmware/floorpump/`](../firmware/floorpump/README.md) |
| `192.168.1.23` | ESP8266 VMC Brink | sketch non ancora nel repository |
| `192.168.1.3` | OpenSprinkler 2.2.1 (porta 9003) | 5 zone attive; controller **disattivato** per scelta |
| `192.168.1.67/.68/.69` | Tasmota termostati (bagno servizio / bagno / living) | `.68` = `tasmota_F294D7` (BathroomThermostat), ancora in uso |
| `192.168.1.252` | Tasmota frigorifero (`DVES_83430A`) | cambia IP via DHCP (visto anche come .2/.253) |
| `192.168.1.119` | Zigbee2MQTT (web `:8080`) | sensori Zigbee; un tempo ospitava NetAlertX (non più) |
| `192.168.1.50` | Server web TinyCam Pro 18.1.2 (porta 8083) | gira su un dispositivo Android, si spegne se l'app si chiude |
| `192.168.1.6–.9, .13, .14` | Telecamere IP | |
| `192.168.1.163` | Plex | |

Accesso remoto: **solo Tailscale** (tailnet `tailf30ba8.ts.net`; `.20` = `domopi`).

## 4. Componenti e stato (14/10/2026)

### App Android (`app/`, versione 9.9)
Installata sul telefono: **`bld_a9748986`** (23/10, commit `63061e7`: legge il risultato del profilo di riscaldamento); prima `bld_289903a4` (23/10, commit `5302810`: lettura a voce di date e trattini, `spokenText` in `SpeechText.kt`: «21/10» → «21 ottobre», intervalli, «—» → «non disponibile»); prima `bld_059f6ff7` (23/10: VMC (T2) e risposta ai chiarimenti, `AssistantFollowUp`: la domanda successiva entro 2 minuti porta domanda e chiarimento, microfono riaperto dopo la lettura del chiarimento); prima `bld_bff71307` (17/10: invia il profilo di riscaldamento ad AI Smart, MQTT a sessione pulita; regole a voce; 16/10: profilo di riscaldamento nella scheda Clima, «Chiede/Non chiede calore», caminetto con fiamma; conferma per i comandi del riscaldamento). Funzioni principali e documenti:

| Funzione | Documento |
|---|---|
| Home con carosello (energia stile WallBox, luci, clima, impianti, piscina, ambienti, telecamere, settaggi, garage, **irrigazione**) | [`AI_MODE/FUNZIONI_APP_OTTOBRE_2026.md`](AI_MODE/FUNZIONI_APP_OTTOBRE_2026.md) |
| Barra in alto LOCALE/REMOTO e allineamento build | `docs/NETWORK/UI-TOPBAR.D1.md` |
| **AI Smart** (domande vocali e scritte, comandi luci/scene/termostati, risposte brevi, riepilogo, avvisi, "Com'è andata oggi") | **guida utente [`GUIDA_UTENTE_AI_SMART.md`](GUIDA_UTENTE_AI_SMART.md)** (da aggiornare a ogni modifica di AI Smart); tecnica: [`AI_MODE/INTERAZIONE_QUERY_SMART.md`](AI_MODE/INTERAZIONE_QUERY_SMART.md), `SMART_*.md` |
| Piscina interattiva, Irrigazione, Telecamere native, campi segreti | [`AI_MODE/FUNZIONI_APP_OTTOBRE_2026.md`](AI_MODE/FUNZIONI_APP_OTTOBRE_2026.md) |
| Credenziali solo nel deposito cifrato | [`SICUREZZA_RETE_E_CREDENZIALI.md`](SICUREZZA_RETE_E_CREDENZIALI.md) |

### Backend House AI (`backend/house_ai/`, su `.20`)
Release attiva: **`20261023-coverage`** (dal 23/10): copertura solo nei dettagli, calcolata sul tempo trascorso; a voce «Mancano dati…» sotto il 95 %. Prima `20261023-periods`: nessun chiarimento sul periodo (senza nome = in corso), calendario del gateway con `current_week/month/year` e `last_days_by_name`. Prima `20261023-clarify`: «ricarica» = Powerwall, più dubbi in un solo chiarimento. Prima `20261023-followup`: campo `follow_up` (domanda precedente + chiarimento, risposta pianificata insieme); «mese/ultimo mese» = mese corrente; giorno senza eventi nel log del clima = «nessun cambio di stato» + ultimo evento precedente. Prima `20261022-topics` (22/10): al modello vanno solo istruzioni e strumenti degli argomenti della domanda (`route()` in `model_gateway.py`; nessun argomento riconosciuto = tutto), ~1300–4000 token invece di ~7700. Prima: `20261021-budget` (minuto di Groq esaurito (413) = «riprova tra 60 s», log dei token con `topics=`). Funzioni: VMC a voce (velocità, «fino alle», «dalle… alle…», automatico); comandi a tempo per luci, scene, caminetto; «accendi il caminetto fino alle…» in Manuale non cambia gli orari e avvisa; lettura del profilo di riscaldamento e del piano dei termostati; regole a voce con conferma (durata una volta salvo «ogni/sempre»); conferma per tutti i comandi del riscaldamento; energia adesso con flussi stimati; lettura del riscaldamento. In `/opt/domopi-house-ai/releases/`,
symlink `current`. Servizi: `house-ai-gateway` (Groq `openai/gpt-oss-120b`, `127.0.0.1:8766`),
`house-ai-backend` (`127.0.0.1:8765`), `house-ai-lan` (HTTPS `192.168.1.20:443` per l'accesso in casa),
timer `house-ai-lan-cert` (certificato) e `house-ai-grid-import` (prelievo giornaliero).
### Notifiche sul telefono (fase G, `backend/ntfy/`, su `.20`)
Server **ntfy 2.28.0** (NTFY.1, 17/10): `https://domopi.tailf30ba8.ts.net:8443`, in casa diretto su
`192.168.1.20:8443` (certificato copiato da `ntfy-cert.timer`), fuori via Tailscale Serve `:8443`.
Accesso negato senza credenziali; `domopi-nodered` scrive su `domopi-*`, l'utente del telefono legge
`domopi-*`. App **ntfy** sul telefono iscritta a `domopi-regole` e `domopi-casa` (avvisi della casa, patch `house_alerts`). Dettagli: [`../backend/ntfy/README.md`](../backend/ntfy/README.md).

Storia dei rilasci: [`AI_MODE/DEPLOYMENT_20_TLS_2026-09-13.md`](AI_MODE/DEPLOYMENT_20_TLS_2026-09-13.md).
Limiti Groq gratuiti per `gpt-oss-120b`: **8000 token al minuto** e **200.000 token al giorno** (finestra mobile di 24 h), 1000 richieste al giorno. Oltre → 413/429, gestito con «riprova tra…». Le prove automatiche consumano la stessa quota del telefono.

### Node-RED su `.20` — patch versionate in `backend/nodered/`
| Patch | Cosa | Stato |
|---|---|---|
| `router_dedup`, `router_heartbeat`, `pr_mode` | stati Digital Twin solo al cambio + heartbeat energia; modo PR | live dal 10/10 |
| `solar_forecast` (SOLAR-FORECAST.1) | previsione FV con tentativi notturni e data Europe/Rome | live dall'11/10 |
| `router_cleanup` + `router_reinit` (ROUTER-CLEANUP.1/.2) | stati booleani numerici, via HiFi e pompa di calore, nodi inutili; chiave `router_initialized_v119` | live dall'11/10 |
| `irrigation` (IRRIGATION.1) | scheda OpenSprinkler: comandi, stato, registrazione EmonCMS | live dall'11/10 |
| `ml_cleanup` (AI-CLIMATE-CLEANUP.1) | via 6 copie scollegate del controller ML | live dall'11/10 |
| `router_fireplace_mode` (ROUTER-FIREPLACE-MODE.1) | modalità caminetto dall'app tradotta e inviata a `.15` | live dal 14/10 |
| `router_fireplace_power` (ROUTER-FIREPLACE-MODE.2) | "Manuale" → `manuale`; pulsante acceso/spento → `alexa/caminetto` on/off su `.15`; chiave `router_initialized_v121` | live dal 14/10 |
| `router_fireplace_retain` (ROUTER-FIREPLACE-RETAIN.1) | modalità, orari e potenza automatica del caminetto **retained** sul broker di `.15`; chiave `router_initialized_v122` | live dal 14/10 |
| `lights_echo_guard` (LIGHTS-ECHO-GUARD.1) | "function 17" consuma per 10 s l'eco dello stato pubblicato dalle catene IR: niente ping-pong accendi/spegni con due comandi opposti ravvicinati (incidente 15/10 21:27); backup `/home/pi/AI_backups/nodered/20261015T212857Z-before-lights-echo-guard` | live dal 15/10 23:29; prova del 16/10 08:46 superata (doppio tocco: 4 invii IR, poi silenzio) |
| `heating_profile` (HEATING-PROFILE.1) | nuovo tab: profilo di riscaldamento Spento/Ridotto/Pieno (auto da media esterna 72 h, soglie 10/16 °C da EmonCMS) e timer dei termostati (fascia → massimo, fuori → minimo), parte in **shadow**; sostituirà i timer del `.15`, che oggi non inviano nulla | pronta, da applicare |
| `heating_profile2` (HEATING-PROFILE.2) | il piano dà anche minimo, massimo e prossimo cambio per termostato; nuovo `zara/interface/heating/profile/learning/stat` con le ultime correzioni dell'utente (per la scheda Clima dell'app); backup `/home/pi/AI_backups/nodered/20261016T154922Z-before-heating-profile2` | live dal 16/10 17:49 |
| `rules_engine` (RULES-ENGINE.1) | nuovo tab «Regole»: motore delle regole naturali (fase H1), regole in prova 24 h, motore in **shadow**; backup `/home/pi/AI_backups/nodered/20261016T164006Z-before-rules-engine`; regola di prova `r-puffer-42` (puffer alto > 42 °C → spegni caminetto) creata il 16/10 18:40 | live dal 16/10 18:40; **motore attivo dal 18/10/2026 21:01** (regole di prova cancellate) |
| `rules_notify` (RULES-ENGINE.2) | ogni evento del motore delle regole (intervenuta, in prova, bloccata, creata, attiva, cancellata, rifiutata) diventa una notifica ntfy sul canale `domopi-regole`; token da `~/.config/domopi/ntfy_nodered.token`; backup `/home/pi/AI_backups/nodered/20261017T192951Z-before-rules-notify` | live 17/10 21:29, provata (notifica «Regola rifiutata» 21:30) |
| `house_alerts` (HOUSE-ALERTS.1) | nuovo tab «Avvisi (DomoPi)»: avvisi ntfy su `domopi-casa` (puffer > 85 °C, schede caminetto/pompa offline 5 min, sensori soggiorno/bagno muti 30 min, esterno muto 2 h, cambio profilo, batteria < 20 %); nessun nodo esistente modificato; backup `/home/pi/AI_backups/nodered/20261017T201104Z-before-house-alerts` | live 17/10 22:11, ingressi verificati |
| `rules_engine3` (RULES-ENGINE.3) | comandi a tempo: trigger `at` (orario con data, ≤ 48 h, una volta), `start_active` solo per questi, ritardo > 15 min = saltato, regole chiuse tolte dopo 24 h; notifica «Comando programmato»; sorgenti in `rules_engine/rules.js` e `rules_notify/ntfy.js`; backup `/home/pi/AI_backups/nodered/20261018T191739Z-before-rules-engine3` | live 18/10 21:17, provata dal telefono 22:56 |
| `ai_vmc_override` (AI-CLIMATE-VMC-OVERRIDE.1) | nodo «VMC priorità manuale»: `zara/interface/ventilation/vmc/override/cmd` `{speed, until}` o `"auto"`, stato retained `…/override/stat`; blocco nella funzione AI Climate prima dell'antirimbalzo (la velocità manuale vince fino alla scadenza); backup `/home/pi/AI_backups/nodered/20261021T205755Z-before-ai-vmc-override` | live 21/10 22:58 |
| `rules_engine4` (RULES-ENGINE.4) | azioni `set_vmc_speed`, `set_vmc_override`, `set_vmc_auto`; solo la funzione del motore; backup `/home/pi/AI_backups/nodered/20261021T210015Z-before-rules-engine4` | live 21/10 23:00 |
| `router_floor_pump` (ROUTER-FLOOR-PUMP.1) | abilitazione pompa pavimento instradata sul broker di `.15`, retained (prima andava sul `.20` e non arrivava); chiave `router_initialized_v123`; backup `/home/pi/AI_backups/nodered/20261016T105204Z-before-router-floor-pump` | live dal 16/10 12:52; prova 12:53 superata (abilita/disabilita dall'app arrivano al `.15`, retained; «disabilita» manda 0 all'ESP) |
| `floorpump_repeat` (FLOORPUMP-REPEAT.1, **sul `.15`**) | la logica della pompa ripete l'ultimo comando nella fascia 30–35 °C, necessario per lo spegnimento di sicurezza di `floorpump_v2`; backup sul `.15` `/home/pi/AI_backups/nodered/20261016T112048Z-before-floorpump-repeat` | live sul `.15` dal 16/10 13:21 |
| `floorpump_repeat2` (FLOORPUMP-REPEAT.2, **sul `.15`**) | la function della pompa non va più in errore senza l'ora di spegnimento del caminetto (bloccava ogni comando con la pompa abilitata) | live sul `.15` dal 16/10 14:25 |
| `floorpump_repeat3` (FLOORPUMP-REPEAT.3, **sul `.15`**) | funzione della pompa ripulita: solo abilitazione e puffer, niente orari del caminetto (decisioni identiche alla versione precedente) | live sul `.15` dal 16/10 17:31 |
| `broker_credentials` | script per le credenziali dei nodi broker | usato il 14/10 |

### Fotovoltaico teorico (`backend/solar/`, su `.20`)
`/home/pi/solarEnergy.php`, avviato ogni 5 minuti dal Node-RED del `.20` (scheda «start local scripts»,
falde Eclipse e Viessmann): luce teorica con cielo sereno pubblicata retained sul broker del `.15`
(`emon/planetary/…`) per EmonCMS. **F1 live dal 21/10/2026 23:50** (password in
`~/.config/domopi/mqtt_emonpi.pass`, identificativo MQTT unico, calcolo identico verificato su 576 casi);
backup `/home/pi/AI_backups/solar/20261021T215045Z-before-f1`. Prossime: F2 (ramo `Irradiance=` rotto e
spento), F3 (previsione Open-Meteo). Dettagli: [`../backend/solar/README.md`](../backend/solar/README.md).

### Firmware delle schede ESP
Sketch versionati in [`../firmware/`](../firmware/README.md) (credenziali solo in `secrets.h`
locale, controllo `python3 firmware/check_firmware.py`). Caminetto: `palazzetti_v2` caricato e
verificato il 14/10. VMC e pompa pavimento si aggiungeranno quando verranno verificati.

### Rete e sicurezza
Vedi [`SICUREZZA_RETE_E_CREDENZIALI.md`](SICUREZZA_RETE_E_CREDENZIALI.md): router chiuso,
broker MQTT solo con utente `domotica`, dove stanno le password, come ripristinare.
Accesso AI Smart in casa: [`AI_MODE/ACCESSO_LAN_SENZA_TAILSCALE.md`](AI_MODE/ACCESSO_LAN_SENZA_TAILSCALE.md),
[`AI_MODE/RUNBOOK_ACCESSO_LAN.md`](AI_MODE/RUNBOOK_ACCESSO_LAN.md).

## 5. Procedure operative

### Build e test dell'app (sulla worktree, da `.40`)
```sh
cd ~/.cache/Google/AndroidStudio2026.1.4/aia/agents/DomoPiAndroidApp-ai-mode
export ANDROID_HOME=$HOME/Android/Sdk JAVA_HOME=$HOME/.gradle/jdks/jetbrains_s_r_o_-25-amd64-linux.2
./gradlew testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin --offline --no-daemon -q
```
La build completa può superare 10 minuti: lanciarla in background. I test grafici
(`connectedDebugAndroidTest`) vanno solo sull'emulatore: sul telefono disinstallerebbero l'app.

### Rilascio dell'app (richiede sorgenti committate e pulite)
```sh
rm -rf tools/__pycache__
PYTHONDONTWRITEBYTECODE=1 python3 -B tools/release_android.py
```
Produce `work/android-builds/bld_<id>.apk` e pubblica l'identità retained su
`domopi/build/android` (credenziali MQTT in `~/.config/domopi/mqtt.json` + `mqtt_password`,
utente `domotica`).

### Installazione sul telefono (adb wireless)
Usare l'adb dell'SDK (`~/Android/Sdk/platform-tools/adb`, non `/usr/bin/adb`). L'utente attiva
"Debug wireless"; il telefono si trova via mDNS:
```sh
A=~/Android/Sdk/platform-tools/adb
P=$($A mdns services | awk '/_adb-tls-connect/ && /192.168.1.121/ {print $3}' | head -1)
$A connect $P && $A -s $P install -r work/android-builds/bld_<id>.apk
```
Se mDNS non lo trova o il dispositivo è "offline": chiedere all'utente l'indirizzo mostrato in
Debug wireless o, se le autorizzazioni sono state revocate, una nuova associazione
(`adb pair IP:PORTA CODICE`). Dopo un riavvio del telefono il debug wireless va riattivato.

### Rilascio del backend House AI su `.20`
Da un commit: `git archive <commit>:backend/house_ai` → copia su `.20` → nuova cartella in
`/opt/domopi-house-ai/releases/<data-nome>` con `REVISION`, `PREVIOUS_RELEASE`, `SHA256.json`,
proprietà root → controllo AST e test come `house-ai`
(`sudo -u house-ai env PYTHONDONTWRITEBYTECODE=1 HOME=/tmp python3 -B -m unittest discover -s tests`,
Python **3.9**) → scambio atomico del symlink `current` → riavvio di
**`house-ai-gateway house-ai-backend house-ai-lan`** → verifica → voce in
`DEPLOYMENT_20_TLS_2026-09-13.md`. Rollback: puntare `current` alla release precedente e riavviare.
Se l'app invia un campo nuovo, rilasciare **prima** il backend.

### Patch Node-RED
Ogni patch in `backend/nodered/<nome>/`: `build.py` (genera `update.json`/`rollback.json`/`manifest.json`
da un export dei flow), `test.js` (`node --test`), `deploy.py` da eseguire su `.20`:
`prepare` (verifica che il runtime coincida e salva il backup in `/home/pi/AI_backups/nodered/`),
`apply BACKUP`, `rollback BACKUP`. Usa l'Admin API con il token in `~/.node-red/.cli-config.json`
(dura 7 giorni; se 401 l'utente rifà
`npx node-red-admin target https://domopi.tailf30ba8.ts.net:1880 && npx node-red-admin login`).
**Ogni modifica al registro del Digital Twin Router deve incrementare `router_initialized_vNNN`**
(oggi `v122`), altrimenti restano attive le tabelle in cache.

### Diagnostica MQTT su `.20`
```sh
mosquitto_sub -h localhost -u domotica -P "$(cat ~/.config/domopi/mqtt_domotica.pass)" -v -t 'zara/interface/#'
```
Log broker: `sudo tail -f /var/log/mosquitto/mosquitto.log`. Il broker di `.15` richiede credenziali proprie.

## 6. Cose da fare dell'utente (non di un agente)
- Collegare in EmonCMS gli input `irrigazione/zona_N` e `zona_N_durata_s` ai feed
  (istruzioni in `backend/nodered/irrigation/README.md`).
- Rifare il login Alexa su `http://192.168.1.20:3456` solo se servono gli annunci di "Alexa Speak".
- Eventuale: riservare un IP fisso al dispositivo TinyCam e disattivare il suo MAC casuale.

## 7. Piano concordato (ordine)
**Punto di ripresa (14/10/2026, pomeriggio):** l'utente vuole prima continuare con **prove sul
campo insieme**, poi riprendere lo sviluppo dai punti sotto. Da osservare: accensione e
spegnimento programmati del caminetto (17:00–19:00, modalità acqua sanitaria) con il firmware
`palazzetti_v2`. Idee fuori piano già discusse: nascondere le righe Piscina/Impianti e
l'importato quando la casa è scollegata; portare nel repository gli sketch di VMC e pompa
pavimento.

**Prove del 14/10 sera:** ciclo automatico del caminetto riuscito (modalità Riscaldamento,
`on` alle 16:30:42 a livello 3, potenza automatica 3 alle 16:35:42, `off` alle 16:55:12; app
allineata). Rilasciati in giornata: ROUTER-FIREPLACE-RETAIN.1 (`v122`), house_ai
`20261014-heating` (strumento `current_heating`), app `bld_1804d204`. `main` resta a `a18f08b`.

**Punto di ripresa (15/10/2026), nell'ordine:**
1. Ciclo di test sul caminetto, insieme all'utente.
2. AI Smart dal telefono: «Com'è il riscaldamento in casa?», «Il caminetto è acceso?», «Quanto
   è caldo il puffer?» (attesi caminetto, puffer, pompa pavimento, caldaia, solare termico e
   termostati con "chiede / non chiede calore").
3. Retain: l'utente reimposta dall'app gli orari del caminetto (sul `.20` sono ancora
   16:30/16:55, sul broker di `.15` 17:00/19:00); poi verificare i retained su `.15`.
4. Prova Manuale → acceso → spento, ascoltando entrambi i broker.
5. Da decidere con l'utente: il comando "pompa pavimento abilitata" dell'app va al broker di `.20`
   (`broker: "home"`), mentre `.15` lo legge dal proprio broker; probabile stesso difetto della
   modalità caminetto. Non provarlo senza consenso: con il puffer sopra 35 °C la pompa partirebbe.
6. Caldaia (Fondital Itaca KRB 24, senza alimentazione da marzo): l'utente invierà foto della
   morsettiera della centralina e dell'OTGW e il manuale d'installazione. Poi: capire come parte
   otmonitor su `.20` (non è un servizio systemd), progettare acceso/spento e modalità solo
   ACS / solo riscaldamento / entrambi via `actions/otmonitor` (CH=, HW=, CS=, SW=) con isteresi e
   tempi minimi, e preparare la prova di riaccensione (oggi il gateway chiede CH=0 e DHW=0).
7. Poi unione in `main` con conferma dell'utente.

1. ~~Prove sul campo e unione in `main`~~ fatte il 14/10 (barra in alto LOCALE/REMOTO nei tre
   casi, slider del caminetto anche con `palazzetti_v2`). Da osservare al primo uso reale:
   `on`/`off` del caminetto con il nuovo firmware.
2. **Predictive Reserve**: valutazione e piano di sviluppo definitivo (priorità bassa; include
   3.1-bis "registro decisioni PR"). Fatti runtime: [`DECISIONI_E_LEZIONI.md`](DECISIONI_E_LEZIONI.md#predictive-reserve).
3. **Node-RED, voci minori** dalla verifica del Digital Twin Bridge: doppio percorso WP-C2 dei
   17 dati clima (funzioni 32/34/36 + mappa composita del router; prima verificare chi usa i
   vecchi topic `casa/clima/...`); "function 17" che scarta un comando RF uguale all'ultimo.
4. **Grafica interattiva del giardino** per l'irrigazione (come la piscina; serve un'immagine
   del giardino con le 5 zone; prima una simulazione web).
5. **Robot** (priorità bassissima): Ecovacs Deebot T9 Max Pro Omni e N8 Pro tramite libreria non
   ufficiale (`node-red-contrib-ecovacs-deebot`), MOVA LiDAX Ultra 800 da verificare; Alexa come
   ripiego (il suo login va rifatto).

**Fase P — preriscaldo del puffer con il caminetto (17/10/2026):** idea dell'utente approvata a
tappe (dati → avviso caldaia oltre il 20 % → consiglio del giorno → automatico con ritorno a Manuale).
Diario: [`AI_MODE/PRERISCALDO_PUFFER.md`](AI_MODE/PRERISCALDO_PUFFER.md). Prossimo: tappa 0 (storico
EmonCMS, sola lettura). Prerequisito per le misure dal vivo: caldaia riaccesa.

**Fase S — salute impianto (21/10/2026):** dopo gli incidenti del 17–21/10 (sottotensione, Mosquitto
fermo all'avvio, Tailscale offline, `.15` nascosto dal Wi‑Fi) controllo reciproco `.20`/`.15` con
avvisi e consiglio pronto, riparazioni sicure, pagina nell'app. Progetto:
[`AI_MODE/SALUTE_IMPIANTO.md`](AI_MODE/SALUTE_IMPIANTO.md). Da realizzare **dopo il deploy di T2 (VMC)**.

**AI Smart, margine dei token (22/10/2026):** con `20261022-topics` una domanda manda ~1300 (luci) – ~4000
(regole e comandi a tempo) token invece di ~7700; ~6300 se non si riconosce l'argomento. Limiti del piano gratuito:
8000 al minuto e **200.000 al giorno** (scoperto il 22/10 esaurendolo con le prove: ~90 domande al giorno invece di
~25). Prove: suite parziale (21 casi su 41 nella seconda tornata, poi limite giornaliero) e 15 frasi di comando
tutte corrette; le differenze restanti dalla suite sono attese superate (riepilogo energia, «ultimi N giorni»).
Groq Dev Tier: aggiornamento non disponibile il 21/10 («high demand»), da riprovare.

### Da indagare (16/10/2026)

- **Incidenti 17–21/10 (risolti):** cadute di tensione sul `.20` (alimentatore cambiato il 19/10);
  Mosquitto `.20` fermo all'avvio del 21/10 (drop-in `wait-network.conf`); `persistence false` dal
  19/10 (ripristinata il 21/10, vecchio `mosquitto.db` in
  `/home/pi/AI_backups/mosquitto/20261021T062821Z-before-persistence-network-online`); `.15`
  irraggiungibile dal Wi‑Fi (riavvio del router); Tailscale offline dopo il riavvio del router
  (riavvio di `tailscaled`). Lezioni in [`DECISIONI_E_LEZIONI.md`](DECISIONI_E_LEZIONI.md#lezioni-tecniche).

- **Caminetto acceso a mano e orari (18/10):** sul `.15` (tab «caminetto», funzione MAIN) la parte
  automatica gestisce solo Integrazione Caldaia, Riscaldamento e Acqua Sanitaria; un'accensione manuale
  imposta `centralinaPalazzettiMode = "manual"` e la esclude. Quindi in Manuale gli orari non contano e
  un caminetto acceso a mano non si spegne alla fine della fascia. AI Smart ora lo dice
  (`20261018-fireplace-until`). Risolto con i comandi a tempo (T1, `AI_MODE/COMANDI_A_TEMPO.md`).
  Prossimi: T2 VMC (velocità), T3 piscina, T4 condizionatore via AI Climate.

- **Broker `.20`, sessioni orfane (17/10):** 362 sessioni conservate, 8 collegate; Mosquitto 146 MB,
  `mosquitto.db` 54 MB. Causa: l'app si collegava con nome casuale e sessione persistente (corretto in
  `bld_bff71307`, sessione pulita). Fatto dall'utente il 17/10 22:07: `persistent_client_expiration 2d`
  in `/etc/mosquitto/conf.d/50-session-expiry.conf`, copia in
  `/home/pi/AI_backups/mosquitto/20261017T200738Z-before-session-expiry`, riavvio. Le sessioni orfane
  scadono 2 giorni dopo il riavvio (~19/10 22:07): **verificare il 20/10** sessioni e memoria.
- **`.15`, pulizia del 17/10:** `pompaPavimento.log` (40 MB, 897.759 righe dal 2021) compresso in
  `/home/pi/data/pompaPavimento.log-20261017.gz` (2,7 MB, verificato) e svuotato; rotazione in
  `/etc/logrotate.d/pompaPavimento` (mensile o 5 MB, 12 archivi, `copytruncate`). Nodo «SC1 | SOE Debug»
  disattivato dall'Admin API (era acceso a runtime; nel flow è già spento). Restano errori ogni 20 s
  di «parse sensorN» e «parse speed pump N» (`TypeError`), da guardare.

- ~~Indicatore del termostato del soggiorno rimasto spento~~ (risolto 16/10): la prova registrata
  delle 15:24 è corretta (`POWER ON` → app «acceso» in 10 ms, il ripetitore del `.15` ogni 20 s
  manda il valore giusto, `OFF` alle 15:27:50 → «spento»). Il router invia solo i cambi: un'app non
  connessa in quell'istante resta sul valore vecchio fino al cambio successivo. Da valutare: alla
  riconnessione rileggere i valori retained. Icona da sostituire con «Chiede/Non chiede calore».

## 8. Note di ripresa
- Memoria dell'agente precedente (Claude Code) in
  `~/.claude/projects/-home-giuseppe-AndroidStudioProjects-DomoPiAndroidApp/memory/`: utile ma
  **non necessaria**; tutto ciò che serve è in questo repository.
- Simulazione web della piscina (artifact privato dell'utente):
  `https://claude.ai/artifact/8LYKhUBS2QPHyBJaQKoXbS`.
- Documentazione storica di settembre: [`AI_MODE/README.md`](AI_MODE/README.md) e
  [`AI_MODE/CHECKPOINT.md`](AI_MODE/CHECKPOINT.md) (contesto, non stato attuale).

## 9. Cronologia dei commit funzionali (dal 10/10/2026)

Per il dettaglio di ciascun incremento: messaggio del commit, README della patch
(`backend/nodered/<patch>/`), voce in `AI_MODE/DEPLOYMENT_20_TLS_2026-09-13.md` per i rilasci del
backend, [`AI_MODE/FUNZIONI_APP_OTTOBRE_2026.md`](AI_MODE/FUNZIONI_APP_OTTOBRE_2026.md) per l'app.

| Commit | Data | Cosa |
|---|---|---|
| `c309b59` | 10/10 | feat(lights): semantic light command evidence and router-aligned light IDs |
| `54dea15` | 10/10 | feat(topbar): WallBox-style LOCALE/REMOTO and build alignment indicators |
| `a708d39` | 10/10 | fix(diagnosis): real MQTT message rate and full traffic log |
| `b2f6c45` | 10/10 | feat(nodered): ROUTER-DEDUP.1 publish Digital Twin states only on change |
| `200f3a0` | 10/10 | fix(app): persist House AI token securely and surface EmonCMS errors |
| `7bf26e2` | 10/10 | feat(nodered): PR-MODE.1 expose Predictive Reserve operating mode and readiness (read-only) |
| `d8630bc` | 10/10 | feat(app): show real Predictive Reserve effect from mode and readiness |
| `ce69838` | 10/10 | fix(house_ai): per-source log freshness; SHADOW log declared as archive |
| `42424b2` | 10/10 | feat(nodered): ROUTER-HEARTBEAT.1 heartbeat for steady energy readings inside the deadband |
| `3d3685f` | 10/10 | feat(app): WallBox-style energy flow diagram on the home (fase 3.4) |
| `18cbd1c` | 10/10 | feat(house_ai): current_predictive_reserve tool (fase 3.1) |
| `36c9b8f` | 10/10 | feat(app): send Predictive Reserve state to House AI (fase 3.1) |
| `bc2d707` | 10/10 | fix(app): parse predictive reserve and daily extreme assistant results |
| `00dfda8` | 10/10 | fix(app): grid import summary shows "—" until obtained, never a false 0 kWh |
| `4c026b8` | 10/10 | feat(house_ai): heating thermostats read and setpoint command (fase 3.2) |
| `9ffd069` | 10/10 | feat(app): AI Smart thermostat setpoint commands, slider sends on release |
| `677389e` | 11/10 | feat(house_ai): spoken-friendly dates in every assistant answer |
| `5237ff6` | 11/10 | feat(app): spoken-friendly dates in AI Smart texts |
| `41670a3` | 11/10 | feat(house_ai): light scenes and Prolunga in AI Smart (fase 3.3) |
| `8d0265f` | 11/10 | feat(app): AI Smart light scenes and Prolunga |
| `9785974` | 11/10 | feat(app): voice-first AI Smart query card |
| `1a34e09` | 11/10 | house_ai: short spoken answers, caveats to diagnostics; current_environment tool |
| `6cefacf` | 11/10 | AI Smart: send environment sensors, parse current_environment results |
| `818bd07` | 11/10 | house_ai: HTTPS on the home LAN so AI Smart works at home without Tailscale |
| `fb01303` | 11/10 | AI Smart: error text says Tailscale is needed only away from home |
| `fd8321c` | 11/10 | house_ai: distinct errors for rate limit, planner and sources |
| `51eb224` | 11/10 | AI Smart: short spoken messages for each failure cause |
| `a684b8c` | 11/10 | house_ai: close HTTPError safely on Python 3.9 |
| `528c557` | 11/10 | AI Smart: say 'qualche secondo' for rate-limit waits up to 5 s |
| `e22c58b` | 11/10 | app: sliders and fireplace times send one command per decision |
| `bb2a4d0` | 11/10 | app: fireplace times accept HHMM (numeric keyboards have no colon) |
| `0f3d3f7` | 11/10 | AI Smart: opening summary with temperatures and energy (fase 3.5 A) |
| `2ccde58` | 11/10 | nodered: SOLAR-FORECAST.1 reliable PV forecast (retries, Rome date, logged failures) |
| `5140a4b` | 11/10 | house_ai: day summary endpoint (fase 3.5 C), no model call |
| `67663f3` | 11/10 | AI Smart: 'Com'è andata oggi' button (fase 3.5 C) |
| `acd3eec` | 11/10 | app: written alerts banner for house disconnected and battery (fase 3.5 B) |
| `dc7d808` | 11/10 | house_ai: publish daily grid import for the app's 'Importato ieri / oggi' |
| `7fd505c` | 11/10 | nodered: ROUTER-CLEANUP.1 numeric boolean states, retired devices, unused nodes |
| `9065ff4` | 11/10 | app: drop retired HiFi lamp mapping |
| `0f82b44` | 11/10 | nodered: ROUTER-CLEANUP.2 bump router init guard to v119 |
| `64f2893` | 11/10 | app: interactive pool picture, grey when off and in colour when on |
| `47927b0` | 11/10 | nodered: IRRIGATION.1 OpenSprinkler control, state and EmonCMS logging |
| `6dd6612` | 11/10 | app: Irrigazione screen and dashboard card for OpenSprinkler |
| `749b62b` | 11/10 | app: subscribe to irrigation topics (dashboard showed 'Stato non ricevuto') |
| `764bc47` | 11/10 | nodered: AI-CLIMATE-CLEANUP.1 remove 6 unwired copies of the ML controller |
| `b85fdb1` | 11/10 | app: secrets out of the code, into the encrypted store |
| `558bc00` | 11/10 | app: native TinyCam viewer instead of WebView, clear errors, configurable cameras |
| `42fdb71` | 14/10 | app: secret fields with explicit paste and show/hide |
| `1c8c60d` | 14/10 | nodered: script to set MQTT broker node credentials from a file |
| `b470d26` | 14/10 | app: reconnect MQTT when user or password change |
