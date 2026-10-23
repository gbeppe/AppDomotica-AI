# Manuale utente — Salute impianto (`.20` e `.15`)

Aggiornato al **23/10/2026** · fase **S1** (controlli e avvisi, nessuna riparazione automatica).
Progetto e decisioni: [`AI_MODE/SALUTE_IMPIANTO.md`](AI_MODE/SALUTE_IMPIANTO.md) · parte tecnica:
[`../backend/health/README.md`](../backend/health/README.md).

## 1. A cosa serve

I due Raspberry tengono in piedi la casa. Il `.20` ha app, AI Smart, regole, Node-RED principale e
avvisi; il `.15` ha EmonCMS, energia, caminetto e pompa del pavimento. Se uno dei due ha un problema,
prima te ne accorgevi per caso. Ora **ogni 2 minuti** ciascun Raspberry controlla sé stesso e l'altro e,
se qualcosa non va, ti manda una notifica sul telefono con **cosa è successo e cosa fare**.

Il controllo è indipendente da Node-RED e da AI Smart, quindi funziona anche quando sono loro a fermarsi.
Fa **solo letture**: non cambia niente, non riavvia niente e non tocca mai l'irrigazione.

## 2. Dove arrivano gli avvisi

Nell'app **ntfy** del telefono, canale **`domopi-casa`**, lo stesso degli altri avvisi della casa. Il
titolo dice quale Raspberry e quale controllo:

| Esempio di titolo | Significato |
|---|---|
| `.15: Configurazione Mosquitto` | un guasto nuovo |
| `.20: Tailscale (ancora)` | promemoria: il guasto c'è ancora dopo 6 ore |
| `.15: Configurazione Mosquitto di nuovo a posto` | il guasto è risolto |

Nel testo trovi il dettaglio (per esempio «persistence = false (atteso true)») e la riga **«Cosa fare:»**.

Le regole degli avvisi:

- **un avviso per guasto**, non uno ogni 2 minuti;
- **promemoria ogni 6 ore** se il guasto resta;
- **messaggio di ritorno alla normalità** quando si risolve;
- **di notte, dalle 23 alle 7, solo gli urgenti** (vedi la tabella sotto). Gli altri arrivano dopo le 7,
  se il guasto c'è ancora;
- alcuni controlli aspettano **più prove fallite di fila** prima di avvisare, per non segnalare un
  intoppo di pochi secondi: l'altro Raspberry, il router, Internet e Tailscale 3 volte (circa 6 minuti);
  broker, pagine web e dati energia 2 volte.

## 3. Cosa viene controllato e cosa fare

Priorità: **U** = urgente, arriva anche di notte con priorità alta; **N** = normale.

