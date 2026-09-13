# Diagnostica dello stack AI Smart

## Obiettivo e scelta

La barra superiore della modalità Smart mostra un'icona a forma di collegamento
con lo stato dello stack necessario alle interrogazioni. La diagnostica misura il
percorso realmente usato dall'app e non prova porte arbitrarie dai dispositivi
Android.

Il controllo è diviso fra i due punti che possiedono le evidenze:

- Android verifica rete, HTTPS/certificato del dominio Tailscale e connessione al
  Digital Twin MQTT;
- il backend `.20` verifica il proprio processo, il gateway loopback, lo stato
  dell'ultima richiesta Groq, EmonCMS `.15` e i quattro log Node-RED allowlisted.

Groq non viene contattato periodicamente: consumerebbe quota e un semplice socket
TCP non proverebbe API key, modello o Structured Outputs. Dopo un riavvio è
`unknown`; diventa verde o rosso dopo una richiesta reale al provider.

## Stati e interfaccia

- verde: tutti i componenti sono operativi;
- giallo: stack utilizzabile ma con un componente degradato o non ancora provato;
- rosso: percorso TLS, backend o gateway bloccano il servizio;
- grigio: controllo non eseguito o in corso.

Premendo l'icona si apre una scheda con un pallino per: percorso TLS
privato/Tailscale, backend `.20`, Digital Twin MQTT, gateway `.20`, Groq,
EmonCMS `.15` e log Node-RED `.20`. La dicitura “percorso TLS privato/Tailscale”
evita di affermare che Android abbia identificato l'app VPN: il successo HTTPS
prova il percorso necessario, incluso DNS e certificato.

La scheda offre aggiornamento manuale e un log in memoria limitato a 50 eventi.
Il log conserva endpoint applicativo, esito e latenza; non contiene domande,
risposte, token, chiavi o valori domestici.

## Ciclo di vita e traffico

Il controllo parte dopo l'inserimento del token, si ripete ogni 60 secondi mentre
la schermata Smart è nello stato lifecycle `STARTED` e viene ripetuto dopo una
domanda. Uscendo verso la dashboard, Compose cancella il ciclo e distrugge manager
e log della sessione. In modalità dashboard non vengono eseguiti controlli né
raccolti log House AI.

## Contratto backend

`GET /v1/health/details` richiede lo stesso Bearer token dell'app e restituisce
`house_ai.stack_health.v1`. La risposta contiene solo identificatore, etichetta,
stato, dettaglio sanitizzato, eventuale latenza e timestamp. Il gateway espone
`GET /v1/health` esclusivamente su loopback e con token interno; riporta soltanto
lo stato dell'ultima comunicazione Groq.

Entrambi gli endpoint sono read-only. Non pubblicano MQTT, non leggono valori
energetici, non inviano prompt di prova e non espongono configurazioni o segreti.

## Installazione e replica

Non servono nuovi pacchetti, porte o credenziali. Backend e gateway continuano a
usare Python 3.9 standard library e le unità systemd esistenti. Per una replica:

1. distribuire insieme `server.py`, `planner.py` e `model_gateway.py`;
2. mantenere gateway su loopback e backend dietro Tailscale Serve TLS;
3. conservare gli environment e i secret file descritti in
   [`GATEWAY_MODELLO.md`](GATEWAY_MODELLO.md);
4. eseguire test backend e Android, attivare la release, riavviare entrambi i
   servizi e interrogare `/v1/health/details` attraverso il dominio TLS.

Rollback: ripuntare `/opt/domopi-house-ai/current` alla release precedente e
riavviare gateway e backend. L'app mostrerà rosso perché il vecchio backend non
possiede l'endpoint dettagliato; le normali domande restano compatibili.
