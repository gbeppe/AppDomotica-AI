# Fotovoltaico teorico (`solarEnergy.php`, sul `.20`)

Calcola, ogni 5 minuti, la luce del sole che arriva sulle due falde del fotovoltaico con il cielo
sereno (solo luce diretta sul piano inclinato) e la pubblica sul broker del `.15` per EmonCMS, che
applica l'efficienza dei pannelli.

| Falda | Inclinazione | Azimut | Superficie | Topic (retained, QoS 0) |
|---|---|---|---|---|
| Eclipse | 40° | 142,5° | 19,66 m² (12 pannelli 1655×990) | `emon/planetary/azimuth`, `altitude`, `smodule` |
| Viessmann | 15° | 142,5° | 19,52 m² (10 pannelli 1722×1134) | `emon/planetary/azimuth2`, `altitude2`, `smodule2` |

`smodule` = W di luce sulla superficie; di notte tutto 0. Azimut verificato dall'utente il 21/10/2026.

## Chi lo chiama

Node-RED `.20`, scheda «start local scripts»: inject ogni 300 s → `php /home/pi/solarEnergy.php
panelModel=Eclipse` → (uscita «codice di ritorno», arriva anche in caso di errore) → ritardo 5 s →
`panelModel=Viessmann`. Il ramo con `Irradiance=` (MQTT `emon/weather/tomorrowSolarIrradiance`) era
**disattivato** e non ha mai funzionato (lo script legge solo il primo argomento e il valore era in
`flow`, non in `msg`): tolto con SOLAR-TILTED.1 (F2).

## Uso

```sh
php solarEnergy.php panelModel=Eclipse                          # pubblica (come Node-RED)
php solarEnergy.php panelModel=Viessmann dry-run at=2026-06-21T10:30:00Z   # stampa, non pubblica
sh compare_with_old.sh /home/pi/AI_backups/solar/<ts>-before-f1/solarEnergy.php   # confronto
```

Codice di uscita 0 = pubblicato; 1 = errore, spiegato su stderr (`panelModel` sconosciuto, password
mancante, broker non raggiungibile). Password dell'utente `emonpi` in
`/home/pi/.config/domopi/mqtt_emonpi.pass` (permessi 600), mai nello script.

## F1 (21/10/2026): cosa è cambiato rispetto allo script del 2024

- password fuori dallo script; identificativo MQTT unico (`solarEnergy-<falda>-<pid>`, prima
  `MyClient` fisso);
- pubblica e attende la conferma di invio (al massimo 5 s) invece di 8 s fissi di attese;
- `panelModel` sconosciuto: errore chiaro invece di avvisi PHP;
- valori arrotondati (gradi a 2 decimali, W a 1 decimale); `Irradiance` usato solo se è un numero;
- calcolo **identico**: `compare_with_old.sh` confronta 576 casi (equinozio e solstizi, ogni 15 minuti,
  due falde) con lo script precedente: 0 differenze oltre l'arrotondamento;
- `solarEnergyViessmann.php` (copia vecchia, non usata da Node-RED) archiviato nel backup.

## F2 e F3 (23/10/2026, decisione dell'utente)

F2: il ramo `Irradiance=` si **toglie**; F3: previsione reale Open-Meteo sul piano inclinato di
ciascuna falda, accanto al valore teorico (`emon/planetary/forecast_smodule`, `forecast_smodule2` e il
riepilogo `zara/interface/energy/solar_forecast/stat`). Patch Node-RED:
[`../nodered/solar_tilted/`](../nodered/solar_tilted/README.md) (SOLAR-TILTED.1).

## Confronto previsione / reale (`compare_forecast.py`, 24/10/2026)

Gira sul `.15` e legge direttamente i file PHPFina di EmonCMS (sola lettura, senza chiave): per un
giorno stampa le medie orarie in W, i kWh, lo scarto della previsione e l'efficienza effettiva di
ciascuna falda (reale elettrico / luce prevista) da confrontare con i coefficienti della previsione (0,12 e 0,195).

```sh
ssh pi@192.168.1.15 'python3 - 2026-10-24' < compare_forecast.py   # senza data: oggi
```

Produzione reale per falda (confermato dall'utente il 24/10): l'inverter **Huawei** (feed 426
`ac_power`) è sulla falda **Eclipse**; la Viessmann è il totale Tesla (307 `solar_instant_power`)
meno il Huawei. Sugli ultimi 15 giorni sereni il Huawei segue la curva teorica Eclipse e il resto
quella Viessmann; il Huawei fa in media il 40 % del totale.

I pannelli Eclipse hanno circa 10 anni (utente, 24/10): l'efficienza reale è più bassa di quella di
targa per l'invecchiamento (tipicamente 0,5–0,8 % all'anno), oltre alle perdite di inverter,
temperatura e sporco.

**Coefficienti (30/10/2026, giornata limpida, ore 9–13):** in EmonCMS, solo sulla **previsione**: `forecast_smodule`
(input 1527) da 0,15 a **0,12**, `forecast_smodule2` (1528) da 0,205 a **0,195**. Il teorico a cielo sereno (`smodule`,
`smodule2`, feed 146 e 302) resta 0,15 e 0,205 di proposito: è il riferimento fisso dei grafici dell'utente, reale
contro teorico di ciascuna falda. Copia dei precedenti in `/home/pi/AI_backups/emoncms/20261030-before-forecast-coeff.txt`
sul `.15`. Falde: Eclipse 12 pannelli, 3 kWp, circa 10 anni; Viessmann 10 pannelli, 3,6 kWp, circa 3 anni, inverter che
taglia a 3 kW. Col sole basso (7, 14–15) il reale è molto sotto il previsto: edifici lontani a est e alberi a ovest nascondono il
sole a inizio e fine giornata, soprattutto d'inverno (utente): i coefficienti si tarano sulle ore centrali. Da
capire: la pinza dell'emonTx della Eclipse (feed 359) legge ~8 % più del Huawei (426) nelle ore centrali e coincide
a bassa potenza; con la pinza i coefficienti sarebbero ~0,13 e ~0,185 (Viessmann vicina alla targa 0,184).

Dashboard «planetary» (id 13): reale contro teorico per falda; il 30/10 aggiunti previsto elettrico 450/452 e Huawei
426 in `content_json` (EmonCMS 12.2 converte lì le vecchie multigraph, la tabella `multigraph` non conta più); copia
in `/home/pi/AI_backups/emoncms/20261030-dashboard13-before.json` sul `.15`.
