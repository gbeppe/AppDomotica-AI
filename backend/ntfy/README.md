# NTFY.1 — notifiche sul telefono (fase G)

Server [ntfy](https://github.com/binwiederhier/ntfy) (Philipp C. Heckel) **2.28.0** arm64 sul
`.20`, pacchetto ufficiale verificato con `checksums.txt`. Un solo indirizzo dentro e fuori casa:
**`https://domopi.tailf30ba8.ts.net:8443`** (stesso schema di
[`ACCESSO_LAN_SENZA_TAILSCALE.md`](../../docs/AI_MODE/ACCESSO_LAN_SENZA_TAILSCALE.md)).

| Dove | Percorso |
|---|---|
| casa (Tailscale spento) | Pi-hole → `192.168.1.20:8443`, HTTPS servito da ntfy |
| fuori (Tailscale acceso) | Tailscale Serve `:8443` → `127.0.0.1:2586` (solo tailnet) |

- `server.yml` → `/etc/ntfy/server.yml`: accesso negato senza credenziali, iscrizioni e interfaccia
  web disattivate, messaggi conservati 12 h, nessun allegato, nessun servizio esterno.
- `ntfy-cert.sh` + `ntfy-cert.{service,timer}`: copia quotidiana (04:30) del certificato di
  `/etc/house-ai/tls` in `/etc/ntfy/tls` (`root:ntfy`, 0640); riavvia ntfy solo se cambia.
- Utenti (in `/var/lib/ntfy/user.db`, mai nel repository): `domopi-nodered` scrive su `domopi-*`
  con un token in `~/.config/domopi/ntfy_nodered.token` (0600); l'utente del telefono legge
  `domopi-*` (password scelta dall'utente).
- Canali: `domopi-regole` (motore delle regole), poi `domopi-casa` (altri avvisi).

## Installazione

```sh
sudo apt install ./ntfy_2.28.0_linux_arm64.deb
sudo install -o root -g root -m 0644 server.yml /etc/ntfy/server.yml
sudo install -o root -g root -m 0755 ntfy-cert.sh /usr/local/sbin/ntfy-cert.sh
sudo install -o root -g root -m 0644 ntfy-cert.service ntfy-cert.timer /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl start ntfy-cert.service
sudo systemctl enable --now ntfy.service ntfy-cert.timer
sudo tailscale serve --bg --https=8443 http://127.0.0.1:2586
```

## Tornare indietro

```sh
sudo systemctl disable --now ntfy ntfy-cert.timer && sudo apt remove ntfy
sudo tailscale serve --https=8443 off
```
