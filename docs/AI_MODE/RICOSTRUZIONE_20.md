# Ricostruzione del `.20` (domopi) su SSD con sistema nuovo

Decisione dell'utente (28/10/2026): come nelle migrazioni precedenti (Raspberry 3 → 4, due cambi di SD), il `.20` si
**reinstalla da zero** sull'SSD Lexar NQ100 invece di copiarlo. Motivi: la SD ha errori ext4 e non conserva in modo
affidabile le scritture (diario in [`BACKUP_RASPBERRY.md`](BACKUP_RASPBERRY.md) §6); Raspberry Pi OS 11 (bullseye) è
fuori supporto di sicurezza; sul sistema attuale ci sono desktop, stampa, ModemManager e resti di Grafana che non
servono.

**Come:** il **Raspberry 3 Model B** dell'utente fa da banco di montaggio (SSD sulla USB, SD con il solo
`bootcode.bin` e il file `timeout` per l'avvio da USB, IP e nome provvisori). Il `.20` resta in funzione sulla sua SD
fino al cambio finale: l'SSD passa al Raspberry 4, si mettono IP e nome definitivi, unico fermo ~30 minuti.
Raspberry Pi OS a 64 bit è lo stesso per i due modelli.

## 1. Com'è oggi (inventario del 28/10/2026, sola lettura)

| Cosa | Stato |
|---|---|
| Hardware | Raspberry Pi 4 Model B Rev 1.5, 2 GB; Wi-Fi e Bluetooth spenti in `config.txt`; OTGW con adattatore **CP2102** (`/dev/serial/by-id/usb-Silicon_Labs_CP2102_USB_to_UART_Bridge_Controller_0001-if00-port0`), ricollegato il 28/10 alla porta nera |
| Sistema | Debian 11 bullseye 64 bit, kernel 6.1.21, desktop LXDE con autologin; 8,3 GB usati |
| Rete | `eth0` 192.168.1.20 (DHCP con `dhcpcd`, IP fisso dato dal router: da verificare), Tailscale `100.100.127.81` (nome `domopi.tailf30ba8.ts.net`) |
| Node-RED | **5.0.8** su **Node 22** (repository NodeSource), progetto **`domopi`**, **20 pacchetti** in palette, https su 1880 (certificato rinnovato da `nodered-tls-renew.timer`), Alexa (amazon-echo) su 8980 e SSDP 1900, context su file (340 KB), Admin API con token |
| Mosquitto | listener **1883** (`allow_anonymous false`, `/etc/mosquitto/credentials`, utente `domotica`), listener **1884 su 192.168.1.20** di sola lettura (`c1-3b-5b-2-21-read-only/passwd` + `acl`), `persistence true`, `persistent_client_expiration 2d`, drop-in `wait-network.conf` |
| Pi-hole | v6 (`pihole-FTL`: DNS 53, web 81/8081; `pihole.toml`, `gravity.db`, certificato `tls.pem`); è il **DNS della casa** |
| Tailscale | `tailscaled`, `tailscale serve` su 443 e 8443 (ntfy), regole iptables proprie |
| House AI | `house-ai-backend`, `house-ai-gateway`, `house-ai-lan` (Python 3.9 di sistema, codice in `/opt/domopi-house-ai`, configurazione in `~/.config/house-ai` e `/etc/house-ai`): https LAN su 192.168.1.20:443, 127.0.0.1:8765/8766; timer `house-ai-grid-import` (drop-in `mqtt.conf`) e `house-ai-lan-cert` |
| ntfy | 2.28.0 su 127.0.0.1:2586 e 192.168.1.20:8443, `ntfy-cert.timer`, dati in `/var/lib/ntfy` e `/var/cache/ntfy` |
| Apache | porta 80: proxy di Alexa verso 8980 (`domopi-alexa-hue.conf`, `timeout=15`) e phpMyAdmin; porta 99 (da verificare) |
| lighttpd | porta 8000 (da verificare: vecchia interfaccia di Pi-hole?) |
| MariaDB | 10.5, database **`domotica`** (3,4 MB; chi lo usa: da verificare), phpMyAdmin |
| otmonitor | **6.5 a 32 bit** (Schelte Bron, `otgw.tclcode.com`) in `/home/pi/otgw/`, con le librerie `armhf`; avviato da **supervisor**; configurazione `~/.config/otmonitor/otmonitor.conf` (seriale `/dev/ttyUSB0`, relay 25238, MQTT sul broker del **`.15`**: `events/central_heating/otmonitor`, `actions/otmonitor`) e `auth.db` |
| DomoPi | backup S4 (`/opt/domopi-backup`, timer), salute impianto (`/opt/domopi-health`, timer), Broadlink (`~/broadlink`, codici IR), script e log in `/home/pi` (fotovoltaico, irrigazione, router, piscina, clima) |
| Altro | `netfilter-persistent` (drop-in `iptables.conf`); crontab `regular.backup.sh` del venerdì (da togliere, B6); `pigpio`, `raspcontroller`, `rsync`, `gldriver-test` (da verificare se servono); repository apt di Grafana (non installato) e NetXMS (chiave scaduta) |

