# Unificazione di Node-RED e dei broker MQTT (`.15` + `.20`)

Studio di fattibilità del 23/10/2026, richiesto dall'utente. **Nessuna modifica fatta**: tutti i dati
vengono da letture in sola lettura delle due macchine e dagli export dei flussi del 23/10. Dopo un primo
esame della destinazione `.15` l'utente ha chiesto di **valutare la destinazione `.20`** (§4–§6).

Obiettivo dell'utente: una sola istanza Node-RED e un solo broker, portando i tab così come sono e
riducendo i collegamenti MQTT fra i due Node-RED. Vincoli: la logica del caminetto non si modifica;
OpenSprinkler non si tocca; ogni passo con conferma esplicita.

## 1. Situazione al 23/10/2026

| | `.15` (emonPi) | `.20` (domopi) |
|---|---|---|
| Hardware | Raspberry Pi 4 (Rev 1.4), 2 GB | Raspberry Pi 4 (Rev 1.5), 2 GB |
| Sistema | Raspbian 10 Buster, **32 bit**, fuori supporto | Debian 11 Bullseye, 64 bit |
| Disco | SD 64 GB: sistema **4,1 GB (891 MB liberi, 78 %)**, dati emoncms 55 GB (7,7 GB usati) | SD 64 GB: 59 GB, 42 GB liberi |
| Node.js / Node-RED | 12.22 / **1.2.9** | 22.23 / 5.0.8 |
| Mosquitto | 1.5.7, solo autenticati | 2.0.11, solo autenticati, porta 1884 in sola lettura (ACL) |
| Flussi | 19 tab (4 disattivati), 681 nodi | 22 tab, 1446 nodi |
| Altri servizi | emonhub (`/dev/ttyAMA0`, RFM2Pi), emoncms + emoncms_mqtt, feedwriter, redis, mariadb (emoncms), demandshaper | house-ai (backend, gateway, LAN), Tailscale, Pi-hole, MariaDB `domotica`, ntfy, otmonitor, apache, lighttpd |
| Carico | load 0,1; ~1,4 GB disponibili | load 0,1; ~1,1 GB disponibili |

### Client collegati ai broker (connessioni aperte il 23/10 sera)

- **Broker `.20`:** Tasmota `.67/.68/.69` (termostati) e `.252` (frigorifero), Zigbee2MQTT `.119`,
  OpenSprinkler `.3`, Node-RED `.15`, Node-RED `.20`; l'app quando è in casa (indirizzo predefinito
  `192.168.1.20` nelle impostazioni).
- **Broker `.15`:** emonhub ed emoncms_mqtt (locali), ESP caminetto `.22`, ESP VMC `.23`, ESP pompa
  pavimento `.21` (scollegato dal 23/10), Shelly 1 `.51/.52/.53`, Shelly EM `.59`, un ESP in `.54`,
  Zigbee2MQTT `.119`, Node-RED `.20`, otmonitor (gira sul `.20`).
- Il broker degli ESP è nel `secrets.h` dei firmware (`MQTT_HOST`): cambiarlo richiede una nuova
  versione via OTA.

### Collegamenti MQTT incrociati fra i due Node-RED

| Verso | Ingresso | Uscita | Tab |
|---|---|---|---|
| Node-RED `.15` → broker `.20` | 9 | 23 | TASMOTAs (termostati), MQTT, Tesla Powerwall, OpenTherm Caldaia (`homeassistant_voice`, `zara/domotics/alexa/message`) |
| Node-RED `.20` → broker `.15` | 73 | 45 | Digital Twin Bridge, Dashboard Android App, AI Climate (anche Predictive Reserve), Avvisi, ZigBee sensors, Amazon Echo, OwnTracks, mqtt to emoncms, irrigazione, broadlink, start local scripts |

Sette topic hanno lo stesso nome su entrambi i broker: `zara/domotics/#`, `zara/domotics/lights/+`,
`zara/domotics/SmallBathroomThermostatMaxValue` e `…MinValue`, `zara/domotics/livingThemostatMaxValue` e
`…MinValue` (scritto così nel topic), `GiuseppeHomePresence`. Il Digital Twin
Bridge copia `zara/domotics/#` dal `.15` al `.20`. Nessuna variabile `global` ha lo stesso nome nelle
due istanze (25 sul `.15`, 40 sul `.20`); il contesto è in memoria su entrambe.

## 2. Il punto chiave: i collegamenti incrociati non vanno riscritti

In una sola istanza Node-RED i due nodi di configurazione del broker convivono (oggi il `.20` usa già
sia `domopi mqtt` sia `emoncms mqtt`). I ~150 nodi incrociati continuano a funzionare **senza
modifiche**: cambia solo la macchina su cui girano. Trasformarli in variabili `global` o fili non è
necessario e in molti casi è peggio: MQTT conserva i valori retained (che ricostruiscono lo stato dopo
un riavvio), è letto da app, backend ed emoncms ed è osservabile dall'esterno. Fili o nodi `link` hanno
senso solo dove oggi ci sono copie da un broker all'altro (Digital Twin Bridge) e solo dopo
l'unificazione, uno per uno.

