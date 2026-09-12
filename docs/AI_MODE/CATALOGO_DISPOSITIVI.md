# Catalogo completo delle schede e del Router

Audit statico dell'11 settembre 2026, branch `feature/ai-home-assistant`.
Il catalogo estende il primo mapping luci/living/ACS a tutte le 13 schermate.
Completezza riferita ai sorgenti Android e al Router fornito, non a un censimento
fisico della casa né a una nuova verifica del runtime Node-RED.

## Risultato e fonti

104 capacità MQTT uniche: 59 di sola lettura, 42 con stato e comando, tre con
solo comando (due cancelli e gli scenari luce). Sono incluse quattro capacità
obsolete della pompa di calore: una lettura e tre letture/comandi. Non abilitarle
come capacità operative dell'assistente.

Estratte 89 dichiarazioni del `registry` e 31 del `compositeRegistry`, unendo
17 sovrapposizioni per topic pubblico; aggiunto il bridge degli scenari fuori
registry. Blocchi commentati esclusi, incluso `climate.ai_enabling`.

Fonte: `flows - 2026-09-11T113811.470.json`, SHA256
`0ddc5a4b57391378a14afa0fa7cffd1d000eea8b7de9b5650d10b7d41ddebbf2`.
Il registry è quello nella funzione Digital Twin Router, non il vecchio YAML
DomoticsAI, escluso dal contratto runtime del progetto.

Il file [device_catalog.json](../../backend/house_ai/device_catalog.json) contiene
per ogni voce dominio, accesso, topic pubblici, sorgente legacy/composita,
trasformazioni originali come testo, riga del Router, riferimenti ai comandi
Android e stato di obsolescenza. Non contiene credenziali. I nomi di dominio
sono classificazioni; gli identificativi non dimostrano da soli entità fisiche.

## Tutte le schermate

| Schermata | Dati e dispositivi | Interazione |
|---|---|---|
| MainDashboard | Energia, ACS/puffer, ambiente, luci, clima e allarmi, VMC, piscina, HVAC, automatismi, garage e anteprima camera | Riepiloghi; navigazione e comandi luce condizionati dalla modalità esperto |
| LightsScreen | Soggiorno, libreria, lampada TV, lettura, camera, HiFi, prolunga e quattro voci senza mapping | Toggle e scene `tv`, `sleep`, `all_on`, `all_off` |
| PoolScreen | Pompa, skimmer, luci vasca e pedana | Quattro toggle; separare le pompe dalle luci |
| AmbientiScreen | Living, camera e ambiente esterno | Temperature/umidità e Humidex; lettura |
| AiManagedScreen | Climatizzazione, logica, VMC, parametri AI e Predictive Reserve | Lettura e modifica dei parametri AI/abilitazioni esposti |
| HvacScreen | VMC, puffer, solare termico, caldaia gas, pompa pavimento, termocamino, termostati living e bagno | Comandi VMC, abilitazione pavimento, termocamino e setpoint/limiti termostati; gas e solare in lettura |
| DomoticaSettingsScreen | Vacanza, ECO luci, luci piscina automatiche, sensore portico, clima automatico | Cinque impostazioni comandabili |
| GarageControlScreen | Due pulsanti cancello | Solo comando `true`; nessun sensore apertura mappato |
| EnergyDetailScreen | Storico FV/casa/rete/batteria/SOC | HTTP EmonCMS, sola lettura, finestre 6/24 ore |
| CamerasScreen | Ingresso e soggiorno | HTTP MJPEG TinyCam, selezione locale dello stream; nessun comando MQTT camera |
| ConfigurationScreen | Connessioni, tema, prefisso Digital Twin, PIN esperto | Impostazioni locali DataStore; non dispositivi MQTT |
| DiagnosisScreen | Connessione, frequenza messaggi, traffico | Diagnostica locale, sola lettura |
| HouseAiScreen | Rapporto storico, grafici ed eventi | API HTTP di consultazione; domande libere e voce ancora da implementare |

