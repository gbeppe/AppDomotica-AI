# Rimozione del redirect porta 80 → 8980 su .20

Data: 10 ottobre 2026. Decisione dell'operatore: l'emulazione Alexa di Node-RED
(`amazon-echo-device`, hub sulla porta 8980) è obsoleta.

## Problema

`/etc/iptables/rules.v4` su `.20` (luglio 2025) conteneva:

```
-A PREROUTING -i eth0 -p tcp -m tcp --dport 80 -j REDIRECT --to-ports 8980
-A PREROUTING -p tcp -m tcp --dport 80 -j REDIRECT --to-ports 8980
-A PREROUTING -p udp -m udp --dport 80 -j REDIRECT --to-ports 8980
```

Le ultime due regole non limitavano destinazione né interfaccia. `.20` è il subnet
router Tailscale di `192.168.1.0/24`: il traffico di un telefono con Tailscale
verso `192.168.1.15:80` (EmonCMS) veniva dirottato su Node-RED:8980, che rispondeva
`404 Cannot GET` (Express). Effetto: storico energia e riepilogo kWh non
disponibili fuori casa o con Tailscale acceso anche a casa. Dalla LAN e da `.20`
EmonCMS rispondeva 200.

## Modifica

Le tre regole sono state rimosse a caldo e da `rules.v4` (validato con
`iptables-restore --test`). Backup:
`/home/pi/AI_backups/iptables/20261010T162544Z-before-remove-alexa-redirect/`
(`rules.v4` originale e `iptables-save` completo).

Verifiche: dal telefono nel tunnel Tailscale (`dev tun1`)
`http://192.168.1.15/emoncms/` → `200 Apache`; storico energia dell'app caricato;
servizi `nodered`, `house-ai-*`, `mosquitto`, `tailscaled`, `apache2` attivi.
`svc:emoncms` di Tailscale Serve (proxy verso `127.0.0.1:80`) non usa queste regole.

## Rollback

```sh
B=/home/pi/AI_backups/iptables/20261010T162544Z-before-remove-alexa-redirect
sudo cp $B/rules.v4 /etc/iptables/rules.v4 && sudo iptables-restore < /etc/iptables/rules.v4
```

I nodi `amazon-echo` restano nei flow, inutilizzati; da rimuovere con la pulizia
dei nodi scollegati.
