# Backup a caldo dei Raspberry e passaggio a SSD (fase S4)

Progetto del 24/10/2026, richiesto dall'utente dopo gli incidenti di alimentazione del 17–21/10: poter
rimettere in piedi `.15` e `.20` (sistema + applicazioni) se la SD si guasta, con backup fatti a
sistema acceso, e ridurre il rischio di guasto passando a SSD. Script:
[`backend/backup/`](../../backend/backup/README.md). Voce S4 di [`SALUTE_IMPIANTO.md`](SALUTE_IMPIANTO.md)
e fase F0 di [`UNIFICAZIONE_NODERED_MQTT.md`](UNIFICAZIONE_NODERED_MQTT.md).

## 1. Cosa c'era (verificato il 24/10, sola lettura)

| Host | Backup | Esito |
|---|---|---|
| `.15` | `emoncms-drive-backup.timer` ogni notte (00:00–01:00): script ufficiale del modulo backup di emoncms (`/opt/emoncms/modules/backup/drive-backup.sh` 2.6.0, OpenEnergyMonitor, non modificato), configurato dall'utente verso NFS `192.168.1.4:/emoncms-backup` (`drive-backup-path.conf`, marcatore `.emoncms-backup-target`); feed in sincronia (copia speculare, senza versioni), dump SQL giornalieri (~5 settimane) e settimanali, `emonhub.conf` e `settings.ini`; verifica settimanale domenica 04:00–05:00 | **funziona** (22–24/10, 22 min, ~2,7 MB scritti per notte); ma con `--if-mounted` **salta in silenzio** se la cartella non è montata: la verifica del 18/10 04:37 è stata saltata («not mounted»). Il 24/10 aggiunto il montaggio automatico (§3) |
| `.15` | cron del 1° del mese `~/regular.backup.sh`: export emoncms + flussi Node-RED via rsync su `192.168.1.4/ZaraHome` | arriva, ma i flussi copiati sono quelli **vecchi di giugno** (`~/.node-red/flows_emonpi.json`): il Node-RED del `.15` lavora in modalità progetti (`projects/emonpi/`) |
| `.20` | cron del venerdì 01:01 `~/regular.backup.sh`: solo flussi, credenziali e `settings.js` di Node-RED via rsync su `192.168.1.74/BACKUPS` | **funziona** (23/10 01:00) |
| `.20` | «chiavetta» USB: lettore di schede con SD da 62,5 GB, partizione btrfs `Backup`, montata dal desktop in `/media/pi/Backup2` (maggio–giugno 2026) | **mai scritta** (contatore btrfs +1 per avvio, nessuno script la usa); scheda tolta durante i problemi di alimentazione |

Nessun backup copriva sistema, `/etc`, house-ai, Mosquitto, ntfy, Pi-hole, Tailscale, MariaDB
`domotica`, `~/AI_climate`, i token in `~/.config/domopi` e i flussi veri del `.15`.

Schede attuali: `.20` SanDisk 64 GB del 10/2023, ~2,3 GB scritti al giorno (journal, MariaDB, Pi-hole,
Node-RED); `.15` Samsung 64 GB del **10/2019** (5 anni accesa), ~0,2 GB al giorno.

## 2. Decisioni dell'utente

- Destinazione: **QNAP principale `192.168.1.4`**; il QNAP `192.168.1.74` fa il backup del `.4`.
- Cartella condivisa **`rasp-backup`** (generica, anche per host futuri), una sottocartella per host
  (`192-168-1-20`, `192-168-1-15`, come gli script esistenti).
- NFS solo per `192.168.1.15` e `192.168.1.20`, lettura/scrittura, **nessuno squash** (proprietari dei
  file conservati per il ripristino), cestino di rete **disattivato** (la rotazione cancella ogni notte).
  Nessun utente QNAP: NFS autorizza per indirizzo. Verifica del 24/10 11:31 riuscita da entrambi.