Nel JSON `android_domain_screens` indica le schermate che consumano un dominio,
non certifica che ogni proprietà del dominio sia visibile in ognuna di esse.
I riferimenti ai comandi sono invece estratti dai publish e dagli helper usati.
La presenza di un comando non costituisce autorizzazione a eseguirlo con la voce.

## Risorse senza MQTT

- TinyCam: Ingresso `936942165`, Soggiorno `1708386743`, endpoint
  `/axis-cgi/mjpg/video.cgi?cameraId={id}`. Nessun accesso video eseguito nell'audit.
- Storico Android EmonCMS: feed 307 FV, 303 casa, 305 rete, 306 batteria, 304 SOC.
- API `house_ai`: `/v1/report?day={day}`, feed 353 AC, 304 SOC e 306 batteria,
  più il log climatico. Non confondere archivio storico e stato MQTT corrente.
- Storico energia validato: feed 305 in W, positivo prelievo e negativo
  immissione; feed 304 SOC in percentuale. Mapping, confronto e copertura in
  [validazione storico energia](VALIDAZIONE_STORICO_ENERGIA.md). Il backend
  espone `/v1/energy/query`; la schermata Android non è ancora collegata.
- Configurazione e diagnosi sono funzioni dell'app, non attuatori domestici.

## Divergenze da conservare nel catalogo

1. Lavanderia, portico, cucina ed esterno: l'app può generare
   `zara/interface/lights/{id}/power/cmd`, ma il Router esportato non contiene
   le corrispondenti rotte. Non classificarli come comandi funzionanti verificati.
2. `heating.heat_pump.*`: quattro voci ancora nel registry, dispositivo rimosso
   secondo `RUNTIME_MQTT_CONTRACT.md`. Non promuovere l'esistenza della rotta a
   prova dell'esistenza dell'impianto.
3. `lights.prolunga.power`: comando/stato presenti, ruolo fisico ancora da
   chiarire. Dominio lights non basta a decidere che sia una lampada.
4. `env.living.temperature` e `climate.thermostat_living.current_temperature`
   condividono `emon/emonth5/temperature_calibrated`: due viste della stessa
   misura, non due sensori da contare.
5. Topic composito effettivo: **`casa/clima/stato_completo`**, verificato nella
   costante e nella condizione di routing. `clima/casa/stato_completo` è un
   commento con ordine invertito. Android riceve le proiezioni pubbliche,
   non deve dipendere dal payload legacy composito.
6. I nomi AI MANAGED PARAMETERS ripetuti non definiscono domini diversi.
   Predictive Reserve ha il proprio topic pubblico e il suo controllo dedicato.
7. Stati retained e valori default dei modelli Android non garantiscono
   freschezza o conferma fisica. Il timestamp full-state vale per quel flusso,
   non data automaticamente le misure indipendenti.

## Voci per dominio

Legenda: R = lettura; RW = stato e comando; C = solo comando. Topic relativi
al prefisso `zara/interface/`. Le righe rinviano alla funzione Router esportata.
Per i payload usare dichiarazioni/trasformazioni e riferimenti UI del JSON;
range visuali e conversioni non sostituiscono una validazione operativa.

### ai

