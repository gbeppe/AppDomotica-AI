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
| `~/.node-red` senza `node_modules`, `~/AI_climate`, `~/.config` (token `domopi`, house-ai, otmonitor), script in `~` | `~/.node-red` senza `node_modules` (flussi veri in `projects/emonpi/`), `~/data` (log caminetto e pompa), `~/.config`, script in `~` |
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
6. Sul `.15`: dati emoncms della notte precedente da `emoncms-drive-backup` (procedura del modulo
   emoncms, da provare in B5).

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
| B2 | `.20`: installazione con timer spenti, `init`, prova a secco, primo backup mirato e prima immagine a mano osservando carico e luci, poi timer | **in corso**: 24/10 12:29 installato con timer spenti (backup `/home/pi/AI_backups/backup/20261024T102900Z-before-backup-s4`), `init` e prove a secco riuscite (backup mirato 33 percorsi, 275 MB; immagine su `…-A.img`); corretto: una prova a secco non vale più come backup (`last_ok`); 13:04 primo backup mirato (275 MB, 106 s); prima immagine con timer temporaneo `domopi-backup-image-first` il 25/10 alle 01:30; timer automatici dopo il controllo dell'immagine |
| B3 | `.15`: come B2 | **in corso**: 24/10 13:14 installato con timer spenti (backup `/home/pi/AI_backups/backup/20261024T111442Z-before-backup-s4`), prove a secco riuscite; 13:15 primo backup mirato (17 percorsi, 40 MB, 31 s); prima immagine con timer temporaneo il 25/10 alle 06:30 (spostata dalle 04:15 per la verifica di emoncms) |
| B4 | salute impianto: avviso se il backup mirato ha più di 26 ore, l'immagine più di 8 giorni o l'ultimo giro è fallito; sul `.15` anche il backup di emoncms (`status.json` sul NAS: ultimo giro oltre 26 ore, errori, cartella non montata); manuale aggiornato | da fare |
| B5 | passaggio a SSD dall'immagine (§6), prima `.15` o `.20` a scelta dell'utente: è anche la prova di ripristino | quando arriva l'hardware |
| B6 | dopo due settimane senza problemi: togliere il cron settimanale del `.20` e quello mensile del `.15` (coperti dai nuovi backup) | da fare |

## 6. Passaggio a SSD

### Perché

| | SD | SSD su USB 3 |
|---|---|---|
| Guasti | controller semplice, poca distribuzione delle scritture, nessuna protezione: con scritture piccole e continue si consuma e cede senza preavviso | controller vero, distribuzione delle scritture, celle di riserva, durata di decine o centinaia di TB scritti |
| Prestazioni sul Pi 4 | ~40 MB/s, poche centinaia di scritture casuali al secondo | ~300–350 MB/s (limite della USB 3), migliaia di operazioni casuali al secondo: è ciò che oggi rallenta le luci IR del `.20` |

Priorità: il `.15` per rischio (SD di 5 anni), il `.20` per prestazioni (10 volte le scritture).

### Hardware (scelto con l'utente il 24/10)

- **SSD SATA 2,5"** da 240–500 GB, uno per host. Preferiti con DRAM e TLC: Samsung 870 EVO, Kingston
  KC600 (Crucial MX500 se disponibile in 250/500 GB); vanno bene anche Crucial BX500 o Lexar NQ100
  (senza DRAM, ~80 TB di durata: decenni al ritmo del `.20`).
- **Adattatore StarTech USB312SAT3CB, variante «USB A 5 Gbps»** (chip ASMedia ASM1153E, UASP, nessuna
  sospensione automatica), uno per host; serve anche per scrivere l'immagine dal PC. Da evitare gli
  adattatori mSATA e quelli USB‑C (sul Pi 4 la USB‑C è solo alimentazione); chip JMicron funzionano ma
  possono richiedere `usb-storage.quirks`.
