# TESLA-STATUS.1 — `.15`: stato del gateway Powerwall in EmonCMS

Host **`.15`** (Node-RED 1.2.9, Node 12, progetto `emonpi`), scheda «Tesla Powerwall». Patch **solo
aggiuntiva**: 8 nodi nuovi, nessun nodo esistente modificato.

Perché: il 23/10/2026 il Powerwall non si è caricato dal fotovoltaico (tutto il solare in rete, la batteria
alimentava la casa fino alla riserva) senza modifiche alle impostazioni; il 24/10 mattina è bastato spostare la
riserva nell'app (0 → 10 → 0 %) perché ricominciasse a caricare. Ora lo stato del gateway viene archiviato per
capire il prossimo caso.

Flusso: inject ogni 5 min (primo giro 30 s dopo il deploy) → `request.js` (6 GET con il cookie del flusso Tesla
principale, `flow.TeslaCookie`; senza cookie salta il giro) → http request (URL da `msg.url`, TLS del flusso
esistente) → `wrap.js` (codice HTTP + risposta; 401/403 azzera il cookie, il flusso principale rifà il login) →
join per `topic` (6 risposte, timeout 20 s) → `convert.js` → «Emoncms Push» esistente (nodo `TeslaPowerwall`).

| Input (feed) | Fonte | Valore |
|---|---|---|
| `pw_mode` (455) | `/api/operation` `real_mode` | 1 autoconsumo, 2 backup, 3 a tempo (autonomous), 0 altro |
| `pw_reserve_pct` (456) | `backup_reserve_percent` | scala del gateway: **5 = 0 % nell'app** |
| `pw_grid_connected` (457), `pw_grid_services` (458) | `/api/system_status/grid_status` | 1/0 |
| `pw_cloud_connected` (459), `pw_running` (460) | `/api/sitemaster` | `connected_to_tesla` (sul firmware 26.34 è 0 anche con l'app in tempo reale: non usarlo per gli avvisi), controllore attivo |
| `pw_target_power` (461) | `/api/system_status` `battery_target_power` | W chiesti alla batteria (+ scarica, − carica) |
| `pw_command_source` (462) | `command_source` | 1 `Configuration`, 9 altro |
| `pw_max_charge_w` (463), `pw_inst_max_charge_w` (464) | `max_charge_power`, `instantaneous_max_charge_power` | W |
| `pw_full_pack_wh` (465), `pw_remaining_wh` (466) | `nominal_full_pack_energy`, `nominal_energy_remaining` | Wh (salute della batteria) |
| `pw_blocks` (467), `pw_charge_clamped` (468), `pw_backup_ready` (469), `pw_disabled_reasons` (470) | `available_blocks`, `battery_blocks[]` | numero, 1/0, 1/0, numero |
| `pw_energy_charged_kwh` (471), `pw_energy_discharged_kwh` (472) | `battery_blocks[].energy_*` | contatori di vita, kWh |
| `pw_grid_faults` (473), `pw_last_fault_ts` (474) | `/api/system_status/grid_faults` | numero di avvisi, ora Unix dell'ultimo |
| `pw_update_state` (475) | `/api/system/update/status` | 0 riuscito/fermo, 1 in corso o sconosciuto, 2 fallito |

Feed PHPFina a 300 s, tag `TeslaPowerwall`, creati il 24/10 con «Log to feed» (backup degli input:
`/home/pi/AI_backups/emoncms/20261024T152523Z-before-pw-feeds/`). Un testo nuovo (modalità, sorgente del
comando, stato dell'aggiornamento) diventa il codice 0/9/1 e compare una volta nella barra di debug
(«TESLA-STATUS: valore nuovo …»). Nella stessa scheda c'è il flusso dell'utente «Get Tesla Status»
(`/api/status`, ogni ora): `gw_fw_version` (453) e `gw_uptime_h` (454).

Test (Node 12 come sul `.15`): `node test.js`.

Deploy sul `.15` (Admin API locale, token in `~/.node-red/.admin-token.json` creato dall'utente, 7 giorni):

```sh
scp -r backend/nodered/tesla_status pi@192.168.1.15:/home/pi/AI_patches/
ssh pi@192.168.1.15 'cd ~/AI_patches/tesla_status && node test.js && python3 deploy.py prepare'
ssh pi@192.168.1.15 'cd ~/AI_patches/tesla_status && python3 deploy.py apply <cartella di backup>'
```

Applicata il 24/10/2026 alle 17:24 (backup `/home/pi/AI_backups/nodered/20261024T152429Z-before-tesla-status1`),
primo invio 17:25:00, 21 input. Ritorno indietro: `python3 deploy.py rollback <cartella di backup>` (toglie gli
8 nodi; input e feed in EmonCMS restano).
