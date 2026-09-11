# Piano di lavoro

## Stato e autorizzazioni

Analisi in lettura svolta parzialmente; documentazione autorizzata. Implementazione, creazione/cambio branch e commit richiedono la conferma concordata con l'utente. Nessuna attivazione di regole è inclusa nella prima versione.

## Fasi

| Fase | Attività autonoma | Contributo dell'utente | Criterio di completamento |
|---|---|---|---|
| A — Catalogo dati | Inventario, metadati, campioni, mapping e qualità | Solo significati e sostituzioni non ricavabili dai flow | Per ogni misura MVP: sorgente, unità, validità e limiti documentati |
| B — Motivi e casi | Dizionario di stati, priorità, log e verifica con EmonCMS | Date/versioni e azioni esterne per episodi non spiegabili | Casi riproducibili di motivo documentato e risposta con dati insufficienti |
| C — Progetto tecnico | API, componenti, stima del carico, alternative backend | Sede di esecuzione, uso di modello locale/remoto, preferenze di costo e dati condivisi | Scelta esplicita di runtime, deployment e contratto dei risultati |
| D — Backend consultazione | Adattatori, catalogo, calcoli, evidenze e conversazione | Accessi strettamente necessari alle sorgenti mancanti | Risultati verificabili senza comandi ai dispositivi |
| E — Android | Schermata AI, grafici/tabelle, riepilogo spontaneo a schermo | Revisione dei messaggi e delle priorità | Interrogazioni e riepiloghi funzionanti senza regressioni MQTT |
| F — Validazione integrata | Test di qualità, prestazioni, errori e disconnessioni | Riscontro su casi domestici ambigui | Criteri MVP superati e limiti dichiarati |
| G — Notifiche facoltative | Progetto e implementazione del trasporto | Eventi, orari e frequenza desiderati | Consegna controllata, deduplicata e disattivabile |
| H — Regole naturali | Schema, validatore, simulazione e motore persistente | Priorità, durata, ripristino e attivazione delle singole regole | Esecuzione backend coerente con protezioni e comandi pubblici |

Le fasi D ed E iniziano solo dopo autorizzazione all'implementazione. Non è necessario ricostruire dieci anni di logica per completare un MVP limitato ai dati validati.

## MVP proposto

1. Stato della casa con freschezza per entità.
2. Consumo AC in un periodo e confronto con il precedente, con distinzione delle modalità solo dove verificabile.
3. Intervalli SOC sotto soglia.
4. Incrocio tra produzione FV e attività AC.
5. Spiegazione di eventi climatici registrati, inizialmente usando il caso del 6 settembre.
6. Conteggio transizioni VMC dopo la validazione della codifica.
7. Riepilogo spontaneo all'apertura e per cambiamenti rilevanti a schermo.

Fuori dal MVP: controllo generativo diretto, generazione/deploy arbitrario di flow, notifiche in background e attivazione di regole.

## Verifiche richieste per l'implementazione

- Campioni mancanti non trasformati in zero; nessuna integrazione attraverso buchi non dichiarati.
- Picchi isolati esclusi con motivazione; carichi reali persistenti conservati.
- Contatori con reset e cambio unità gestiti senza consumi negativi spurii.
- Transizioni contate una volta anche con messaggi duplicati; nessuna transizione inventata attraverso un buco.
- Giorni e confronti definiti in Europe/Rome, inclusi cambi d'ora.
- SHADOW non attribuito come causa di comandi fisici.
- Motivi generici o versione ignota producono una risposta qualificata, non una causa inventata.
- Riepilogo senza valori obsoleti presentati come correnti; previsione con orizzonte e sorgente.
- Nessun publish di comando nella modalità consultazione.
- Test Android appropriati e verifica del contratto MQTT esistente dopo le modifiche.

## Prossime azioni

1. Revisionare questi documenti e completare il catalogo minimo dei feed.
2. Risolvere il mapping storico della velocità VMC e dei diversi feed AC.
3. Definire l'API e confrontare le opzioni backend sulla base del carico reale; scegliere modello e collocazione con l'utente.
4. Presentare un perimetro implementativo concreto e richiedere autorizzazione per branch dedicato e lavoro applicativo.
5. Preparare un commit specifico dei documenti solo dopo la conferma Git richiesta.
