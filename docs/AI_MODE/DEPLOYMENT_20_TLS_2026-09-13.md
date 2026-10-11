# Deployment persistente House AI su `.20`

Eseguito il 13 settembre 2026 con autorizzazione esplicita dell'utente.

## Stato installato

- Release attiva: `/opt/domopi-house-ai/releases/20260913-climate-reason`, con
  `REVISION` uguale a `ef1de8b`;
- release precedente disponibile per rollback:
  `/opt/domopi-house-ai/releases/20260913-stack-health`, revisione `f2d50d8`;
- symlink attivo: `/opt/domopi-house-ai/current`;
- utente non interattivo: `house-ai`;
- `house-ai-gateway.service`: abilitato e attivo, `127.0.0.1:8766`;
- `house-ai-backend.service`: abilitato e attivo, `127.0.0.1:8765`;
- configurazione: `/etc/house-ai/{gateway,backend}.env`, `root:house-ai`, 0640;
- chiave Groq: `/etc/house-ai/secrets/groq.key`, `house-ai:house-ai`, 0600;
- token Android recuperabile privatamente dall'utente in
  `/home/pi/.config/house-ai/android.token`, `pi:pi`, 0600;
- Tailscale Serve: `https://domopi.tailf30ba8.ts.net/` verso backend 8765,
  accessibile solo dalla tailnet; gateway non esposto;
- il precedente servizio Tailscale `svc:emoncms` è rimasto invariato.

Health locale autenticato: HTTP 200, `mode=read_only`, planner e log configurati.
TLS: certificato Let's Encrypt valido per `domopi.tailf30ba8.ts.net`; health
autenticato HTTP 200. Domanda reale HTTPS sul prelievo del 6 settembre: piano
corretto, una evidenza EmonCMS e stato `complete`.

L'aggiornamento successivo aggiunge `energy_daily_extreme`: selezione read-only
del massimo o minimo giornaliero su EmonCMS, con esclusione dei giorni senza
copertura completa. Non richiede nuove dipendenze, porte, credenziali o modifiche
a `.15`, Node-RED, Mosquitto e Tailscale.

La release `16e47fa` è stata compilata e ha superato 63 test come utente di
servizio su Python 3.9.2 prima dello spostamento atomico del symlink. Dopo il
riavvio, health autenticato, servizi e quattro log allowlisted risultavano
disponibili. La domanda originale è riuscita attraverso il TLS dell'app; dati e
limiti sono in
[`VERIFICA_ESTREMI_GIORNALIERI_2026-09-13.md`](VERIFICA_ESTREMI_GIORNALIERI_2026-09-13.md).

La release successiva `30ddc13` mantiene nel secondo confronto anche i valori
dei giorni parziali come energia osservata. Ha superato compilazione e 64 test su
Python 3.9.2 prima dell'attivazione; health e prova TLS reale sono riusciti senza
modificare servizi, porte o credenziali.

La release `f2d50d8` aggiunge gli endpoint health autenticati senza nuove porte,
dipendenze o credenziali. Ha superato 66 test su `.20` con Python 3.9.2 prima
dell'attivazione. La verifica TLS ha osservato gateway, EmonCMS e log verdi,
Groq inizialmente sconosciuto e poi verde dopo una richiesta reale. Evidenza in
[`VERIFICA_DIAGNOSTICA_STACK_2026-09-13.md`](VERIFICA_DIAGNOSTICA_STACK_2026-09-13.md).

La release `ef1de8b` aggiunge la consultazione read-only di stato e motivo del
condizionatore senza nuove porte, dipendenze o credenziali. Ha superato il
parsing sintattico e 69 test su `.20` con Python 3.9.2 prima dell'attivazione.
Le domande reali «Perché il condizionatore è acceso?» e «Perché?» sono riuscite
tramite TLS, gateway Groq e record MQTT retained. Evidenza in
[`VERIFICA_CLIMATIZZAZIONE_2026-09-13.md`](VERIFICA_CLIMATIZZAZIONE_2026-09-13.md).

