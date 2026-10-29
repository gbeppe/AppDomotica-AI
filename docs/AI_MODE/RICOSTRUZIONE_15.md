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
| 29/10/2026 | **Banco** come per il `.20` ([`RICOSTRUZIONE_20.md`](RICOSTRUZIONE_20.md)): Raspberry 3 con SD `bootcode.bin`, SSD **NS100** staccato dal `.15` (non montato), cavo `192.168.1.212`, alias SSH `emonpi-new`. Sistema: **Raspberry Pi OS Lite Bookworm 64 bit** («Legacy» nell'Imager) invece di trixie, perché EmonScripts è provato su Bookworm (e lì l'Imager applica tutte le impostazioni con `firstrun.sh`; tastiera lituana corretta in italiano). Aggiornato, IPv6 tolto, Wi-Fi e Bluetooth spenti, `smartmontools` (`smartd` spento), `git`. |
| 29/10/2026 | **emoncms con EmonScripts** (`stable`, `ecde57d`, come `init.sh` della documentazione ufficiale): `config.ini` da `emonsd.config.ini` con firmware, emonPiLCD, emonsd, Docker, network e one-wire **spenti**, nome `emonpi-new`, password di MariaDB e MQTT **del `.15`** (copiate senza mostrarle, impronte verificate). Risultato: emoncms 12.2.0, PHP 8.4 (Sury), MariaDB 10.11, Redis 7, Mosquitto 2.0.11, emonhub, `emoncms_mqtt`, `feedwriter`, `service-runner`, `emoncms_sync`. Correzione: lo script non riesce a installare il modulo Python `redis` con pip su Bookworm (controlla `python3.11/EXTERNALLY-MANAGED` ma toglie `python3.13/…`), quindi `service-runner` non partiva → `python3-redis` da Debian. **DemandShaper non installato** (sul `.15` è acceso ma senza programmazioni; decisione dell'utente). |
| 29/10/2026 | **Dati di emoncms ripristinati** con lo script ufficiale `drive-restore.sh` dal backup di stanotte sul QNAP (permesso NFS **temporaneo in sola lettura** per `192.168.1.212`, dato dall'utente; montato `ro` in `/mnt/emoncms-backup-ro`): prova a secco, poi ripristino in 12 minuti (dump `emoncms-emonpi-2026-10-29`, 490 file PHPFina 6,9 GB, 56 PHPTimeSeries 831 MB). Confronto col `.15`: 317 feed, 494 input, stesse chiavi API (impronte), stessi dati di ieri su tre feed. **Mosquitto:** file degli utenti del `.15` (`emonpi`, `wallbox-core`, hash `$6$` accettati dalla 2.0.11), `persistence true`; prova d'accesso ok. |
| 29/10/2026 | **Node-RED** 5.0.8 su Node 22 (stessa unità e drop-in del nuovo `.20`, più `banco-safe.conf` con `node-red --safe`), cartella `~/.node-red` del `.15` senza moduli; pacchetti: solo quelli usati dal progetto `emonpi` (light-scheduler, stoptimer, emoncms, bigtimer, timerswitch, timeout, traffic, email) più dashboard (per `ZaraDomotics-Unified`) e Twitter e Volumio. **Twitter e Volumio:** i 3 nodi «Tweet» e `volumio event`/`volumio command` **disattivati** (scelta dell'utente: restano visibili; le funzioni «tweet termocaminetto» e «tweet impianto pavimento» intatte). Nel git del progetto, sul banco: commit `7f44075` «stato dei flow del `.15` al 29/10» (modifiche dal 21/06 mai committate) e `f3699a0` con la sola disattivazione (15 righe). Risultato in modalità sicura: progetto `emonpi` caricato, **nessuno dei 42 tipi di nodo mancante**, 27 nodi disattivati (22 già dell'utente). |
| 29/10/2026 | **Backup S4 e salute impianto** copiati dal `.15` (`/opt`, `/etc`, unità e drop-in dell'immagine) con `nfs-common` e `rsync`; tutti i timer spenti, compresi `emoncms-drive-backup` e la sua verifica. |
| 29/10/2026 | **Caricatore di avvio del `.15`** aggiornato (conferma dell'utente, caminetto spento) come sul `.20`: da 03/09/2020 a **23/09/2026** (canale `default`, stesse impronte del `.20`), configurazione invariata più `USB_MSD_STARTUP_DELAY=2000`, vecchio in `/home/pi/AI_backups/eeprom/`. Riavvio 11:10:35, raggiungibile 11:11:21; Node-RED con flow avviati e collegato ai due broker, 179 feed aggiornati in 2 minuti. In errore all'avvio, da prima e innocui: `emonPiLCD` (nessun display) e `isc-dhcp-server` (punto d'accesso Wi-Fi di emonSD). Cambio del `.15` deciso per il 30/10, di giorno, caminetto spento. |

| 29/10/2026 | **Huawei:** il programma che legge l'inverter via Modbus RTU è `gbeppe/huawei2emoncms` (privato), girava sul **Raspberry 3 usato come banco** (dati fermi dal 28/10 19:31, quando gli è stata tolta la SD). Decisione dell'utente: lettura sul nuovo `.15` (servizio `huawei2emoncms`, venv con `pymodbus` 3.6.9, utente `huawei`, emoncms locale, chiavetta CH341 `/dev/serial/by-id/usb-1a86_USB_Serial-if00-port0`, attivo all'avvio). Tolte le prove: scheda Node-RED «Huawei FusionSolar API» (commit `d861937` del progetto), `huawei_solar.py` e `huawei-solar-lib` non copiati; tenuti input `huawei` e feed 426–437 (usati sul `.20`). |
| 29/10/2026 | **Cambio riuscito.** Prima del fermo: UART per la radio (`enable_uart=1`, senza `console=serial0`), percorsi `/boot/firmware` nel backup, montaggio NFS del backup di emoncms in `fstab`, `/home/pi` del `.15`, un giro di `drive-backup` a sistema acceso (26 min). **Fermo dalle 14:36:57:** fermati Node-RED, emonhub, `emoncms_mqtt`; `feedwriter` rimesso in moto finché i buffer Redis `feed:*:buffer` sono stati scritti (fermarlo subito li perdeva), poi fermati gli altri servizi; ultimo `drive-backup` (14:37–15:05) e `drive-restore` delle differenze sul banco (11 min; ultimi valori 14:36 identici sui feed confrontati); `mosquitto.db`, contesto di Node-RED, `~/data`, crontab (`regular.backup.sh` mensile, senza `wifi-check`); modalità sicura tolta, nome `emonpi`, `drive-backup-path.conf` su `/media/nas-backup/emoncms`, timer di backup, salute ed `emoncms-drive-backup` attivati (immagine settimanale ancora spenta). Vecchio `.15` e banco spenti 15:18; SD vecchia nel cassetto; SSD e chiavetta RS485 sul Raspberry 4; **in rete 15:26:51**, avvio da USB al primo colpo. Correzioni al primo avvio: **emonhub non era attivato all'avvio** (EmonScripts lo collega in `/lib/systemd/system` senza `enable`) → `systemctl enable emonhub`; **`emoncms_mqtt` in crash** con PHP 8.4 (`Unsupported operand types: string - string` in `ratechange`, valore vuoto in `process:ratechange:262` su Redis) → chiave tolta, servizio stabile, 193 feed aggiornati al minuto (179 sul vecchio). Verifiche: radio emonTx3 ed emonTH5, Huawei (232 V, 50 Hz, stato 512), Node-RED senza errori e collegato ai due broker, app ok (utente). Permesso NFS temporaneo per `.212` tolto dall'utente. Da osservare: caminetto alle 17 da orario, pompa del pavimento, termostati, eventuale ritorno del valore vuoto di `ratechange`. |
| 29/10/2026 pomeriggio | Caminetto acceso da orario alle 17:00 dal nuovo `.15`. Nell'app il `.15` restava grigio con i guasti dello spegnimento: la salute impianto (gira come `root`) non trovava `paho` (installato da EmonScripts solo per `pi` in `~/.local`) e l'errore di pubblicazione è silenzioso → `python3-paho-mqtt` da Debian (conferma dell'utente). Backup mirato lanciato a mano (riuscito); controllo dell'immagine settimanale tolto dalla configurazione della salute finché l'immagine resta spenta (copia in `/home/pi/AI_backups/health/`). Salute del `.15`: 31 controlli su 31 ok. |

## 6. Lista del cambio del `.15` (eseguita il 29/10/2026)
1. Prima: `emonhub.conf` del `.15` (il ripristino l'ha già messo), utente e password dell'editor Node-RED e token dell'Admin API (`~/.node-red/.admin-token.json`, `crea_token_nodered.sh`), `huawei_solar.py` e il resto di `/home/pi` utile, crontab (`regular.backup.sh` fino a B6; `wifi-check` no).
2. Fermo: fermare Node-RED e i servizi di emoncms sul vecchio `.15`; un ultimo giro di `drive-backup.sh` e poi `drive-restore.sh` sul nuovo (solo le differenze), `mosquitto.db`, contesto di Node-RED.
3. Sul nuovo: nome `emonpi`; `fstab` con `192.168.1.4:/emoncms-backup /media/nas-backup nfs … x-systemd.automount` e `drive-backup-path.conf` → `/media/nas-backup/emoncms`; togliere `banco-safe.conf`; UART per la radio (`enable_uart=1`, console seriale spenta, Bluetooth spento) e `emonhub` su `/dev/ttyAMA0`.
4. `backup.conf`: `/boot/firmware/config.txt` e `cmdline.txt` al posto di `/boot/…`, `DEVICE=/dev/sda`, rivedere `IMAGE_SKIP_DATA` (i dati di emoncms non sono più una partizione a sé); salute impianto (`emoncms` status sul nuovo montaggio).
5. Spegnere il vecchio `.15` (SD nel cassetto), SSD sul Raspberry 4 della scheda emonPi, accendere; IP `.15` dal router; timer di backup, salute, `emoncms-drive-backup`; togliere il permesso NFS temporaneo per `.212`.
6. Verifiche con l'utente: dati dell'emonTx in arrivo, caminetto (comandi e potenza automatica), pompa del pavimento, termostati, collegamenti di `.20`, app e WallBox al broker, feed che avanzano, MyElectricFlow.