| Identificativo | Accesso | Stato | Comando | Provenienza / righe |
|---|---|---|---|---|
| ai.system_enabled | RW | `ai/system_enabled/stat` | `ai/system_enabled/cmd` | casa/clima/stat/AI_climate_enabling (r. 120) |
| ai.compressor_on_min | RW | `ai/compressor_on_min/stat` | `ai/compressor_on_min/cmd` | casa/clima/stat/min_run_time (r. 148) |
| ai.compressor_off_min | RW | `ai/compressor_off_min/stat` | `ai/compressor_off_min/cmd` | casa/clima/stat/min_off_time (r. 149) |
| ai.night_humidex_threshold | RW | `ai/night_humidex_threshold/stat` | `ai/night_humidex_threshold/cmd` | casa/clima/stat/target_humidex (r. 150) |
| ai.night_vmc_max_speed | RW | `ai/night_vmc_max_speed/stat` | `ai/night_vmc_max_speed/cmd` | casa/clima/stat/vmc_max_notte (r. 151) |
| ai.deficit_tolerance_min | RW | `ai/deficit_tolerance_min/stat` | `ai/deficit_tolerance_min/cmd` | casa/clima/stat/deficit_tolerance_time (r. 152) |
| ai.morning_ac_management | RW | `ai/morning_ac_management/stat` | `ai/morning_ac_management/cmd` | casa/clima/stat/grace_mode_solar (r. 153) |
| ai.morning_humidex_emergency | RW | `ai/morning_humidex_emergency/stat` | `ai/morning_humidex_emergency/cmd` | casa/clima/stat/emergency_humidex_away (r. 154) |

### ai_climate

| Identificativo | Accesso | Stato | Comando | Provenienza / righe |
|---|---|---|---|---|
| ai_climate.allarme | R | `ai_climate/allarme/stat` | — | casa/clima/system_health/stat (r. 7) |

### climate

| Identificativo | Accesso | Stato | Comando | Provenienza / righe |
|---|---|---|---|---|
| climate.thermostat_living.current_temperature | R | `climate/thermostat_living/current_temperature/stat` | — | emon/emonth5/temperature_calibrated (r. 212) |
| climate.thermostat_living.target_temperature | RW | `climate/thermostat_living/target_temperature/stat` | `climate/thermostat_living/target_temperature/cmd` | cmnd/tasmota_F32F4F/TEMPTARGETSET (r. 213) |
| climate.thermostat_living.power | R | `climate/thermostat_living/power/stat` | — | emon/TermostatLiving/Switch (r. 214) |
| climate.thermostat_living.min_temperature | RW | `climate/thermostat_living/min_temperature/stat` | `climate/thermostat_living/min_temperature/cmd` | zara/domotics/livingThemostatMinValue (r. 215) |
| climate.thermostat_living.max_temperature | RW | `climate/thermostat_living/max_temperature/stat` | `climate/thermostat_living/max_temperature/cmd` | zara/domotics/livingThemostatMaxValue (r. 216) |
| climate.thermostat_bath.current_temperature | R | `climate/thermostat_bath/current_temperature/stat` | — | emon/bagnoServizio/temperature (r. 218) |
| climate.thermostat_bath.target_temperature | RW | `climate/thermostat_bath/target_temperature/stat` | `climate/thermostat_bath/target_temperature/cmd` | cmnd/SmallBathroomThermostat/TEMPTARGETSET (r. 219) |
| climate.thermostat_bath.power | R | `climate/thermostat_bath/power/stat` | — | emon/bagnoServizio/Switch (r. 220) |
| climate.thermostat_bath.min_temperature | RW | `climate/thermostat_bath/min_temperature/stat` | `climate/thermostat_bath/min_temperature/cmd` | zara/domotics/SmallBathroomThermostatMinValue (r. 221) |
| climate.thermostat_bath.max_temperature | RW | `climate/thermostat_bath/max_temperature/stat` | `climate/thermostat_bath/max_temperature/cmd` | zara/domotics/SmallBathroomThermostatMaxValue (r. 222) |
| climate.full_state.timestamp | R | `climate/full_state/timestamp/stat` | — | timestamp (r. 269) |
| climate.full_state.data_ora_formattata | R | `climate/full_state/data_ora_formattata/stat` | — | data_ora_formattata (r. 274) |
| climate.control.humidex_reference | R | `climate/control/humidex_reference/stat` | — | metriche_ambientali.humidex (r. 356) |

### energy

