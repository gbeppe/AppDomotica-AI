# TS-MIG.1 — Endpoint LAN canonici

Data: 23 settembre 2026.
Branch isolato: `feature/ts-mig-1-subnet-routing`, base `40bdfb4`.
Worktree: `DomoPiAndroidApp-ts-mig-1`, accanto al worktree Smart.

## Isolamento e ambito

Il worktree `DomoPiAndroidApp-ai-mode`, su `feature/ai-home-assistant`, aveva
modifiche non committate (inclusi aggiornamenti semantici Smart e dashboard).
Non sono state spostate, copiate o eliminate. Questo branch nasce dal commit
corrente, non dalla working copy dirty; il suo APK non include quegli incrementi
non committati. Per questo non è stato installato automaticamente sul telefono.

L'implementazione interessa solo tre file di produzione:

- `MainActivity.kt`: MQTT e selezione EmonCMS;
- `MainDashboard.kt`: host TinyCam e rimozione del testo LOCALE/REMOTO;
- `CamerasScreen.kt`: host TinyCam.

Aggiunti il test `CanonicalLanRoutingSourceTest.kt` e questo documento.
Durante implementazione e verifica non sono stati eseguiti commit, staging o push.
Successivamente l’utente ha autorizzato il commit dedicato dei cinque file TS-MIG.1,
senza push. Nessun cambiamento a infrastruttura o dipendenze.

## Prima / dopo

```text
PRIMA
Preferenze MQTT + subnet rilevata + prova TCP
  -> host MQTT locale/remoto + ConnectionMode globale
      -> EmonCMS locale/remoto
      -> TinyCam locale/remoto

DOPO
settings.domopiIp + domopiPort -> MQTT
settings.emoncmsIp             -> EmonCMS
settings.tinycamLocalIp + port -> TinyCam (dashboard, camere, diagnosi)
Sistema operativo/Tailscale   -> scelta del percorso di rete
```

Gli host sono quelli già persistiti come locali; nessun valore è migrato o
sovrascritto. Non vengono aggiunti indirizzi IP hardcoded. EmonCMS attende il
valore del DataStore invece di tentare inizialmente un IP predefinito nella UI.

## Subnet router e conferma TinyCam

**Correzione del 10 ottobre 2026:** il subnet router Tailscale è `.20`, come
nella topologia WallBox; la precedente indicazione `.15` era errata. `.15` resta
host LAN EmonCMS (e broker MQTT legacy). L'app indirizza sempre gli host LAN:
il passaggio attraverso `.20` è routing IP gestito dal sistema
operativo/Tailscale, senza proxy applicativo.

TinyCam `.50:8083` è il server LAN effettivo. Il vecchio campo remoto indicava
un percorso alternativo allo stesso server, non un servizio cloud separato:
la classificazione CAN SIMPLIFY è confermata.