## 3. Destinazione `.15`: perché è la più costosa

- Node-RED 1.2.9 su Node 12 non importa bene i flussi del `.20` (gruppi, `link call`) e molti dei 36
  moduli del `.20` non si installano (mysql2, alexa-remote2, tapo, broadlink, onvif, …); il sistema ha
  891 MB liberi. Serve prima **una nuova emonSD (Bookworm, 64 bit)**, cioè reinstallare emonPi e
  migrare emoncms.
- Tutti i dispositivi del `.20` (Tasmota, Zigbee2MQTT, OpenSprinkler, app) vanno ricollegati.
- Restano legati al `.20`: la scheda relè sui GPIO 4/6/22/26 (tab «4 relays»), l'hub Alexa emulato
  (porta 8980, nuova ricerca dei dispositivi), MariaDB `domotica`, house-ai, Tailscale, Pi-hole, ntfy.
- Il `.20` resterebbe comunque acceso per i servizi sopra.

## 4. Destinazione `.20`: valutazione

**Idea:** il `.15` torna a essere solo emonPi (emonhub, emoncms, il suo Mosquitto); tutti i tab del
`.15` passano al Node-RED del `.20` **identici**, con i nodi broker rimappati così:

| Nodo broker nei tab del `.15` | Diventa sul `.20` | Effetto |
|---|---|---|
| `localhost` (`8a9d92d6.75627`) | `emoncms mqtt` (`cfd9898e.1367b8`, `192.168.1.15:1883`) | stessi topic, stesso broker di oggi |
| `192.168.1.20` (`c3a3a5d7.517a08`) | `domopi mqtt` (`3ed6302a.5af928`) | stessi topic, stesso broker di oggi |

Così ogni tab parla con gli stessi broker e gli stessi topic di oggi: **nessun dispositivo va
ricollegato** e la logica (caminetto compreso) resta invariata.

### Vantaggi

