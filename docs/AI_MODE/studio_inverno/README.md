# Studio «La casa d'inverno»

Pagina: [`index.html`](index.html) (aprire nel browser; `data.js` accanto, Chart.js da cdnjs). Progetto e
conclusioni: [`../AI_CLIMATE_INVERNO.md`](../AI_CLIMATE_INVERNO.md), sezione 7.

Per rigenerarla (per esempio dopo un altro inverno), dal PC `.40`:

```sh
W=/tmp/studio-inverno; mkdir -p $W/emon
./fetch_emon.sh $W/emon 136 48 242 132 189 246 388 179 181 180 147 195 214 42 41 40 43 44 263 362 198 197 \
    303 305 306 307 304 234 236 237 238 239 241 145 137        # copia dal .15, bassa priorità
python3 hourly.py $W/emon                                      # -> $W/hourly.npz
# archivio Open-Meteo con latitudine e longitudine di backend/solar/solarEnergy.php (LAT, LON), 2 decimali:
curl -s "https://archive-api.open-meteo.com/v1/archive?latitude=<LAT>&longitude=<LON>&start_date=2023-05-01&end_date=$(date +%F)&hourly=temperature_2m&timezone=Europe%2FRome" -o $W/openmeteo.json
python3 align_openmeteo.py $W                                  # -> $W/tout_openmeteo.npy
python3 build_data.py $W                                       # -> data.js
```

Tarature e prezzi sono in testa a `build_data.py` (caldaia ×0,66 dalle bollette del gas; pellet dai 55 sacchi del
2025-26; gas 1 €/Smc, pellet 0,40 €/kg, luce comprata 0,24 €/kWh, venduta 0,075 €/kWh). Le bollette restano fuori
dal repository: nel codice ci sono solo i consumi mensili di gas.
