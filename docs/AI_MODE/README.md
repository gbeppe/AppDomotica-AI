# Modalità AI della casa

Documentazione di analisi e progetto — 11 settembre 2026.

Stato: proposta da revisionare prima dell'implementazione. Sono autorizzati questi documenti; codice Android, flow Node-RED, dispositivi e archivi rimangono invariati.

## Documenti

- [Analisi e architettura](ANALISI_ARCHITETTURA.md): obiettivi, componenti e confini.
- [Audit delle evidenze](AUDIT_EVIDENZE.md): verifiche eseguite, risultati e limiti.
- [Piano di lavoro](PIANO_DI_LAVORO.md): fasi, responsabilità e criteri di completamento.

## Vincoli concordati

1. Conservare il Digital Twin Router e il contratto MQTT pubblico esistente.
2. Usare EmonCMS locale come archivio delle misure; integrare i log per le motivazioni.
3. Escludere dalle analisi del funzionamento ordinario glitch isolati e transitori di sviluppo, senza cancellare gli originali e rendendo tracciabili le esclusioni.
4. Conservare ed eseguire le future regole nel backend, indipendentemente da Android e dal modello generativo.
5. Prevedere domande esplicite e riepiloghi spontanei della casa.
6. Scegliere l'ambiente backend in base a modularità ed efficienza; nessun linguaggio è ancora vincolante.
7. Prima di creare o cambiare branch e prima del commit, chiedere conferma. Nessun commit su `main`. Questa documentazione è preparata nel working tree per un successivo commit specifico.

Non sono incluse chiavi API, password, copie integrali dei flow o dei log domestici.