| Identificativo | Accesso | Stato | Comando | Provenienza / righe |
|---|---|---|---|---|
| energy.solar.power | R | `energy/solar/power/stat` | — | TeslaPowerwall/solar_instant_power (r. 174) |
| energy.home.consumption | R | `energy/home/consumption/stat` | — | TeslaPowerwall/load_instant_power (r. 175) |
| energy.battery.soc | R | `energy/battery/soc/stat` | — | TeslaPowerwall/SOE (r. 176) |
| energy.grid.power_raw | R | `energy/grid/power_raw/stat` | — | TeslaPowerwall/site_instant_power (r. 177) |
| energy.battery.power_raw | R | `energy/battery/power_raw/stat` | — | TeslaPowerwall/battery_instant_power (r. 178) |
| energy.puffer.acs | R | `energy/puffer_acs/stat` | — | emon/shellyACS/tempACS (r. 183) |
| energy.solar.surplus | R | `energy/solar/surplus/stat` | — | metriche_elettriche.surplus_w (r. 306) |
| energy.grid.import | R | `energy/grid/import/stat` | — | metriche_elettriche.grid_import_w (r. 311) |
| energy.grid.export | R | `energy/grid/export/stat` | — | metriche_elettriche.grid_export_w (r. 316) |
| energy.battery.charge | R | `energy/battery/charge/stat` | — | metriche_elettriche.battery_charge_w (r. 321) |
| energy.battery.discharge | R | `energy/battery/discharge/stat` | — | metriche_elettriche.battery_discharge_w (r. 326) |
| energy.ac.power | R | `energy/ac/power/stat` | — | metriche_elettriche.consumo_ac_w (r. 331) |
| energy.home.historical_average_band | R | `energy/home/historical_average_band/stat` | — | metriche_elettriche.consumo_medio_storico_fascia_w (r. 336) |

### env

| Identificativo | Accesso | Stato | Comando | Provenienza / righe |
|---|---|---|---|---|
| env.living.temperature | R | `env/living/temperature/stat` | — | emon/emonth5/temperature_calibrated (r. 225) |
| env.living.humidity | R | `env/living/humidity/stat` | — | emon/emonth5/humidity (r. 226) |
| env.living.humidex | R | `env/living/humidex/stat` | — | emon/emonth5/humidex (r. 227) |
| env.bedroom.temperature | R | `env/bedroom/temperature/stat` | — | emon/cameraMatrimoniale/temperature (r. 229) |
| env.bedroom.humidity | R | `env/bedroom/humidity/stat` | — | emon/cameraMatrimoniale/humidity (r. 230) |
| env.bedroom.humidex | R | `env/bedroom/humidex/stat` | — | emon/cameraMatrimoniale/humidex (r. 231) |
| env.outdoor.temperature | R | `env/outdoor/temperature/stat` | — | emon/weather/extTemp (r. 233) |
| env.outdoor.humidity | R | `env/outdoor/humidity/stat` | — | emon/weather/extHumidity (r. 234) |
| env.outdoor.humidex | R | `env/outdoor/humidex/stat` | — | emon/sensore_outdoor/humidex (r. 235) |
| env.solar_altitude | R | `env/solar_altitude/stat` | — | metriche_ambientali.altitudine_sole (r. 361) |

### fireplace

| Identificativo | Accesso | Stato | Comando | Provenienza / righe |
|---|---|---|---|---|
| fireplace.main.power | RW | `fireplace/main/power/stat` | `fireplace/main/power/cmd` | zara/domotics/palazzetti/acceso (r. 204) |
| fireplace.main.level | RW | `fireplace/main/level/stat` | `fireplace/main/level/cmd` | emon/focolare/powerraw (r. 205) |
| fireplace.main.mode | RW | `fireplace/main/mode/stat` | `fireplace/main/mode/cmd` | zara/domotics/palazzetti/modalita (r. 206) |
| fireplace.main.start_time | RW | `fireplace/main/start_time/stat` | `fireplace/main/start_time/cmd` | zara/domotics/palazzetti/oraavvio (r. 207) |
| fireplace.main.stop_time | RW | `fireplace/main/stop_time/stat` | `fireplace/main/stop_time/cmd` | zara/domotics/palazzetti/oraspegnimento (r. 208) |
| fireplace.main.auto_power | RW | `fireplace/main/auto_power/stat` | `fireplace/main/auto_power/cmd` | zara/domotics/controlloAutomaticoPotenzaCaminetto (r. 209) |