| Telefono | Tailscale | Destinazione MQTT nell'app | Percorso atteso |
| --- | --- | --- | --- |
| Wi-Fi casa | OFF | Host LAN MQTT configurato (.20 nell'installazione) | LAN → .20 |
| Wi-Fi casa | ON | Stesso host | Routing OS → .20 |
| 4G/5G | ON | Stesso host | Tailscale → subnet router .20 → host LAN |
| 4G/5G | OFF | Stesso host | Non raggiungibile, normale errore connessione |

Questa matrice rimane da collaudare con il nuovo APK sul telefono. La subnet
infrastrutturale è già stata validata dall'utente; i test JVM non sostituiscono
il collaudo di questa build. House AI rimane sul proprio URL HTTPS attuale.

## Comportamento preservato

MQTT: autenticazione, client Paho, sottoscrizioni, topic, coda, riconnessione
Paho e ciclo esterno (8 s dopo tentativo, 15 s se connesso) invariati. Eliminata
solo la selezione geografica e la prova TCP che la guidava. Nessun fallback
all'indirizzo remoto quando l'endpoint canonico non risponde. MqttManager non
è modificato.

EmonCMS: EnergyRepository e autenticazione invariati. Il fallback di percorso
`/emoncms/feed/data.json` → `/feed/data.json` rimane sullo stesso host.

TinyCam: credenziali, porta, identificativi, URL stream e WebView invariati.
DiagnosisScreen già leggeva il valore LAN: ora i consumatori sono allineati
senza modificare il probe diagnostico.

House AI: UNCHANGED. Conservati URL HTTPS, TLS, repository, schermate e health.
GitHub: UNCHANGED. Non cambia nessun collegamento del backend Smart.

## Debito esplicitamente rinviato a TS-MIG.2

- `ConnectionMode` e il relativo StateFlow rimangono in ConnectivityManager;
  nessun consumatore esterno legge o aggiorna più la modalità geografica.
- Helper di rete/subnet e parametri ConnectivityManager delle schermate
  conservati. La prova TCP serve ancora a DiagnosisScreen.
- Le tre preferenze remote, i Flow e i campi Settings restano disponibili,
  ma non influenzano gli endpoint operativi. Nessuna migrazione distruttiva.
- Rimosso il testo LOCALE/REMOTO; pallino MQTT e navigazione alla diagnosi
  restano. Non introdotto un nuovo badge o redesign.
- Non risolti in questo WP: errori HTTP rappresentati come dati vuoti,
  SSL permissivo della WebView, doppio meccanismo di retry MQTT, applicazione
  di nuovi parametri MQTT mentre già connesso e coda comandi offline.

## Verifica e limiti

Quattro test architetturali seguono la convenzione di source-contract test già
usata dal progetto: dipendenze MQTT, endpoint EmonCMS, coerenza dei tre accessi
TinyCam e assenza di ConnectionMode/RemoteIp nei consumatori operativi.

Sono controlli statici sui collegamenti Compose/Activity, non simulazioni del
sistema Android o di Tailscale. Non dimostrano raggiungibilità, timing dei retry,
ricezione fisica dei comandi o rendering sul dispositivo. Non è stata introdotta
una nuova architettura soltanto per rendere unit-testabile il selettore rimosso.

Comandi di regressione dal nuovo worktree:

```sh
JAVA_HOME=/home/giuseppe/.gradle/jdks/jetbrains_s_r_o_-25-amd64-linux.2 \
ANDROID_HOME=/home/giuseppe/Android/Sdk ./gradlew test assembleDebug --console=plain
git diff --check
```

`git apply --check` del solo diff applicativo sul worktree Smart dirty è una
verifica a secco: nessuna modifica applicata; compatibilità PASS. Non sostituisce
il test dell'eventuale futura integrazione completa.

## Collaudo sul telefono ancora necessario

| Rete | MQTT, EmonCMS e TinyCam: stesso host LAN configurato |
| --- | --- |
| Wi-Fi casa, Tailscale OFF | Successo atteso via LAN |
| Wi-Fi casa, Tailscale ON | Successo atteso con routing del sistema operativo |
| Mobile, Tailscale ON | Successo atteso attraverso subnet router .20 |
| Mobile, Tailscale OFF | Errore normale, nessun fallback remoto/pubblico |

Per ciascun servizio verificare host effettivo, autenticazione, risultato UI e
ripresa dopo interruzione. Un guasto MQTT non deve cambiare host EmonCMS/TinyCam.
Non inviare comandi ai dispositivi durante i primi test di raggiungibilità:
i comandi eventualmente già accodati dal client sono un caso distinto.
House AI conserva l'eccezione HTTPS Tailscale Serve: questa matrice non promette
il suo funzionamento con Tailscale spento.

## Ripresa

Il lavoro è raccolto in un commit dedicato sul branch TS-MIG.1, su richiesta
dell’utente. Il worktree Smart rimane sul ramo originale, senza necessità di
checkout o stash. Prima di integrare, rivedere la patch e completare il collaudo
con gli incrementi Smart non committati. Non sostituire l'app Smart corrente con questo APK pensando
che includa automaticamente quelle modifiche.

## Esito finale verificato

TS-MIG.1: PASS implementazione e verifiche locali; collaudo sul campo pendente.

- `./gradlew test assembleDebug`: BUILD SUCCESSFUL.
- 95 test debug superati, zero failure/error/skipped; inclusi i quattro nuovi
  guardrail architetturali. Nessun test strumentale/emulatore eseguito.
- `git diff --check`: PASS.
- File House AI, GitHub, MqttManager, EnergyRepository, SettingsManager,
  CameraStreamComponent e DiagnosisScreen: nessun diff rispetto alla base.
- Impronta aggregata SHA256 di percorsi/contenuti tracked e untracked non ignorati
  del worktree Smart identica prima/dopo:
  `e14d03ca8b9f74fce63ab6e5cc6f45f385a2e4135948b0b43e343bd65c968800`.
- Il contesto originale è ancora su `feature/ai-home-assistant`; nessun checkout,
  stash, reset, commit o push. La modifica è soltanto nel worktree TS-MIG.1.

Stato del worktree TS-MIG.1 al termine della verifica, prima del commit autorizzato:

```text
 M app/src/main/java/com/domopi/app/MainActivity.kt
 M app/src/main/java/com/domopi/app/ui/screens/CamerasScreen.kt
 M app/src/main/java/com/domopi/app/ui/screens/MainDashboard.kt
?? app/src/test/java/com/domopi/app/data/CanonicalLanRoutingSourceTest.kt
?? docs/NETWORK/
```
