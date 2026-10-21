# Sicurezza di rete e credenziali (ottobre 2026)

Stato dopo il lavoro dell'11–14 ottobre 2026. Nessun segreto è scritto in questo documento:
qui ci sono solo **dove** stanno e **come** si ripristina.

## Accesso da fuori casa

- Unica via: **Tailscale**. `.20` (domopi) è subnet router per `192.168.1.0/24`; l'app usa
  sempre gli indirizzi di casa e Android li instrada su Tailscale quando si è fuori.
- AI Smart: `https://domopi.tailf30ba8.ts.net` (Tailscale Serve, solo tailnet); in casa lo stesso
  nome punta a `192.168.1.20` tramite Pi-hole e il servizio `house-ai-lan`
  (`docs/AI_MODE/RUNBOOK_ACCESSO_LAN.md`).

## Router ASUS (192.168.1.1)

L'IP pubblico è dinamico e **non** è dietro CGNAT: ogni apertura del router è raggiungibile da
internet. Il 14/10 erano esposti MQTT senza password, Node-RED, SSH di `.40`, amministrazione
QNAP, telecamere, EmonCMS e OpenSprinkler.

| Impostazione | Stato dal 14/10/2026 |
|---|---|
| WAN → Virtual Server / Port Forwarding | **OFF** (regole ancora elencate ma disattivate) |
| WAN → DMZ | **No** |
| WAN → Connessione ad Internet → Abilita UPnP | **No** (verificato: il router non risponde più a SSDP) |
| Enable IGDv2 (IPv6 pinhole) | No |

Da controllare a mano se si cambia router o firmware: accesso web da WAN (Amministrazione →
Sistema), AiCloud, server VPN dell'ASUS, firewall IPv6.

## Broker MQTT di `.20`

- Porta 1883: **`allow_anonymous false`**, un solo utente: **`domotica`**. L'utente `domopi`
  (password pubblicata in passato nel codice dell'app su GitHub) è stato eliminato.
- **Dal 19/10/2026 21:11 `allow_anonymous true`** (scelta dell'utente, anche sul `.15` alle 21:14):
  da richiudere dopo aver dato credenziali a zigbee2mqtt (.119) e MQTT Explorer (.40), guardando
  prima nel log se ci sono altri client anonimi (punto C del piano).
- `persistence true` (ripristinata il 21/10: dal 19/10 era `false` e i retained si perdevano a ogni
  riavvio); `persistent_client_expiration 2d` (`conf.d/50-session-expiry.conf`).
- Porta 1884 (`192.168.1.20`): listener separato di sola lettura per l'adattatore c1321,
  invariato (`/etc/mosquitto/conf.d/40-c1321-read-only-listener.conf`).
- File utenti `/etc/mosquitto/credentials`: `root:mosquitto` 0640.
- Bridge verso il QNAP (`qnap-bridge.conf.P`, già inattivo dal 2025, con password in chiaro
  leggibile da tutti): rimosso; il broker sul QNAP non esiste più.

Client che usano `domotica` (verificati nel log il 14/10): Tasmota termostato living (.69),
bagno (.68), bagno di servizio (.67), frigorifero (.252, cambia IP via DHCP), OpenSprinkler (.3),
Node-RED su `.20` e su `.15`, Zigbee2MQTT (.119), app Android, servizio
`house-ai-grid-import`, MQTT Explorer su `.40`.

### Dove sta la password di `domotica`

| Posto | File / meccanismo |
|---|---|
| `.20` | `/home/pi/.config/domopi/mqtt_domotica.pass` (pi, 0600) |
| `.20`, servizio prelievo | `/etc/house-ai/mqtt/mosquitto_pub` (root:house-ai 0640) + drop-in `/etc/systemd/system/house-ai-grid-import.service.d/mqtt.conf` |
| `.40` | `~/.config/domopi/mqtt_domotica.pass` e `~/.config/domopi/mqtt_password` (tool di rilascio, con `mqtt.json`) |
| Node-RED `.20` | credenziali cifrate del nodo broker "domopi mqtt" (`backend/nodered/broker_credentials/set_broker_user.py`) |
| Node-RED `.15`, Zigbee2MQTT, Tasmota, OpenSprinkler | nelle rispettive configurazioni |
| App | deposito cifrato Android Keystore (Configurazione → broker Z-AI) |

Comandi di diagnostica su `.20`:
`mosquitto_sub -h localhost -u domotica -P "$(cat ~/.config/domopi/mqtt_domotica.pass)" -v -t '#'`

### Cambiare la password in futuro

1. Generare la nuova password nel file di `.20`, poi
   `sudo mosquitto_passwd -b /etc/mosquitto/credentials domotica "$(cat FILE)"` e
   `sudo systemctl reload mosquitto` (le sessioni aperte restano).
2. Aggiornare ogni client dell'elenco: Tasmota via
   `http://IP/cm?cmnd=Backlog MqttUser domotica; MqttPassword …`; OpenSprinkler via
   `/co?pw=…&mqtt=…` (JSON **senza** le graffe esterne, altrimenti il firmware lo salva
   malformato); Node-RED `.20` con `set_broker_user.py`; gli altri a mano.
3. Controllare nel log `/var/log/mosquitto/mosquitto.log` che ogni client si ricolleghi.

### Ripristino

Backup in `/home/pi/AI_backups/mosquitto/`:
`20261014T075020Z-before-new-user`, `20261014T081228Z-before-close-anonymous` (contiene
`mosquitto.conf` e `credentials` di prima). Per tornare all'accesso anonimo: copiare i due file
al loro posto e `sudo systemctl reload mosquitto`.

## App Android

- Nessuna credenziale nel codice (test `NoSecretsInCodeTest`). Password MQTT e TinyCam, PIN
  admin, chiave EmonCMS e token House AI solo nel deposito cifrato (Android Keystore).
- Campi segreti con pulsante **Incolla** (svuota gli appunti dopo l'uso) e **Mostra/Nascondi**.
- Cambiando utente o password MQTT l'app si ricollega da sola.

## Rischi accettati

- **Chiave EmonCMS di sola lettura**: pubblicata nella storia del repository (commit `cc9abfd`).
  Non cambiata perché usata da molti sistemi. Permette solo di leggere i dati, ed EmonCMS non è
  più raggiungibile da internet: resta leggibile solo da rete di casa e tailnet.
- **Rete di casa**: chi è sul Wi-Fi raggiunge ancora le interfacce web locali (Tasmota senza
  password, telecamere, EmonCMS con login). Il broker invece richiede la password.
- **Login Alexa in Node-RED** scaduto dal 22/09 (annunci della scheda "Alexa Speak" non
  funzionanti); da rifare su `http://192.168.1.20:3456` solo se servono.
