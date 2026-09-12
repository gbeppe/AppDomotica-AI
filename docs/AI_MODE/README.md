# Modalità AI della casa

Documentazione di analisi e progetto — 11 settembre 2026.

Stato: prototipo locale in corso nel branch `feature/ai-home-assistant`, con backend di consultazione e schermata Android. Deployment e integrazione con un modello restano da definire; flow Node-RED, dispositivi e archivi non sono modificati.

## Documenti

- [Analisi e architettura](ANALISI_ARCHITETTURA.md): obiettivi, componenti e confini.
- [Audit delle evidenze](AUDIT_EVIDENZE.md): verifiche eseguite, risultati e limiti.
- [Piano di lavoro](PIANO_DI_LAVORO.md): fasi, responsabilità e criteri di completamento.

- [Verifica integrazione](VERIFICA_INTEGRAZIONE.md): risultati su EmonCMS reale e schermata Android.

- [Mapping stato corrente](MAPPING_STATO_CORRENTE.md): luci, living, ACS e limiti di freschezza.
- [Validazione storico energia](VALIDAZIONE_STORICO_ENERGIA.md): feed rete/SOC,
  segni, unità, copertura e prime domande backend.

## Vincoli concordati

1. Conservare il Digital Twin Router e il contratto MQTT pubblico esistente.
2. Usare EmonCMS locale come archivio delle misure; integrare i log per le motivazioni.
3. Escludere dalle analisi del funzionamento ordinario glitch isolati e transitori di sviluppo, senza cancellare gli originali e rendendo tracciabili le esclusioni.
4. Conservare ed eseguire le future regole nel backend, indipendentemente da Android e dal modello generativo.
5. Prevedere domande esplicite e riepiloghi della casa nella scheda e anche vocalmente, accettando richieste vocali complesse in italiano (es. luci accese, temperature living e ACS).
6. Scegliere l'ambiente backend in base a modularità ed efficienza; nessun linguaggio è ancora vincolante.
7. Nessun commit su `main`. I commit di checkpoint e degli incrementi successivi
   sul branch dedicato sono stati autorizzati dall'utente il 12 settembre.

Non sono incluse chiavi API, password, copie integrali dei flow o dei log domestici.

Il mapping è ora esteso a tutte le schede nel [catalogo dispositivi e capacità](CATALOGO_DISPOSITIVI.md), con distinzioni fra letture, comandi, risorse non MQTT e voci obsolete.

## Avanzamento del 12 settembre 2026

Completamento della scheda AI smart sul sottoinsieme luci/living/ACS, provenienza delle letture e interazione vocale: vedere [implementazione, verifiche e limiti](AVANZAMENTO_STATO_VOCE.md). Le osservazioni MQTT riportate sopra restano la fotografia dell’11 settembre, non letture correnti.

## Domande storiche sui flussi energetici

Requisito esplicito: AI smart deve rispondere anche a domande complesse su
prelievo mensile dalla rete, carica media Tesla e altri flussi energetici,
a testo e a voce. Vedere [casi, significati e verifiche](DOMANDE_ENERGIA.md).
Questa estensione non è ancora disponibile nel prototipo corrente.

Primo incremento backend disponibile: calcolo prelievo rete/SOC medio via CLI e
API, mapping live validato e 26 test passati. Integrazione nella scheda aperta.
Dettagli e ripresa nel [checkpoint](CHECKPOINT.md).
