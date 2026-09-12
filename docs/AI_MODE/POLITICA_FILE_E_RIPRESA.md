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