### garage

| Identificativo | Accesso | Stato | Comando | Provenienza / righe |
|---|---|---|---|---|
| garage.gate_1 | C | — | `garage/gate_1/cmd` | zara/domotics/garage/gate_1 (r. 109) |
| garage.gate_2 | C | — | `garage/gate_2/cmd` | zara/domotics/garage/gate_2 (r. 110) |

### heating

| Identificativo | Accesso | Stato | Comando | Provenienza / righe |
|---|---|---|---|---|
| heating.puffer.top_temperature | R | `heating/puffer/top_temperature/stat` | — | emon/resolDL2/sensor3 (r. 181) |
| heating.puffer.bottom_temperature | R | `heating/puffer/bottom_temperature/stat` | — | emon/resolDL2/sensor2 (r. 182) |
| heating.solar_thermal.collector_temperature | R | `heating/solar_thermal/collector_temperature/stat` | — | emon/resolDL2/sensor1 (r. 186) |
| heating.solar_thermal.pump_speed | R | `heating/solar_thermal/pump_speed/stat` | — | emon/resolDL2/pump1 (r. 187) |
| heating.gas_boiler.flame | R | `heating/gas_boiler/flame/stat` | — | emon/OTGcaldaia/flame (r. 190) |
| heating.gas_boiler.modulation | R | `heating/gas_boiler/modulation/stat` | — | emon/OTGcaldaia/modulation (r. 191) |
| heating.floor_pump.enabled | RW | `heating/floor_pump/enabled/stat` | `heating/floor_pump/enabled/cmd` | zara/domotics/pompapavimento/abilitata (r. 194) |
| heating.floor_pump.running | R | `heating/floor_pump/running/stat` | — | zara/domotics/floorpumpstate (r. 195) |
| heating.heat_pump.enabled **OBSOLETO** | RW | `heating/heat_pump/enabled/stat` | `heating/heat_pump/enabled/cmd` | zara/domotics/HP/abilitata (r. 198) |
| heating.heat_pump.solar_divert **OBSOLETO** | RW | `heating/heat_pump/solar_divert/stat` | `heating/heat_pump/solar_divert/cmd` | zara/domotics/HP/solardivertmode (r. 199) |
| heating.heat_pump.target_temperature **OBSOLETO** | RW | `heating/heat_pump/target_temperature/stat` | `heating/heat_pump/target_temperature/cmd` | casa/clima/stat/target_temp_inverno (r. 200) |
| heating.heat_pump.running **OBSOLETO** | R | `heating/heat_pump/running/stat` | — | emon/AC/HPstatus (r. 201) |

### lights

| Identificativo | Accesso | Stato | Comando | Provenienza / righe |
|---|---|---|---|---|
| lights.living.power | RW | `lights/living/power/stat` | `lights/living/power/cmd` | zara/domotics/lights/livinglamp (r. 157) |
| lights.libreria.power | RW | `lights/libreria/power/stat` | `lights/libreria/power/cmd` | zara/domotics/lights/shelflamp (r. 158) |
| lights.tv.power | RW | `lights/tv/power/stat` | `lights/tv/power/cmd` | zara/domotics/lights/tvlamp (r. 159) |
| lights.reading.power | RW | `lights/reading/power/stat` | `lights/reading/power/cmd` | zara/domotics/lights/readinglight (r. 160) |
| lights.bedroom.power | RW | `lights/bedroom/power/stat` | `lights/bedroom/power/cmd` | zara/domotics/lights/bedroomlight (r. 161) |
| lights.prolunga.power | RW | `lights/prolunga/power/stat` | `lights/prolunga/power/cmd` | zara/domotics/lights/prolunga (r. 162) |
| lights.hifi.power | RW | `lights/hifi/power/stat` | `lights/hifi/power/cmd` | stat/tasmota_86AD14/POWER (r. 165) |
| lights.scene | C | — | `lights/scene/cmd` | special_scene_bridge (r. 599) |

