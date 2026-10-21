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
`panelModel=Viessmann`. Il ramo con `Irradiance=` (MQTT `emon/weather/tomorrowSolarIrradiance`) è
**disattivato** e non ha mai funzionato: lo script legge solo il primo argomento e il valore era in
`flow`, non in `msg`. Decisione sul ramo: fase F2.

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

Prossime fasi possibili: F2 togliere o rifare il ramo con irraggiamento; F3 previsione reale
(Open-Meteo, irraggiamento sul piano inclinato) affiancata al valore teorico.
