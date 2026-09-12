# Verifica smart energia — 12 settembre 2026

Worktree dedicato `DomoPiAndroidApp-ai-mode`, branch `feature/ai-home-assistant`.

| Verifica | Esito | Confine della prova |
|---|---|---|
| unittest backend | 51/51 | strumenti, piani, HTTP, errori, periodo/futuro, log e confronto |
| unitari Android | 36/36 | MQTT 17, contratto outgoing 6, stato casa 9, stato energia/voce 4 |
| AiSmartScreenTest | 7/7 | Compose, domanda/dettatura simulata, testo letto identico, fonti e chiarimenti |
| HouseAiRepositoryTest | 2/2 | HTTP loopback reale, snapshot JSON, risposta e autenticazione/schema errati |
| EnergyNavigationTest | 1/1 | MainActivity: dashboard, smart, storico e ritorno classico in rete isolata |
| Build debug e APK test | PASS | JDK 25 e SDK locale, esecuzione Gradle offline isolata |
| Sintassi Python e diff whitespace | PASS | AST dei moduli e git diff --check |
| Parser su copie reali dei quattro log .20 | PASS con limiti espliciti | letture SSH soltanto; nessun deployment |
| Valutazione linguistica su modello reale | NON ESEGUITA | provider/gateway ancora da scegliere e configurare |

Suite strumentale contenuto/repository: 9 test in 43,302 s. Navigazione eseguita
separatamente dopo aver aggiunto quel test: 1 test in 13,597 s. I conteggi non
includono un collaudo MQTT domestico, audio reale o un modello generativo.
Le build sono riuscite con avvisi preesistenti su API deprecate/Gradle futuro.

Ricevute riproducibili:

- [Test UI e repository](evidence/2026-09-12/energy-ui-tests.txt)
- [Navigazione MainActivity](evidence/2026-09-12/energy-navigation-test.txt)
- [Audit log reali con hash e conteggi](evidence/2026-09-12/energy-log-audit.json)

Controllo visivo di quattro schermate, temi chiaro/scuro: riepilogo energia,
risposta parziale, provenienza, chiarimento. Nelle porzioni ispezionate non
risultano sovrapposizioni o testi illeggibili; il contenuto resta scorrevole.
I valori e le date mostrati negli screenshot sono fixture dei test.

- [Riepilogo](evidence/2026-09-12/energy-light-summary.png)
- [Risposta](evidence/2026-09-12/energy-light-answer.png)
- [Provenienza](evidence/2026-09-12/energy-light-evidence.png)
- [Chiarimento](evidence/2026-09-12/energy-dark-clarification.png)

Non sono stati cambiati Digital Twin Router, flussi Node-RED o servizi Raspberry.
L'osservatore Android aggiunto usa soltanto topic pubblici; il repository HTTP
non possiede capacità di publish. Per ripresa/configurazione e limiti funzionali:
[SMART_ENERGIA.md](SMART_ENERGIA.md).

Le quattro catture sono state rigenerate eseguendo soltanto i due test
interessati (2/2, 17,245 s), dopo che una successiva esportazione dall’emulatore
aveva sostituito le prime copie con PNG vuoti. Controllati formato e dimensioni
dei file archiviati. [Ricevuta rigenerazione](evidence/2026-09-12/energy-screenshot-tests.txt).