### logica_controllo

| Identificativo | Accesso | Stato | Comando | Provenienza / righe |
|---|---|---|---|---|
| logica_controllo.stagione_attuale | R | `logica_controllo/stagione_attuale/stat` | — | registry (r. 43); stagione_attiva (r. 284) |
| logica_controllo.cuscinetto_richiesto_kwh | R | `logica_controllo/cuscinetto_richiesto_kwh/stat` | — | registry (r. 48); logica_controllo.cuscinetto_richiesto_kwh (r. 427) |
| logica_controllo.cuscinetto_sicurezza_kwh | R | `logica_controllo/cuscinetto_sicurezza_kwh/stat` | — | registry (r. 53); logica_controllo.cuscinetto_sicurezza_kwh (r. 422) |
| logica_controllo.blocco_emergenza_attivo | R | `logica_controllo/blocco_emergenza_attivo/stat` | — | registry (r. 58); logica_controllo.blocco_emergenza_attivo (r. 416) |
| logica_controllo.kwh_stimati_in_batteria | R | `logica_controllo/kwh_stimati_in_batteria/stat` | — | registry (r. 63); logica_controllo.kwh_stimati_in_batteria (r. 411) |
| logica_controllo.previsione_ricarica_batteria_percent | R | `logica_controllo/previsione_ricarica_batteria_percent/stat` | — | registry (r. 68); logica_controllo.previsione_ricarica_batteria_percent (r. 406) |
| logica_controllo.previsione_solare_data | R | `logica_controllo/previsione_solare_data/stat` | — | registry (r. 73); logica_controllo.previsione_solare_data (r. 401) |
| logica_controllo.previsione_solare_domani_kwh | R | `logica_controllo/previsione_solare_domani_kwh/stat` | — | registry (r. 78); logica_controllo.previsione_solare_domani_kwh (r. 396) |
| logica_controllo.tempo_mancante_anticiclo_minuti | R | `logica_controllo/tempo_mancante_anticiclo_minuti/stat` | — | registry (r. 83); logica_controllo.tempo_mancante_anticiclo_minuti (r. 391) |
| logica_controllo.soc_minimo_applied | R | `logica_controllo/soc_minimo_applied/stat` | — | registry (r. 88); logica_controllo.soc_minimo_applied (r. 386) |
| logica_controllo.soglia_attivazione_applicata | R | `logica_controllo/soglia_attivazione_applicata/stat` | — | registry (r. 93); logica_controllo.soglia_attivazione_applicata (r. 381) |
| logica_controllo.stanza_rilevamento_vmc | R | `logica_controllo/stanza_rilevamento_vmc/stat` | — | registry (r. 98); logica_controllo.stanza_rilevamento_vmc (r. 376) |
| logica_controllo.vmc_portata_stimata_m3h | R | `logica_controllo/vmc_portata_stimata_m3h/stat` | — | registry (r. 103); logica_controllo.vmc_portata_stimata_m3h (r. 371) |

### pool

| Identificativo | Accesso | Stato | Comando | Provenienza / righe |
|---|---|---|---|---|
| pool.pump.power | RW | `pool/pump/power/stat` | `pool/pump/power/cmd` | zara/domotics/lights/poolpump (r. 168) |
| pool.skimmer.power | RW | `pool/skimmer/power/stat` | `pool/skimmer/power/cmd` | zara/domotics/lights/poolskimmer (r. 169) |
| pool.water.power | RW | `pool/water/power/stat` | `pool/water/power/cmd` | zara/domotics/lights/poollight (r. 170) |
| pool.deck.power | RW | `pool/deck/power/stat` | `pool/deck/power/cmd` | zara/domotics/lights/poolfloor (r. 171) |

