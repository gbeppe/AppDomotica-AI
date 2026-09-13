# Politica dei file e ripresa del progetto

Vincolo confermato dall'utente il 12 settembre 2026.

## File necessari al progetto

Codice sorgente, test, configurazioni prive di segreti, mapping validati,
documentazione, decisioni architetturali, stato di avanzamento e istruzioni di
ripresa devono essere creati dentro il repository e inclusi in un commit sul
branch `feature/ai-home-assistant` al termine di ogni incremento coerente.

Un lavoro non si considera consegnato o riprendibile se una nuova chat avrebbe
bisogno di un file presente soltanto in una cartella ignorata, in una cache o
nella memoria della sessione. Prima del commit verificare almeno `git status`,
`git diff --check`, test pertinenti e assenza di credenziali nei nuovi diff.

## Documentazione obbligatoria di architettura e deployment

Vincolo ribadito dall'utente il 13 settembre 2026.

Ogni incremento che modifica modello, provider, gateway, fonti dati,
autenticazione, Tailscale, servizi di sistema o host di esecuzione deve aggiornare
la documentazione nello stesso commit del codice o della configurazione. La
documentazione deve permettere a una persona che non ha seguito la sessione di
capire la scelta e replicare l'installazione su un altro Raspberry Pi.

Devono essere registrati almeno:

1. problema, requisiti e vincoli che hanno guidato la scelta;
2. alternative valutate, motivi della scelta e compromessi accettati;
3. architettura risultante, flussi dei dati, confini di fiducia e responsabilità
   dei singoli host;
4. inventario dei tool e dei servizi, distinguendo ciò che era già presente,
   ciò che è stato installato e ciò che è stato usato solo temporaneamente;
5. versioni, compatibilità hardware e software, prerequisiti, costi e limiti del
   provider, con data e fonte quando possono cambiare;
6. prestazioni attese e misure osservate, mantenendole esplicitamente distinte;
7. comandi e file necessari per installazione, configurazione, verifica,
   aggiornamento, replica e rollback;
8. gestione dei segreti con nome della variabile, percorso, proprietario,
   permessi e procedura di rotazione, senza riportare mai il valore;
9. prove eseguite, risultato, limiti della verifica e stato reale del deployment.

Per il dominio energia i riferimenti minimi da mantenere allineati sono:

- `docs/AI_MODE/SMART_ENERGIA.md` per requisiti e comportamento funzionale;
- `docs/AI_MODE/GATEWAY_MODELLO.md` per decisioni su modello, Groq, gateway e
  Tailscale;
- un documento datato `DEPLOYMENT_*.md` per lo stato effettivamente installato;
- un documento datato `VERIFICA_*.md` quando vengono svolte prove su servizi o
  sorgenti reali;
- `docs/AI_MODE/CHECKPOINT.md` per il punto di ripresa complessivo.

Nei documenti il provider scelto deve essere chiamato **Groq**. **Grok** indica
un prodotto differente e non deve essere usato come sinonimo. Ogni documento
deve inoltre distinguere configurazioni di esempio, stato osservato sugli host,
test simulati e verifiche effettuate su sorgenti reali.

## File temporanei e sacrificabili

Le aree ignorate, tra cui `.validation/`, `.gradle/`, `build/`, `app/build/`,
`.idea/` e altri percorsi di cache, possono contenere esclusivamente artefatti
rigenerabili: cache, log completi di esecuzione, APK, output intermedi, emulatori,
screenshot di lavoro e campioni grezzi usati durante una verifica.

Quando un artefatto temporaneo contiene informazioni necessarie alla ripresa,
si conserva nel repository una sintesi sufficiente e priva di segreti: origine,
periodo, metodo, risultato, limiti, hash o test di riproduzione. Le credenziali,
i token e i dati sensibili non vengono committati.

## Collocazione del worktree

Il worktree corrente è sotto la cache di Android Studio:
`/home/giuseppe/.cache/Google/AndroidStudio2026.1.4/aia/agents/DomoPiAndroidApp-ai-mode`.
Il suo Git comune è però `/home/giuseppe/AndroidStudioProjects/DomoPiAndroidApp/.git`.
Il branch e i commit sopravvivono quindi alla perdita del worktree temporaneo e
permettono di ricrearlo. Non affidarsi comunque a file non committati nel
worktree: prima di interrompere, rendere pulito `git status` salvando tutti i
file di progetto autorizzati in un commit.

## Punto di ingresso per una nuova chat

1. Verificare o ricreare il worktree del branch `feature/ai-home-assistant`.
2. Leggere `README.md`, `docs/AI_MODE/CHECKPOINT.md` e questa politica.
3. Verificare che `git status --short` sia pulito prima di iniziare nuove modifiche.
4. Usare i documenti versionati come fonte dello stato; trattare cache e file
   ignorati come facoltativi e potenzialmente assenti.
