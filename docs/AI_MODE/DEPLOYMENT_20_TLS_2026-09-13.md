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

## Release del 23 ottobre 2026 — chiarimenti in una domanda

`20261023-clarify`: nel prompt energia «ricarica» senza altro = batteria di casa (Powerwall), e più dubbi
vanno chiesti tutti nella stessa domanda. Prova dal telefono delle 08:20: «Percentuale media di
ricarica ultima settimana?» chiedeva solo «quale dispositivo», non il periodo. Nella stessa giornata,
app: il risultato `house_ai.heating_profile.v1` non era letto dall'app («Schema evidenza non
riconosciuto» dal 17/10); `AssistantSchemaContractTest` ora controlla anche `heating_profile_tools.py`
e `rules_tools.py`.

## Release del 23 ottobre 2026 — risposta ai chiarimenti, log del clima senza eventi

`20261023-followup`: campo facoltativo `follow_up` `{question, clarification}` (massimo 500 caratteri
ciascuno) nella richiesta; il pianificatore riceve «Domanda precedente… Chiarimento chiesto… Risposta
dell'utente…» (oltre 1000 caratteri: solo la risposta) e i controlli sulle parole dell'utente («ogni»,
«fino alle», regole) guardano domanda e risposta. «Mese», «ultimo mese» = mese corrente (prima:
chiarimento; richiesta dell'utente). Log del clima: scrive solo ai cambi di stato, quindi un giorno senza
righe dice «non ha registrato cambi di stato» e l'ultimo evento precedente (`previous_record`). Prova
del 23/10 07:55 che l'ha motivato: «perché il condizionatore non è partito ieri notte?» → «nessun record
utilizzabile» (il 22/10 non c'erano eventi; l'ultimo il 21/10 07:52, logica invernale).

## Release del 22 ottobre 2026 — istruzioni per argomento

`20261022-topics`: `route()` sceglie gli argomenti dalla domanda (parole chiave su testo minuscolo senza
accenti; i comandi a tempo portano anche le regole; nessun argomento = tutti) e al modello vanno solo le
sezioni di istruzioni e gli strumenti di quegli argomenti, con catalogo e schema ridotti e JSON compatto.
Token misurati: luci ~1250, clima ~1700, energia ~1850, riscaldamento ~2500, regole/comandi a tempo
2900–4150, tutto ~6300 (prima ~7700). Ritocchi: «prelievo» = `grid_import_kwh`; «ultimo mese/settimana»
chiede chiarimento anche con metrica chiara; «ieri notte» del condizionatore = `backend_log_day`.
Prove dalla copia in `/tmp` sul `.20`: 15 frasi di comando corrette; suite 33/41 con la prima versione del prompt, poi
interrotta dal limite giornaliero di Groq (200.000 token).

## Release del 21 ottobre 2026 — minuto di Groq esaurito

`20261018-vmc` (commit `2843aed`): VMC a voce. Le frasi con orario davano 502: ~7700 token di richiesta
contro 8000 al minuto, e a minuto esaurito Groq risponde 413. `20261021-tokens` (commit `25e1701`):
diagnosi sbagliata, ritirata. `20261021-budget` (commit `bff62fa`): 413 → 429 «riprova tra 60 secondi»,
`max_completion_tokens` 2048, riga `plan tokens …` nel journal del gateway.

## Release del 18 ottobre 2026 — caminetto «fino alle» e comandi a tempo

`20261018-fireplace-until` (commit `da566fd`): in Manuale «accendi il caminetto fino alle…» non cambia
gli orari e avvisa. Poi `20261018-timed-commands` (commit `b3c2897`), con RULES-ENGINE.3 sul
Node-RED: strumento `schedule_command`. Prove reali (nessun comando): «accendi il caminetto fino
alle 23:00» → «Accendo il caminetto e alle 23:00 (oggi) spengo il caminetto. Confermi?»; «accendi la
luce del portico dalle 22 alle 23» → due orari; «spegni tutte le luci fino alle 7 di domani» →
adesso + 07:00 (domani). Prova dal telefono 22:53–22:56 riuscita.

## Release del 17 ottobre 2026 — profilo di riscaldamento in lettura

Release attiva: `/opt/domopi-house-ai/releases/20261017-heating-profile2` (prima
`20261017-heating-profile`, commit `e1070b7`). Nuovo strumento `current_heating_profile` (solo
lettura). Prove reali con il profilo e il piano retained del `.20`: «che profilo di riscaldamento è
attivo?» → Ridotto, automatico con 34 ore di dati su 48; «quando cambia il termostato del bagno?»
→ prossimo cambio domani alle 17:00 a 18 gradi (la prima release chiedeva il periodo: corretto);
«perché il soggiorno è a 17,5 gradi?» → fuori fascia, prossimo cambio. Richiede l'app `bld_bff71307`.

## Release del 17 ottobre 2026 — regole a voce (fase H2)

Release attiva: `/opt/domopi-house-ai/releases/20261017-rules-voice2`, commit `4c866da`
(prima `20261017-rules-voice`, commit `668b908`); `PREVIOUS_RELEASE` = `20261017-rules-voice`.
Test superati su `.20`; servizi `active`. Prove reali: «quando la temperatura del puffer alto
raggiunge i 42 gradi spegni il caminetto» → «Creo questa regola: quando il puffer alto supera 42
gradi spengo il caminetto, una volta. Confermi?»; «se fuori scende sotto 5 gradi metti il bagno a
21» → regola con termostato del bagno a 21, **una volta** (la prima release la rendeva «ogni
volta», corretto); «ogni sera alle 22 spegni tutte le luci» → regola a orario ogni giorno, ogni
volta; «che regole ci sono?» → elenco; «cancella la regola del caminetto» → conferma della
cancellazione. Richiede l'app `bld_955d49d8`.

## Release del 16 ottobre 2026 — richieste di regole automatiche

Release attiva: `/opt/domopi-house-ai/releases/20261016-rules-guard`, commit `4d898e8`;
`PREVIOUS_RELEASE` = `20261016-confirm-safe`. Test superati su `.20`; servizi `active`. Prove
reali: «quando la temperatura del puffer alto raggiunge i 42 gradi spegni il caminetto» e «voglio
impostare una automazione per spegnere il caminetto quando il puffer alto raggiunge i 40 gradi» →
`clarification_required`, «Le regole automatiche non sono disponibili; posso eseguire solo comandi
immediati…», nessuna conferma proposta; «spegni il caminetto» → «Spengo il caminetto. Confermi?».

## Release del 16 ottobre 2026 — domanda di conferma senza parola di conferma

Release attiva: `/opt/domopi-house-ai/releases/20261016-confirm-safe`, commit `d8b1787`;
`PREVIOUS_RELEASE` = `20261016-heating-confirm`. Test superati su `.20`; servizi `active`.
Prova reale: «metti il bagno a 21 gradi» → «Imposto il termostato del bagno a 21 gradi.
Confermi?» (`confirmation_required`). Motivo: alle 09:16 lo stesso comando era partito senza
conferma, quasi certamente per l'eco di «…tocca Conferma.». Richiede l'app `bld_40937b38`
(microfono 1 s dopo la lettura).

## Release del 16 ottobre 2026 — conferma per tutti i comandi del riscaldamento

Release attiva: `/opt/domopi-house-ai/releases/20261016-heating-confirm`, commit `30f1aa1`;
`PREVIOUS_RELEASE` = `20261015-voice-confirm`. Test superati su `.20` (attivazione
condizionata all'esito); servizi riavviati e `active`. Prove reali: «metti il soggiorno a 20
gradi» → `confirmation_required`, «Imposto il termostato del living a 20 gradi. Confermi? Di'
confermo o tocca Conferma.»; «metti il caminetto in manuale e accendilo» (in Riscaldamento) →
due `confirmation_required`, una sola domanda «Metto il caminetto in modalità Manuale e lo
accendo. Confermi? …». Nuovo campo `confirmation` {prompt, done}. Richiede l'app `bld_64bec1e3`.

## Release del 15 ottobre 2026 — conferma a voce

Release attiva: `/opt/domopi-house-ai/releases/20261015-voice-confirm`, commit `5caf031`;
`PREVIOUS_RELEASE` = `20261015-fireplace-level`. Test superati su `.20` (attivazione
condizionata all'esito); servizi riavviati e `active`. Cambia solo il testo della richiesta di
conferma: «spegni il caminetto» in Manuale → `confirmation_required`, «Confermi lo spegnimento
del caminetto? Di' confermo o tocca Conferma.». La parola «confermo» è riconosciuta dall'app
(`bld_67a6ea29`) e non arriva al backend. Prova reale dell'accensione con «confermo»: 16/10.

## Release del 15 ottobre 2026 — livello del caminetto in Manuale

Release attiva: `/opt/domopi-house-ai/releases/20261015-fireplace-level`, commit
`290906f`; `PREVIOUS_RELEASE` = `20261015-fireplace-commands`. 159 test superati su `.20`
(attivazione condizionata all'esito); servizi riavviati e `active`. Prove reali (il backend non
pubblica comandi): «metti il caminetto a 4» in Manuale acceso → `command_ready`, «Ok, imposto il
caminetto al livello 4.»; in Riscaldamento → `rejected`, «…il livello si cambia solo in Manuale.
Con la potenza automatica attiva il livello lo decide la casa.»; «alza il caminetto» →
chiarimento «A quale livello vuoi impostare il caminetto (1-6)?». Richiede l'app `290906f`
(`bld_9e3985c3`).

## Release del 15 ottobre 2026 — comandi del caminetto

Release attiva: `/opt/domopi-house-ai/releases/20261015-fireplace-commands`, commit
`5b0d9f7`; `PREVIOUS_RELEASE` = `20261015-fireplace-wording`. 156 test superati su `.20`
(attivazione condizionata all'esito); servizi riavviati e `active`. Prove reali (il backend non
pubblica comandi): «metti il caminetto in modalità acqua sanitaria dalle 12:00 alle 13:00» →
`command_ready` Acqua Sanitaria 12:00–13:00; «accendi il caminetto» in Riscaldamento →
`rejected`, «…passa prima a Manuale.»; «metti il caminetto a 4» → chiarimento del modello
(livello non disponibile). Richiede l'app `c27f0f5`.

## Release del 15 ottobre 2026 — modalità del caminetto in una frase propria

Release attiva: `/opt/domopi-house-ai/releases/20261015-fireplace-wording`, commit
`fe484b1`; `PREVIOUS_RELEASE` = `20261015-energy-overview`. 148 test superati su `.20`;
servizi riavviati e `active`. Prova reale: «Il caminetto è acceso?» → «Caminetto spento. È in
modalità Riscaldamento, programmato dalle 16:30 alle 16:55, con potenza automatica attiva.»
Solo testo della risposta: nessuna modifica all'app.

## Release del 15 ottobre 2026 — energia adesso con flussi stimati

Release attiva: `/opt/domopi-house-ai/releases/20261015-energy-overview`, commit
`66c4b9e`; `PREVIOUS_RELEASE` = `20261014-heating`. 148 test superati su `.20`
(Python 3.9); riavviati gateway, backend e `house-ai-lan`, tutti `active`. Nuovo strumento
`current_energy_overview`. Prova reale con i valori live: «energia?» → `summary`, «Il
fotovoltaico produce 3,5 kilowatt, la batteria è al 48 per cento e si sta caricando, la casa
consuma 516 watt, non stai importando dalla rete.»; «dettaglio energia» → `flows`, stessa frase
più «Dal fotovoltaico: 516 watt alla casa e 3 kilowatt alla batteria.». Le app precedenti a
`1726c2b` non riconoscono il nuovo risultato: installare subito l'app aggiornata.

## Release del 14 ottobre 2026 — impianto di riscaldamento in sola lettura

Release attiva: `/opt/domopi-house-ai/releases/20261014-heating`, commit
`8c193c7`; `PREVIOUS_RELEASE` = `20261011-grid-import`. 137 test superati su `.20`
(Python 3.9); riavviati gateway, backend e `house-ai-lan`, tutti `active`. Nuovo strumento
`current_heating` e termostato descritto come "chiede / non chiede calore". Prova reale sul
backend con i valori live: «Com è il riscaldamento in casa?» → piano `current_heating` +
`current_thermostats`, risposta «Caminetto spento, modalità Riscaldamento dalle 16:30 alle
16:55, potenza automatica attiva. Puffer: alto 46 gradi, …». Le app precedenti non inviano
`current_heating` (nessun errore); l'app con `8053672` richiede questa release.

## Release dell'11 ottobre 2026 — prelievo giornaliero per la Home

Release attiva: `/opt/domopi-house-ai/releases/20261011-grid-import`, commit
`dc7d808`; `PREVIOUS_RELEASE` = `20261011-day-summary`. Test superati su `.20`;
gateway, backend e `house-ai-lan` riavviati e `active`. Installati
`house-ai-grid-import.service` / `.timer` (ogni 15 min a `:01`); prima esecuzione
alle 15:52 `success`, retained `import_kwh_oggi` 0.07 e `import_kwh_ieri` 0.12. Dettagli e
ripristino in [`PRELIEVO_RETE_GIORNALIERO.md`](PRELIEVO_RETE_GIORNALIERO.md).

## Release dell'11 ottobre 2026 — riepilogo del giorno (fase 3.5 C)

Release attiva: `/opt/domopi-house-ai/releases/20261011-day-summary`, commit
`5140a4b`; `PREVIOUS_RELEASE` = `20261011-error-messages`. 127 test superati su `.20`;
riavviati gateway, backend e `house-ai-lan`, tutti `active`. `GET /v1/summary/day`
(strumenti fissi, senza Groq) provato in locale e via LAN: `complete`, «Oggi fino alle
15:14: prodotti 14,7 kilowattora, consumati 6,4, prelevati 0,1, immessi 3,1. Previsione
per domani: 10 kilowattora.», copertura del tempo trascorso 99,9%. Prima, alle 14:15,
applicata in Node-RED SOLAR-FORECAST.1 (`backend/nodered/solar_forecast/`). App
`bld_b9fc8589` (`67663f3`) installata. Fase 3.5 A (riepilogo all'apertura, solo app)
installata in precedenza con `bld_d5cc0cd7` (`0f3d3f7`).

## Release dell'11 ottobre 2026 — messaggi d'errore distinti

Release attiva: `/opt/domopi-house-ai/releases/20261011-error-messages`, commit
`a684b8c`; `PREVIOUS_RELEASE` = `20261011-lan-tls`. Un primo staging da `fd8321c` è
fallito su `.20` (chiusura di `HTTPError` senza corpo su Python 3.9) e non è mai stato
attivato; corretto in `a684b8c`, 121 test superati su `.20`. Riavviati gateway,
backend e `house-ai-lan`, tutti `active`. Prova reale con 4 domande ravvicinate:
1ª e 4ª `complete`, 2ª e 3ª HTTP 429 `rate_limited` con `retry_after_s` 1 e
`Retry-After: 1`. App `bld_0b8cc93d` (`528c557`) installata.

## Release dell'11 ottobre 2026 — AI Smart in casa senza Tailscale

Release attiva: `/opt/domopi-house-ai/releases/20261011-lan-tls`, commit
`818bd07`; `PREVIOUS_RELEASE` = `20261011-short-answers`. 118 test superati su
`.20` (compresi i test TLS di `lan_tls.py` su Python 3.9.2), servizi `active`.
Installati `house-ai-lan.service` (HTTPS solo su `192.168.1.20:443`, client solo
`192.168.1.0/24`) e `house-ai-lan-cert.timer` (copia settimanale del certificato
Tailscale in `/etc/house-ai/tls/`, scadenza attuale 12/12/2026). Pi-hole: record
locale `192.168.1.20 domopi.tailf30ba8.ts.net` aggiunto dall'utente, backup in
`/etc/pihole/pihole.toml.bak-20261011-house-ai-lan`. Verifiche dal PC: certificato
valido, 401 senza token, 404 fuori API, HTTP in chiaro chiuso; domanda reale via
LAN `complete`. App `bld_0adde659` (`fb01303`) installata; prova dell'utente in
casa con Tailscale spento riuscita. Tailscale Serve, Apache, Node-RED e Pi-hole
invariati. Dettagli e ripristino completo in
[`ACCESSO_LAN_SENZA_TAILSCALE.md`](ACCESSO_LAN_SENZA_TAILSCALE.md).

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
