# Provenienza libreria: UI Node-RED, Alexa e regola TV LG

> Aggiornamento: la [catalogazione dashboard_only_v2](../actor_policy/README.md)
> sostituisce le precedenti classificazioni: dashboard utente, Alexa e tutte le
> altre sorgenti automazione. Le descrizioni precedenti restano storiche dove divergono.


Implementato e distribuito il 22 settembre 2026. Incremento del punto 3 limitato
agli ingressi diretti verificati; scheduler, scene e altre luci non inclusi.

## Modifiche e motivazione

Tre nuovi Function marker, ognuno sul solo collegamento operativo pertinente:

| Sorgente | Autore | origin | Collegamento annotato |
|---|---|---|---|
| UI switch `ad32e56b.84cfe` | utente | nodered_dashboard | verso `c14c803c.86e27` |
| Alexa `2e66d0dd.ac93d` | utente | alexa | verso `9d7004c9.a67e28` |
| Ramo true del controllo LG `47b4e6deb69b994a` | automazione | lg_tv_rule | verso `5bd87c6ad192e8a0` |

UI passthru=false verificato; l'altra uscita del suo fan-out è preservata.
LG viene marcato DOPO la decisione della regola, con requested_state=false,
preservando il payload originale "true". È un comando automatico della libreria,
indipendentemente da chi abbia acceso la TV. Il dato retained viene conservato
come source_retained: può essere un riallineamento che attiva la regola, non
necessariamente una nuova accensione della TV.

I marker creano command_id al punto sorgente con stage=source_selected.
I due report RM libreria accettano questo stadio e actor utente/automazione;
il riscontro resta stage=send_invoked. Non si inventa uno stadio dispatched
per percorsi che non attraversano function 17. I comandi app continuano a usare
il precedente percorso dispatched e il loro identificativo originale.

Nessun payload operativo, filtro, timer, configurazione IR o nodo RM modificato.
Solo aggiunta di _semantic_command, rimossa dai report prima del legacy MQTT.
L'attribuzione source_edge identifica il ramo logico, non autentica il publisher
o una persona: publisher_authenticated resta false. I marker non provano stato
fisico; execution_confirmed e physical_confirmation restano false.

## Backup e deploy

Backup completo su .20:
`/home/pi/AI_backups/nodered/20260922T113756Z-before-libreria-origins`.
Contiene runtime-before/planned/after, plan e file progetto/credenziali cifrate
presenti. Nessun token o backup privato è salvato nel repository.

Admin API v2, TLS verificato, controllo revisione, tipo nodes. Cinque nodi
modificati e tre aggiunti. Deploy alle 13:38:08 CEST; nessun riavvio del servizio.
Revisione finale `2e6cb3dc3d5023a00154fe39d9947b247e40aaff136dbde00172559a05664a3e`.
GET successivo uguale alla configurazione prevista, servizio active, nessun
errore nella finestra log controllata. Nessun comando alle luci inviato dall'agente.

## Verifica da effettuare sul campo

Osservare `zara/interface/semantic/v1/rm_receipts/lights.libreria.power/stat`.

1. Switch libreria nella dashboard web Node-RED, ON/OFF: utente/nodered_dashboard.
2. Comando vocale diretto Alexa "luci libreria", ON/OFF: utente/alexa.
3. Normale attivazione della regola LG: automazione/lg_tv_rule, requested_state=false.
4. App Android ON/OFF: utente/app_interface come prima.

Confrontare nuovi command_id e orari: un retained vecchio non è una nuova prova.
Cinque test isolati passati: tre percorsi sorgente→report RM simulato, compatibilità
app, assenza di attribuzione su input invalidi e conservazione collegamenti.
Questi test non validano Alexa reale o dispositivi fisici. Non attribuire gli
altri percorsi solo perché arrivano allo stesso RM.

## Replica e rollback

build.py genera update/rollback/manifest da export runtime v2; test.js verifica
la logica senza hardware. deploy.py prepare/apply esegue il backup e controlla
il runtime prima e dopo. Usare la propria base aggiornata e ispezionare il diff.
Non importare update.json come duplicato di nodi operativi.

Rollback: ripristinare i cinque nodi di rollback.json ed eliminare soltanto i
TRE ID aggiunti elencati in manifest.json, dopo aver verificato che nel frattempo
non siano stati modificati. Usare deploy dei soli nodi e revisione corrente.
Conservare publisher, function 17, Router e patch RM precedenti. Non ripristinare
ciecamente tutto il vecchio flow se esistono modifiche successive.

## Rimanente

Scheduler/send_state, scene condivise e percorso soggiorno/prolunga richiedono
verifiche specifiche prima della marcatura. L'integrazione Smart delle risposte
(punto 4) e l'attribuzione causale dei report legacy non sono completate qui.

## Estensione scene

Disponibile e distribuita l'[estensione scene della libreria](../libreria_scenes/README.md).
Conserva provenienza app/UI/Alexa fino al riscontro RM; lo scheduler resta
non attribuito perché il nodo schedex elimina la causa di emissione.

## Estensione agli altri dispositivi

L'[estensione alle luci rimanenti](../all_lights/README.md) è distribuita su .20.
La matrice distingue RM send_invoked, Shelly publisher_input, comandi app solo
routed e percorsi ancora sconosciuti; non equivale a copertura causale completa.
