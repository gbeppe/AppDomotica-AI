# ALEXA-HUE.1 — `.20`: Alexa stabile (luci, scene, caminetto, VMC, skimmer)

**Problema:** Alexa perdeva i dispositivi dell'hub `amazon-echo-hub` (Node-RED, porta 8980): bisognava
riscoprirli e cancellare a mano i doppioni. Gli Echo recenti parlano con un ponte Hue emulato **solo sulla
porta 80**, qualunque porta annunci l'hub; il vecchio reindirizzamento iptables 80 → 8980 non c'era più
(`/etc/iptables/rules.v4` riscritto il 10/10) e sulla porta 80 Apache rispondeva 404 alle richieste Hue.

**Correzione (25/10/2026, decisione dell'utente):**
1. **Apache** sulla porta 80 passa all'hub solo `/api` e `/description.xml`
   ([`domopi-alexa-hue.conf`](domopi-alexa-hue.conf) in `/etc/apache2/conf-available/`, moduli `proxy` e
   `proxy_http`). Nessuna regola del firewall: sopravvive ai riavvii e alle modifiche di iptables/Tailscale.
   Pi-hole 6 non usa quegli indirizzi su Apache. Backup: `/home/pi/AI_backups/alexa/20261025T134416Z-before-hue-proxy/`.
   Ritorno indietro: `sudo a2disconf domopi-alexa-hue && sudo systemctl reload apache2`.
   Dal 26/10 `timeout=15 retry=0` sui due `ProxyPass`: durante il backup notturno l'hub rispondeva lento e ogni
   richiesta di Alexa teneva occupato un processo di Apache fino a 300 s (139 processi, 872 MB).
2. **Node-RED** (questa patch, 13 nodi): disattivati 11 dispositivi non usati a voce (pompa piscina,
   condizionatore e temperatura, pompa di calore e temperatura, acqua calda, telecamere 1–5); disattivati la
   scheda «Alexa Speak» e l'account `alexa-remote-account` (annunci già scollegati dal 19/10; chiusa la porta
   3456 del login). Restano 21 dispositivi: luci, scene (luci, televisione, notte), caminetto (acceso,
   livello, reset), ventilazione, luci della piscina, skimmer.

Verifiche: risposte Hue identiche su porta 80 e 8980; comando di prova accettato («success»); dopo il deploy
21 dispositivi sulla porta 80 e porta 3456 chiusa; l'utente ha riscoperto i dispositivi e le luci rispondono.

**Regola per il futuro:** i nodi `amazon-echo-device` **non si cancellano e ricreano** (cambierebbe
l'identificativo e Alexa creerebbe doppioni): si rinominano o si disattivano. Un dispositivo nuovo: nodo nuovo
collegato all'hub, poi «Alexa, scopri i dispositivi».

Generata da `build.py <flows.json>`. Test: `node --test test.js`. Deploy sul `.20`: `deploy.py prepare`, poi
`apply BACKUP` (applicata il 25/10 alle 14:48, backup `/home/pi/AI_backups/nodered/20261025T134755Z-before-alexa-hue1`),
`rollback BACKUP`.
