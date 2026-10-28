# Ricostruzione del `.15` (emonpi) su SSD con sistema nuovo

Decisione dell'utente (28/10/2026): il `.15` non si copia sull'SSD come il `.20`, si **ricostruisce** con
**Raspberry Pi OS Lite** recente e lo **script di installazione ufficiale di emoncms** (EmonScripts), poi si
rimettono dati e configurazioni. Motivi: sistema del 2020 (Raspbian 10, PHP 7.3, Node 12, Node-RED 1.2.9) con moduli
emoncms già rotti dagli aggiornamenti (`postprocess`, corretto a mano il 24/10). Il `.15` comanda **caminetto,
pompa del pavimento e termostati**: la logica del caminetto va riportata **identica** (regola del progetto).

SSD: **Lexar NS100 256 GB** con adattatore StarTech, controllati il 28/10 (SMART ok, `uas`, TRIM possibile).

## 1. Com'è oggi (inventario del 28/10/2026, sola lettura)

| Cosa | Stato |
|---|---|
| Hardware | Raspberry Pi 4 Model B 2 GB, scheda emonPi con radio RFM su `/dev/ttyAMA0` (38400), I²C presente (`/dev/i2c-1`, display dell'emonPi), Bluetooth e Wi-Fi disattivati in `config.txt` |
| Sistema | Raspbian 10 (emonSD-24Jul20), kernel 5.10, SD con 3 partizioni: `/boot`, `/` (4,1 GB, 78 % pieno), `/var/opt/emoncms` (ext2, 55 GB, 7,7 GB usati); `fstab` per nome di dispositivo |
| Rete | IP fisso `192.168.1.15/24` in `dhcpcd.conf`, gateway `.1`, DNS `192.168.1.2` e `8.8.8.8` (il `.2` è da chiarire: il DNS di casa è il `.20`); nome `emonpi` |
| emoncms | core 12.2.0 in `/var/www/emoncms`, moduli in `/opt/emoncms/modules` (backup, demandshaper, postprocess, sync, usefulscripts); Redis con buffer di 300 s; MQTT attivo; dati PHPFina 6,9 GB, PHPTimeSeries 834 MB; database `emoncms` piccolo (0,2 MB) |
| Servizi emoncms | apache2, mariadb 10.3, redis 5, feedwriter, emoncms_mqtt, service-runner, emoncms_sync, demandshaper, emonhub, emonPiLCD (inattivo), log2ram |
| emonhub | interfacce `RFM2Pi` (radio), `MQTT`, `emoncmsorg`; nodi 5 `emonpi`, 6 `emontxshield` |
| Mosquitto | 1.5.7, `allow_anonymous false`, `persistence true`, utenti `emonpi` e `wallbox-core` |
| Node-RED | 1.2.9 su Node 12, progetto attivo **`emonpi`** (c'è anche `ZaraDomotics-Unified`), **32 pacchetti** in palette (`node_modules` 579 MB), context su file (36 KB), Admin API con token |
| DomoPi | backup S4 (`/opt/domopi-backup`, timer), salute impianto (`/opt/domopi-health`, timer), backup emoncms sul QNAP (modulo backup, `/media/nas-backup` NFS con automount), patch Node-RED (TESLA-STATUS.1, TESLA-FW-INSTALL.1, FLOORPUMP-REPEAT.3, …) |
| Altro | crontab: `regular.backup.sh` mensile (da togliere, B6) e `wifi-check` di emonSD; `/opt/dataplicity` con `supervisor` attivo (tunnel remoto `tuxtunnel`); `ufw` abilitato ma inattivo; `huawei_solar.py` in `/home/pi` (non usato da cron, servizi o flow) |

## 2. Risposte dell'utente (28/10/2026)
- **Dataplicity**: non si usa → non si reinstalla (cartella archiviata col resto).
- **`huawei_solar.py`** e il progetto Node-RED **`ZaraDomotics-Unified`**: **servono** → si riportano entrambi.
- **Display dell'emonPi**: non c'è → niente emonPiLCD né I²C.
- **DNS `192.168.1.2`**: sconosciuto all'utente → sul sistema nuovo DNS `192.168.1.20` (Pi-hole) con riserva
  `1.1.1.1`.
- Ancora da decidere: il momento della ricostruzione. Il `.15` resta fermo **2–3 ore** (caminetto e pompa del
  pavimento senza comando): meglio una giornata mite, di giorno, caminetto spento.

## 3. Prima del giorno della ricostruzione (senza fermare niente)
1. **Prova di compatibilità dei flow con Node-RED nuovo sul PC**: il progetto `emonpi` caricato in un Node-RED 4 su
   Node 20 **senza avviare i flow** (avvio sicuro, nessun collegamento ai broker veri): elenco dei pacchetti che non
   si installano o dei nodi cambiati. Da questo dipende se si passa a Node-RED 4 o a una versione intermedia.
2. **Elenco esatto da rimettere**, preso dal backup mirato della notte prima (`rasp-backup/192-168-1-15/snapshots/`):
   `emonhub.conf`, file delle password di Mosquitto, `settings.js` (con `credentialSecret`), progetto `emonpi` con il
   suo `.git`, context su file, token dell'Admin API, configurazioni DomoPi.
3. **Backup emoncms della notte riuscito e verificato** (stato in `status.json`), più un'immagine verificata del `.15`.
4. Scrittura di Raspberry Pi OS Lite (64 bit, versione corrente) sull'NS100 dal PC con Raspberry Pi Imager: utente
   `pi`, nome `emonpi`, SSH con chiave, fuso orario Europe/Rome.

## 4. Il giorno della ricostruzione
| Passo | Cosa | Verifica |
|---|---|---|
| R1 | Spegnere il `.15`, togliere la SD (nel cassetto, etichettata), SSD sulla porta blu, accendere | parte dall'SSD, raggiungibile via SSH (prima con DHCP) |
| R2 | IP fisso `192.168.1.15` (NetworkManager), nome, UART per la radio (Bluetooth spento), I²C, aggiornamenti | rete, `/dev/ttyAMA0` |
| R3 | EmonScripts (installazione ufficiale): emoncms, MariaDB, Redis, Mosquitto, emonhub, servizi emoncms | pagina emoncms, servizi attivi |
| R4 | **Ripristino emoncms** dal QNAP con `drive-restore.sh` (feed e database: stesse chiavi API, così `.20`, app e Node-RED continuano a funzionare) | feed e grafici dei 3 inverni, chiavi uguali |
| R5 | Mosquitto: utenti `emonpi` e `wallbox-core` (file delle password dal backup), `allow_anonymous false`, `persistence true` | collegamenti di `.20`, emonhub, WallBox |
| R6 | emonhub con la configurazione di prima | dati dell'emonTx in arrivo |
| R7 | Node-RED (versione decisa al passo 3.1), pacchetti, progetto `emonpi` con `.git`, `settings.js`, context, Admin API | **nessun nodo mancante**, flow avviati, caminetto e pompa del pavimento come prima (prova con l'utente) |
| R8 | DomoPi: backup S4 (`DEVICE=/dev/sda`, senza `IMAGE_SKIP_DATA` da rivedere), montaggio NFS del backup emoncms, salute impianto, TRIM | primo backup mirato, salute impianto verde |
| R9 | Osservazione di 1–2 giorni: feed, caminetto, avvisi | — |

Ritorno indietro in qualsiasi momento: spegnere, togliere l'SSD, rimettere la SD, accendere (il `.15` torna
com'era; i dati raccolti nel frattempo sull'SSD si riportano dopo).

## 5. Diario

| Data | Fatto |
|---|---|
| 28/10/2026 | Decisione dell'utente (sistema nuovo con EmonScripts). Inventario del `.15` (sezione 1) e piano. Nessuna modifica. |
