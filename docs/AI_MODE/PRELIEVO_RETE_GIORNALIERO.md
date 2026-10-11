# "Importato ieri / oggi" — pubblicazione del prelievo giornaliero

Data: 11 ottobre 2026. Verifica del Digital Twin Bridge: l'app legge
`zara/interface/energy/grid/import_kwh_oggi/stat` e `import_kwh_ieri/stat`, ma nessun
flow Node-RED né lo stato completo del clima li pubblicava; la Home mostrava sempre «—».

## Soluzione

`backend/house_ai/grid_import_publisher.py`, eseguito ogni 15 minuti (`:01`, `:16`, …)
da `house-ai-grid-import.timer` come utente `house-ai`:

- calcola da EmonCMS (feed 305, stesso `energy_report` dell'assistente) il prelievo di
  oggi dalla mezzanotte all'ora attuale e quello di ieri;
- pubblica i kWh (due decimali) **retained**, QoS 1, sul broker locale con
  `mosquitto_pub`;
- se la copertura dei campioni è insufficiente (oggi < 80% del tempo trascorso, ieri
  < 95%) o EmonCMS non risponde, **cancella** il retained invece di pubblicare zero.

Nessuna modifica a Node-RED né all'app. Configurazione: `/etc/house-ai/backend.env`
(già esistente, per URL e chiave EmonCMS in sola lettura).

## Installazione (release con `grid_import_publisher.py`)

```sh
R=/opt/domopi-house-ai/current/deploy
sudo install -o root -g root -m 0644 $R/house-ai-grid-import.service $R/house-ai-grid-import.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl start house-ai-grid-import.service      # prima pubblicazione
sudo systemctl enable --now house-ai-grid-import.timer
```

Controlli: `systemctl list-timers house-ai-grid-import.timer`,
`journalctl -u house-ai-grid-import.service -n 5` (stampa solo i kWh),
`mosquitto_sub -h localhost -v -W 3 -t 'zara/interface/energy/grid/+/stat'`.

## Ripristino

```sh
sudo systemctl disable --now house-ai-grid-import.timer
sudo rm -f /etc/systemd/system/house-ai-grid-import.service /etc/systemd/system/house-ai-grid-import.timer
sudo systemctl daemon-reload
mosquitto_pub -h localhost -r -n -t zara/interface/energy/grid/import_kwh_oggi/stat
mosquitto_pub -h localhost -r -n -t zara/interface/energy/grid/import_kwh_ieri/stat
```
