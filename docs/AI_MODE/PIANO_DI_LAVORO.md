# Piano di lavoro

## Stato e autorizzazioni

Analisi svolta parzialmente. L'utente ha autorizzato il branch dedicato, il commit iniziale dei documenti e la prosecuzione del lavoro. Il branch `feature/ai-home-assistant` è disponibile in un worktree separato; la cartella Android Studio originale resta su `main`. Nessuna attivazione di regole o installazione sui Raspberry è inclusa in questa fase.

### Avanzamento implementativo

Primo modulo in `backend/house_ai`: adattatore EmonCMS di sola lettura, estrattore degli eventi climatici e CLI. Nessuna dipendenza dal modello o dal codice Android. Python con libreria standard è una scelta locale per questo prototipo; deployment e modello restano da concordare.

Verifica locale: tre test passati (giorni con cambio d'ora, esclusioni/evidenze, limiti richieste). Sul log reale del 6 settembre: 62 eventi mantenuti, sei esclusioni; ritrovati gli eventi delle righe 4261 e 4293. L'adattatore di rete non è ancora stato verificato end-to-end nella nuova implementazione. Sono ora presenti join con le serie AC, API server e schermata Android per i rapporti giornalieri; la verifica integrata resta da completare.

## Fasi

| Fase | Attività autonoma | Contributo dell'utente | Criterio di completamento |
|---|---|---|---|
| A — Catalogo dati | Inventario, metadati, campioni, mapping e qualità | Solo significati e sostituzioni non ricavabili dai flow | Per ogni misura MVP: sorgente, unità, validità e limiti documentati |
| B — Motivi e casi | Dizionario di stati, priorità, log e verifica con EmonCMS | Date/versioni e azioni esterne per episodi non spiegabili | Casi riproducibili di motivo documentato e risposta con dati insufficienti |
| C — Progetto tecnico | API, componenti, stima del carico, alternative backend | Sede di esecuzione, uso di modello locale/remoto, preferenze di costo e dati condivisi | Scelta esplicita di runtime, deployment e contratto dei risultati |
| D — Backend consultazione | Adattatori, catalogo, calcoli, evidenze e conversazione | Accessi strettamente necessari alle sorgenti mancanti | Risultati verificabili senza comandi ai dispositivi |
| E — Android | Schermata AI, grafici/tabelle, riepilogo a schermo e vocale, domande vocali anche composte | Revisione dei messaggi e delle priorità | Interrogazioni testuali/vocali e riepiloghi coerenti, senza regressioni MQTT |
| F — Validazione integrata | Test di qualità, prestazioni, errori e disconnessioni | Riscontro su casi domestici ambigui | Criteri MVP superati e limiti dichiarati |
| G — Notifiche facoltative | Progetto e implementazione del trasporto | Eventi, orari e frequenza desiderati | Consegna controllata, deduplicata e disattivabile |
| H — Regole naturali | Schema, validatore, simulazione e motore persistente | Priorità, durata, ripristino e attivazione delle singole regole | Esecuzione backend coerente con protezioni e comandi pubblici |

Le fasi D ed E includono ora un prototipo locale del rapporto giornaliero e il contratto `house_ai.daily_report.v1`. Non è necessario ricostruire dieci anni di logica per completare un MVP limitato ai dati validati.

## MVP proposto

1. Stato della casa con freschezza per entità.
2. Consumo AC in un periodo e confronto con il precedente, con distinzione delle modalità solo dove verificabile.
3. Intervalli SOC sotto soglia.
4. Incrocio tra produzione FV e attività AC.
5. Spiegazione di eventi climatici registrati, inizialmente usando il caso del 6 settembre.
6. Conteggio transizioni VMC dopo la validazione della codifica.
7. Riepilogo dello stato nella scheda e anche vocalmente; modalità delle letture spontanee da definire.
8. Domande vocali in italiano, anche composte: conteggio luci accese e richiesta congiunta delle temperature living e ACS come primi casi di accettazione.

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

## Ripresa del 11 settembre 2026

Branch verificato: `feature/ai-home-assistant`, nella worktree `DomoPiAndroidApp-ai-mode`.
Sei test backend passati, inclusa la propagazione al rapporto dei limiti del log
non configurato. Compilazione Android `:app:compileDebugKotlin --offline` riuscita
con SDK locale. Verifica integrata della schermata isolata con EmonCMS reale
e log fornito eseguita: vedere [resoconto](VERIFICA_INTEGRAZIONE.md). Nessuna installazione o attivazione sui Raspberry eseguita.

## Requisito vocale aggiunto l'11 settembre 2026

L'utente richiede stato della casa comunicato anche a voce e richieste vocali
complesse. Requisiti e criteri in [Analisi e architettura](ANALISI_ARCHITETTURA.md).
La verifica integrata già eseguita copre il rapporto storico nella scheda:
riconoscimento vocale, sintesi e domande composte non sono ancora implementati.

## Prossime azioni

1. Completare il catalogo minimo e risolvere il mapping storico VMC/AC.
2. Estendere la verifica alla navigazione completa e definire aggiornamento dei log e accesso remoto.
3. Concordare collocazione del backend e modello locale/remoto.
4. Completare i casi MVP e la verifica visiva della schermata.

5. Validare entità luci e sensori living/ACS; progettare acquisizione vocale, interpretazione delle richieste composte e sintesi coerente con la scheda.

## Mapping corrente verificato

Catalogo iniziale di otto punti luce e due temperature preparato, con osservazione
MQTT di sola lettura e interprete offline dello stato. Dieci test backend passati.
Vedere [mapping ed evidenze](MAPPING_STATO_CORRENTE.md). La classificazione di
Prolunga / Allarme, i punti luce non mappati e la freschezza sorgente restano aperti.

## Catalogo esteso delle schede

Audit statico delle 13 schermate e del registry/compositeRegistry: 104 capacità MQTT,
risorse HTTP/locali, quattro comandi UI senza rotta e quattro capacità obsolete
isolate. Dodici test backend passati. Vedere [catalogo completo](CATALOGO_DISPOSITIVI.md).
Il dominio è ricavato dai topic e dalle sezioni reali, senza equipararlo
a classificazione fisica certa o ad autorizzazione di comando.

## Avanzamento del 12 settembre 2026

Completamento della scheda AI smart sul sottoinsieme luci/living/ACS, provenienza delle letture e interazione vocale: vedere [implementazione, verifiche e limiti](AVANZAMENTO_STATO_VOCE.md). Le osservazioni MQTT riportate sopra restano la fotografia dell’11 settembre, non letture correnti.

## Requisito aggiunto: storico energia

La prosecuzione deve includere [domande complesse sui flussi energetici](DOMANDE_ENERGIA.md):
prelievo rete nel mese, media carica Tesla nella settimana e interrogazioni
composte. Prima dell'implementazione validare serie, unità/segni, periodi e
significato della percentuale richiesta. Requisito documentato, non implementato.

## Incremento storico energia implementato — 12 settembre 2026

Aggiunti `backend/house_ai/energy_history.py`, comando CLI `energy` e sette test.
Calcoli prelievo rete e SOC medio temporale, copertura esplicita, gestione
di null/invalidi/duplicati conflittuali, limiti e blocchi per richieste mensili.
Periodi Europe/Rome con fine esclusa e cambio d'ora; nomi distinti per periodi
di calendario e ultimi giorni completi. Unità e segno import devono essere
forniti esplicitamente sulla base di una verifica della sorgente.

Verifica locale iniziale: **19 test backend passati**, sintassi Python e diff Git validi.
Prova sintetica: 1 kW costante nel mese di agosto produce 744 kWh, copertura 100%.
Non è un risultato domestico reale.

Passo successivo completato: mapping e campioni reali verificati in sola lettura,
con esiti in [VALIDAZIONE_STORICO_ENERGIA.md](VALIDAZIONE_STORICO_ENERGIA.md).
Aggiunti catalogo sorgenti, interprete italiano e endpoint protetto. Totale:
26 test backend passati. Prossimo passo: collegare l'endpoint a UI e voce Android.
Nessun collegamento Android incluso in questo incremento.
Codice, documentazione e validazione sono salvati nel commit `d0bce5e`.
