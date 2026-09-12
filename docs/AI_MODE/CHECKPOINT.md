# Checkpoint — 12 settembre 2026

## Dove riprendere

Worktree: `/home/giuseppe/.cache/Google/AndroidStudio2026.1.4/aia/agents/DomoPiAndroidApp-ai-mode`
Branch: `feature/ai-home-assistant`; base precedente: `78a720f`.
L'utente ha autorizzato esplicitamente il commit di tutte le modifiche al progetto
e la prosecuzione il 12 settembre. Nessun push o deployment richiesto.

## Stato salvato

Schermata AI smart, stato MQTT con provenienza, consultazione deterministica
luci/living/ACS, dettatura e sintesi, test unitari e strumentali, documentazione.
Dettagli in [avanzamento](AVANZAMENTO_STATO_VOCE.md).
Le verifiche precedenti documentano 32 test Android e 12 backend passati;
non sono stati rieseguiti per il commit di checkpoint.

## Ripresa successiva al checkpoint `27c89c2`

Corretto il selettore del test «Chiedi», usando la semantica aggregata del
pulsante. Build `:app:assembleDebug :app:assembleDebugAndroidTest` riuscita.
Rieseguiti i quattro test UI su emulatore isolato: **4/4 passati**, 27,887 s.
Ispezionati cinque screenshot in tema chiaro/scuro: riepilogo, provenienza,
risposta composta, disconnessione e dati mancanti. Testi leggibili e controlli
coerenti nelle porzioni visualizzate; le schermate sono scorrevoli.
Evidenze salvate in [evidence/2026-09-12](evidence/2026-09-12/).
I valori sono fixture dei test, non letture domestiche correnti.

## Prossime attività

1. Verificare navigazione completa e voce su telefono; i test appena passati
   coprono contenuto isolato e callback simulati, non riconoscimento e
   riproduzione acustica reali né navigazione MainActivity con MQTT reale.
2. Restano da concordare backend/modello/accesso remoto e da risolvere
   freschezza sorgente, entità mancanti e mapping storico VMC/AC.

## Riproduzione locale e confini

Gli script locali `.validation/run-gradle.sh` e `.validation/run-ui.sh`
utilizzano bwrap con rete isolata, SDK `/home/giuseppe/Android/Sdk` e JDK 25
già presente. Sono ausili locali, con cache, APK e screenshot ignorati da Git;
restano sul disco e non vengono eliminati. Codice, test e stato di avanzamento
sono versionati. Nessuna credenziale o chiave locale va aggiunta al commit.

Preservare contratto `zara/interface`, Digital Twin Router e worktree principale.
La modalità AI resta di sola lettura; nessuna autorizzazione a deployment sui
Raspberry, comandi dispositivi o attivazione di regole.

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

## Persistenza dei lavori

Tutto ciò che serve a sviluppo e ripresa deve essere nel repository e
committato; `.validation/` e le altre cache devono restare sacrificabili. Il
repository Git comune è fuori dal worktree temporaneo, in
`/home/giuseppe/AndroidStudioProjects/DomoPiAndroidApp/.git`. Regole complete in
[POLITICA_FILE_E_RIPRESA.md](POLITICA_FILE_E_RIPRESA.md).