Copia completa dei file: backup mirato di ogni notte sul QNAP (`rasp-backup/192-168-1-20/snapshots/`, dal 28/10 con
tutta `/home/pi`); la copia dell'SSD del 28/10 17:41 si archivia sul QNAP prima di riformattarlo.

## 2. Decisioni
- **Raspberry Pi OS Lite 64 bit**, versione corrente, senza desktop.
- **otmonitor nella versione ufficiale a 64 bit** (`otgw.tclcode.com/download/otmonitor-aarch64`, aggiornata al
  29/08/2026; scelta dell'utente del 28/10): niente librerie `armhf`. Avvio con un servizio systemd invece di
  supervisor, seriale per nome stabile (`/dev/serial/by-id/…`), configurazione e `auth.db` dal backup.
- Non si reinstallano: desktop, CUPS, ModemManager, PackageKit, Grafana, NetXMS (salvo diversa indicazione).

## 3. Ordine di costruzione (sul Raspberry 3, IP provvisorio)
Ogni passo con la sua verifica; nessun servizio del banco deve **comandare** la casa prima del cambio (Node-RED con
i flow fermi, broker e House AI senza collegamenti verso dispositivi veri).

| Passo | Cosa |
|---|---|
| C1 | Raspberry Pi OS Lite sull'SSD con Raspberry Pi Imager (utente `pi`, chiave SSH, nome provvisorio `domopi-new`, Europe/Rome); prova di avvio sul Raspberry 4 **senza schermo** (successo = indirizzo dal router e SSH; ~5 minuti di fermo del `.20`). Solo se non parte: cavo micro-HDMI per vedere l'errore, ripiego avvio misto con una SD nuova solo per l'avvio |
| C2 | Base: aggiornamenti, rete, `smartmontools`, TRIM (regola udev + `fstrim.timer`), nessun montaggio automatico |
| C3 | Mosquitto (due listener, utenti e ACL dal backup, persistenza, drop-in) |
| C4 | Node 22, Node-RED 5.0.8, palette (20 pacchetti), progetto `domopi` con `.git`, `settings.js`, context, Admin API, https |
| C5 | Pi-hole (configurazione e liste da `pihole.toml`/`gravity.db`) |
| C6 | House AI (tre servizi, timer, certificati), ntfy |
| C7 | Apache (proxy Alexa), MariaDB con `domotica` dal dump notturno, phpMyAdmin se serve |
| C8 | otmonitor 64 bit, Broadlink e script di `/home/pi`, crontab utili |
| C9 | Backup S4, salute impianto, avvisi |
| C10 | Cambio: spegnere il `.20`, SSD sul Raspberry 4, IP e nome definitivi, Tailscale (stessa identità da `/var/lib/tailscale`), avvio dei flow, verifiche con l'utente (luci, Alexa, app, AI Climate, regole, notifiche, DNS) |

Ritorno indietro al cambio: spegnere, rimettere la SD attuale, accendere.

## 4. Diario

| Data | Fatto |
|---|---|
| 28/10/2026 | Decisione dell'utente (reinstallazione, Raspberry 3 come banco). SD `bootcode.bin` per il Raspberry 3 preparata. Inventario (sezione 1). otmonitor 64 bit scelto. Nessuna modifica al `.20`. |
| 28/10/2026 sera | **C1**: Raspberry Pi OS Lite 64 bit (Debian 13 trixie, kernel 6.18) scritto con l'Imager; il Raspberry 3 parte dall'SSD con la SD `bootcode.bin` (lampeggio regolare del verde = normale). cloud-init ha applicato solo nome e utente: Wi-Fi bloccato (paese non impostato, sistemato con `raspi-config nonint do_wifi_country IT` e `nmcli --ask device wifi connect`), nessuna chiave SSH (messe con `ssh-copy-id`; con l'agente del PC `id_rsa` si blocca: voce `domopi-new` in `~/.ssh/config` con `IdentityAgent none`), fuso orario Europe/London, `sudo` con password. **Alimentazione:** col primo cavo cali di tensione continui (`0x50005`) e un blocco di 3,5 minuti; con l'altro cavo prova di 10 minuti (4 GB scritti e letti, ~35 MB/s, limite USB 2) con 60/60 letture `0x0`, nessun blocco né errore. Pulizia: Bluetooth e udisks2 spenti, cloud-init disattivato (`/etc/cloud/cloud-init.disabled`), fuso Europe/Rome; `sudo` senza password per `pi` (`/etc/sudoers.d/010_pi-nopasswd`, scelta dell'utente). Indirizzo provvisorio `192.168.1.72` (Wi-Fi). |
