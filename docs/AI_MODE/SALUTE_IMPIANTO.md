# Salute impianto (fase S) — progetto

Stato: **S1 attiva dal 23/10/2026 10:13** su `.20` e `.15` (`backend/health/`, 14 test). Decisioni
D1–D4 prese il 23/10 (sotto). Manuale utente: [`../MANUALE_SALUTE_IMPIANTO.md`](../MANUALE_SALUTE_IMPIANTO.md).

## Perché

Il `.20` e il `.15` non sono più «host di comodo»: se uno dei due si ferma smettono di funzionare
app, AI Smart, regole, avvisi e in parte riscaldamento e luci. Nei problemi di questi giorni se ne
è accorto l'utente, per caso, e la causa si è trovata indagando a mano:

| Data | Problema | Come si è visto | Rimedio |
|---|---|---|---|
| 14/10 e 21/10 | `tailscaled` sul `.20` offline dopo il riavvio del router | app senza casa fuori dal Wi‑Fi | riavvio di `tailscaled` |
| 17/10 15:35 → 19/10 | cadute di tensione sul `.20` (492 eventi, ~13/h); il 18/10 23:15 ha riavviato il `.20` all'inizio di un deploy | trovato per caso nei log | alimentatore nuovo, adattatore OTGW scollegato e ricollegato |
| 19/10 | `persistence false` su Mosquitto `.20` (retained persi a ogni riavvio) | trovato il 21/10 | `persistence true`, vecchio `mosquitto.db` spostato nel backup |
| 21/10 mattina | Mosquitto `.20` fermo all'avvio: listener 1884 legato a `192.168.1.20` prima del DHCP, systemd si arrende | app senza broker | drop-in `wait-network.conf` (network-online, riprova senza limite) |
| 21/10 mattina | `.15` irraggiungibile dal PC in Wi‑Fi (ARP incompleto), raggiungibile dal `.20` via cavo | app e SSH dal PC | riavvio del router ASUS |

## Principi

1. **Il controllo non dipende da ciò che controlla.** Non gira in Node-RED né in House AI, che sono
   tra le cose da controllare: script leggero con timer systemd, su **entrambi** i Raspberry.
2. **Controllo reciproco:** il `.20` controlla il `.15` e viceversa; se uno tace, l'altro avvisa.
3. **Leggero:** il `.20` ha poca memoria e la SD va risparmiata (lezione di Grafana del 15/10);
   il `.15` ha ~1 GB libero. Niente database: un file di stato piccolo, log ruotato, solo le
   transizioni (ok → guasto → ok) generano scritture e avvisi.
4. **Solo letture**, salvo le riparazioni della lista «automatiche» sotto.
5. **Mai** OpenSprinkler, mai segreti nei messaggi o nei log (regole 4 e 5 di `CLAUDE.md`);
   credenziali lette dai file già esistenti (`~/.config/domopi/…`).
6. **Un avviso per guasto**, con causa probabile e cosa fare; promemoria al più ogni 6 ore; avviso
   di ritorno alla normalità.

## Cosa controlla

Ogni 2 minuti (le cose costose ogni 15).