La release `20260914-smart-lights`, revisione `b41bf2a`, aggiunge i tool chiusi
`current_lights` e `set_light_state`. Ha superato 71 test su Python 3.9.2; una
prova autenticata ha verificato il piano ON della libreria senza pubblicare MQTT,
e una fixture di transizione ha verificato l'attribuzione `automazione`. Dettagli
in [`SMART_LUCI.md`](SMART_LUCI.md).

## Release del 10 ottobre 2026 — freschezza dei log

Release attiva: `/opt/domopi-house-ai/releases/20261010-log-freshness`, commit
`ce69838` (branch `feature/ui-topbar`); `PREVIOUS_RELEASE` =
`20260922-smart-light-semantics-r2` (rollback: ripuntare `current` e riavviare
i due servizi). Prima del deploy verificato che i 41 file della release r2
coincidessero con il codice committato. 82 test superati su `.20` con Python
3.9.2 come utente `house-ai`, controllo sintattico AST, symlink spostato in modo
atomico, servizi `active`.

Novità: freschezza per fonte dei log Node-RED (apprendimento orario, previsione
giornaliera, eventi clima a evento, SHADOW dichiarato archivio fermo al
06/09/2026). Health `logs`: `online`/`degraded`/`offline` con dettaglio per file.
Domanda reale dopo il deploy («Cosa dice il log shadow del Predictive Reserve di
ieri?»): `insufficient_data`, «il log non è più aggiornato dal 06/09/2026».

## Release dell'11 ottobre 2026 — risposte brevi e sensori ambiente

Release attiva: `/opt/domopi-house-ai/releases/20261011-short-answers`, commit
`1a34e09`; `PREVIOUS_RELEASE` = `20261011-light-scenes`. 110 test superati su
`.20`, servizi `active`. Risposte lette brevi (comandi: "Ok, accendo …"); scene,
timer, stadi dei comandi e copertura passano nei limiti mostrati in diagnostica.
Nuovo strumento di lettura `current_environment` (living, camera da letto,
cameretta, bagno servizio, locale tecnico, esterno). Prova reale con Groq: 7
domande su 7 con piano atteso (stato luci, temperatura living, temperature casa,
"Living?", umidità esterna, cameretta, accensione lampada TV). Nessun comando
inviato dal backend. App `bld_fe52e869` (`6cefacf`) installata. Prima il
backend: le release precedenti rifiutano `current_environment` (HTTP 400).

## Release dell'11 ottobre 2026 — scene luci e Prolunga (fase 3.3)

Release attiva: `/opt/domopi-house-ai/releases/20261011-light-scenes`, commit
`41670a3`; `PREVIOUS_RELEASE` = `20261011-spoken-dates`. 103 test superati su
`.20`, servizi `active`, catalogo con `set_light_scene` e `lights_prolunga`.
Prova reale con Groq: 7 domande su 7 con piano atteso (scene tv, sleep, all_off,
all_on; spegnimento e stato della Prolunga con stato letto dal broker;
chiarimento per una luce non mappata). Nessun comando inviato dal backend. App
`bld_811b0571` (`8d0265f`) installata. Prima il backend: le release precedenti
rifiutano `lights_prolunga` nello snapshot luci (HTTP 400).

## Release dell'11 ottobre 2026 — date leggibili nelle risposte

Release attiva: `/opt/domopi-house-ai/releases/20261011-spoken-dates`, commit
`677389e`; `PREVIOUS_RELEASE` = `20261011-thermostats`. 98 test superati su `.20`,
servizi `active`. Le risposte usano «gg/mm alle hh:mm» e periodi «dal gg/mm al
gg/mm» (ultimo giorno compreso) invece di timestamp ISO; anno solo se diverso da
quello corrente. App `bld_112565b0` (`5237ff6`) installata con lo stesso formato
per riepilogo e provenienza. Compatibile in entrambi gli ordini di rilascio.

