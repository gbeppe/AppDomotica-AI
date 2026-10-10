# Provenienza delle scene — libreria

> Aggiornamento: la [catalogazione dashboard_only_v2](../actor_policy/README.md)
> sostituisce le precedenti classificazioni: dashboard utente, Alexa e tutte le
> altre sorgenti automazione. Le descrizioni precedenti restano storiche dove divergono.


Distribuita il 22 settembre 2026. Quattro scene app (tv, sleep, all_on, all_off),
pulsanti corrispondenti della dashboard Node-RED e scene Alexa (TV, notte, luci).
Solo il riscontro della libreria è esteso; nessun completamento dichiarato per
le altre lampade azionate dalle medesime scene.

## Architettura

Scene Dispatcher conserva l'origine app in `_semantic_scene` nei messaggi che
ricrea; ignora la marcatura per richieste retained. Sette marcatori sulle uscite
UI/Alexa portano origine esplicita prima delle confluenze. Due adattatori prima
degli RM libreria trasformano la scena in comando ON/OFF in base al ramo raggiunto,
dopo i filtri e ritardi esistenti. Nessuna condizione, ritardo o payload cambiato.
I percorsi individuali utente/automazione già marcati restano invariati.

Il record rm_receipts aggiunge `scene`; command_id deriva dall'identificativo
scena con suffisso libreria e stato. Stage send_invoked, non conferma fisica.
Origini app_interface, nodered_dashboard, alexa; per il pulsante all_off con
passthru e inject manuale condiviso: nodered_ui_or_editor. Nessuna autenticazione
individuale del publisher implicita. Non marcare come click certo il passthrough.

## Scheduler: limite verificato

Letto il codice installato node-red-contrib-schedex/index.js: send(event,manual)
alla riga 176 emette solo {topic,payload}. Anche il ramo send_state alle righe
341–348 ricrea {topic,payload}, senza conservare la causa. Il parametro manual
non viene pubblicato. Perciò l'uscita schedex non consente di distinguere con
certezza evento pianificato e richiesta manuale. Nessuna marcatura automatica
aggiunta allo scheduler e nessuna modifica al pacchetto installato.

Completare questo tratto richiede una strumentazione della sorgente schedex che
esporti la causa, verificata contro la versione installata e resistente agli
aggiornamenti. Una correlazione temporale con l'inject non costituisce prova.
Il punto 3 resta quindi parziale per lo scheduler.

## Deploy e verifiche

Backup runtime completo e file progetto/credenziali cifrate su .20:
`/home/pi/AI_backups/nodered/20260922T114232Z-before-libreria-scenes`.
15 nodi modificati, 9 aggiunti, identificati in manifest.json. API v2 con rev e
TLS verificato, deploy nodes. Stop 13:42:58 CEST, avvio 13:42:59; servizio active,
nessun errore nella finestra log controllata. GET post-deploy uguale al piano.
Revisione `85605df304ef5157f6f3ddf11e50bdbecc86c5b0e8301d076092b3ccf0bb5861`.
Eventuali messaggi pendenti nei delay ricreati possono andare persi al deploy;
questo impatto è stato comunicato prima dell'applicazione.

Quattro test isolati passati: scene app e retained, marcatori fino al report RM,
conservazione collegamenti e compatibilità comando individuale automatico.
Non sono prove hardware; nessuna scena azionata dall'agente.

## Prove utente

Durante il normale uso di una scena osservare
`zara/interface/semantic/v1/rm_receipts/lights.libreria.power/stat`.
Attesi actor utente, origin del canale, scene coerente, requested_state del ramo
realmente raggiunto e nuovo command_id. Se condizioni holiday/ECO o ritardi
impediscono il ramo libreria, l'assenza del receipt è compatibile con la logica.
La scena aziona più dispositivi come prima: non inviarla solo per un test se
l'effetto complessivo non è desiderato. Verificare separatamente app/UI/Alexa.

## Replica e rollback

build.py opera su export aggiornato; test.js non usa hardware. deploy.py conserva
revisioni e backup prima di apply. update.json non va importato come duplicato.
Rollback: dopo verifica di assenza di cambi successivi ripristinare i soli nodi
di rollback.json ed eliminare gli added_ids del manifest con deploy nodes.
Non ripristinare l'intero vecchio runtime alla cieca; conservare tutte le altre
patch. I retained precedenti non attestano attività successiva al rollback.
