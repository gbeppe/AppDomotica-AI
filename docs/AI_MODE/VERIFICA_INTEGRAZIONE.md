# Verifica locale del 11 settembre 2026

Worktree `DomoPiAndroidApp-ai-mode`, branch `feature/ai-home-assistant`.
Verifica in consultazione; nessuna installazione sui Raspberry o sul telefono.

## Sorgenti e risultati

EmonCMS interrogato direttamente su `.15`, percorso `/emoncms`, tramite
l'adattatore del prototipo. Catalogo: 256 feed. Per il 6 settembre 2026,
Europe/Rome, ciascuno dei feed 353 (AC), 304 (SOC) e 306 (batteria) restituisce
2.880 campioni. AC: sei null conservati; SOC e batteria: nessun null e nessuna
esclusione SOC con il filtro implementato.

Energia AC integrata negli intervalli validi: **3,1770320454 kWh**.
Copertura temporale: **99,548611%**, quindi il valore non è un totale completo
certificato. Il rapporto non integra attraverso i campioni mancanti.

Il log climatico è la copia fornita `/home/giuseppe/Downloads/clima_controllo.log`,
non un log sincronizzato dal Raspberry. SHA256:
`3c3f2338348bdd60607507c99ec5cfba17dab55c526f7d6f7837ebd2634c5fd6`.
Il parser mantiene 62 eventi ed esclude sei record per la giornata.

- Riga 4261, comfort notturno raggiunto: media AC zero nei due minuti prima e dopo,
  quattro campioni per finestra.
- Riga 4293, peak shaving: media AC 493,1525 W prima e 9,4300 W dopo,
  quattro campioni per finestra.

Queste osservazioni non provano la versione della strategia né attribuiscono
un'autorità operativa ai log SHADOW.

## API

Servizio temporaneo avviato su loopback, con token effimero e sorgenti reali.
Verificati: 401 senza credenziale; 200 per health autenticato; 400 per data
invalida o duplicata; 404 per percorso inesistente; 200 per il rapporto.
Le metriche attraverso HTTP coincidono con quelle dell'adattatore diretto.

## Android

Build offline `assembleDebug`, `assembleDebugAndroidTest` e `testDebugUnitTest`
riuscite. Test esistenti: 17 MqttManager e sei contratto MQTT, tutti passati.
Sei test Python passati dalla cartella `backend/house_ai`.

Test Compose `HouseAiScreenTest`: esegue la schermata senza avviare MainActivity
né MqttManager, collegandola via inoltro ADB all'API temporanea. Richiede gli
argomenti strumentali `houseAiUrl` e `houseAiToken`; in loro assenza viene saltato.
Il caso di riferimento è il 6 settembre con 62 eventi nel log fornito.

Verifica: pulsante disabilitato senza configurazione, invio della richiesta,
rapporto reale, conteggio eventi, scorrimento e messaggio per data invalida.
Test UI passato in tema chiaro e scuro; immagini ispezionate visivamente:
nessuna sovrapposizione del contenuto, grafici e messaggi leggibili. Nel tema
scuro verificato anche lo scorrimento al motivo peak shaving del caso reale.
Le immagini sono conservate localmente in `.artifacts/house-ai-validation/`
(e sottocartella `dark`), insieme agli esiti strumentali.

## Limiti

Prova su emulatore Android 14, non sul telefono fisico. L'emulatore è stato
avviato in modalità temporanea senza salvataggio dello snapshot. Un iniziale
blocco della System UI ha richiesto di chiudere il dialogo e ripetere le immagini.
Nessun modello generativo, deployment persistente, TLS remoto, aggiornamento
continuo dei log o notifica è stato validato. Il test della schermata isolata
non copre la navigazione della dashboard completa o il funzionamento MQTT live.

Al termine: backend e emulatore temporanei arrestati, inoltro ADB rimosso,
token temporaneo eliminato e SHA256 del log originale invariato.
