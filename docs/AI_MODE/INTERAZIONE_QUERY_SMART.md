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

Attivazioni, disattivazioni e selezioni future verranno richieste scrivendo o
dettando una query. In questa fase rimangono disabilitate: il backend e l'app
sono read-only e nessun comando MQTT viene pubblicato.

La futura implementazione dovrà trasformare il testo in un'intenzione
strutturata validata contro una allowlist derivata dal registry. Il modello non
potrà produrre direttamente topic o payload. Prima dell'esecuzione l'app dovrà
mostrare azione, destinazione e valore e richiedere conferma esplicita. Dopo la
pubblicazione, il risultato dovrà distinguere comando inviato, `/stat`
convergente, timeout e stato fisico non verificato.

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

Il planner può scegliere soltanto lo strumento deterministico `current_lights`,
per un punto luce o per tutti quelli mappati. La risposta riporta stati
dichiarati dal Digital Twin. Il timestamp è l'istante di ricezione Android e un
messaggio retained non dimostra freschezza, durata dello stato, autore della
transizione o stato fisico. Domande come «chi ha acceso?» e «da quanto è
accesa?» richiedono uno storico eventi con transizioni e autore, oggi assente.

L'audit ha rimosso dalla UI Smart la pubblicazione diretta su topic `/cmd` o
`/cmnd` e il riconoscimento locale di frasi di comando. Le future azioni
passeranno da strumenti chiusi, validazione del target e riscontro del nuovo
stato, senza affidare al modello topic o payload arbitrari.
