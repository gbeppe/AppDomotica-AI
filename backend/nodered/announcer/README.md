# ANNOUNCER.1 — annunci vocali in ombra

Progetto: [`docs/AI_MODE/ANNUNCI_VOCALI.md`](../../../docs/AI_MODE/ANNUNCI_VOCALI.md). Scheda «Annunci (DomoPi)» con
[`announcer.js`](announcer.js): decide cosa direbbe e scrive solo il registro (contesto `file`, ultimi 1000; retained
`zara/interface/announcer/log/stat`, ultimi 100). **Non parla**: la scheda Alexa Speak resta disattivata.

Riceve stati MQTT, il solare per i kWh del giorno e, con un collegamento nuovo, gli avvisi della funzione «avvisi» di
HOUSE-ALERTS.1, la cui soglia del puffer passa da 85/80 a 60/58 °C (decisione dell'utente).

```sh
python3 build.py <flows-export.json> && TZ=Europe/Rome node --test test.js     # 8 casi
python3 deploy.py prepare | apply BACKUP | rollback BACKUP
```
