# TESLA-FW-INSTALL.1 — `.15`: data di installazione del firmware del gateway Powerwall

Host **`.15`**, scheda «Tesla Powerwall», function dopo «Get Tesla Status» (`5811d15d.e9acb8`, scritta
dall'utente il 24/10 e ora chiamata «Tesla status -> gw_*»), riscritta da `gw.js`.

- **Perché:** l'utente vuole vedere nell'app la versione del firmware e quando è stata installata (dopo il
  blocco della carica del 23/10). Il gateway non fornisce la data di installazione (`/api/status` dà versione,
  ora di avvio e uptime; `/api/system/update/status` dà solo lo stato, con le ore a 0).
- **Come:** un aggiornamento finisce con un riavvio del gateway. Quando la versione letta cambia, la data è l'ora
  di avvio del gateway (esatta, `gw_fw_installed_exact` = 1). La prima volta si parte dall'ultimo avvio noto
  (`exact` = 0: per 26.34.0 è il 05/10/2026 08:36, molto probabilmente l'aggiornamento ma non certo). Memoria nel
  context store `file` (`gwFwTrack`), sopravvive ai riavvii di Node-RED.
- **Limite:** se il gateway si riavviasse di nuovo prima della lettura successiva (ogni ora), la data sarebbe
  quella del secondo riavvio.
- Nuovi input (nodo `TeslaPowerwall`): `gw_fw_installed` (Unix, feed **476**) e `gw_fw_installed_exact`
  (feed **477**), PHPFina 3600 s; il resto della function è invariato.
- L'app li legge con `feed/fetch.json?ids=453,476,477` (schermata «Storico Energia», «INDICATORI CHIAVE»).

Test (Node 12 come sul `.15`): `node test.js` (5 casi). Generata da `build.py <export runtime>`.
Applicata il 27/10/2026 alle 09:58 (backup `/home/pi/AI_backups/nodered/20261027T085801Z-before-tesla-fw-install1`);
feed creati alle 09:58 (backup degli input `/home/pi/AI_backups/emoncms/20261027T085825Z-before-gw-fw-installed-feeds`).
Ritorno indietro: `python3 deploy.py rollback <cartella di backup>`.
