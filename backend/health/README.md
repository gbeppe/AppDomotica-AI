# Salute impianto, fase S1 (`.20` e `.15`)

Progetto e decisioni: [`docs/AI_MODE/SALUTE_IMPIANTO.md`](../../docs/AI_MODE/SALUTE_IMPIANTO.md) · manuale utente:
[`docs/MANUALE_SALUTE_IMPIANTO.md`](../../docs/MANUALE_SALUTE_IMPIANTO.md) (da aggiornare a ogni modifica dei controlli).

`health_check.py` gira ogni 2 minuti (timer systemd `domopi-health.timer`, utente root, solo letture)
su entrambi i Raspberry, fuori da Node-RED e House AI. Ogni giro:

1. esegue i controlli dovuti: leggeri a ogni giro, pesanti (configurazione Mosquitto, Internet, disco,
   memoria, temperatura, errori della scheda, orologio) ogni 15 minuti;
2. confronta con lo stato in `/var/lib/domopi-health/state.json` e manda su ntfy (`domopi-casa`) un
   avviso per guasto con il consiglio, un promemoria ogni 6 ore e un messaggio quando è a posto. Dalle
   23 alle 7 solo gli urgenti (Raspberry o router muto, broker, servizi principali, sottotensione,
   errori della scheda); gli altri arrivano dopo le 7 se il guasto c'è ancora;
3. pubblica il riepilogo retained su `zara/system/health/<host>/stat` (schema `domopi.system_health.v1`,
   nessun segreto) per la pagina dell'app (S3).

| Controllo | `.20` | `.15` |
|---|---|---|
| alimentazione (`get_throttled`, eventi di sottotensione) | ✓ | ✓ |
| servizi systemd | mosquitto, nodered, house-ai-×3, ntfy, pihole-FTL, tailscaled, apache2 | mosquitto, nodered, emonhub, emoncms_mqtt, feedwriter, redis, mariadb, apache2 |
| broker locale (collegamento; retained `zara/domotics/lights/tvlamp` sul `.20`) | ✓ | ✓ |
| configurazione Mosquitto (`persistence`, `allow_anonymous` per listener) | ✓ | ✓ |
| Node-RED, House AI, EmonCMS rispondono | ✓ | ✓ |
| Tailscale `Running` | ✓ | — |
| l'altro Raspberry (ping + 1883), router, Internet (3 prove) | ✓ | ✓ |
| dati energia: ultimo valore dei feed Powerwall (305) ed emonTx (24) < 10 min | — | ✓ |
| emonTx al contrario: pinza CT1 e rete Powerwall di segno opposto per 10 letture | — | ✓ |
| disco, memoria, temperatura, errori della scheda, orologio | ✓ | ✓ |

Credenziali (copiate dal deploy, mai stampate): `/etc/domopi-health/ntfy.token` (token di
`domopi-nodered`), sul `.20` `/etc/domopi-health/mqtt/mosquitto_{sub,pub}` (opzioni `-u/-P`, letti con
`XDG_CONFIG_HOME`), sul `.15` `/etc/domopi-health/mqtt.pass` (utente `domotica` del broker `.20`). Il
`.15` raggiunge ntfy su `192.168.1.20:8443` con il nome TLS `domopi.tailf30ba8.ts.net`.

Nessuna riparazione automatica in S1 (prima settimana di osservazione); S2 aggiunge il riavvio di
`tailscaled` e dei servizi fermi (decisione D3) e la riserva sul `.15` (D2).

## Test e prova a secco

```sh
python3 -B -m unittest discover -s tests          # 14 casi, comandi e rete simulati
sudo python3 -B health_check.py prova.json         # config senza "ntfy"/"publish", stato in /tmp
```

## Installazione e ritorno indietro

```sh
# .15: le credenziali passano dal .20 via ssh, senza essere stampate
ssh pi@192.168.1.20 'cat ~/.config/domopi/ntfy_nodered.token' | ssh pi@192.168.1.15 'install -d -m 700 /tmp/domopi-health-secrets && cat > /tmp/domopi-health-secrets/ntfy.token'
ssh pi@192.168.1.20 'cat ~/.config/domopi/mqtt_domotica.pass' | ssh pi@192.168.1.15 'cat > /tmp/domopi-health-secrets/mqtt.pass'
sudo sh deploy.sh 20 install        # sul .20, dalla cartella con i file
sudo sh deploy.sh 15 install        # sul .15
sudo sh deploy.sh <host> rollback   # rimuove timer, script, configurazione e stato
journalctl -u domopi-health -n 20   # cosa è successo negli ultimi giri
```
