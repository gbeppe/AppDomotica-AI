# AI Smart in casa senza Tailscale (variante B)

Obiettivo: in casa AI Smart funziona con il solo Wi-Fi; Tailscale serve solo fuori
casa. L'app non cambia indirizzo: usa sempre `https://domopi.tailf30ba8.ts.net`.

| Dove | Tailscale | Chi risolve il nome | Percorso |
|---|---|---|---|
| casa | spento | Pi-hole `.20` → `192.168.1.20` | `house-ai-lan` (LAN :443) → backend |
| casa o fuori | acceso | MagicDNS → `100.100.127.81` | Tailscale Serve → backend (invariato) |
| fuori | spento | — | non raggiungibile (voluto) |

## Componenti

- `backend/house_ai/lan_tls.py`: HTTPS solo su `192.168.1.20:443`, client ammessi solo
  da `192.168.1.0/24`, inoltra a `127.0.0.1:8765` solo le route dell'API (GET health,
  health/details, report, energy/query; POST assistant/query), corpo max 32 KB, max 16
  client contemporanei, nessun log del traffico. Autenticazione invariata nel backend.
- `deploy/house-ai-lan.service`: utente `house-ai`, unica capability
  `CAP_NET_BIND_SERVICE`, stesso hardening degli altri servizi.
- `deploy/lan_cert.sh` + `house-ai-lan-cert.{service,timer}`: settimanale, come root
  copia il certificato Let's Encrypt di `tailscale cert` in `/etc/house-ai/tls/`
  (`root:house-ai`, 0640) e riavvia `house-ai-lan` solo se il file cambia.
- `deploy/lan.env.example` → `/etc/house-ai/lan.env` (nessun segreto).
- Pi-hole: record locale `192.168.1.20 domopi.tailf30ba8.ts.net` (`dns.hosts`).

Non tocca Apache, Node-RED, dashboard, phpMyAdmin, Mosquitto né Tailscale Serve. La
porta 443 su `192.168.1.20` era libera: Tailscale usa la 443 solo su `100.100.127.81`.

## Installazione (dopo il deploy della release che contiene `lan_tls.py`)

```sh
R=/opt/domopi-house-ai/current/deploy
sudo install -o root -g house-ai -m 0640 $R/lan.env.example /etc/house-ai/lan.env
sudo install -o root -g root -m 0644 $R/house-ai-lan.service $R/house-ai-lan-cert.service \
     $R/house-ai-lan-cert.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl start house-ai-lan-cert.service      # crea /etc/house-ai/tls
sudo systemctl enable --now house-ai-lan.service house-ai-lan-cert.timer
```

Verifica dal PC prima del DNS (il nome punta a mano all'IP LAN):

```sh
curl -sS --resolve domopi.tailf30ba8.ts.net:443:192.168.1.20 \
     https://domopi.tailf30ba8.ts.net/v1/health -o /dev/null -w '%{http_code}\n'   # 401 senza token
```

Solo dopo: record su Pi-hole (con backup di `pihole.toml`).

## Certificato e ripristino

Controlli, rinnovo manuale del certificato e comandi per annullare tutto (anche in
parte) sono in [`RUNBOOK_ACCESSO_LAN.md`](RUNBOOK_ACCESSO_LAN.md).

## Sicurezza

Prima il backend era raggiungibile solo dalla tailnet; ora anche dalla LAN di casa
(compresi dispositivi IoT e ospiti sul Wi-Fi). Restano TLS e token Android; il backend
non esegue comandi (li pubblica l'app via MQTT): con il token si leggono i dati di
casa e si consuma quota Groq.
