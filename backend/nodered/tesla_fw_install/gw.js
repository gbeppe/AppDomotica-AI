// Stato del gateway Tesla -> input numerici per EmonCMS (TESLA-FW-INSTALL.1: data di installazione del firmware)
  const s = msg.payload;

  // Il gateway dà l'uptime in formato Go, per esempio "464h13m21.51s"
  function goDurationToSeconds(d) {
      const m = String(d).match(/(?:(\d+)h)?(?:(\d+)m(?!s))?(?:([\d.]+)s)?/);
      if (!m) return null;
      return (+m[1] || 0) * 3600 + (+m[2] || 0) * 60 + (+m[3] || 0);
  }

  // La versione "26.34.0 7d707328" diventa 263400 (anno*10000 + mese*100 + patch)
  const v = String(s.version).split(" ")[0].split(".").map(Number);
  const fw = v.length >= 3 ? v[0] * 10000 + v[1] * 100 + v[2] : null;

  const up = goDurationToSeconds(s.up_time_seconds);   // secondi dall'avvio
  const boot = up === null ? null : Math.round(Date.now() / 1000 - up);

  // Il gateway non dice quando è stato installato il firmware. Un aggiornamento finisce con un riavvio:
  // quando la versione cambia, la data è l'ora di avvio del gateway (esatta). La prima volta si parte
  // dall'ultimo avvio noto (non esatta). Memoria su file: sopravvive ai riavvii di Node-RED.
  let track = flow.get("gwFwTrack", "file") || null;
  if (fw !== null && boot !== null && (!track || track.version !== fw)) {
      track = {version: fw, installed: boot, exact: track ? 1 : 0};
      flow.set("gwFwTrack", track, "file");
  }

  msg.payload = {
      gw_start_time: boot,
      gw_uptime_h: up === null ? null : Math.round(up / 36) / 100,
      gw_fw_version: fw,
      gw_fw_installed: track ? track.installed : null,
      gw_fw_installed_exact: track ? track.exact : null,
      gw_is_new: s.is_new ? 1 : 0,
      gw_cellular_disabled: s.cellular_disabled ? 1 : 0,
      gw_can_reboot: s.can_reboot ? 1 : 0,
      gw_commission_count: Number(s.commission_count) || 0
  };
  // Toglie i valori non validi, che EmonCMS rifiuterebbe
  for (const k in msg.payload) if (msg.payload[k] === null) delete msg.payload[k];

  // msg.nodegroup = "tesla_gateway";   // nome del nodo in EmonCMS, oppure lo imposti nel nodo emoncms
  return msg;
