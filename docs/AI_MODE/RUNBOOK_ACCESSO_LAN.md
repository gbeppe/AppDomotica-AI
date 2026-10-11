# Runbook — AI Smart via LAN: certificato e ripristino

Istruzioni operative per l'accesso LAN di House AI installato l'11/10/2026
(architettura in [`ACCESSO_LAN_SENZA_TAILSCALE.md`](ACCESSO_LAN_SENZA_TAILSCALE.md)).
Tutti i comandi vanno eseguiti su `.20` (`ssh pi@192.168.1.20`). Nessun comando qui
stampa token o chiavi.

## Cosa c'è su `.20`

| Elemento | Percorso | Ruolo |
|---|---|---|
| servizio HTTPS LAN | `/etc/systemd/system/house-ai-lan.service` | `192.168.1.20:443` → backend `127.0.0.1:8765` |
| configurazione | `/etc/house-ai/lan.env` (`root:house-ai`, 0640, senza segreti) | indirizzo, porta, subnet ammessa, percorsi certificato |
| certificato | `/etc/house-ai/tls/cert.pem`, `key.pem` (`root:house-ai`, 0640) | copia del certificato Let's Encrypt di Tailscale |
| rinnovo | `/etc/systemd/system/house-ai-lan-cert.{service,timer}` | settimanale, esegue `deploy/lan_cert.sh` della release attiva |
| DNS | Pi-hole `dns.hosts`: `192.168.1.20 domopi.tailf30ba8.ts.net` | in casa il nome porta all'IP LAN |
| backup DNS | `/etc/pihole/pihole.toml.bak-20261011-house-ai-lan` | configurazione Pi-hole prima del record |

## Certificato

**Come funziona.** Il nome `domopi.tailf30ba8.ts.net` ha un certificato Let's Encrypt
di 90 giorni gestito da Tailscale (lo stesso usato da Tailscale Serve). Ogni settimana
(lunedì notte, ritardo casuale fino a 6 h, recupero al riavvio se saltato)
`house-ai-lan-cert.service`:

1. chiede il certificato con `tailscale cert`, che lo rinnova da solo quando manca
   circa un terzo della validità (~30 giorni);
2. si ferma con errore se il certificato ottenuto scade entro 24 h;
3. se è identico a quello installato non fa nulla;
4. se è cambiato lo copia in `/etc/house-ai/tls/` e riavvia `house-ai-lan`
   (pochi secondi di indisponibilità solo per l'accesso LAN).

**Controlli.**

```sh
systemctl status house-ai-lan.service --no-pager
systemctl list-timers house-ai-lan-cert.timer --no-pager      # prossima esecuzione
systemctl status house-ai-lan-cert.service --no-pager         # esito dell'ultima
sudo openssl x509 -in /etc/house-ai/tls/cert.pem -noout -enddate
```

Dal PC di casa (senza Tailscale serve solo la LAN): risposta attesa `401`, verifica
TLS `0`.

```sh
curl -sS --resolve domopi.tailf30ba8.ts.net:443:192.168.1.20 \
     https://domopi.tailf30ba8.ts.net/v1/health -o /dev/null \
     -w 'HTTP %{http_code} TLS %{ssl_verify_result}\n'
```

**Sintomo tipico di certificato scaduto o mancante:** in casa con Tailscale spento
AI Smart risponde "Assistente non raggiungibile…", mentre con Tailscale acceso
funziona. Nei log: `journalctl -u house-ai-lan-cert.service -u house-ai-lan.service -n 50`.

**Rinnovo manuale** (sicuro da ripetere; riavvia il servizio solo se serve):

```sh
sudo systemctl start house-ai-lan-cert.service
systemctl show house-ai-lan-cert.service -p Result      # atteso Result=success
```

Se fallisce: verificare che Tailscale sia attivo (`tailscale status`), che HTTPS sia
ancora abilitato nella console Tailscale (DNS → HTTPS Certificates) e che il nome
del nodo sia ancora `domopi`. Un cambio di nome del nodo o della tailnet cambia il
nome del certificato: vanno aggiornati `NAME` in `deploy/lan_cert.sh`, il record
Pi-hole e l'URL House AI nell'app.

**Dopo un deploy di House AI.** Il timer esegue lo script della release attiva
(`/opt/domopi-house-ai/current/deploy/lan_cert.sh`); il servizio LAN usa
`lan_tls.py` della release attiva e va riavviato insieme agli altri:

```sh
sudo systemctl restart house-ai-gateway house-ai-backend house-ai-lan
```

Una release precedente all'11/10/2026 (`20261011-short-answers` o più vecchia) non
contiene `lan_tls.py`: tornare a quella release ferma l'accesso LAN (il servizio
fallisce e si riprova ogni 5 s). In quel caso eseguire anche il ripristino
qui sotto.

## Per annullare tutto

Ripristino completo: `.20` torna come prima dell'11/10/2026 e AI Smart funziona solo
con Tailscale. Tailscale Serve, Apache, Node-RED, Mosquitto e il backend non vengono
toccati.

```sh
# 1. servizio LAN e rinnovo
sudo systemctl disable --now house-ai-lan.service house-ai-lan-cert.timer
sudo rm -f /etc/systemd/system/house-ai-lan.service \
           /etc/systemd/system/house-ai-lan-cert.service \
           /etc/systemd/system/house-ai-lan-cert.timer \
           /etc/house-ai/lan.env
sudo rm -rf /etc/house-ai/tls
sudo systemctl daemon-reload

# 2. record DNS su Pi-hole (resta solo il record preesistente del WallBox)
sudo pihole-FTL --config dns.hosts '["192.168.1.4 registry.wallbox.lan"]'
dig +short @192.168.1.20 domopi.tailf30ba8.ts.net      # atteso: nessun risultato
```

Il file `lan_tls.py` resta nella release ma non viene eseguito. Sul telefono:
spegnere e riaccendere il Wi-Fi per svuotare la cache DNS.

**Ripristini parziali.**

| Obiettivo | Comando |
|---|---|
| sospendere solo l'accesso LAN, lasciando tutto installato | `sudo systemctl stop house-ai-lan` (riparte al riavvio di `.20`; per tenerlo fermo `disable --now`) |
| riattivarlo | `sudo systemctl enable --now house-ai-lan` |
| togliere solo il record DNS | passo 2 sopra: in casa senza Tailscale il nome non si risolve più; il servizio resta acceso ma inutilizzato |
| ripristinare l'intera configurazione Pi-hole di prima | `sudo cp -a /etc/pihole/pihole.toml.bak-20261011-house-ai-lan /etc/pihole/pihole.toml && sudo systemctl restart pihole-FTL` — annulla anche eventuali altre modifiche a Pi-hole fatte dopo l'11/10, preferire il passo 2 |

**Reinstallare** dopo un ripristino: sezione "Installazione" di
[`ACCESSO_LAN_SENZA_TAILSCALE.md`](ACCESSO_LAN_SENZA_TAILSCALE.md), poi il record Pi-hole.