## Release dell'11 ottobre 2026 — termostati riscaldamento (fase 3.2)

Release attiva: `/opt/domopi-house-ai/releases/20261011-thermostats`, commit
`4c026b8`; `PREVIOUS_RELEASE` = `20261010-predictive-reserve`. 95 test superati su
`.20` (Python 3.9.2, utente `house-ai`), controllo AST, symlink atomico, servizi
`active`, catalogo con `current_thermostats` e `set_thermostat_target`. Prova
reale con Groq e snapshot dei termostati letto dal broker: 7 domande su 7 con
piano atteso (due letture, due setpoint 20,0 e 19,5 °C, chiarimento per 25 °C,
per «alza un po'» e per il condizionatore). Il backend non pubblica su MQTT:
nessun comando è arrivato ai termostati. Limite osservato: il piano gratuito
Groq consente 8000 token/minuto e ogni domanda ne usa circa 3500, quindi più di
due domande al minuto ricevono HTTP 502 (429 dal provider). App
`bld_9eb70b87` (`9ffd069`) pubblicata; prima il backend, perché le release
precedenti rifiutano il campo `current_thermostats` (HTTP 400).

## Release del 10 ottobre 2026 — stato del Predictive Reserve (fase 3.1)

Release attiva: `/opt/domopi-house-ai/releases/20261010-predictive-reserve`, commit
`18cbd1c`; `PREVIOUS_RELEASE` = `20261010-log-freshness`. 89 test superati su
`.20` (Python 3.9.2, utente `house-ai`), controllo AST, symlink atomico, servizi
`active`. Nuovo tool chiuso `current_predictive_reserve` sullo snapshot
`current_predictive_reserve` inviato dall'app (topic PR-MODE.1, valori in
allowlist). Domanda reale «Il PR è attivo? In che modalità è?»: piano
`current_predictive_reserve`, risposta «controllo disattivato … Modalità: ACTIVE ·
Modello: pronto». Il backend va rilasciato prima dell'app: le versioni precedenti
rifiutano il campo `current_predictive_reserve` (HTTP 400).

Dal 10/10/2026 l'app salva il token cifrato (Android Keystore): va inserito una
sola volta, copiandolo privatamente da `/home/pi/.config/house-ai/android.token`.

L'app precompila questo URL e `HouseAiRepository` accetta HTTP soltanto per
loopback di test. Il token non è incorporato nell'APK e resta in memoria nella
schermata. Sul telefono connesso alla stessa tailnet, copiare privatamente il
contenuto di `~/.config/house-ai/android.token` nel campo token. Non inviarlo in
chat o screenshot.

## Operazioni utili

```sh
systemctl status house-ai-gateway house-ai-backend
tailscale serve status
```

Riavvio dopo aggiornamento:

```sh
sudo systemctl restart house-ai-gateway house-ai-backend
```

Rollback applicativo: puntare `/opt/domopi-house-ai/current` alla release
precedente verificata, poi riavviare i due servizi. Disattivazione TLS House AI:

```sh
sudo tailscale serve --https=443 off
```

Arresto e disabilitazione completa, senza rimuovere configurazioni o release:

```sh
sudo systemctl disable --now house-ai-backend house-ai-gateway
```

Non usare `tailscale serve reset`: rimuoverebbe anche configurazioni Serve non
appartenenti a House AI. `.15`, Node-RED, Mosquitto e i log non sono stati modificati.

## Limiti residui

Il TLS è privato Tailscale: il telefono deve appartenere alla tailnet e avere
connettività Tailscale. Non è stato configurato Funnel pubblico. Il test da
workstation ha verificato TLS e query completa; resta da inserire il token sul
telefono fisico e provare testo, riconoscimento e sintesi vocale. La release
finale è costruita dal commit registrato in `REVISION`; il symlink `current`
viene spostato solo dopo copia e test completi.
