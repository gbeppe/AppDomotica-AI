# HOUSE-ALERTS.1 — avvisi della casa sul telefono

Nuovo tab «Avvisi (DomoPi)» sul `.20`: avvisi nell'app ntfy, canale **`domopi-casa`**
([`backend/ntfy`](../../ntfy/README.md)). Avvisi scelti dall'utente il 17/10/2026:

| Avviso | Sorgente | Scatta | Rientro |
|---|---|---|---|
| Puffer alto troppo caldo | `zara/interface/heating/puffer/top_temperature/stat` | sopra 85 °C (priorità alta) | sotto 80 °C |
| Scheda del caminetto / della pompa pavimento scollegata | `/esp8266/{palazzetti,floorPump}/online` (broker `.15`, last will) | `offline` da 5 minuti (priorità alta) | `online` |
| Sensore del soggiorno / del bagno senza dati | `emon/emonth5/temperature_calibrated`, `emon/bagnoServizio/temperature` | nessun valore da 30 minuti | primo valore |
| Temperatura esterna mancante | `emon/weather/extTemp` | nessun valore da 2 ore | primo valore |
| Profilo di riscaldamento cambiato | `zara/interface/heating/profile/stat` | ogni cambio di profilo (informativo) | — |
| Batteria quasi scarica | `zara/interface/energy/battery/soc/stat` | sotto 20 % | sopra 25 % |

- Ogni avviso parte una volta al passaggio; lo stesso avviso non si ripete prima di 2 ore. Il
  messaggio di rientro arriva solo se l'avviso era stato inviato.
- Stato nel contesto `file` di Node-RED: dopo un riavvio niente avvisi ripetuti; il «senza dati»
  conta dall'ultimo valore vero.
- Token di pubblicazione letto da `/home/pi/.config/domopi/ntfy_nodered.token` all'avvio e ogni ora.
- Nessun nodo esistente modificato; sorgenti in sola lettura.

```sh
python3 build.py <flows-export.json> && node --test test.js
python3 deploy.py prepare | apply BACKUP | rollback BACKUP
```