### predictive_reserve

| Identificativo | Accesso | Stato | Comando | Provenienza / righe |
|---|---|---|---|---|
| predictive_reserve.control_enabled | RW | `predictive_reserve/control_enabled/stat` | `predictive_reserve/control_enabled/cmd` | casa/clima/stat/predictive_reserve_control_enabled (r. 122) |

### settings

| Identificativo | Accesso | Stato | Comando | Provenienza / righe |
|---|---|---|---|---|
| settings.holiday_mode | RW | `settings/holiday_mode/stat` | `settings/holiday_mode/cmd` | zara/domotics/modalitavacanza (r. 113) |
| settings.eco_lights | RW | `settings/eco_lights/stat` | `settings/eco_lights/cmd` | zara/domotics/luciECO (r. 114) |
| settings.pool_lights_auto | RW | `settings/pool_lights_auto/stat` | `settings/pool_lights_auto/cmd` | zara/domotics/luciPiscinaAuto (r. 115) |
| settings.porch_sensor | RW | `settings/porch_sensor/stat` | `settings/porch_sensor/cmd` | zara/domotics/porch_sensor (r. 116) |
| settings.ac_auto | RW | `settings/ac_auto/stat` | `settings/ac_auto/cmd` | zara/domotics/ACAuto (r. 117) |

### stato_condizionatore

| Identificativo | Accesso | Stato | Comando | Provenienza / righe |
|---|---|---|---|---|
| stato_condizionatore.modalita_aria | R | `stato_condizionatore/modalita_aria/stat` | — | registry (r. 18); stato_condizionatore.modalita_aria (r. 452) |
| stato_condizionatore.temperatura_impostata_c | R | `stato_condizionatore/temperatura_impostata_c/stat` | — | registry (r. 23); stato_condizionatore.temperatura_impostata_c (r. 447) |
| stato_condizionatore.motivo_logica | R | `stato_condizionatore/motivo_logica/stat` | — | registry (r. 28); stato_condizionatore.motivo_logica (r. 442) |
| stato_condizionatore.stato_attuale | R | `stato_condizionatore/stato_attuale/stat` | — | registry (r. 33); stato_condizionatore.stato_attuale (r. 437) |

### ventilation

| Identificativo | Accesso | Stato | Comando | Provenienza / righe |
|---|---|---|---|---|
| ventilation.vmc.speed | RW | `ventilation/vmc/speed/stat` | `ventilation/vmc/speed/cmd` | /esp8266/brink/fanspeed (r. 248) |
| ventilation.vmc.reason | R | `ventilation/vmc/reason/stat` | — | stato_vmc.motivo_logica (r. 467) |
| ventilation.vmc.outdoor_humidex | R | `ventilation/vmc/outdoor_humidex/stat` | — | stato_vmc.humidex_esterno (r. 472) |
| ventilation.vmc.outdoor_temperature | R | `ventilation/vmc/outdoor_temperature/stat` | — | stato_vmc.temperatura_esterna_c (r. 477) |

## Verifica e riproduzione

12 test backend passati. Verificata separatamente la corrispondenza fra tutti i
topic stat/cmd dichiarati fuori commento e il catalogo, incluse le proiezioni
composite e il bridge scenari; nessun topic di stato duplicato nel risultato.

Estrazione senza eseguire il JavaScript del Router:

```sh
python3 -B backend/house_ai/tools/build_device_catalog.py --root . \
  --router '/percorso/flows - 2026-09-11T113811.470.json' \
  --output backend/house_ai/device_catalog.json
```

Il generatore è mirato al formato dell'esportazione fornita; per un Router nuovo
va ripetuto il controllo di copertura. Le fonti e gli hash delle schermate sono
nel JSON. Il catalogo esteso resta separato dal primo interprete luci/living/ACS:
nessun nuovo comando, connessione o dominio è attivato automaticamente.