| Avviso | Dove | Priorità | Cosa significa | Cosa fare |
|---|---|---|---|---|
| **Alimentazione** | entrambi | U se la sottotensione è in corso, altrimenti N | l'alimentatore non dà abbastanza corrente (più di 3 cali in un'ora, o un calo adesso) | controlla alimentatore e cavo; scollega gli USB non essenziali. Fu la causa dei riavvii del `.20` del 17–19/10 |
| **Servizio …** (mosquitto, nodered, house-ai-…, ntfy, pihole-FTL, tailscaled, apache2; sul `.15` emonhub, emoncms_mqtt, feedwriter, redis, mariadb) | ciascuno i suoi | U per mosquitto e nodered (e emonhub sul `.15`), altrimenti N | il servizio è fermo | `sudo systemctl restart <servizio>`, poi guarda il log (`journalctl -u <servizio> -n 50`). Se non sai, chiedi all'agente |
| **Broker MQTT** | entrambi | U | il broker non risponde o, sul `.20`, ha perso i messaggi salvati (retained) | controlla Mosquitto (`systemctl status mosquitto`); se mancano i retained, controlla `persistence` |
| **Configurazione Mosquitto** | entrambi | N | `persistence` o `allow_anonymous` diversi dall'atteso | verifica se il cambio è voluto. Non viene ripristinato da solo. Oggi l'atteso è `persistence true` e `allow_anonymous false` su entrambi (dal 23/10) |
| **House AI**, **Node-RED**, **EmonCMS** | dove girano | N | la pagina o il servizio non risponde | controlla il servizio e il suo log |
| **Tailscale** | `.20` | N | il `.20` non è collegato alla rete Tailscale: fuori casa l'app non raggiunge la casa | `sudo systemctl restart tailscaled`; se non basta, riavvia il router e poi ricontrolla (successo il 14/10 e il 21/10) |
| **Raspberry .15 / .20** | l'altro | U | l'altro Raspberry non risponde | controlla alimentazione e cavo di rete; se serve, togli e ridai corrente |
| **Router** | entrambi | U | il router non risponde | controlla il router ASUS |
| **Internet** | entrambi | N | niente Internet | controlla router e linea |
| **Dati Powerwall / Dati emonTx** | `.15` | N | EmonCMS non riceve dati da più di 10 minuti | controlla il dispositivo (gateway Tesla, emonTx), emonhub e la rete |
| **emonTx al contrario** | `.15` | N | la pinza della rete dell'emonTx misura con il segno opposto al Powerwall per 10 letture di fila (circa 20 minuti con potenza sopra 150 W) | l'adattatore di tensione dell'emonTx è stato girato o cambiato: **gira la spina di 180°** (successo dal 19 al 23/10) |
| **Disco** | entrambi | N | meno di 500 MB liberi | fai spazio: `sudo du -xh / --max-depth=2 \| sort -h \| tail`. Il `.15` ha un disco piccolo (~900 MB liberi) |
| **Memoria** | entrambi | N | meno di 100 MB di memoria disponibile | guarda i processi con `top` |
| **Temperatura CPU** | entrambi | N | oltre 75 °C | controlla ventilazione e posizione |
| **Scheda SD** | entrambi | U | errori della scheda di memoria nel kernel | **fai subito l'immagine di backup** della scheda; valuta il passaggio a SSD |
| **Orologio** | entrambi | N | l'ora non è sincronizzata (le regole a orario sbaglierebbero) | controlla `timedatectl` e la rete |
| **Controllo …** | entrambi | N | il controllo stesso ha avuto un errore | guarda `journalctl -u domopi-health -n 20` o chiedi all'agente |

## 4. Vedere lo stato quando vuoi

**Nell'app** (dalla build successiva a `bld_a9748986`): il **cuore** nella barra in alto, accanto
all'icona della build, è verde se `.20` e `.15` sono a posto, rosso se c'è un guasto, grigio se uno dei
due non manda aggiornamenti da più di 6 minuti. Toccandolo si apre la pagina **«Salute impianto»**:

- una scheda per Raspberry con il semaforo, l'ora dell'ultimo controllo, i guasti con il consiglio
  («Cosa fare») e, a richiesta, tutti i controlli;
- in casa (LOCALE), **«Dal Wi-Fi del telefono»**: il telefono prova a raggiungere broker e Node-RED di
  `.20` e `.15` e il router, appena apri la pagina e con «Ricontrolla». Se un Raspberry non risponde al
  telefono ma l'altro lo vede, consiglia **«riavvia il router ASUS»** (il caso del 21/10);
- fuori casa senza collegamento la pagina lo dice e mostra gli ultimi stati ricevuti.

Il riepilogo che l'app legge è pubblicato a ogni giro sul broker del `.20`:

- `zara/system/health/20/stat` e `zara/system/health/15/stat`, JSON con `ok` generale e l'elenco dei
  controlli (nome, esito, dettaglio, consiglio). Si legge anche con MQTT Explorer.

Dal terminale del Raspberry interessato:

```sh
systemctl list-timers domopi-health.timer      # quando è stato l'ultimo giro e quando il prossimo
journalctl -u domopi-health -n 20              # avvisi inviati negli ultimi giri ("fault", "recovered")
sudo cat /var/lib/domopi-health/state.json     # stato completo dell'ultimo giro
```

