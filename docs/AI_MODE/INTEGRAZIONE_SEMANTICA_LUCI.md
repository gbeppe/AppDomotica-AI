# Record Node-RED nelle risposte Smart

Aggiornamento del 22 settembre 2026. La semantica è costruita da Node-RED;
Android raccoglie e trasporta le evidenze, House AI le valida e le presenta.
Questo incremento non modifica flow, algoritmi, comandi hardware o dashboard.

## Decisione e comportamento

È stata rimossa l'euristica Android che attribuiva all'utente una transizione
entro 30 secondi da un comando locale e all'automazione ogni altra transizione.
Una coincidenza temporale non prova la provenienza. Lo stato `/power/stat`
continua a essere uno stato dichiarato e non viene modificato da un record di
comando. L'aggiornamento ottimistico della dashboard resta invariato.

Le risposte a «chi ha acceso/spento?» riportano l'ultimo comando documentato
**disponibile**, con stato richiesto, autore, origine, timestamp Node-RED e fase.
La classificazione accettata è `actor_policy=dashboard_only_v2`:

- `utente`: `app_interface` e `nodered_dashboard`;
- `automazione`: le altre origini documentate da Node-RED, compresi Alexa,
  schedex e regole di movimento.

L'app_interface non distingue ancora Smart dalla dashboard. L'autore è una
categoria funzionale, non un'identità autenticata né la causa provata dello
stato fisico. Origine mancante o policy precedente non diventano automazione.
Gli eventuali `last_actor` dei vecchi client vengono accettati nel vecchio
formato per compatibilità ma esclusi dall'evidenza utilizzata nelle risposte.

## Sorgenti e fasi

Android sottoscrive solo i topic di stato di queste famiglie, sotto
`zara/interface/semantic/v1/`, con suffisso `{entity_id}/stat`:

| Famiglia | Fase | Significato utilizzabile |
| --- | --- | --- |
| `routed_commands` | `routed` | Router ha instradato il comando |
| `dispatched_commands` | `dispatched` | Comando inoltrato al ramo di invio |
| `rm_receipts` | `send_invoked` | Invocata la funzione Broadlink `sendData` |
| `publisher_inputs` | `publisher_input` | Messaggio arrivato all'ingresso del publisher MQTT |

Nessuna fase conferma pubblicazione al broker, ricezione hardware o commutazione
fisica. Sono richiesti `execution_confirmed=false`, `physical_confirmation=false`
e `publisher_authenticated=false`; altri contratti richiedono una futura revisione.

Le nove entità sono living, libreria, tv, reading, laundry, porch_entrance,
service_entrance nel dominio lights e water/deck nel dominio pool.
`lights.tv.power` viene associato a `lights_tv` tramite il mapping dei topic,
non tramite nomi arbitrari ricevuti dal broker. I prefissi personalizzati non
sono accettati da questo contratto, che usa `zara/interface`.

## Selezione e limiti temporali

Per ogni luce Android conserva un solo comando documentato. Per lo stesso
`command_id` può avanzare da routed a dispatched a send_invoked/publisher_input;
uno stadio precedente ricevuto dopo non lo sostituisce. Stato richiesto, autore
e origine devono rimanere coerenti. Per ID diversi prevale il timestamp evento
Node-RED maggiore, non l'ordine di ricezione Android. A parità di timestamp si
mantiene il record già raccolto. Gli ID sono opachi e non vengono interpretati.

Questa è una selezione dell'ultimo **riscontro documentato**, non un ordinamento
causale completo dei comandi: un riscontro tardivo può avere timestamp maggiore.
Non esiste in questo trasporto una cronologia separata di ogni ON e OFF. Se
l'ultimo record è OFF, non identifica chi ha richiesto il precedente ON.
La risposta esplicita il limite. Per uno storico completo occorre aggiungerlo
al registry/backend, senza ricostruirlo da transizioni Android.

Retained è evidenza conservata, non nuovo evento o freschezza. La riconnessione
azzera lo snapshot Android; i retained validi lo ricostruiscono. I record
precedenti a `dashboard_only_v2` non vengono riscritti né riclassificati:
finché non arrivano nuovi eventi validi, l'autore può risultare non disponibile.
Un record malformato viene ignorato; non cancella l'ultimo record valido, che
rimane esplicitamente solo l'ultimo disponibile.

## Contratto app → House AI

