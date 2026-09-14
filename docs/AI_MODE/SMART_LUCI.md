# Dominio luci della modalità Smart

Stato architetturale del 14 settembre 2026.

## Obiettivi

Il dominio permette di consultare gli otto punti luce presenti nel registry,
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
2. `light` appartiene all'allowlist degli otto ID; `state` è `on` oppure `off`.
3. Il backend restituisce `house_ai.light_command.v1` senza topic MQTT.
4. Il parser Android ricontrolla ID e stato.
5. `MqttManager.setSmartLightState` risolve l'unico topic
   `zara/interface/{domain}/{device}/power/cmd` e pubblica `true` o `false`, QoS 1.
6. Lo stato fisico non viene dichiarato confermato finché non arriva `/stat`.

Sono esclusi topic `/cmnd`, alias legacy, scene e target non presenti nel mapping.
Una richiesta composta può contenere più operazioni, entro il limite generale di
sei, ma ciascuna resta validata separatamente.

## Attribuzione utente o automazione

`AiSmartState` conserva lo stato valido precedente, i comandi app in attesa e
l'ultima transizione osservata per luce. Dashboard e modalità Smart registrano
il comando nello stesso tracker prima della pubblicazione.

- se il nuovo `/stat` coincide con il target richiesto dall'app entro 30 secondi,
  l'autore è `utente`;
- se cambia rispetto a uno stato valido precedente senza tale corrispondenza,
  l'autore è `automazione`;
- il primo messaggio della sessione, soprattutto se retained, inizializza lo
  stato e non viene attribuito.

Questa è un'attribuzione della transizione osservata nella sessione Android. Non
è uno storico persistente e non distingue più utenti. Per una cronologia che
sopravviva a riavvii, Node-RED dovrà pubblicare o salvare un evento firmato con
ID luce, stato, autore e timestamp. I quattro log attuali in `/home/pi/AI_climate`
non contengono eventi delle luci.

## Interfaccia

Il riepilogo parte espanso. Un tocco sull'intestazione o sulla freccia lo
minimizza in una barra; lo stesso controllo lo espande nuovamente. Lo stato è
`rememberSaveable`. Query e risposta rimangono riquadri separati e non contengono
switch o selettori.

## Verifica e replica

Eseguire dalla radice:

```bash
cd backend/house_ai
python3 -B -m pytest -q

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
