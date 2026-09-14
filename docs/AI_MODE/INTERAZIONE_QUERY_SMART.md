# Home e interazione della modalità Smart

Decisione aggiornata il 13 settembre 2026.

## Struttura della home

La modalità Smart usa il linguaggio naturale come interfaccia principale. La
home contiene esattamente tre riquadri funzionali:

1. **Stato della domotica**: elenca soltanto dispositivi dichiarati attivi e
   misure ambientali valide;
2. **Chiedi alla casa**: campo modificabile, dettatura e invio esplicito;
3. **Risposta**: testo identico a quello pronunciato, stato della risposta e
   provenienza espandibile.

URL backend, token, diagnostica stack e storico sono nel menu ausiliario della
barra superiore. L'indicatore colorato dello stack resta visibile per segnalare
subito un guasto, ma i dettagli si aprono come diagnostica ausiliaria.

La home Smart non contiene switch, slider, selettori, stepper o altri widget di
controllo. Questi restano nelle schede della modalità dashboard.

## Riepilogo

Il riepilogo non mostra dispositivi spenti. Per i dispositivi attivi usa solo
stati booleani validi dei punti luce mappati e lo stato dichiarato del
condizionatore. Nel primo mapping la misura ambientale del riepilogo è la
temperatura living; ACS resta interrogabile ma non viene presentata come misura
ambientale. Stato mancante o invalido non diventa OFF o zero.

Retained e timestamp Android restano evidenze di ricezione, senza dimostrare
freschezza della sorgente o stato fisico. Il riepilogo lo dichiara esplicitamente.

## Azioni via query

La modalità Smart accetta ON/OFF per una singola luce mappata. Groq seleziona
`set_light_state` con un ID chiuso e `on`/`off`; il validatore rifiuta campi,
target o stati estranei al catalogo. Il backend non restituisce topic o payload.
Android risolve l'ID nell'allowlist locale e pubblica una sola volta sul topic
pubblico `zara/interface/.../power/cmd`, con payload booleano. Tutti gli altri
comandi restano non disponibili.

La risposta distingue comando pronto dall'esito fisico: la pubblicazione MQTT
non dimostra che la lampada abbia cambiato stato. Il successivo `/stat` aggiorna
lo stato dichiarato e l'attribuzione dell'evento osservato.

## Correzione del prototipo precedente

Il prototipo con una schermata separata di 23 widget Smart è stato rimosso dopo
la decisione di prodotto. Il censimento dei 9 comandi Clima e 14 Impianti resta
un'evidenza utile per costruire in futuro l'allowlist delle intenzioni, ma non è
esposto come interfaccia Smart e non viene sottoscritto da un modello UI
dedicato.

## Verifica

I test devono verificare i tre riquadri, l'apertura della configurazione dal
menu, la diagnostica, l'assenza dei precedenti widget, la modifica della
trascrizione prima dell'invio e l'identità fra risposta scritta e parlata.
## Audit dominio luci (14 settembre 2026)

La app invia al backend lo snapshot `house_ai.current_lights_input.v1`, separato
da energia e climatizzazione. Sono ammessi soltanto gli otto identificativi del
registry e i rispettivi topic pubblici `zara/interface/.../stat`; valori mancanti
o non interpretabili non diventano `OFF`.

Il planner usa `current_lights` per un punto luce o per tutti quelli mappati. La
risposta riporta stati dichiarati dal Digital Twin. Il timestamp è l'istante di
ricezione Android e un messaggio retained non dimostra freschezza, durata o
stato fisico.

Per «chi ha acceso/spento?» Android registra le transizioni osservate durante
la sessione. Se una transizione coincide entro 30 secondi con un comando inviato
dall'app, il responsabile è `utente`; ogni altra transizione successiva a uno
stato iniziale valido è `automazione`. Il primo stato ricevuto, incluso retained,
non viene scambiato per una transizione e resta senza autore. Il registro è di
sessione: dopo il riavvio serve osservare una nuova transizione.

L'audit ha eliminato alias `/cmnd`, topic duplicati e riconoscimento locale di
frasi. L'abilitazione successiva usa invece uno strumento chiuso, una sola
allowlist e il riscontro separato del nuovo stato, senza affidare al modello
topic o payload arbitrari.

## Riquadro riepilogo a tendina

All'ingresso il riepilogo è espanso. L'intera intestazione e l'icona a freccia
lo richiudono; quando è minimizzato resta una barra compatta che può essere
toccata per riaprirlo. Lo stato usa `rememberSaveable`, quindi sopravvive alle
ricomposizioni e lascia spazio verticale ai riquadri query e risposta.
