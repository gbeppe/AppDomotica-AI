# Fase energia — Digital Twin e integrazione Android

Incremento del 12 settembre 2026 sul branch `feature/ai-home-assistant`.

## Obiettivo

Usare entrambe le fonti concordate senza confonderle:

- Digital Twin pubblico MQTT per potenze e SOC correnti;
- EmonCMS `.15` per serie storiche, integrazioni e confronti.

La stessa domanda scritta o trascritta dalla voce viene inviata all'assistente
dinamico. Il pianificatore sceglie uno strumento corrente o storico; il backend
valida il piano e restituisce una sola risposta adatta a schermo e sintesi.

## Stato corrente Android

`EnergySmartState` osserva soltanto cinque topic pubblici:

| Metrica | Topic relativo |
|---|---|
| Produzione FV | `energy/solar/power/stat` |
| Consumo casa | `energy/home/consumption/stat` |
| Scambio rete con segno | `energy/grid/power_raw/stat` |
| Potenza batteria con segno | `energy/battery/power_raw/stat` |
| SOC Powerwall | `energy/battery/soc/stat` |

Per ogni valore conserva numero valido o qualità `invalid`, ora di ricezione,
retained e topic completo realmente ricevuto. Mai ricevuto, zero e invalido sono
stati distinti. Un messaggio fuori ordine non sostituisce quello più recente.
Lo stato viene azzerato quando cambia il prefisso o si completa una connessione,
così un contesto precedente non viene attribuito alla nuova sessione.

Il payload inviato al backend contiene solo osservazioni valide e lo stato della
connessione. Il backend non considera l'ora Android timestamp della misura e non
considera retained prova di freschezza o conferma fisica.

## Strumenti e interfaccia

Il catalogo dinamico ora espone:

- `current_energy_metric` per le cinque letture Digital Twin;
- `energy_metric` per le sette aggregazioni storiche EmonCMS.

`POST /v1/assistant/query` accetta `question` e lo snapshot facoltativo
`current_energy`. Lo schema è chiuso: metriche, campi, valori non finiti, SOC
fuori 0–100 e topic di comando vengono respinti.

La schermata AI smart permette di inserire indirizzo e token del backend. Il
token resta soltanto nello stato in memoria della schermata. Il pulsante
«Chiedi» invia la stessa stringa proveniente dalla tastiera o dalla trascrizione
vocale; la risposta del backend è mostrata e può essere letta dalla stessa
sintesi vocale già presente. L'app non riceve capacità di publish dal repository
HTTP e non passa credenziali MQTT o EmonCMS al pianificatore.

## Limiti della fase

Il backend dinamico richiede ancora un gateway di pianificazione configurato.
Senza `HOUSE_AI_PLANNER_URL` risponde `not_configured`. La compilazione e i test
con planner simulato validano contratti e flusso, non la qualità linguistica di
un modello reale. URL e token del backend non sono ancora integrati nelle
impostazioni permanenti; il token viene intenzionalmente perso uscendo dalla
composizione. Nessun backend è stato installato sui Raspberry.

La freschezza sorgente dei topic resta sconosciuta. Per questo la risposta
corrente espone ricezione e retained nei risultati strutturati, ma non dichiara
automaticamente «adesso» in senso fisico. Rete e batteria conservano il valore
con segno; il testo futuro dovrà spiegare import/export e carica/scarica quando
la domanda riguarda il flusso corrente.

## Aggiornamento successivo del 12 settembre

Per lo stato finale dell'implementazione, le funzioni aggiunte, la configurazione
e i limiti ancora aperti consultare [SMART_ENERGIA.md](SMART_ENERGIA.md).