`house_ai.current_lights_input.v1` conserva `schema`, `connected`, `observations`
e aggiunge la mappa opzionale `commands`, indicizzata con gli ID Android validati.
I vecchi snapshot senza commands sono ancora validi. Gli stati camera/HiFi dei
client precedenti vengono validati e poi esclusi dal catalogo attuale, senza
respingere l’intera domanda né ripristinare quei target nei comandi. Ogni comando trasporta:

- schema semantico, entity_id, command_id;
- actor, actor_policy, origin, attribution;
- requested_state, stage, observed_at_ms;
- received_at_ms, retained, source_topic;
- i tre flag di conferma/autenticazione esplicitamente falsi.

Android scarta campi accessori del record originale. Backend e parser Android
validano identità, topic/fase, tipi, policy, coerenza autore/origine e timestamp;
è tollerato uno scarto massimo di 60 secondi verso il futuro rispetto alla
ricezione. JSON MQTT limitato a 16.384 caratteri; HTTP limitato a 32.768 byte,
per contenere nove evidenze e gli snapshot energia/clima. Nessuna nuova porta,
dipendenza Python, API key o credenziale.

`current_lights` restituisce osservazioni e comandi separati e può rispondere
anche senza stato quando esiste un comando valido. Groq seleziona il tool;
il testo finale è deterministico, condiviso tra schermo e lettura vocale.
Nei dettagli dell'evidenza Android sono consultabili topic, command_id, policy,
fase, origine e timestamp sorgente/ricezione. Il record ricevuto non viene mai
convertito in un comando da eseguire dall'app.

## File coinvolti

- Android: `SemanticLightCommand.kt`, `AiSmartState.kt`, `MqttManager.kt`,
  `HouseAiRepository.kt`, `EnergyAssistantAnswer.kt` e test delle evidenze.
- Backend: `light_semantics.py`, `energy_tools.py`, `energy_presenter.py`,
  `model_gateway.py`, `server.py`, test del contratto e del limite HTTP.
- Il catalogo backend viene allineato alle nove luci già presenti nelle modifiche
  dashboard dell'utente; camera e HiFi non sono più target del planner.

## Replica e ordine di aggiornamento

1. Distribuire prima il backend aggiornato, che accetta anche il vecchio client.
   Conservare la release precedente; preparare una nuova directory in
   `/opt/domopi-house-ai/releases/` senza sovrascrivere quella attiva.
2. Copiare il contenuto di `backend/house_ai`, escludendo cache e segreti.
   Registrare revisione Git e manifest SHA256 dei file; una working copy non
   committata va dichiarata come tale, senza attribuirle una falsa revisione.
3. Su Raspberry eseguire `python3 -B -m unittest discover -s tests -q` nella
   nuova directory, anche come utente `house-ai`.
4. Spostare atomicamente `/opt/domopi-house-ai/current` sulla nuova release e
   riavviare `house-ai-gateway` e `house-ai-backend`. Conservare configurazioni
   `/etc/house-ai`, token e chiave Groq. Controllare health autenticato via TLS.
5. Compilare e installare l'app aggiornata. Prima del nuovo backend il campo
   commands sarebbe respinto con HTTP 400 dal vecchio contratto.
6. In Smart chiedere «chi ha spento la lavanderia?» e verificare origine e fase
   contro un record reale. Per TV/libreria con soli retained vecchi attendere un
   normale nuovo evento; non usare i record vecchi come prova della nuova policy.

Rollback: ripristinare prima la precedente app, quindi il symlink backend alla
release registrata in `PREVIOUS_RELEASE`, riavviando i due servizi. Tornare al
backend vecchio con il nuovo APK causa incompatibilità del campo commands.
Non occorre alcun deploy Node-RED o intervento su `.15` per questo incremento.

## Verifica

Test Kotlin: separazione tra comando e stato, policy precedente rifiutata,
origine incoerente, tipi JSON errati, topic/entity divergenti, record senza
stato, replay retained vecchio e regressione di fase. Test Python: stesso
contratto, quattro fasi con testo distinto, legacy actor scartato, conferme
non supportate rifiutate, nove luci e richiesta HTTP oltre 8 KB.

Le prove live e la release effettivamente attivata sono riportate nel resoconto
`VERIFICA_SEMANTICA_LUCI_2026-09-22.md`. Nessun comando ON/OFF viene inviato dalle
prove automatiche; il test sul dispositivo resta distinto dalla validazione
dei record, del planner e della risposta.
