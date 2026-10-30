# Secondo Pi-hole sul `.15` (DNS ridondante)

Decisione dell'utente del 30/10/2026: Pi-hole sul `.20` era un punto unico di guasto (con il `.20` spento la casa
non naviga). Sul `.15` gira un secondo Pi-hole, stessa versione (Core 6.5, Web 6.6.1, FTL 6.8) e stessa
configurazione, interfaccia web sulle porte 81 e 8081 (l'80 è di emoncms). Il router distribuisce due DNS.

- `/etc/pihole/pihole.toml` copiato dal `.20` prima dell'installazione ufficiale (`--unattended`).
- [`pihole_sync.sh`](pihole_sync.sh), in `/opt/domopi-backup/` sui due host: ogni notte il `.20` esporta un archivio
  Teleporter nella cartella condivisa `rasp-backup` del QNAP (`pihole-sync/`) alle 02:50 ([`cron-20`](cron-20)), il `.15`
  lo importa alle 03:05 ([`cron-15`](cron-15)) e riavvia `pihole-FTL`. Niente SSH fra i due host. Un archivio
  rotto o più vecchio di 36 ore non viene importato. Registro `/var/log/pihole-sync.log`.
- Installazione: `install -m 0644 cron-20 /etc/cron.d/domopi-pihole-sync` (sul `.15` `cron-15`).
- Le modifiche a Pi-hole vanno fatte sul `.20`: quelle fatte sul `.15` vengono sovrascritte la notte.