Nel journal compare una riga solo quando parte un avviso, un promemoria o un ritorno alla normalità.
Un giro senza novità non scrive niente, per non consumare la scheda SD.

## 5. Mettere in pausa, riprendere, togliere

```sh
sudo systemctl stop domopi-health.timer        # pausa (riprende al riavvio del Raspberry)
sudo systemctl disable --now domopi-health.timer   # pausa anche dopo i riavvii
sudo systemctl enable --now domopi-health.timer    # riprendi
cd ~/AI_patches/health && sudo sh deploy.sh 20 rollback   # togli tutto dal .20 (15 per il .15)
```

Durante un lavoro programmato (per esempio un riavvio voluto del `.15`) puoi mettere in pausa il
timer sull'**altro** Raspberry, oppure ignorare l'avviso: quando torna tutto a posto arriva il messaggio
di ritorno alla normalità.

## 6. Cambiare soglie e servizi

La configurazione di ogni Raspberry è in `/etc/domopi-health/config.json` (nel repository:
`backend/health/health-20.json` e `health-15.json`). Le voci principali:

| Voce | A cosa serve | Valore attuale |
|---|---|---|
| `services`, `urgent_services` | servizi da controllare e quali sono urgenti | vedi tabella sopra |
| `quiet_hours` | ore di silenzio per gli avvisi non urgenti | `[23, 7]` |
| `confirm` | prove fallite di fila prima di avvisare, per controllo | altro Pi, router, Internet, Tailscale 3; broker, web, energia 2 |
| `mosquitto.expect` | configurazione attesa del broker | `persistence true`, `allow_anonymous false` |
| `emoncms_feeds` | feed energia da sorvegliare e età massima | Powerwall 305 ed emonTx 24, 600 s |
| `emontx_sign` | soglia di potenza e letture per «emonTx al contrario» | 150 W, 10 letture |

Le modifiche si fanno nel repository e si installano con `deploy.sh`, come ogni cambiamento: le prepara
l'agente e le conferma l'utente.

## 7. Domande frequenti

- **Ricevo un avviso ma tutto sembra funzionare.** Può essere un guasto ancora in fase iniziale (per
  esempio un servizio secondario fermo) o una soglia da tarare: la prima settimana serve a questo.
  Segnalalo e la soglia si corregge.
- **Se cade il `.20`, chi mi avvisa?** Il `.15` se ne accorge, ma oggi manda gli avvisi tramite il
  server ntfy che sta proprio sul `.20`. La riserva sul `.15` (Tailscale e ntfy di riserva) arriva con
  la fase S2. Fino ad allora te ne accorgi dall'app che non si collega.
- **Riavvia i servizi da solo?** Non ancora. Dopo la settimana di osservazione (S2) riavvierà da solo
  `tailscaled` e i servizi fermi, al massimo 3 volte in un'ora, avvisandoti ogni volta.
- **Usa credenziali?** Sì, il token ntfy di Node-RED e l'utente MQTT `domotica`, copiati in
  `/etc/domopi-health/` e leggibili solo dall'amministratore. Non compaiono mai negli avvisi né nel
  riepilogo.

## 8. Storico

| Data | Cosa |
|---|---|
| 23/10/2026 10:13 | S1 attiva su `.20` e `.15`. Primo avviso: `persistence false` sul Mosquitto del `.15` (così da almeno il 2020); corretto alle 10:16 con `persistence true` |
| 23/10/2026 | Pagina «Salute impianto» e cuore nella barra in alto dell'app (fase S3), dalla build successiva a `bld_a9748986` |
| 23/10/2026 10:23 e 10:34 | Broker `.20` e `.15` senza accesso anonimo: atteso `allow_anonymous false`; sul `.15` il controllo usa l'utente `emonpi`. Nel log del `.15` compare un «Socket error» a ogni giro del `.20`: è il suo test di raggiungibilità della porta 1883, non un dispositivo rifiutato |