- Collegamento a una **porta USB 3 blu**, cavo corto; un SSD SATA da 2,5" (~0,5 A) non richiede un
  alimentatore separato con l'alimentatore dedicato del Pi. Un **UPS** resta consigliato.

### Prerequisiti verificati il 24/10

- `.20`: bootloader del 11/01/2023, ordine di avvio predefinito (prima SD, poi USB): pronto.
  `fstab` e `cmdline.txt` usano i PARTUUID: l'immagine si avvia da SSD senza modifiche.
- `.15`: bootloader del 03/09/2020 con `BOOT_ORDER=0xf41` (prima SD, poi USB): l'avvio da USB funziona;
  c'è un aggiornamento del 29/04/2021 che lo rende più affidabile, da applicare prima (con conferma).
  **`fstab` monta per nome di dispositivo** (`/dev/mmcblk0p1/p2/p3`): sull'SSD le partizioni si chiamano
  `/dev/sda1/2/3`, quindi l'`fstab` dell'SSD va cambiato con i PARTUUID (`PARTUUID=43ed7bb4-01/02/03`)
  prima del primo avvio, altrimenti il sistema non parte.

### Procedura (per ciascun host, con l'utente presente)

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

- **Password rsync** dei moduli sul `.4` e sul `.74`: è quella predefinita, scritta in chiaro negli
  script `regular.backup.sh` (stampata per errore il 24/10 leggendo un file di verifica sul `.20`). Da
  cambiare sui QNAP; con B6 gli script che la usano spariscono.
- Confermare che la replica del `.4` sul `.74` comprenda la nuova cartella `rasp-backup`.
- ~~Journal del `.20` a 4 GB~~ **fatto il 24/10 12:42** (richiesta dell'utente): vecchio journal (maggio–ottobre,
  4,0 GB) archiviato sul QNAP in `rasp-backup/192-168-1-20/journal-archive/20261024`, poi
  `/etc/systemd/journald.conf.d/50-domopi-size.conf` (`SystemMaxUse=500M`, `SystemMaxFileSize=50M`),
  riavvio di `systemd-journald` e `--vacuum-size=500M`: da 3,9 GB a 528 MB, disco da 15 a 11 GB usati.
  Prima era senza limiti (predefinito: 10 % del disco fino a 4 GB). Ritorno indietro: togliere il file e
  riavviare `systemd-journald`.
- ~~`/etc/emoncms-nas.cred` sul `.15`~~ **cancellato il 24/10** (richiesta dell'utente): credenziali del vecchio
  montaggio SMB del QNAP, sostituito da NFS. Ne resta una copia nell'istantanea del 24/10 sul QNAP
  (`rasp-backup/192-168-1-15/snapshots/2026-10-24/etc/`); se era un utente QNAP dedicato, disattivarlo.
- **Ricostruzione del `.15` su un emonSD recente**, insieme a B5: oggi emonSD-24Jul20 (Raspbian 10, PHP 7.3, 32 bit)
  con moduli emoncms aggiornati per PHP 8 (il 24/10 si è rotto `postprocess`, corretto a mano, vedi HANDOFF).
  Immagine scelta: **emonSD-01Feb24** (Bookworm 32 bit, PHP 8.1, Pi 4, hardware emonPi1/emonBase con RFM su
  `/dev/ttyAMA0`); la 10Mar26 (Trixie 64 bit) è preconfigurata per emonPi3 e richiederebbe di adattare `emonhub.conf`.
  Non è un aggiornamento ma una ricostruzione: Node-RED del caminetto copiato identico, Mosquitto con gli utenti,
  emonhub, dati emoncms (modulo di backup, export/import), backup S4, montaggi NFS, script del fotovoltaico.
  L'SSD prende il sistema nuovo, la SD attuale resta intatta come ritorno indietro. Da pianificare con l'utente.
- La SD nel lettore USB del `.20`: controllarne il contenuto in sola lettura quando l'utente la rimette.
