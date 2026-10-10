# Verifica integrazione semantica luci — 22 settembre 2026

## Risultato e stato reale

Implementazione app e backend completata nella working copy
`feature/ai-home-assistant`; nessun commit/push eseguito in questo incremento.
Preservate le modifiche dashboard dell'utente. Nessun flow Node-RED modificato,
nessun deploy Node-RED e nessun comando di collaudo a dispositivi.

Release backend finale attiva su `.20`:
`/opt/domopi-house-ai/releases/20260922-smart-light-semantics-r2`.
`house-ai-gateway` e `house-ai-backend` sono active. Health autenticato tramite
`https://domopi.tailf30ba8.ts.net/v1/health`: HTTP 200, status ok,
planner_configured e log_root_configured true. TLS verificato normalmente.

Release intermedia conservata:
`/opt/domopi-house-ai/releases/20260922-smart-light-semantics`.
Release precedente all'intero intervento, anch'essa conservata:
`/opt/domopi-house-ai/releases/20260914-smart-lights`.
Ogni nuova directory contiene `PREVIOUS_RELEASE`, `REVISION` e `SHA256.json`.
REVISION segnala esplicitamente che il contenuto comprende modifiche non
committate: per l'identificazione esatta valgono i digest dei file.

La seconda release aggiunge compatibilità con gli stati camera/HiFi inviati
dalle vecchie app: validati, poi esclusi dal catalogo attuale. Non sono
reintrodotti come target di comando. Prima dell'attivazione sono stati
ricontrollati manifest e symlink attivo; in caso di fallimento dell'health la
procedura prevedeva il ripristino della release precedente.

## Test automatici

- Backend locale: **78 test unittest passati**.
- Raspberry `.20`, Python **3.9.2**, utente servizio `house-ai`: parsing AST,
  integrità SHA256 e **78 test unittest passati** prima dell'attivazione.
- Android: **95 test unitari passati**, zero failure/error/skipped;
  `:app:testDebugUnitTest :app:assembleDebug`, build completata.
- `git diff --check`: nessun errore whitespace.

Sono stati corretti due test precedenti rimasti al conteggio di otto luci,
coerentemente con il catalogo dell'utente che ora ne contiene nove. Il nuovo
test HTTP verifica una richiesta completa oltre il vecchio limite di 8 KB e
il rifiuto di un corpo oltre 32 KB. I test semantici verificano l'assenza di
attribuzioni locali, la separazione stato/comando e il rifiuto di evidenze non
coerenti o con conferme fisiche non supportate.

## Prove reali MQTT → TLS → Groq → risposta

Sottoscrizione MQTT in sola lettura su `.20`, limitata a quattro secondi,
a quattro famiglie semantiche e ai topic luce `/power/stat`.
Raccolti nove stati validi e due record compatibili con `dashboard_only_v2`:
lavanderia e ingresso servizio. Dieci record legacy/non validi per il contratto
corrente esclusi dall'attribuzione. Non è un censimento permanente del broker.

Gli snapshot per queste prove sono stati costruiti da uno script sul Raspberry
secondo il contratto Android: `received_at_ms` rappresenta la ricezione dello
script di verifica, non una cattura eseguita nell'app. Le prove non sostituiscono
il collaudo dell'interfaccia Android o una verifica della commutazione fisica.

| Domanda | Piano Groq | Risultato verificato |
| --- | --- | --- |
| Chi ha spento la luce della lavanderia? | current_lights, lights_laundry | Comando OFF, automazione, motion_rule, publisher_input; evento 22/09/2026 22:08:00.134 Europe/Rome |
| Chi ha spento la luce ingresso servizio? | current_lights, lights_service_entrance | Comando OFF, automazione, motion_rule, publisher_input; evento 22/09/2026 22:08:08.299 Europe/Rome |
| Chi ha acceso la lampada TV? | current_lights, lights_tv | Stato acceso dichiarato, autore non disponibile: solo evidenza precedente alla policy corrente |

Tutti i piani contenevano esclusivamente `current_lights`. Risposte con limiti
espliciti su retained, causalità, storico incompleto e assenza di conferma
fisica/autenticazione del mittente. Prima serie eseguita sulla release iniziale;
dopo la correzione di compatibilità, health e domanda reale sulla lavanderia
ripetuti con successo sulla release finale r2.

## APK e replica del collaudo

APK prodotto: `app/build/outputs/apk/debug/app-debug.apk`.
SHA256: `0dd05223e1d9c89d012306c76d21e2095c42ad10f9244823268bedb8ad892767`.
Archivio backend finale di staging:
`d0120575b9ec2b8e7c968c6320bba6ee48247be2d4a9b80b3e2db0ac75b06a26`.
I file rigenerabili e le risposte grezze di verifica sono nella directory locale
`.validation/smart-light-semantics/`, fuori dal materiale da committare.

L'APK non è stato installato né lanciato nell'emulatore in questa sessione.
Per completare la prova utente:

1. Avviare la nuova build da Android Studio oppure installare l'APK debug.
2. Entrare in Smart con MQTT e backend TLS collegati e attendere i retained.
3. Chiedere «Chi ha spento la luce della lavanderia?» e confrontare origine,
   fase e timestamp nel riquadro risposta e nei dettagli dell'evidenza.
4. Dopo un normale comando dashboard a TV/libreria, ripetere «chi ha
   acceso/spento?» e verificare un record nuovo con policy dashboard_only_v2.
   Non interpretare un vecchio retained privo di policy come una nuova prova.

Replica architetturale e rollback:
[Integrazione semantica luci](INTEGRAZIONE_SEMANTICA_LUCI.md).
