# Stato corrente e voce — avanzamento del 12 settembre 2026

Lavoro nel solo worktree `DomoPiAndroidApp-ai-mode`, branch
`feature/ai-home-assistant`. Checkpoint Git autorizzato dall’utente il 12 settembre; nessun deployment o modifica al worktree
principale. Vedere [stato di ripresa](CHECKPOINT.md). La documentazione dell'11 settembre resta il riferimento per le
osservazioni reali; i valori 24,2 °C, 41,1 °C e il conteggio 6/1/1 non sono nuove
letture live.

## Punto di ripresa verificato

Il commit di base `78a720f` contiene già `AiSmartState`, la raccolta dei dieci
topic dentro `MqttManager`, il pulsante dashboard e la destinazione `ai_smart`
in `MainActivity`. Manca invece il file `AiSmartScreen` richiamato dalla
navigazione. Il catalogo esteso di 104 capacità è un audit separato: non abilita
automaticamente nuovi domini, interpretazioni fisiche o comandi nell'assistente.

## Implementazione

Completata la scheda AI smart con riepilogo aggiornato dalle osservazioni,
domanda testuale e risposta per luci/living/ACS, anche nella stessa frase.
La schermata riceve uno stato immutabile e callback di navigazione; non riceve
un client MQTT né una funzione di comando. Lo storico mantiene la sua schermata
e API separate.

L'adattatore di consultazione riusa la ricezione MQTT Android esistente; non
apre un secondo client o aggiunge sottoscrizioni. La modifica al callback
conserva il topic completo effettivamente ricevuto, incluso il prefisso
configurato. I payload `/cmd`, legacy, sconosciuti e delle entità escluse non
entrano nel modello AI. Gli aggiornamenti fuori ordine vengono ignorati;
un nuovo dato invalido sostituisce il vecchio valore senza presentarlo come
valido. Zero e OFF restano distinti da assenza/invalidità.

Il dettaglio per ogni entità mostra valore o qualità mancante/invalida, topic,
ora di ricezione in Europe/Rome e retained. L'ora sorgente resta sconosciuta;
la ricezione recente non certifica la freschezza. Per una voce mai ricevuta
viene mostrato il topic relativo atteso, senza inventare una ricezione.
Prolunga / Allarme e i punti non mappati conservano le esclusioni documentate.

Dettatura tramite attività vocale di sistema, avviata solo dal pulsante:
trascrizione modificabile e invio esplicito con «Chiedi». È richiesta la
preferenza offline, che il provider può non rispettare; la scheda informa
che il servizio del dispositivo può elaborare l'audio online. Nessun ascolto
continuo o invio automatico delle trascrizioni.

Sintesi con voce italiana offline disponibile sul dispositivo. Legge lo stesso
testo passato alla scheda, inclusi i limiti; arrestabile dall'utente e fermata
quando la schermata esce in background, viene chiusa o cambia la connessione.
In assenza di voce/provider, la consultazione testuale resta disponibile.
Le risposte sono deterministiche sul sottoinsieme mappato: non costituiscono
un'integrazione con un modello conversazionale generale.

## Verifiche

- 32 test unitari Android passati: 17 `MqttManagerTest`, sei contratto MQTT,
  nove nuovi `AiSmartStateTest`.
- 12 test Python backend passati.
- La prova di corrispondenza confronta i topic e le classificazioni luci del
  modello Android con `backend/house_ai/current_catalog.json`.

Compilazione/test offline con JDK 25 già disponibile. Cache, home Java, file
temporanei e artefatti nuovi sono confinati in `.validation/` nel worktree;
la build viene eseguita con filesystem esterno di sola lettura e rete isolata.
Gli artefatti di build ordinari restano nelle directory di build del worktree.

## Restano aperti

Prova acustica e dettatura reale su telefono, navigazione completa, freschezza sorgente verificabile, classificazioni mancanti,
estensione dell'interprete al catalogo completo, scelte di backend/modello e
accesso remoto. Lo snapshot Python non viene ancora esposto con una nuova API:
questo incremento collega lo stato alla scheda tramite l'osservatore Android.

La verifica della schermata isolata non prova la navigazione completa con
MQTT reale né autorizza installazioni sui Raspberry o attivazioni di regole.

## Verifica UI completata dopo il checkpoint

Build APK e APK test riuscita; quattro test strumentali su contenuto isolato
passati dopo la correzione del selettore del pulsante «Chiedi». Cinque screenshot
chiari/scuri ispezionati. Esiti ed evidenze nel [checkpoint](CHECKPOINT.md).