| Controllo | Dove | Guasto se | Problema di questi giorni |
|---|---|---|---|
| alimentazione: `vcgencmd get_throttled`, eventi di sottotensione nel kernel | `.20`, `.15` | sottotensione in corso, o > 3 eventi in un'ora | alimentatore del `.20` |
| servizi systemd: `mosquitto`, `nodered`, `house-ai-*`, `ntfy`, `pihole-FTL`, `tailscaled`, `apache2` (`.20`); `mosquitto`, `nodered`, `emoncms*` (`.15`) | ciascuno i propri | non `active` | Mosquitto fermo all'avvio |
| broker: collegamento con le credenziali, ricezione di un retained essenziale (es. profilo di riscaldamento sul `.20`, modalità caminetto sul `.15`) | ciascuno i propri | non si collega, o retained assente | broker fermo, `persistence false` |
| configurazione Mosquitto: `persistence true`, `allow_anonymous` come deciso | ciascuno i propri | diversa dall'atteso | `persistence false` del 19/10 |
| Tailscale: `tailscale status` online, Serve attivo | `.20` | offline > 5 minuti | 14/10 e 21/10 |
| Node-RED: risponde l'interfaccia, i flussi girano (età dell'ultimo messaggio di un tab noto) | ciascuno i propri | fermo o muto > 10 min | — |
| House AI: `GET` di stato su `127.0.0.1:8765` | `.20` | non risponde | — |
| raggiungibilità: l'altro Raspberry (ping + porta 1883), router `192.168.1.1`, Internet (DNS e HTTPS) | `.20`, `.15` | 3 prove fallite di fila | `.15` nascosto dal Wi‑Fi (vedi il controllo dal telefono) |
| schede ESP (caminetto `.22`, pompa `.21`), termostati, sensori | — | già coperti da HOUSE-ALERTS.1 | — |
| dati energia: ultimo valore dei feed Powerwall (305) ed emonTx (24) in EmonCMS (redis) | `.15` | più vecchio di 10 min | 23/10: dati che sembravano fermi |
| emonTx al contrario: pinza CT1 e rete Powerwall di segno opposto (sopra 150 W) per 10 letture | `.15` | segno opposto | 19–23/10: adattatore di tensione girato |
| disco (`/` < 500 MB), memoria e swap, temperatura CPU > 75 °C, errori di I/O della SD nel kernel | `.20`, `.15` | soglia superata | `.15` con 110 MB liberi il 16/10 |
| orologio sincronizzato (le regole `at` dipendono dall'ora) | `.20`, `.15` | non sincronizzato | — |

## Riparazioni

**Automatiche, sempre con notifica** («ho riavviato X alle HH:MM»), al più 3 tentativi in un'ora,
poi solo avviso:

- `tailscaled` offline da più di 5 minuti → riavvio (due volte su due ha risolto);
- servizio sopra fermo → `systemctl restart` (Mosquitto ha già il drop-in che riprova da solo);
- nessun'altra azione automatica.

**Mai automatiche, consiglio pronto nella notifica:**

| Situazione | Consiglio |
|---|---|
| un Raspberry raggiungibile dall'altro ma non dal telefono in Wi‑Fi | «Riavvia il router ASUS» (caso del 21/10) |
| Tailscale ancora offline dopo i riavvii, e di recente il router si è riavviato | «Riavvia il router, poi controllo Tailscale» |
| sottotensione | «Controlla l'alimentatore e il cavo del `.20`; scollega gli USB non essenziali» |
| disco quasi pieno | elenco delle cartelle più grandi e cosa si può pulire |
| errori della SD | «Fai subito l'immagine di backup; valuta il passaggio a SSD» |
| configurazione Mosquitto cambiata | «La configurazione non è quella attesa: `persistence`…» (nessun ripristino automatico: può essere voluto) |
| un Raspberry non risponde affatto | «Controlla alimentazione e cavo di rete del `.15`; se serve, togli e ridai corrente» |

## Chi avvisa se cade il `.20`

Il server ntfy **e** il subnet router Tailscale sono sul `.20`: se cade, gli avvisi non partono e da
fuori casa il telefono non raggiunge nemmeno il `.15`. Rimedi possibili (da decidere, D2):

- **a.** l'app se ne accorge da sola (vedi sotto) e lo dice: funziona sempre, ma solo quando la si apre;
- **b.** Tailscale anche sul `.15`, come **secondo subnet router** (Tailscale passa da solo all'altro
  se uno cade) e secondo ntfy sul `.15` per gli avvisi «il `.20` non risponde»;
- **c.** canale esterno (ntfy.sh pubblico con argomento lungo e casuale, o Telegram): esce su
  Internet, contenuto minimo («il `.20` non risponde da 10 minuti»).

Consiglio: **a + b**; c solo se l'utente lo preferisce.

## Nell'app (S3)

- Pagina **«Salute impianto»** (dal menu e dalla barra LOCALE/REMOTO): semafori per `.20`, `.15`,
  router, Tailscale, broker e alimentazione, con l'ora dell'ultimo controllo, la causa probabile e
  il consiglio. Legge lo stato retained pubblicato dai due controlli
  (`zara/system/health/<host>/stat`, solo dati non sensibili).
- **Controllo dal Wi‑Fi del telefono:** l'app prova a raggiungere `.20` e `.15` (porta 1883 e
  Node-RED). Se i Raspberry dicono di vedersi ma il telefono non ne vede uno → «Riavvia il router
  ASUS». È l'unico modo di vedere il caso del 21/10, che dai Raspberry non si nota.
