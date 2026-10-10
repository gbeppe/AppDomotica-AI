# UI-TOPBAR.D1 — Indicatori della topbar (LOCALE/REMOTO e Build)

Data: 10 ottobre 2026. Branch `feature/ui-topbar` (da `feature/ai-home-assistant`,
dopo il merge di TS-MIG.1). Porting dei meccanismi WallBox UI-TOPBAR.1 e
REL-APP.1 dal repository `~/AndroidStudioProjects/new2/WallBox` (`develop`).

## Decisioni dell'operatore

| Punto | Decisione |
|---|---|
| Subnet router Tailscale | `.20` (corretto anche TS-MIG.1, che indicava `.15`) |
| Dominio Wi-Fi di casa | `casa.zara` predefinito, subnet `192.168.1.0/24`; modificabili in Configurazione |
| Topic identità di build | `domopi/build/android`, retained, broker `.20` (eccezione documentata nel contratto MQTT, sez. 9) |
| Host di release | `.40` (questo PC), come WallBox |

## Indicatore LOCALE/REMOTO

Solo indicatore: gli endpoint restano quelli LAN canonici di TS-MIG.1 e il
percorso lo sceglie Android/Tailscale. Classificatore e osservatore reattivo
delle reti Android sono quelli di WallBox (`com.domopi.app.network`).

| Evidenza Android | Testo | Colore |
|---|---|---|
| Prima classificazione | `…` | grigio |
| Wi-Fi senza VPN con dominio `casa.zara` | LOCALE | verde |
| VPN con route esatta `192.168.1.0/24` | REMOTO | verde |
| Fuori casa, nessuna route VPN verso casa | REMOTO | rosso |
| Nessuna rete IP utile | NON IN RETE | rosso |
| Evidenza incompleta | UNKNOWN | rosso lampeggiante |

Tocco → scheda "Percorso di accesso" con descrizione e valori in uso.

## Icona Build

Sostituisce il controllo GitHub (`GithubVersionChecker`, rimosso). Confronta
l'identità dell'APK (`BuildConfig.GIT_SHA`, `BUILD_DIRTY`, `BUILD_ID`,
`BUILD_TIMESTAMP`) con quella retained su `domopi/build/android`.

| Stato | Icona |
|---|---|
| Identità uguale in tutti i campi | verde |
| Identità diversa (incluse tutte le build di sviluppo `dev_…`) | rossa fissa |
| Broker non raggiungibile dopo 15 s dall'avvio, identità assente (3 s dopo la sottoscrizione) o non valida | rossa lampeggiante |
| Primo caricamento | grigia |

Il lampeggio è lo stesso di UNKNOWN: nella topbar "lampeggiante" significa sempre
"non determinabile". Tocco → scheda "Build App" con APK installato e ultima release.

## Comando di release

Su `.40`, dalla root di un worktree del repository canonico, con sorgenti puliti
e committati:

```sh
python3 tools/release_android.py
```

Verifica repository e sorgenti, compila `assembleDebug` passando l'identità
(`-Pdomopi.release.*`), controlla che l'APK la contenga, salva record e APK
immutabili in `work/android-builds/` (ignorata da Git, con `release.log`),
pubblica l'identità QoS 2 retained e la rilegge dal broker. Non installa nulla.
`python3 tools/publish_android_build.py republish work/android-builds/<id>.json`
ripubblica un record esistente.

Credenziali MQTT fuori dal repository, create dall'operatore (permessi 600):

```text
~/.config/domopi/mqtt.json       {"host": "192.168.1.20", "port": 1883, "username": "<utente>"}
~/.config/domopi/mqtt_password   <password>
```

## Verifiche

- Android: test unitari (classificatore e osservatore portati da WallBox,
  validazione rete di casa, identità e allineamento build, guardie di sorgente).
- Strumenti: `python3 -B -m unittest discover -s tools -p 'test_*.py'`.
- Collaudo sul telefono (operatore): avvio `…` → LOCALE verde a casa; 4G con
  Tailscale → REMOTO verde e servizi raggiungibili; 4G senza VPN → REMOTO rosso
  e Build rossa lampeggiante; dopo la prima release → Build verde.

Predictive Reserve: nessun impatto.
