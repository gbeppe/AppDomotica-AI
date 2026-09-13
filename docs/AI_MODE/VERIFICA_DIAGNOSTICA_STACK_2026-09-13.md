# Verifica diagnostica stack Smart

Eseguita il 13 settembre 2026 sulla revisione applicativa e backend `f2d50d8`.

## Verifiche software

- backend: 66 test Python superati localmente e su `.20` con Python 3.9.2;
- Android: test JVM e `assembleDebug` superati;
- compilazione completa dei test Android strumentali superata;
- 12 test mirati `AiSmartScreenTest` e `HouseAiRepositoryTest` superati
  sull'emulatore Android 14;
- test isolato di apertura della scheda e dei relativi elementi UI superato.

La prima compilazione incrementale dei test strumentali non risolveva
`DomoPiTheme`, già presente nell'artefatto app. La ricompilazione completa con
`--rerun-tasks` è riuscita; il problema era la cache incrementale, non il codice.

## Verifica reale su `.20`

Release preparata in `/opt/domopi-house-ai/releases/20260913-stack-health`, testata
prima dello spostamento atomico di `current` e attivata con entrambi i servizi
systemd operativi. `verify_install.py` ha confermato modalità read-only, planner
e quattro log allowlisted.

Una richiesta autenticata a
`https://domopi.tailf30ba8.ts.net/v1/health/details` ha restituito HTTP 200 subito
dopo il riavvio:

- backend: online;
- gateway: online;
- Groq: unknown;
- EmonCMS: online;
- log Node-RED: online.

Dopo una domanda reale, riuscita con HTTP 200, la stessa diagnostica ha riportato
Groq online. Questo prova che lo stato provider deriva dall'ultima comunicazione
reale e che il controllo periodico non genera inferenze o consumo di quota.

Il test TLS è stato eseguito senza stampare token, chiavi, domande nei log o dati
energetici. `.15`, Node-RED, Mosquitto e la configurazione Tailscale non sono
stati modificati.

## Stato e rollback

Release attiva: `20260913-stack-health`, revisione `f2d50d8`. Rollback immediato:
`20260913-partial-extremes`, revisione `30ddc13`, seguito dal riavvio di
`house-ai-gateway` e `house-ai-backend`. Il rollback elimina la diagnostica
dettagliata ma conserva le normali interrogazioni Smart.