- Consultazione dal PC: cartelle degli host `750` root:0, cioè leggibili dal gruppo amministratori del QNAP
  (gid 0; l'utente `giuseppe` è amministratore, `admin` è disattivato) e da nessun altro: dentro ci sono segreti.
- **L'immagine settimanale è il backup principale**: sul `.4` deve esserci sempre un'immagine pronta da
  scrivere su una SD (o SSD) nuova e ripartire; i giorni dopo l'immagine si recuperano dai **backup
  mirati dei file** (ogni notte, solo le cartelle che cambiano e contano). I dati emoncms restano al
  loro backup notturno.
- Conservazione: 14 istantanee giornaliere + 8 settimanali; immagini A/B + 2 mensili.
- Hardware: **SSD** su entrambi (§6).

## 3. Come funziona

| Livello | Quando | Contenuto | Serve a |
|---|---|---|---|
| **Immagine** (`image`) | `.20` domenica 04:40, `.15` sabato 04:40 (lontano dalla verifica settimanale di emoncms) | file `.img` avviabile con la stessa tabella delle partizioni della SD (stesso identificativo, quindi stessi PARTUUID) e l'ultima partizione ridotta allo spazio usato + 20 % + 1 GiB; aggiornato con `rsync` a sistema acceso; coppia A/B; poi **verificato** e, una volta al mese, copiato come immagine mensile | rimettere tutto su un disco nuovo in ~20 minuti |
| **Backup mirato dei file** (`snapshot`) | ogni notte 03:40 (±10 min) | dump di MariaDB, poi `rsync` delle cartelle elencate in `backup-<host>.conf` in `snapshots/AAAA-MM-GG/`; i file uguali al giorno prima sono collegamenti fisici (non occupano spazio) | recuperare i giorni dopo l'immagine, un file, una configurazione o un database |
| **Esito** | a ogni giro | `/var/lib/domopi-backup/status-{snapshot,image}.json`, copia sul QNAP | avviso della salute impianto (B4) |

Cartelle del backup mirato (prova del 24/10: `.20` 343 MB e 12 mila file, `.15` 63 MB e 7 mila file):

| `.20` | `.15` |
|---|---|
| `/etc` (Mosquitto, house-ai, ntfy, Pi-hole senza la cronologia delle query, systemd), `config.txt` e `cmdline.txt` | `/etc` (Mosquitto, emonhub, systemd), `config.txt` e `cmdline.txt` |
| **tutta `/home/pi` dal 28/10** (richiesta dell'utente: log, script, codici IR di Broadlink in `~/broadlink`, `~/otgw`, `~/irrigation`, `~/AI_patches`, `~/AI_backups`…) senza `~/.node-red/node_modules`, `~/.cache`, `~/.npm`, `~/.local/share/pnpm`; prima copia 558 MB, verificata sul QNAP (Broadlink: 100 file identici) | `~/.node-red` senza `node_modules` (flussi veri in `projects/emonpi/`), `~/data` (log caminetto e pompa), `~/.config`, script in `~` |
| `/opt/domopi-*`, `/var/lib/{mosquitto,ntfy,tailscale}`, `/var/cache/ntfy`, crontab, `/root`, `/usr/local` | `/opt/domopi-*`, `/var/lib/mosquitto`, crontab, `/root`, `/usr/local`, `settings.ini` di emoncms |
| dump `domotica` | dump `emoncms` (dati dei feed: backup emoncms) |

Verifica dell'immagine, prima di marcarla buona (`<immagine>.ok`): `fsck` in sola lettura di ogni
partizione, `cmdline.txt` che punta al PARTUUID della partizione di sistema dell'immagine, kernel,
`fstab` e `init` presenti. Se una verifica fallisce l'immagine non riceve il `.ok`, quella della
settimana prima resta la buona e la salute impianto avvisa.

Coerenza a caldo: l'immagine si avvia come dopo una mancanza di corrente. Sistema e programmi sono
fermi e si copiano bene; MariaDB si ripara da sola all'avvio e in ogni caso c'è il dump fatto un attimo
prima (anche dentro l'immagine). Mosquitto e Node-RED non hanno problemi (lo stato retained si
ricostruisce). Non si fermano servizi: sul `.15` vorrebbe dire fermare la logica del caminetto. I feed
emoncms dentro l'immagine possono essere indietro di qualche minuto: i dati autorevoli sono quelli di
`emoncms-drive-backup`.

Sicurezze: marcatore `.rasp-backup-target` obbligatorio (mai scrivere sulla SD se la cartella non è
montata, stessa lezione di `drive-backup.sh`), un backup alla volta (`flock`), priorità minima di CPU
e disco (il `.20` è sensibile all'I/O: i comandi IR delle luci rallentano), un'immagine interrotta o non
verificata non sostituisce quella buona.

La cartella NFS **non è in `fstab`**: ogni giro la monta su `/mnt/rasp-backup` e la smonta alla fine, quindi al
riavvio non c'è niente da rimontare (i timer hanno `Persistent=true`: un giro saltato a Raspberry spento parte dopo
l'avvio). Se il QNAP cade a metà, il montaggio `soft` fa fallire rsync invece di bloccarlo; in chiusura lo smontaggio
passa a `umount -l` se quello normale non riesce; i backup mirati incompiuti dei giorni prima (`.incomplete-*`) si
cancellano al giro dopo; l'immagine senza `.ok` viene rifatta.

Montaggio del backup emoncms sul `.15` (24/10, decisione dell'utente): in `fstab` la riga di
`192.168.1.4:/emoncms-backup` ha in più `x-systemd.automount,x-systemd.mount-timeout=30` (copia dell'originale in
`/home/pi/AI_backups/backup/20261024T115124Z-before-fstab-automount/fstab`): se all'avvio il NAS non risponde, la
cartella si monta da sola al primo accesso invece di restare smontata fino al riavvio. `drive-backup.sh` accede al
percorso e quindi fa partire il montaggio; con il NAS giù ora si ferma con un errore invece di saltare. Provato:
prima dell'accesso `autofs`, dopo `nfs4`.

Prove di guasto del 24/10 sul `.20` (stato a parte, `DOMOPI_BACKUP_STATE`): cartella inesistente (fermo in 0 s),
QNAP irraggiungibile `192.0.2.1` (fermo in 121 s, «Connection timed out»), marcatore mancante (fermo prima di
scrivere): ogni volta `ok: false` con il motivo, cartella smontata, **nessun file scritto sulla SD**.

Perdita massima dopo un guasto: **un giorno** (immagine + backup mirato della notte); dati emoncms: una notte.

### Spazio previsto

| | `.20` | `.15` |
|---|---|---|
| backup mirato: primo / ogni notte in più | ~0,35 GB / pochi MB–0,2 GB | ~0,06 GB / pochi MB |
| immagine (A, B e 2 mensili) | ~19 GB ciascuna (file sparso) | ~14 GB ciascuna (dati emoncms inclusi) |
| totale stimato | ~80 GB | ~60 GB |

Sul `.4` ci sono 1,6 TB liberi.

## 4. Ripristino

### Da immagine (disco guasto)

1. Dal PC `.40` aprire la cartella `rasp-backup` del QNAP via SMB (utente amministratore del QNAP)
   oppure aggiungere temporaneamente il `.40` alle regole NFS.
2. Scegliere in `<host>/images/` l'immagine (A, B o mensile) con il file `.ok` più recente.
3. Scriverla sul disco nuovo (SD da almeno 32 GB o SSD): Raspberry Pi Imager («Usa personalizzata»)
   oppure `sudo dd if=<immagine> of=/dev/sdX bs=4M conv=fsync status=progress`.
4. Avviare, poi allargare l'ultima partizione:
   - `.20`: `sudo raspi-config nonint do_expand_rootfs` e riavvio;
   - `.15`: `sudo parted /dev/<disco> resizepart 3 100%` e `sudo resize2fs /dev/<disco>3`.
5. Riportare i giorni dopo l'immagine dal backup mirato più recente (`snapshots/latest/`) con
   `rsync -aH --numeric-ids` e, se serve, il dump di MariaDB.
6. Sul `.15` l'immagine **non contiene i dati di emoncms** (decisione dell'utente del 25/10,
   `IMAGE_SKIP_DATA=/var/opt/emoncms`): la partizione 3 ha solo le cartelle (`phpfina/`, `phptimeseries/`, …)
   con proprietari e permessi. **Prima del primo avvio**, dal PC con il disco collegato: allargare la
   partizione 3 (`parted resizepart 3 100%` + `resize2fs`), montarla e copiarci le cartelle dei feed dal backup
   di emoncms (`192.168.1.4:/emoncms-backup/emoncms/phpfina`, `phptimeseries`, … con `rsync -a --numeric-ids`);
   il database `emoncms` dell'immagine è quello della sera della copia, il dump più recente è in
   `snapshots/latest/var/backups/domopi/mysql/emoncms.sql.gz`. Senza i dati emoncms ripartirebbe con i
   feed vuoti. Da provare in B5 (per il `.15` la migrazione a SSD si fa invece con la copia diretta).

### Un file o una configurazione

Montare la cartella sul Raspberry e copiare con `cp -a` da `snapshots/<giorno>/<percorso>`.

### Un database

`gunzip -c snapshots/<giorno>/var/backups/domopi/mysql/domotica.sql.gz | sudo mysql domotica`
(sul `.15` il database `emoncms`).

### Prova di ripristino in sicurezza

**Mai avviare una copia mentre l'originale è in rete**: stesso IP, stessi flussi Node-RED che comandano
i dispositivi, stessa identità Tailscale, stessi client MQTT. La prova si fa sullo stesso Raspberry:
spegnere, collegare il disco ripristinato al posto della SD, avviare e controllare. Con il passaggio a
SSD (§6) la prova di ripristino e la migrazione sono la stessa operazione.

## 5. Fasi

| Fase | Contenuto | Stato |
|---|---|---|
| B0 | cartella `rasp-backup` sul `.4`, regole NFS, prova di scrittura da `.15` e `.20` | **fatta** 24/10 |
| B1 | progetto e script (`backend/backup/`), test della rotazione; revisione con l'utente: backup mirati, verifica dell'immagine, immagini mensili | **fatta** 24/10 |
| B2 | `.20`: installazione con timer spenti, `init`, prova a secco, primo backup mirato e prima immagine a mano osservando carico e luci, poi timer | **in corso**: 24/10 12:29 installato con timer spenti (backup `/home/pi/AI_backups/backup/20261024T102900Z-before-backup-s4`), `init` e prove a secco riuscite (backup mirato 33 percorsi, 275 MB; immagine su `…-A.img`); corretto: una prova a secco non vale più come backup (`last_ok`); 13:04 primo backup mirato (275 MB, 106 s); prima immagine con timer temporaneo `domopi-backup-image-first` il 25/10 alle 01:30: **fallita** (rsync fermato dal kernel per memoria a 02:17, ~900 MB su 1,9 GB di RAM e 100 MB di swap; nessun `.ok`), correzioni del 25/10 sotto; **seconda prova del 26/10 01:30 fallita** anch'essa per memoria, dopo 3 ore su `/home` (rsync 886 MB su tre processi, ognuno sotto il limite di 600 MB): la scrittura nell'immagine (disco virtuale sopra NFS, scheda di rete del `.20` a 100 Mbit/s) è molto più lenta della lettura e i dati in attesa si accumulano in memoria; in più Apache teneva 139 processi (richieste di Alexa in attesa fino a 300 s, corretto con `timeout=15`). Correzione preparata il 26/10 (`IMAGE_STAGING=/var/lib/domopi-backup/staging` in `backup-20.conf`: immagine costruita sulla SD, ~10 GB occupati su 45 liberi, poi copiata sul QNAP con `dd` a I/O diretto e verificata di nuovo lì); provata a mano il 26/10 pomeriggio: **sospesa**. Anche scrivendo sulla SD rsync cresceva (fino a ~650 MB su una sola cartella) e il `.20` è diventato lentissimo per circa 20 minuti (SSH quasi muto; Node-RED, broker e servizi rimasti attivi), fermato a mano. Correzioni comunque utili tenute nello script: copia a pezzi di al massimo 10 000 file, disco virtuale con `--direct-io`, arresto pulito. **Decisione:** nessuna altra prova dell'immagine sul `.20` in funzione; B5 del `.20` copiando la SD sull'SSD dal PC a `.20` spento; l'immagine del `.20` si rivede dopo l'SSD. Cancellata `~/.nvm.P` (905 MB, 39 000 file, copia non usata, decisione dell'utente). Il file di lavoro incompleto resta in `/var/lib/domopi-backup/staging/` (3 GB). **Timer:** dal 26/10 backup mirato ogni notte acceso; immagine spenta fino alla correzione |
| B3 | `.15`: come B2 | **in corso**: 24/10 13:14 installato con timer spenti (backup `/home/pi/AI_backups/backup/20261024T111442Z-before-backup-s4`), prove a secco riuscite; 13:15 primo backup mirato (17 percorsi, 40 MB, 31 s); **timer accesi il 26/10** (backup mirato ogni notte, immagine il sabato); prima immagine con timer temporaneo il 25/10 alle 06:30 (spostata dalle 04:15 per la verifica di emoncms): **fallita** in 50 s (la partizione di sistema è montata anche come `/var/log.bak` da log2ram e lo script prendeva le due righe come un solo percorso) |
| B4 | salute impianto: avviso se il backup mirato ha più di 26 ore, l'immagine più di 8 giorni o l'ultimo giro è fallito; sul `.15` anche il backup di emoncms (`status.json` sul NAS: ultimo giro oltre 26 ore, errori, cartella non montata); dopo B5 lo stato SMART degli SSD; manuale aggiornato | **attivo dal 26/10** (`check_backups` in `backend/health`, 5 test); l'avviso «Immagine di backup» del `.20` resta finché l'immagine non è corretta; SMART dopo B5 |
| B5 | passaggio a SSD dall'immagine (§6), prima il `.20` (scelta dell'utente del 24/10), poi il `.15`: è anche la prova di ripristino | **`.20` fatto il 29/10/2026** con un sistema nuovo ([`RICOSTRUZIONE_20.md`](RICOSTRUZIONE_20.md)): Raspberry 4 parte dall'SSD NQ100, vecchia SD nel cassetto; immagine settimanale del `.20` da riattivare dopo qualche giorno di stabilità. `.15`: ricostruzione, [`RICOSTRUZIONE_15.md`](RICOSTRUZIONE_15.md) |
| B6 | dopo due settimane senza problemi: togliere il cron settimanale del `.20` e quello mensile del `.15` (coperti dai nuovi backup) | da fare |

### Correzioni dopo le prime immagini (25/10/2026)

- **Punti di montaggio:** di ogni partizione si usa solo il montaggio della sua radice (`findmnt` `FSROOT` = `/`);
  le cartelle della partizione nascoste da un altro montaggio ma visibili altrove si copiano al loro posto
  nell'immagine. Su emonSD la `/var/log` vera della SD (sotto il tmpfs di log2ram, vista in `/var/log.bak`)
  finisce in `/var/log` dell'immagine, così Mosquitto, Apache ed emoncms ritrovano le loro cartelle di log.
- **Esclusioni separate:** `SNAPSHOT_EXCLUDES` (backup mirato: moduli di Node-RED, database delle query di
  Pi-hole) e `IMAGE_EXCLUDES` (immagine: solo il database di Pi-hole, che si rigenera). Prima l'immagine escludeva
  anche `node_modules` di Node-RED: un ripristino avrebbe avuto Node-RED senza moduli.
- **Memoria:** la partizione di sistema si copia una cartella principale alla volta (`/usr`, `/home`, `/var`, …;
  elenco più corto e avanzamento nel registro); ogni rsync ha un limite di memoria (`prlimit --as`,
  `RSYNC_MAX_VM_MB=600`) e si ferma con errore invece di soffocare il Raspberry; `OOMScoreAdjust=500` nelle unità.
  Il controllo della memoria per servizio (`MemoryMax=`) non funziona: nei kernel di `.15` e `.20` il cgroup della
  memoria è disattivato (si attiverebbe con `cgroup_enable=memory` in `cmdline.txt` e un riavvio).
- **Dati di emoncms fuori dall'immagine del `.15`** (decisione dell'utente del 25/10): hanno già il loro backup
  notturno con verifica settimanale; l'immagine tiene la partizione 3 con le sole cartelle, piccola (1 GiB), e
  il ripristino diventa immagine + backup di emoncms (§4 punto 6). Un'immagine con l'ultima partizione più
  grande del doppio del necessario viene ricreata.
- `sfdisk --disk-id` non esiste sul `.15` (util-linux 2.33): l'identificativo si legge da `sfdisk -d`
  (`label-id`). La prima immagine completa del `.15` del 25/10 (13:21–13:5x, rsync sempre sotto 40 MB, sistema
  in 6 minuti) si era fermata qui, alla verifica.
- Test senza root: `tests/test_paths.sh` (montaggi, esclusioni, dati saltati, identificativo del disco) oltre a
  `tests/test_rotation.sh`.
- **26/10: `/boot` del `.20` smontata dallo script (13:44–13:51).** Le partizioni dell'immagine hanno di proposito
  i PARTUUID della SD; con `losetup -P` udev puntava `/dev/disk/by-partuuid/…-01` al disco virtuale e, quando
  questo è stato chiuso, systemd ha smontato `/boot`, che il `.20` monta per PARTUUID. Rimontata, nulla perso.
  Correzione: un disco virtuale per partizione con offset e dimensione, senza tabella delle partizioni (niente
  PARTUUID doppi). Nello stesso giorno ripristinata la funzione di rotazione, cancellata per errore in una modifica
  (sarebbe fallito il backup mirato notturno del `.20`; i test l'hanno trovato prima).
- Prova del `.15` con lo script corretto (26/10 15:00–15:09): riuscita, `192-168-1-15-B.img` verificata, rsync
  sotto 40 MB. La scrittura diretta del disco virtuale (`--direct-io`) è ora facoltativa (`LOOP_DIRECT_IO`, spenta
  di default, accesa solo nella configurazione del `.20`): con l'immagine su NFS i file system scritti così non si
  montavano (prima prova del `.15` delle 14:59, immagine B scartata, nessun `.ok`).
- Lezione: una prova a secco di rsync sull'intera SD del `.20` per misurare la memoria ha rallentato SSH per circa
  15 minuti (nessun servizio fermo). Prove pesanti sui Raspberry di casa solo con un limite di memoria.

## 6. Passaggio a SSD

### Perché

| | SD | SSD su USB 3 |
|---|---|---|
| Guasti | controller semplice, poca distribuzione delle scritture, nessuna protezione: con scritture piccole e continue si consuma e cede senza preavviso | controller vero, distribuzione delle scritture, celle di riserva, durata di decine o centinaia di TB scritti |
| Prestazioni sul Pi 4 | ~40 MB/s, poche centinaia di scritture casuali al secondo | ~300–350 MB/s (limite della USB 3), migliaia di operazioni casuali al secondo: è ciò che oggi rallenta le luci IR del `.20` |

Priorità: il `.15` per rischio (SD di 5 anni), il `.20` per prestazioni (10 volte le scritture).

### Hardware (scelto con l'utente il 24/10)

- **SSD SATA 2,5": Lexar NQ100 256 GB e Lexar NS100 256 GB**, uno per host (ordinati dall'utente il 24/10,
  arrivo 25/10 e 28/10). Senza DRAM,
  durata dichiarata di alcune decine di TB scritti: decenni al ritmo del `.20` (~2,3 GB/giorno). Scartato il
  Samsung 870 EVO (DRAM, TLC, 150 TB, 5 anni) perché costava il doppio (110 € contro 53 €) senza vantaggi
  utili per questo carico; la protezione vera sono le immagini settimanali. Alternative equivalenti: SanDisk
  SSD Plus, Kingston KC600, Crucial BX500/MX500. Da evitare i vecchi dischi meccanici da 2,5" (lenti nelle
  piccole scritture sparse, ~1 A all'avvio dalla USB del Pi, età): al più per provare un ripristino dal PC.
- **Adattatore StarTech USB3S2SAT3CB** (USB 3.0 5 Gbps, chip ASMedia ASM1153E secondo StarTech, UASP; da
  confermare con `lsusb` all'arrivo), uno per host, ordinati il 24/10 (arrivo 28/10 e 30/10); serve anche per scrivere l'immagine dal PC. Da evitare gli
  adattatori mSATA e quelli USB‑C (sul Pi 4 la USB‑C è solo alimentazione); chip JMicron funzionano ma
  possono richiedere `usb-storage.quirks`.
- Collegamento a una **porta USB 3 blu**, cavo corto; un SSD SATA da 2,5" (~0,5 A) non richiede un
  alimentatore separato con l'alimentatore dedicato del Pi. Un **UPS** resta consigliato.

### Controlli all'arrivo degli SSD (prima di B5)

- Collegare ogni SSD con il suo adattatore a una porta USB 3 del `.20` (o al PC): `smartctl -i -a` per
  modello, **firmware** e stato SMART (annotarli qui); `lsusb -t` per il driver `uas`.
- **TRIM attraverso l'adattatore:** `lsblk --discard` (DISC-GRAN/DISC-MAX diversi da 0). Con l'ASM1153E di
  solito serve una regola udev che imposti `provisioning_mode=unmap`, poi `fstrim -v` di prova; dopo B5
  attivare `fstrim.timer`.
- Il controllo SMART degli SSD (usura, settori riallocati, errori) entra negli avvisi della salute impianto
  (B4).
- **Fatti il 28/10/2026** (un SSD per Raspberry, porta USB blu; `smartmontools` installato su entrambi, `smartd`
  spento; SMART letto con `smartctl -d sat`):

  | | NQ100 → `.20` | NS100 → `.15` |
  |---|---|---|
  | Firmware | SN21395 | SN21561 |
  | SMART | PASSED, 0 ore, 0 scritture, 0 riallocati, 0 errori CRC, 28 °C | PASSED, 0 ore, 0 scritture, 0 riallocati, 0 errori CRC, 28 °C |
  | Adattatore | StarTech `14b0:0206`, driver `uas`, 5 Gbit/s | uguale |
  | TRIM | `provisioning_mode=full` di base; con `unmap` il disco accetta il TRIM (prova di 1 MB su disco vuoto) | uguale |
  | Alimentazione | nessun calo (`throttled=0x0`) | nessun calo |

  Dopo B5: regola udev `ACTION=="add|change", ATTRS{idVendor}=="14b0", ATTRS{idProduct}=="0206",
  SUBSYSTEM=="scsi_disk", ATTR{provisioning_mode}="unmap"` e `fstrim.timer`.

### Prerequisiti verificati il 24/10

- `.20`: bootloader del 11/01/2023, ordine di avvio predefinito (prima SD, poi USB): pronto.
  `fstab` e `cmdline.txt` usano i PARTUUID: l'immagine si avvia da SSD senza modifiche.
- `.15`: bootloader del 03/09/2020 con `BOOT_ORDER=0xf41` (prima SD, poi USB): l'avvio da USB funziona;
  c'è un aggiornamento del 29/04/2021 che lo rende più affidabile, da applicare prima (con conferma).
  **`fstab` monta per nome di dispositivo** (`/dev/mmcblk0p1/p2/p3`): sull'SSD le partizioni si chiamano
  `/dev/sda1/2/3`, quindi l'`fstab` dell'SSD va cambiato con i PARTUUID (`PARTUUID=43ed7bb4-01/02/03`)
  prima del primo avvio, altrimenti il sistema non parte.

### Procedura del `.20` (decisa il 26–28/10/2026): copia dal PC a Raspberry spento

Le immagini del `.20` costruite col sistema acceso lo hanno rallentato fino a non rispondere (25–26/10), quindi la
SD si copia sull'SSD **dal PC** con [`backend/backup/clone_sd_to_ssd.sh`](../../backend/backup/clone_sd_to_ssd.sh):
stessa tabella delle partizioni e stesso identificativo del disco (i PARTUUID di `cmdline.txt` e `fstab` del `.20`
restano validi, verificato), partizione di avvio byte per byte, sistema con `e2image` (solo i blocchi usati),
ultima partizione allargata a tutto l'SSD, controlli finali. La SD viene solo letta. Senza `--yes` mostra il piano.

**Il `.20` è il DNS della casa (Pi-hole):** da spento nessun dispositivo naviga, nemmeno il PC con questa sessione.
Prima di spegnerlo, sul PC: `sudo resolvectl dns wlp8s0 1.1.1.1 192.168.1.20`; dopo: `sudo resolvectl revert wlp8s0`.
Scegliere un momento in cui la casa può restare ~20 minuti senza Internet (caminetto, caldaia e `.15` non ne
dipendono).

1. DNS del PC come sopra; backup mirato fresco sul `.20` (`sudo systemctl start domopi-backup-snapshot`), poi
   `sudo poweroff`.
2. Togliere alimentazione e SD; l'NQ100 con il suo adattatore passa al PC, la SD nel lettore del PC.
3. Sul PC `lsblk -d -o NAME,SIZE,MODEL` per riconoscere i due dischi; `sudo bash clone_sd_to_ssd.sh <SD> <SSD>`
   (piano), poi con `--yes`.
4. SSD sulla porta USB blu del `.20`, SD nel cassetto con etichetta, accendere (bootloader 2023: prima SD, poi USB).
5. Controlli: sistema su `sda`, driver `uas`, servizi e salute impianto, Node-RED (https), broker, Alexa, app.
6. Dopo: regola udev del TRIM e `fstrim.timer`; `DEVICE=/dev/sda` nella configurazione del backup; riattivare timer
   e avviso dell'immagine (la salute impianto del repository ha ancora il controllo); DNS del PC ripristinato.

Ritorno indietro: spegnere, togliere l'SSD, rimettere la SD, accendere.

### Diario del `.20` (28/10/2026) e procedura corretta

1. 14:31 backup mirato, `.20` spento. `clone_sd_to_ssd.sh` si è fermato prima di scrivere: **la SD del `.20` ha
   errori ext4** (`e2fsck -fn`): lista degli orfani rovinata, ~300 voci di cartella verso file cancellati
   (phpMyAdmin, binlog di MariaDB), cartelle con il blocco di controllo rovinato (una in
   `/usr/lib/node_modules/node-red/node_modules/npm/…`). Probabilmente dai cali di tensione del 17–19/10.
2. Copia con `REPAIR_COPY=1` (copia così com'è e ripara solo la copia sull'SSD, ~40 min a 4 MB/s dal lettore del
   PC). Struttura perfetta (`e2fsck` codice 0), ma **3 407 elementi finiti in `lost+found`**: `e2fsck` ha staccato
   il contenuto delle cartelle col blocco di controllo rovinato, che il kernel in funzione invece legge. Sull'SSD
   mancavano 1 230 file in `/usr/lib/aarch64-linux-gnu`, 15 in `/etc` (tutta `/etc/mysql`), 9 in
   `/var/lib/dpkg/info`. In più `sfdisk` non aveva copiato l'identificativo del disco (corretto a mano e nello
   script).
3. Avvio dall'SSD non riuscito (porta blu, poi nera; spie rossa e verde fisse, nessuna rete), anche con
   `usb-storage.quirks=14b0:0206:u`. Caricatore di avvio aggiornato con lo strumento ufficiale (conferma
   dell'utente): da 11/01/2023 a **23/09/2026** (canale `default` del repository `raspberrypi/rpi-eeprom`; l'archivio
   di bullseye si ferma al 2023) con `USB_MSD_STARTUP_DELAY=2000`; copia del vecchio in
   `/home/pi/AI_backups/eeprom/`. Ancora nessun avvio.
4. Sul `.20` acceso l'SSD collegato a caldo è perfetto: `uas` a 5 Gbit/s, nessun reset, nessun calo
   (`throttled=0x0`); `uas` e `usb-storage` sono incorporati nel kernel 6.1.21; caratteristiche ext4 uguali alla SD.
   Il confronto dei file ha trovato i mancanti del punto 2.
5. **SSD rifatto sul `.20`**: identificativo del disco proprio `2026d020` (diverso dalla SD `ba3739cc`, così non si
   confondono), `mkfs.ext4` con gli strumenti del `.20`, `rsync -aHAXx --numeric-ids --delete` dal sistema in
   funzione (`systemd-run` con `Nice=19`, I/O idle, `MemoryMax=700M`; 22 minuti, 176 944 elementi, 8,4 GB, nessun
   errore, `.20` sempre reattivo), seconda passata con i servizi che scrivono fermati (17:39:44–17:41:21), partizione di
   avvio allineata, `fstab` e `cmdline.txt` dell'SSD su `2026d020-01/02`, `e2fsck` codice 0.
6. Avvio dall'SSD ancora non riuscito. La prova di avvio misto (SD per l'avvio, sistema dall'SSD) **non è
   avvenuta**: sul `.20` il `cmdline.txt` della SD scritto dal PC risultava **vuoto (0 byte)** e il caricatore ha
   usato la versione vecchia: **la SD non conserva in modo affidabile le scritture**. `cmdline.txt` della SD
   rimesso a posto e riletto. Il `.20` è sulla SD, servizi regolari.

**Lezioni:** con una SD danneggiata non riparare la copia: ricopiare con `rsync` dal sistema in funzione su un
file system nuovo. Il desktop del `.20` (pcmanfm/udisks) **monta da solo** un disco collegato: le regole udev con
`ID_VENDOR_ID` non valgono per questo adattatore, serve `ID_USB_VENDOR_ID` (sull'SSD c'è
`/etc/udev/rules.d/99-domopi-no-automount-sd.rules` per la SD).

**Superato la sera stessa:** l'utente ha deciso di reinstallare il `.20` da zero ([`RICOSTRUZIONE_20.md`](RICOSTRUZIONE_20.md));
la copia sull'SSD si archivia sul QNAP, poi Raspberry Pi OS Lite pulito e prova di avvio sul Raspberry 4 senza
schermo (il cavo micro-HDMI serve solo se non parte).

### Procedura generale (dall'immagine sul QNAP, con l'utente presente)

1. Ultima immagine verificata sul `.4` (lanciare `domopi-backup-image.service` a mano la sera prima).
2. Dal PC: collegare l'SSD con l'adattatore, scrivere l'immagine
   (`sudo dd if=<immagine> of=/dev/sdX bs=4M conv=fsync status=progress`), allargare l'ultima partizione
   (`parted resizepart` + `resize2fs`, a disco non montato); solo `.15`: cambiare l'`fstab` dell'SSD con i
   PARTUUID.
3. Controllo dell'adattatore sul PC: `lsusb` (chip), prova di lettura e scrittura prolungata.
4. Spegnere il Raspberry (`sudo poweroff`), togliere la SD, collegare l'SSD alla porta USB 3, accendere.
5. Controlli: `lsblk` (sistema su `sda`), `lsusb -t` (driver `uas`), servizi e salute impianto tutti
   verdi, Node-RED, broker, app; sul `.15` feed emoncms che avanzano e caminetto; riportare i giorni
   dopo l'immagine dal backup mirato (§4 punto 5).
6. Aggiornare la configurazione del backup (`DEVICE=/dev/sda`) e lanciare subito un'immagine nuova.
7. La SD tolta va **nel cassetto** come riserva, etichettata con data e host. Non lasciarla inserita
   come riserva automatica: se l'SSD cedesse, il Raspberry ripartirebbe da una SD vecchia che
   comanderebbe la casa con una configurazione superata.

Ritorno indietro: spegnere, togliere l'SSD, rimettere la SD originale, accendere.

## 7. Punti aperti

- **DNS di riserva** nel router (dopo B5, da discutere): oggi il `.20` è l'unico DNS (Pi-hole) e da spento la casa resta
  senza Internet; un secondo DNS nel DHCP dà continuità ma alcuni dispositivi lo userebbero a volte, saltando i filtri.
- **`.15`: decisione dell'utente del 28/10:** sistema nuovo con Raspberry Pi OS Lite e lo **script di installazione
  ufficiale di emoncms** (EmonScripts) al posto dell'immagine emonSD; da pianificare dopo il `.20`.

- **Password rsync** dei moduli sul `.4` e sul `.74`: è quella predefinita, scritta in chiaro negli
  script `regular.backup.sh` (stampata per errore il 24/10 leggendo un file di verifica sul `.20`). Da
  cambiare sui QNAP; con B6 gli script che la usano spariscono.
- Confermare che la replica del `.4` sul `.74` comprenda la nuova cartella `rasp-backup`. Hybrid Backup Sync
  programmato dall'utente alle **12:00** (25/10), lontano dalla finestra dei backup (00:00–06:00).
- **Ripristino di emoncms:** livello 1 superato il 25/10 (tutti i 315 feed presenti nel backup, intestazioni e
  dati a campione identici, dump del database integro, `drive-restore.sh --dry-run` pulito); il ripristino vero
  (livello 3) si fa con l'SSD del `.15`, sulla ricostruzione con emonSD-01Feb24 (decisione dell'utente).
- ~~Journal del `.20` a 4 GB~~ **fatto il 24/10 12:42** (richiesta dell'utente): vecchio journal (maggio–ottobre,
  4,0 GB) archiviato sul QNAP in `rasp-backup/192-168-1-20/journal-archive/20261024`, poi
  `/etc/systemd/journald.conf.d/50-domopi-size.conf` (`SystemMaxUse=500M`, `SystemMaxFileSize=50M`),
  riavvio di `systemd-journald` e `--vacuum-size=500M`: da 3,9 GB a 528 MB, disco da 15 a 11 GB usati.
  Prima era senza limiti (predefinito: 10 % del disco fino a 4 GB). Ritorno indietro: togliere il file e
  riavviare `systemd-journald`.
- ~~`/etc/emoncms-nas.cred` sul `.15`~~ **cancellato il 24/10** (richiesta dell'utente): credenziali del vecchio
  montaggio SMB del QNAP, sostituito da NFS. Ne resta una copia nell'istantanea del 24/10 sul QNAP
  (`rasp-backup/192-168-1-15/snapshots/2026-10-24/etc/`); se era un utente QNAP dedicato, disattivarlo.
- ~~Ricostruzione del `.15` su un emonSD recente~~ superata il 28/10: sistema nuovo con EmonScripts, piano in
  [`RICOSTRUZIONE_15.md`](RICOSTRUZIONE_15.md). Nota originale: oggi emonSD-24Jul20 (Raspbian 10, PHP 7.3, 32 bit)
  con moduli emoncms aggiornati per PHP 8 (il 24/10 si è rotto `postprocess`, corretto a mano, vedi HANDOFF).
  Immagine scelta: **emonSD-01Feb24** (Bookworm 32 bit, PHP 8.1, Pi 4, hardware emonPi1/emonBase con RFM su
  `/dev/ttyAMA0`); la 10Mar26 (Trixie 64 bit) è preconfigurata per emonPi3 e richiederebbe di adattare `emonhub.conf`.
  Non è un aggiornamento ma una ricostruzione: Node-RED del caminetto copiato identico, Mosquitto con gli utenti,
  emonhub, dati emoncms (modulo di backup, export/import), backup S4, montaggi NFS, script del fotovoltaico.
  L'SSD prende il sistema nuovo, la SD attuale resta intatta come ritorno indietro. Da pianificare con l'utente.
- La SD nel lettore USB del `.20`: controllarne il contenuto in sola lettura quando l'utente la rimette.
