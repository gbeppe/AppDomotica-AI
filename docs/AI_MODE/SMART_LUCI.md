# Dominio luci della modalità Smart

Stato aggiornato il 22 settembre 2026. Contratto e verifica:
[Integrazione semantica Node-RED](INTEGRAZIONE_SEMANTICA_LUCI.md).

## Obiettivi

Il dominio permette di consultare i nove punti luce presenti nel registry,
accendere o spegnere una singola luce tramite query e rispondere a domande come
«chi ha acceso la libreria?». La home non espone widget: testo e voce restano
l'unica interfaccia operativa Smart.

## Flusso di consultazione

Android raccoglie esclusivamente valori booleani validi dai topic `/stat` e
costruisce `house_ai.current_lights_input.v1`. Missing e payload invalidi non
diventano `OFF`. Il gateway Groq sceglie `current_lights`; il backend valida ID,
topic, tipi, timestamp e retained, quindi compone la risposta deterministicamente.

## Flusso di comando

1. Groq può produrre solo `set_light_state(light, state)`.
2. `light` appartiene all'allowlist dei nove ID; `state` è `on` oppure `off`.
3. Il backend restituisce `house_ai.light_command.v1` senza topic MQTT.
4. Il parser Android ricontrolla ID e stato.
5. `MqttManager.setSmartLightState` risolve l'unico topic
   `zara/interface/{domain}/{device}/power/cmd` e pubblica `true` o `false`, QoS 1.
6. `/stat` è uno stato dichiarato: non conferma fisicamente il comando.

Sono esclusi topic `/cmnd`, alias legacy, scene e target non presenti nel mapping.
Una richiesta composta può contenere più operazioni, entro il limite generale di
sei, ma ciascuna resta validata separatamente.

## Attribuzione utente o automazione

Node-RED costruisce l'attribuzione con policy `dashboard_only_v2`: dashboard app
e dashboard Node-RED sono `utente`; le altre origini documentate sono
`automazione`. I comandi manuali tramite inject editor appartengono ad automazione.
Android non attribuisce più autori dalle transizioni o dai comandi locali.

Le risposte usano l'ultimo comando documentato disponibile e distinguono
`routed`, `dispatched`, `send_invoked` e `publisher_input`. Non provano la causa
fisica dello stato né costituiscono una cronologia completa ON/OFF. I retained
con policy precedente non forniscono un autore valido per la nuova catalogazione.
Dettagli, replica e limiti nel [contratto semantico](INTEGRAZIONE_SEMANTICA_LUCI.md).

## Interfaccia

Il riepilogo parte espanso. Un tocco sull'intestazione o sulla freccia lo
minimizza in una barra; lo stesso controllo lo espande nuovamente. Lo stato è
`rememberSaveable`. Query e risposta rimangono riquadri separati e non contengono
switch o selettori.

## Verifica e replica

Eseguire dalla radice:

```bash
cd backend/house_ai
python3 -B -m unittest discover -s tests -q

cd ../..
ANDROID_HOME=/home/giuseppe/Android/Sdk \
JAVA_HOME=/home/giuseppe/.gradle/jdks/jetbrains_s_r_o_-25-amd64-linux.2 \
./gradlew testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin --no-daemon
```

Sul server più potente copiare il backend come descritto in
`DEPLOYMENT_20_TLS_2026-09-13.md`, riavviare prima il gateway e poi House AI e
verificare che il catalogo esponga `current_lights` e `set_light_state`. Nessuna
API key Groq o token House AI deve entrare nel repository.

## Rollback

Rimuovere `set_light_state` dal catalogo e dal prompt disabilita la generazione
di nuovi comandi. Sul client, non collegare `onLightCommand` oppure tornare al
commit precedente impedisce la pubblicazione Smart senza modificare dashboard,
Digital Twin Router o topic `/stat`.

## Verifica storica del 14 settembre (precedente alla migrazione semantica)

La release `/opt/domopi-house-ai/releases/20260914-smart-lights`, revisione
`b41bf2af49c5548b7dd678742452c3589639c7f1`, è attiva tramite il symlink
`/opt/domopi-house-ai/current`. Su Python 3.9.2 ha superato 71 test `unittest`;
gateway e backend risultano `active` dopo il riavvio.

Una richiesta locale autenticata, senza client MQTT e quindi senza attuazione,
ha fatto selezionare a Groq:

```json
{"id":"op1","tool":"set_light_state","light":"lights_libreria","state":"on"}
```

Il risultato `house_ai.light_command.v1` non contiene un topic. Una seconda
richiesta «Chi ha acceso la luce libreria?» con evidenza di fixture ha scelto
`current_lights` e ha restituito `automazione`. Queste prove certificano planner,
validazione e presentazione; non certificano ancora pubblicazione dall'app né
commutazione fisica. La release precedente per rollback è
`/opt/domopi-house-ai/releases/20260913-climate-reason`.
