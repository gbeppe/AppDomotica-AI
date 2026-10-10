# Node-RED .20: TLS Tailscale e rinnovo automatico

Installazione eseguita il 22 settembre 2026. Editor/API:
`https://domopi.tailf30ba8.ts.net:1880`. Il client deve poter raggiungere il nodo
via Tailscale. Usare il nome DNS: il certificato non copre 192.168.1.20.
Username/password Node-RED rimangono richiesti. Nessuna modifica alla porta 443
Tailscale Serve che pubblica House AI, né alle configurazioni ASUS/QNAP.
La sola sostituzione del certificato non modifica bind, firewall o port forwarding.

## Ragionamento

Il vecchio certificato Let's Encrypt per zararesidenza.ddns.net era scaduto dal
24 novembre 2024; quel nome non viene più usato per Node-RED secondo l'utente.
Il nodo ha già il nome Tailscale e HTTPS abilitato. `tailscale cert` ha fornito
un certificato valido dal 13 settembre al 12 dicembre 2026, riutilizzando quello
valido disponibile. Nessun certificato o chiave privata è incluso nel repository.

Node-RED 5.0.4 supporta https come funzione e httpsRefreshInterval; verificato
nel red.js installato, che ricarica il contesto TLS con setSecureContext.
Le due proprietà https statiche duplicate in settings.js sono state sostituite
con una funzione unica e intervallo di un'ora. Un riavvio iniziale è necessario;
i successivi rinnovi non riavviano il runtime.

## Installazione e replica

Prerequisiti: host registrato in Tailscale, HTTPS abilitato per il suo nome,
Node-RED con supporto refresh TLS, utente di servizio pi. Per un altro host
adattare DOMAIN e gruppo nello script, percorsi e target CLI. Non copiare le chiavi
private da questo host. Non alterare le regole Serve del servizio Smart.

File versionati: renew_certificate.py, nodered-tls-renew.service e relativo timer.
Sul server lo script è installato root-owned a `/usr/local/sbin/nodered-tls-renew`,
modo 0755; unità in `/etc/systemd/system/`, modo 0644.
Prima di installare, salvare flow completo, settings.js e certificati precedenti
in una directory privata. Eseguire il rinnovatore una prima volta, poi abilitare
il timer con systemctl daemon-reload e enable --now nodered-tls-renew.timer.

Il timer gira ogni giorno con ritardo casuale fino a un'ora. Lo script invoca
`tailscale cert` senza stampare segreti, verifica hostname, validità residua
almeno sette giorni e corrispondenza chiave/certificato. Le generazioni sono in
`/etc/nodered/tls/generation-<hash>`; permessi directory 0750 root:pi, file 0640.
Il link current viene sostituito atomicamente. In caso di errore resta il
certificato corrente e systemd registra il fallimento; non è configurato un
avviso esterno. Le vecchie generazioni restano per rollback.

Configurazione Node-RED, sostituendo le sole proprietà https esistenti:

```javascript
https: function () {
    const fs = require("fs");
    const dir = fs.realpathSync("/etc/nodered/tls/current");
    return {key: fs.readFileSync(dir + "/key.pem"),
            cert: fs.readFileSync(dir + "/cert.pem")};
},
httpsRefreshInterval: 1,
```

Il realpath unico mantiene coerenti chiave e certificato durante un rinnovo.
Verificare settings con node --check prima dell'unico riavvio iniziale. Il riavvio
interrompe temporaneamente i flow e riesegue le logiche di startup preesistenti.

## Evidenze del deploy

Backup completo disponibile SOLO su .20:
`/home/pi/AI_backups/nodered/20260922T111938Z-before-tls`.
Contiene settings.js, ssl e flow/credenziali cifrate presenti nel progetto.
Flow SHA256 prima e dopo identico:
`5924bd1ee9b7b053916ad6b950b432884a0fd2ee45911ad2146cb839a94a3880`.

Stop ore 13:20:00 CEST; flow avviati 13:20:12; entrambi i broker riconnessi
13:20:13. HTTPS verificato senza bypass TLS: /flows restituisce 401 (login
richiesto). Anche House AI su 443 restituisce 401 con TLS valido; ciò verifica
il trasporto e la protezione, non una query applicativa autenticata.
Rinnovatore rieseguito con successo: certificate already current. Timer abilitato.
Il caricamento periodico del certificato nuovo non è ancora stato osservato
su una futura rotazione reale. Errore Alexa Account not initialised presente
anche prima del riavvio (09:06), non modificato in questo intervento.

## Accesso API per il deploy successivo

Il target CLI dell'utente pi è già impostato al nuovo URL. L'utente esegue
`node-red admin login` e inserisce le credenziali nel terminale. Non inviare token
o password in chat. Proteggere ~/.node-red/.cli-config.json con chmod 600.
Il deploy libreria/lettura resta da effettuare dopo login, previa lettura runtime
con revisione API e nuovo backup. Il rinnovo TLS non ha distribuito quella patch.

## Rollback

Ripristinare settings.js dal backup sopra e riavviare Node-RED; i vecchi file ssl
non sono stati sovrascritti. Disabilitare il solo timer nodered-tls-renew.timer.
Questo ripristina però il certificato SCADUTO: non è una soluzione TLS valida.
Per annullare soltanto un rinnovo futuro, riportare atomicamente current alla
precedente generazione ancora valida e attendere il refresh; nessun flow cambia.
Non usare tailscale serve reset e non cancellare context o configurazioni MQTT.
