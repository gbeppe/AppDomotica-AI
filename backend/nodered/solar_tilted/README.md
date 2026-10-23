# SOLAR-TILTED.1 — `.20`: ramo `Irradiance=` tolto (F2) e previsione sul piano inclinato (F3)

Fasi F2/F3 di [`../../solar/README.md`](../../solar/README.md), approvate dall'utente il 23/10/2026.
Tab «start local scripts».

- **F2**: tolti i 6 nodi del ramo disattivato `Irradiance=` (MQTT `emon/weather/tomorrowSolarIrradiance`,
  funzione, due ritardi, due `exec` di `solarEnergy.php`): non ha mai funzionato. La catena del cielo
  sereno (inject 300 s → Eclipse → 5 s → Viessmann) resta com'è.
- **F3**: nuova funzione «Fotovoltaico: previsione sul piano inclinato» ([`tilted.js`](tilted.js)):
  - ogni ora (e 30 s dopo l'avvio) una richiesta Open-Meteo per falda, `global_tilted_irradiance`
    con inclinazione 40° (Eclipse) e 15° (Viessmann), azimut −37,5° (convenzione Open-Meteo, = 142,5°
    bussola); stesse coordinate già usate da SOLAR-FORECAST.1;
  - ogni 300 s pubblica sul broker del `.15` (retained, come `smodule`) i W di luce sulla falda per
    l'ora in corso: `emon/planetary/forecast_smodule` e `forecast_smodule2`;
  - riepilogo retained `zara/interface/energy/solar_forecast/stat` (`domopi.solar_forecast_tilted.v1`):
    per falda W adesso, kWh di luce di oggi e domani (giorni di Roma), ora della previsione.
  - Valori = **luce sul piano** (prima dell'efficienza dei pannelli), confrontabili con `smodule`.
    Dati più vecchi di 6 ore non vengono pubblicati; risposta non valida = avviso e dati precedenti.
- SOLAR-FORECAST.1 (previsione giornaliera per il Predictive Reserve), router e altri tab invariati.

```sh
python3 build.py <flows-export.json> && TZ=Europe/Rome node --test test.js     # 4 casi
python3 deploy.py prepare | apply BACKUP | rollback BACKUP                     # sul .20
```

Applicata il 23/10/2026 alle 18:09 (backup `/home/pi/AI_backups/nodered/20261023T160950Z-before-solar-tilted`);
primi valori verificati alle 18:11 (oggi 97,2 kWh di luce sulle due falde, domani 70,1).

Dopo il deploy: in EmonCMS i nuovi input `forecast_smodule`/`forecast_smodule2` (nodo `planetary`)
vanno registrati su feed, con l'utente.
