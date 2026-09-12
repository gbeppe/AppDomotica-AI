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

L'ispezione del risultato locale `.validation/ui-results.txt` mostra quattro test
strumentali eseguiti, tre passati e uno fallito:
`compoundAnswerMatchesSpokenTextAndChangesOnDisconnection`, riga 37.
Il selettore `useUnmergedTree = true` punta al testo «Chiedi» e non alla
semantica del pulsante disabilitato: verificare/correggere e rieseguire.
La validazione UI non è quindi ancora completa.

## Prossime attività

1. Correggere il selettore del test, ricompilare e rieseguire i quattro test
   su emulatore isolato; esaminare gli screenshot chiari/scuri.
2. Aggiornare questo checkpoint con gli esiti effettivi.
3. Verificare navigazione completa e voce su telefono; distinguere i callback
   simulati da riconoscimento e riproduzione acustica reali.
4. Restano da concordare backend/modello/accesso remoto e da risolvere
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