- Se l'app non trova più la casa, lo mostra con i controlli da fare invece di valori vuoti.
- AI Smart: strumento in sola lettura `system_health` («come sta l'impianto?», «perché l'app non si
  collega?»), con aggiornamento di `GUIDA_UTENTE_AI_SMART.md`.

## Rinforzi hardware (dell'utente, consigliati)

- alimentatore dedicato per ogni Pi (fatto per il `.20` il 19/10); niente caricatori multiporta;
- **UPS piccolo** per router, `.20` e `.15` (gli spegnimenti bruschi sono il rischio maggiore per le SD);
- **immagine di backup periodica** di ciascun Raspberry; poi valutare l'avvio da SSD.

## Fasi

| Fase | Contenuto | Stato |
|---|---|---|
| **S1** | script di controllo con timer systemd sul `.20` e sul `.15`, controllo reciproco, avvisi ntfy su `domopi-casa` con consiglio, stato retained `zara/system/health/<host>/stat`; prima settimana **solo osservazione** (avvisi a una soglia larga, per tarare) | da fare dopo T2 |
| **S2** | riparazioni automatiche della lista sopra; Tailscale e ntfy di riserva sul `.15` se scelto in D2 | da fare |
| **S3** | pagina «Salute impianto» nell'app con il controllo dal Wi‑Fi; strumento AI Smart `system_health` | pagina scritta il 23/10 (cuore nella barra in alto, controllo dal Wi‑Fi all'apertura e con «Ricontrolla», decisioni dell'utente); `system_health` da fare |
| S4 | (opzione) immagine di backup automatica, storico dei guasti | da valutare |

File previsti: `backend/health/` (script, unità systemd, `README.md`, `deploy.sh` con backup e
rollback come per le patch Node-RED). Ogni deploy sugli host con conferma dell'utente.

## Decisioni (prese con l'utente il 23/10/2026)

- **D1:** controlli ogni 2 minuti (pesanti ogni 15); dalle 23 alle 7 solo gli avvisi urgenti.
- **D2:** a + b: l'app lo segnala quando la si apre, e Tailscale + ntfy di riserva sul `.15` (S2).
- **D3:** riparazioni automatiche per `tailscaled` e servizi fermi (S2, dopo la settimana di osservazione).
- **D4:** `allow_anonymous true` atteso finché non si fa il punto C; poi `false`.

## Avanzamento

| Data | Cosa |
|---|---|
| 21/10/2026 | Progetto scritto dopo gli incidenti del 17–21/10; da realizzare dopo il deploy di T2 (VMC) |
| 23/10/2026 | D1–D4 decise; S1 scritta (`backend/health/`), 14 test; prova a secco: `.20` 25/25 verdi, `.15` 24/25 (rosso: `persistence false` sul Mosquitto del `.15`, perde i retained a ogni riavvio; da decidere se correggerlo) |
| 23/10/2026 10:13 | S1 installata (`deploy.sh`, backup in `/home/pi/AI_backups/health/`): `.20` 25/25, `.15` un avviso inviato (`persistence false`) |
| 23/10/2026 | S3: pagina dell'app (`SystemHealth.kt`, `SystemHealthScreen.kt`, iscrizione a `zara/system/health/+/stat`, 3 test); semaforo grigio dopo 6 minuti senza aggiornamenti; consiglio «riavvia il router ASUS» dal controllo del telefono |
| 23/10/2026 10:16 | `.15`: `persistence true` (era false da almeno il 2020: `mosquitto.db` fermo al 19/08/2020); backup `/home/pi/AI_backups/mosquitto/20261023T081633Z-before-persistence-true`; retained di prova riuscito |