- Nessuna reinstallazione: il `.20` è già aggiornato, a 64 bit, con spazio e con quasi tutti i moduli.
- Nessun dispositivo da toccare (né Tasmota, né Zigbee2MQTT, né OpenSprinkler, né ESP, né app).
- Alexa, GPIO, MariaDB, house-ai restano dove sono.
- Ogni tab si sposta e si riporta indietro in pochi secondi (disattiva da una parte, attiva dall'altra).
- Il `.15` alleggerito (niente Node-RED) lascia più memoria e spazio a emoncms; il Node-RED del `.15`
  esce dall'esposizione verso internet.
- Gli strumenti di patch (`deploy.py`, token Admin API, registro del router) lavorano già sul `.20`.

### Svantaggi e rischi

- **Dipendenza del caminetto dal `.20`:** oggi la logica del caminetto e il suo ESP stanno sullo stesso
  `.15`. Dopo lo spostamento la logica gira sul `.20` e passa dalla rete per arrivare al broker del
  `.15`. Il `.20` ha avuto incidenti il 17–21/10 (sottotensione, Mosquitto fermo all'avvio). Mitigazioni:
  salute impianto S1/S2, alimentazione e UPS, spostare il caminetto **per ultimo** o lasciarlo sul `.15`
  (decisione dell'utente, §6).
- **Più servizi su una macchina sola** (una SD): servono prima un'immagine di backup e, in prospettiva,
  un SSD.
- **Credenziali da reinserire:** le credenziali dei nodi sono cifrate con la chiave del Node-RED del
  `.15` e non passano con l'export (emoncms apikey, broker, e-mail, TLS Tesla, FusionSolar). Vanno
  reinserite nell'editor del `.20`, mai nel repository.
- **Passaggio da Node-RED 1.2.9 a 5.0.8 e da Node 12 a 22:** di solito compatibile in avanti, ma va
  verificato tab per tab (funzioni, `light-scheduler`, `bigtimer`, `timerswitch`, `stoptimer` già
  presenti sul `.20`).

### Cosa va adattato (dai flussi del `.15`)

| Elemento | Dove | Cosa fare |
|---|---|---|
| Moduli assenti sul `.20` | OpenTherm (`timeout`), Sheet 1 (`traffic`, `e-mail`) | installare `node-red-contrib-timeout`, `node-red-contrib-traffic`, `node-red-node-email` |
| Twitter (3 nodi `twitter out`) | Sheet 1 | API chiusa: lasciare disattivati o togliere (decisione dell'utente) |
| Volumio, «Flow 1» ×2, «emoncms backup (dropbox)» | tab già disattivati | non spostare; il backup Dropbox (`sudo php …/backup.php`) è locale al `.15`, eventualmente cron |
| File di log `/home/pi/data/{vmc,palazzetti,pompaPavimento}.log` | Sheet 1, caminetto | nuova cartella sul `.20` (es. `/home/pi/data/`), copia dello storico |
| `http in` `/ciao` e `videoalert/14` | shellies, Allarmi Videocamera | trovare chi li chiama (telecamere?) e puntarli a `.20:1880` |
| 12 funzioni con URL `192.168.1.15` | Sheet 1 | nessuna: chiamano emoncms del `.15`, raggiungibile dal `.20` |
| Nodi `emoncms` e server `http://192.168.1.15/emoncms` | Tesla, MQTT, FusionSolar | nessuna (modulo già sul `.20`), solo apikey da reinserire |
| Nodi `link` | SC-1 Tesla Source SHADOW ↔ Tesla Powerwall | spostare i due tab insieme |
| Controlli salute S1 e app (`SystemHealth`: «Node-RED del .15») | `backend/health/`, app | aggiornare dopo lo spegnimento del Node-RED del `.15`; manuale salute |

## 5. Broker: due con ruoli chiari, poi eventualmente uno

Con la rimappatura del §4 il Node-RED unico usa ancora i due broker come oggi. Per arrivare a un broker
solo ci sono due strade, da decidere dopo che i tab sono sul `.20`:

1. **Due broker con ruoli chiari e ponte (consigliato):** il Mosquitto del `.15` resta per emonhub,
   emoncms e gli ESP sul campo; il Mosquitto del `.20` fa da ponte (bridge) verso il `.15` solo per
   l'elenco di topic che servono. emonPi resta autonomo: registra anche se il `.20` è spento. Il ponte va
   progettato a elenco (non `#` in entrambi i sensi) per non creare anelli con il Digital Twin Bridge e
   con i sette topic omonimi.
2. **Broker unico sul `.20`:** emonhub (`mqtt_host = 127.0.0.1` in `emonhub.conf`) ed emoncms (`[mqtt]`
   in `settings.ini`) puntano al `.20`; gli ESP (`.21/.22/.23`, `.54`) ricevono un firmware con
   `MQTT_HOST` del `.20`; Shelly `.51–.53`, `.59` e Zigbee2MQTT si riconfigurano. Si spegne il Mosquitto
   del `.15`, ma emoncms smette di registrare quando il `.20` è fermo.

## 6. Piano proposto (destinazione `.20`)

Ogni passo che modifica una macchina va confermato a parte.

| Fase | Contenuto | Modifiche |
|---|---|---|
| **F0 Backup** | immagine completa delle due SD (voce S4), export dei flussi ed elenco dei campi credenziali per tab | nessuna sui servizi |
| **F1 Analisi** | chi chiama `/ciao` e `videoalert/14`; flussi di `Sheet 1` e caminetto; prova d'importazione dei tab del `.15` in un Node-RED 5 di prova sul `.40` **senza broker** (tab disattivati) per trovare errori di tipo o di versione | nessuna |
| **F2 Preparazione `.20`** | moduli `timeout`, `traffic`, `email`; cartella dei log; script `build.py` che rimappa i nodi broker e marca i tab disattivati | `.20` (con conferma) |
| **F3 Spostamento a tab** | importazione disattivata sul `.20`; poi, un tab alla volta: disattiva sul `.15`, attiva sul `.20`, credenziali, verifica di 24 h. Ordine dal meno al più delicato: led strip, Allarmi Videocamera, shellies, Huawei FusionSolar, Tesla Powerwall + SC-1 (insieme), MQTT, TASMOTAs, OpenTherm Caldaia, Sheet 1, **caminetto per ultimo** | `.20` e `.15`, un tab per volta |
| **F4 Spegnimento** | dopo due settimane senza problemi: `nodered` fermato (non disinstallato) sul `.15`; salute impianto, app e manuali aggiornati | `.15` |
| **F5 Broker** | scelta fra §5.1 e §5.2 e relativo piano | da decidere |
| **F6 Pulizia (facoltativa)** | dove una copia da un broker all'altro non serve più a nessun lettore esterno, sostituirla con un nodo `link` | `.20`, caso per caso |

Ritorno indietro in F3: riattivare il tab sul `.15` e disattivarlo sul `.20` (pochi secondi); in F4
basta riavviare `nodered` sul `.15`.

### Decisioni dell'utente prima di partire

1. **Caminetto:** spostarlo per ultimo sul `.20` oppure lasciare sul `.15` un Node-RED minimo con il
   solo caminetto (più indipendente dal `.20`).
2. **Broker finale:** due broker con ponte (§5.1) o broker unico (§5.2).
3. **Tab obsoleti:** Twitter, Volumio, i due «Flow 1», backup Dropbox: togliere o lasciare disattivati.
4. **Quando:** consigliato dopo F0 (backup) e dopo la settimana di osservazione della salute impianto
   (S2, fino al 30/10); meglio con l'UPS.

## 7. Confronto

| | `.15` | `.20` | macchina nuova (SSD) |
|---|---|---|---|
| Reinstallazione | sì (emonSD nuova, migrazione emoncms) | no | sì (tutto da installare) |
| Dispositivi da ricollegare | tutti quelli del `.20` | nessuno | tutti |
| Elementi legati all'hardware | GPIO, Alexa, MariaDB restano sul `.20` | nessuno | GPIO da spostare |
| Macchine accese alla fine | due | due | tre o due |
| Rischio sul caminetto | reinstallazione del `.15` | dipendenza dal `.20` | dipendenza dalla nuova |
| Valutazione | sconsigliata | **consigliata** | da considerare con il nuovo hardware |
