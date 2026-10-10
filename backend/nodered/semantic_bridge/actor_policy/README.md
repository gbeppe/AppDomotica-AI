# Catalogazione autore — dashboard_only_v2

Regola richiesta dall'utente e distribuita il 22 settembre 2026:

- utente: comandi dalle dashboard Node-RED e app;
- automazione: tutti gli altri, inclusi Alexa, regole, scheduler e inject editor.

Questa è una classificazione funzionale del progetto, non l'identità fisica di
chi ha parlato ad Alexa o premuto un inject. Sostituisce la precedente decisione
che includeva Alexa e operazioni manuali dell'editor nella categoria utente.
origin conserva la sorgente; actor_policy=dashboard_only_v2 identifica i nuovi
metadati e riscontri aggiornati. I retained vecchi non sono riscritti.

## Implementazione

Marcatori Alexa riclassificati automazione; marker dashboard restano utente.
Per all_off sul nodo UI con passthru, la presenza di socketid della sessione
frontend distingue dashboard da inject editor. Verificato nel codice installato
node-red-dashboard/ui.js: frontend crea toSend con socketid, passthrough conserva
il messaggio in ingresso; l'inject cablato non aggiunge socketid. Non è una prova
crittografica di identità e non va generalizzata a publisher arbitrari.

Aggiunto un marcatore dopo schedex, prima dello switch operativo. Pianificazione
e send_state dell'editor appartengono entrambi ad automazione secondo la nuova
regola: non occorre modificarne il pacchetto o distinguerli per il solo actor.
origin=schedex non dice se l'evento sia pianificato, manuale o di inizializzazione.
I report RM accettano entrambi gli autori; gli adattatori libreria consumano anche
la provenienza dello scheduler. Orari, filtri e payload invariati.

Il canale app_interface resta utente per convenzione dell'interfaccia dell'app:
i payload attuali non distinguono dashboard classica e Smart. Non viene
attribuita un'identità autenticata al client MQTT. Per separare i due canali
servirebbe un ulteriore campo origin inviato dall'app.

Il limite di osservabilità rimane: dove un comando dashboard perde la provenienza
attraversando MQTT non si può concludere che provenga da una sorgente diversa.
Questi casi non vengono arbitrariamente etichettati automazione. La categoria
richiesta è binaria, ma alcuni percorsi restano senza evidenza sufficiente per
assegnarla. Conferma fisica e causalità del report legacy restano separate.

## Deploy e backup

Backup completo runtime e file progetto/credenziali cifrate su .20:
`/home/pi/AI_backups/nodered/20260922T193732Z-before-actor-policy`.
62 nodi aggiornati prevalentemente nei metadati e 1 marcatore aggiunto. API v2
TLS verificato, controllo revisione e deploy nodes. Runtime dopo deploy uguale
al piano, servizio active. Revisione:
`39808cfd42ae5f110fe71908f985f22d917eb32b2c11b545aef56d3be3e04f61`.
Il redeploy ricrea scheduler e nodi modificati, con possibili emissioni startup
secondo le logiche preesistenti; impatto comunicato prima del deploy.

Cinque test isolati passati: compilazione di tutte le Function modificate,
Alexa/dashboard, pulsante con passthrough, schedex→adattatore→report RM e wiring.
Nessun comando ON/OFF di collaudo inviato dall'agente; verifiche utente sul campo
ancora da eseguire. Gli eventi futuri dovrebbero mostrare actor_policy v2, Alexa
automazione e dashboard utente. Non usare retained precedenti come prova nuova.

## Replica e rollback

build.py genera patch sulla base corrente; test.js verifica offline.
deploy.py prepare/apply salva backup completo e controlla revisioni prima/dopo.
Non importare update.json come duplicato. Per rollback ripristinare solo i
modified_ids da rollback.json e rimuovere added_ids del manifest, dopo verifica
di modifiche successive. Usare rev corrente e deploy nodes, non ripubblicare
ciecamente tutto il vecchio flow. Il rollback ripristina la vecchia catalogazione.

I generatori delle cartelle precedenti rappresentano incrementi storici: non
rieseguirli sul runtime nuovo senza riconciliare la policy, perché contengono
la classificazione precedente. I loro vecchi test non certificano questa policy.
Android e backend conversazionale non sono modificati da questo incremento:
la migrazione del consumo semantico Smart rimane il punto 4.

## Incremento successivo: consumo Smart

L'integrazione Android/House AI successiva è documentata in
[Integrazione semantica luci](../../../../docs/AI_MODE/INTEGRAZIONE_SEMANTICA_LUCI.md).
Non richiede ulteriori modifiche a questo flow; i retained precedenti alla policy
continuano a essere esclusi dall'attribuzione nelle risposte.
