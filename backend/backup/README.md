# Backup a caldo dei Raspberry sul QNAP (fase S4)

Progetto, decisioni e procedure di ripristino:
[`docs/AI_MODE/BACKUP_RASPBERRY.md`](../../docs/AI_MODE/BACKUP_RASPBERRY.md).

`rasp_backup.sh` gira come root da systemd, con i sistemi accesi, e scrive sulla cartella condivisa
NFS `192.168.1.4:/rasp-backup` (QNAP principale; il `.74` ne fa la copia). Uno script per tutti gli
host, una configurazione per host (`backup-20.conf`, `backup-15.conf` → `/etc/domopi-backup/backup.conf`).

| Comando | Quando | Cosa fa |
|---|---|---|
| `rasp_backup.sh init` | una volta | crea `<host>/{snapshots,images}` e il marcatore `.rasp-backup-target` |
| `rasp_backup.sh snapshot` | ogni notte 03:40 (timer) | dump MariaDB in `/var/backups/domopi/mysql/`, poi backup mirato con rsync delle cartelle in `SNAPSHOT_SOURCES` (configurazione), con collegamenti fisici al giorno prima (`--link-dest`); rotazione 14 giorni + 8 settimane |
| `rasp_backup.sh image` | domenica 04:40 (timer) | aggiorna con rsync l'immagine `.img` avviabile della coppia A/B senza `.ok` valido, la verifica (`fsck`, PARTUUID in `cmdline.txt`, kernel, `fstab`) e una volta al mese la copia come `<host>-M-AAAA-MM.img` (ultime 2) |
| `… --dry-run` | prove | mostra cosa farebbe, senza scrivere sul QNAP |
| `rasp_backup.sh rotate-test <date…>` | test | stampa le istantanee che la rotazione cancellerebbe |

Sicurezze:
- senza il marcatore `.rasp-backup-target` lo script si ferma: una cartella non montata non viene
  mai riempita sulla SD;
- un solo backup alla volta (`flock`), priorità minima di CPU e disco (`Nice=19`, `IOSchedulingClass=idle`);
- un'immagine interrotta o non verificata non sostituisce quella buona: `<immagine>.ok` si scrive solo
  dopo la verifica;
- i percorsi mancanti del backup mirato vengono segnalati nel registro e nello stato, non fermano il giro;
- esclusi i file enormi rigenerati ogni giorno e inutili per il ripristino (journal di systemd,
  cronologia delle query di Pi-hole sul `.20`, `node_modules` di Node-RED nel backup mirato), le cache,
  `/proc`, `/sys`, `/dev`, `/run`, `/tmp`, i montaggi in `/mnt` e `/media`, lo swap.
- dopo il passaggio a SSD: `DEVICE=/dev/sda` nella configurazione (predefinito `/dev/mmcblk0`).

Esito: `/var/lib/domopi-backup/status-{snapshot,image}.json` (schema `domopi.backup_status.v1`: `ok`,
`last_ok`, durata, dettagli), copiato anche in `<host>/` sul QNAP; lo leggerà il controllo della
salute impianto.

Nessuna credenziale: il QNAP autorizza per indirizzo (solo `.15` e `.20`, «nessuno squash»),
MariaDB con il socket di root. Le istantanee **contengono segreti** (token, file di password,
stato di Tailscale): la cartella sul QNAP non va aperta ad altri host né condivisa.

## Test

```sh
bash backend/backup/tests/test_rotation.sh        # rotazione, sul PC, senza root
bash -n backend/backup/rasp_backup.sh             # sintassi
```

## Installazione (con conferma dell'utente, un host alla volta)

```sh
scp -r backend/backup pi@192.168.1.20:/home/pi/AI_patches/backup-s4
ssh pi@192.168.1.20
cd ~/AI_patches/backup-s4
sudo sh deploy.sh 20 install --no-timers         # copia script, configurazione e unità; timer spenti
sudo /opt/domopi-backup/rasp_backup.sh init
sudo /opt/domopi-backup/rasp_backup.sh snapshot --dry-run
sudo systemctl start domopi-backup-snapshot.service   # primo backup mirato
sudo systemctl start domopi-backup-image.service      # prima immagine
sudo systemctl enable --now domopi-backup-snapshot.timer domopi-backup-image.timer
```

Ritorno indietro: `sudo sh deploy.sh 20 rollback` (toglie script e timer; i backup sul QNAP restano).
