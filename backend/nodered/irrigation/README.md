# IRRIGATION.1 — OpenSprinkler dall'app e registrazione su EmonCMS

Data: 11 ottobre 2026. Nuova scheda Node-RED "Irrigazione OpenSprinkler (DomoPi)" (13 nodi
aggiunti, nessuno modificato: il Digital Twin Router non è toccato).

OpenSprinkler 2.2.1 su `192.168.1.3:9003`: zone 1–5 (retro piazzola, retro cucina, ingresso
secondario, fronte ingresso, fronte garage; S06–S08 disattivate), programmi Vacanza, Semina,
alba fronte, alba retro. OpenSprinkler pubblica su MQTT ma non accetta comandi MQTT: Node-RED
usa l'API HTTP. Password: MD5 in `/home/pi/.config/domopi/opensprinkler.md5` (utente pi, 0600),
caricata all'avvio e ogni ora nel context del flow; mai nei log, mai su MQTT, mai nel repository.

## Comandi (dall'app, non retained)

| Topic | Payload | API OpenSprinkler |
|---|---|---|
| `zara/interface/irrigation/zone/N/start/cmd` | minuti 1–60 | `cm?sid=N-1&en=1&t=s` |
| `zara/interface/irrigation/zone/N/stop/cmd` | `true` | `cm?sid=N-1&en=0` |
| `zara/interface/irrigation/program/N/start/cmd` | `true` | `mp?pid=N-1&uwt=0` |
| `zara/interface/irrigation/all/stop/cmd` | `true` | `cv?rsn=1` |
| `zara/interface/irrigation/controller/enabled/cmd` | `true`/`false` | `cv?en=1/0` |

Zona o programma sconosciuti, minuti fuori 1–60 e valori diversi vengono scartati. Esito su
`zara/interface/irrigation/last_command/stat` (`ok`, `reason` o codice OpenSprinkler).

## Stato

Ogni 10 s (e subito dopo un comando) `GET /ja`; su
`zara/interface/irrigation/state/stat` (retained, schema `domopi.irrigation.v1`) solo i campi
necessari: controller attivo, ritardo pioggia, zone (stato, secondi residui), programmi.
Pubblicato quando cambia e almeno ogni 60 s.

## Registrazione su EmonCMS (broker .15)

- `emon/irrigazione/zona_1` … `zona_5`: 1 se la zona irriga, 0 se ferma, ogni 10 s;
- `emon/irrigazione/zona_N_durata_s`: durata misurata (s) alla fine di ogni irrigazione.

### Collegare gli input ai feed (una volta, dall'interfaccia EmonCMS)

Dopo il deploy, in EmonCMS → **Input** compare il nodo `irrigazione` con 10 input. Per ciascun
input `zona_N`:

1. clic sull'icona della chiave inglese a destra della riga;
2. processo **Log to feed**, motore **PHPFina** (Fixed Interval), intervallo **10 s**,
   nome feed `irrigazione_zona_N` (tag `irrigazione`) → **Add**;
3. salvare.

Per ciascun input `zona_N_durata_s`: stessa procedura con motore **PHPTimeSeries**
(variabile, un punto per irrigazione), nome `irrigazione_zona_N_durata_s`.

**Stato al 30/10/2026** (emoncms del nuovo `.15`): `zona_1`…`zona_5` collegati ai feed **478–482**
(`irrigazione_zona_N`, PHPFina 10 s, tag `irrigazione`). Gli input `zona_N_durata_s` compaiono alla
prima irrigazione: vanno collegati allora (PHPTimeSeries). Decisione dell'utente: **una sola fonte**,
questo nodo; il nodo `opensprinkler` (messaggi MQTT di OpenSprinkler, stazioni 0/1/3/4 numerate da 0,
feed 215–231, fermo dal 22/09) resta solo come storico.

Dall'API (emoncms 12.2) il collegamento vuole la lista dei processi in JSON:
`POST input/process/set.json?inputid=<id>` con
`processlist=[{"fn":"process__log_to_feed","args":[<feedid>]}]` (il vecchio formato `1:<feedid>`
è rifiutato con «Invalid process list format»).

Minuti d'irrigazione su un periodo = media del feed `zona_N` × durata del periodo, oppure
somma dei valori `durata_s` / 60. I litri si ottengono moltiplicando per la portata di ogni zona.

I vecchi feed `station_*` (eventi di OpenSprinkler, fermi al 22/09/2024) restano come archivio.

File: `load_password.js`, `poll.js`, `command.js`, `response.js` (corpi delle function),
`build.py`, `update.json`, `rollback.json`, `manifest.json`, `test.js` (9 test), `deploy.py`
(`prepare`, `apply BACKUP`, `rollback BACKUP`: il rollback rimuove i 13 nodi).
