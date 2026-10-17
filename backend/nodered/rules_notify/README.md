# RULES-ENGINE.2 — notifiche delle regole sul telefono

Aggiunge al tab «Regole (DomoPi)» del `.20` l'invio di ogni evento del motore
(`intervenuta`, `in prova`, `bloccata`, `creata`, `attiva`, `cancellata`, `rifiutata`) al server
ntfy locale ([`backend/ntfy`](../../ntfy/README.md), NTFY.1), canale `domopi-regole`.

- Unico nodo modificato: il motore (`RULES-ENGINE.1/logic`), un filo in più sull'uscita eventi.
- Nuovi nodi: `notifica ntfy` ([`ntfy.js`](ntfy.js)) → `http request` a `127.0.0.1:2586` →
  `esito ntfy` (avviso se non è 200); `carica token ntfy` all'avvio e ogni ora da
  `/home/pi/.config/domopi/ntfy_nodered.token` (0600, mai nel flow né nel repository).
- Priorità: intervenuta e bloccata 4 (alta), in prova / creata / attiva / rifiutata 3, cancellata 2.

```sh
python3 build.py <flows-export.json> && node --test test.js
python3 deploy.py prepare | apply BACKUP | rollback BACKUP
```
