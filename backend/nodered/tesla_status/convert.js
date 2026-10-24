// TESLA-STATUS.1: stato del Powerwall -> input numerici pw_* per EmonCMS (nodo TeslaPowerwall)
const r = msg.payload || {};
function body(k, isList) {
    const x = r[k];
    if (!x || x.code !== 200 || x.body === null || typeof x.body !== "object") return null;
    return Array.isArray(x.body) === !!isList ? x.body : null;
}
const MODES = {self_consumption: 1, backup: 2, autonomous: 3};
const SOURCES = {Configuration: 1};
const p = {};
const unknown = [];

const op = body("operation");
if (op) {
    p.pw_mode = MODES[op.real_mode] || 0;
    if (!MODES[op.real_mode]) unknown.push("real_mode=" + op.real_mode);
    p.pw_reserve_pct = Number(op.backup_reserve_percent);    // scala del gateway: 5 = 0 % nell'app
}
const g = body("grid");
if (g) {
    p.pw_grid_connected = g.grid_status === "SystemGridConnected" ? 1 : 0;
    p.pw_grid_services = g.grid_services_active ? 1 : 0;
}
const sm = body("sitemaster");
if (sm) {
    p.pw_cloud_connected = sm.connected_to_tesla ? 1 : 0;
    p.pw_running = sm.running ? 1 : 0;
}
const s = body("system");
if (s) {
    const blocks = Array.isArray(s.battery_blocks) ? s.battery_blocks : [];
    const sum = k => blocks.reduce((a, b) => a + (Number(b[k]) || 0), 0);
    p.pw_target_power = Number(s.battery_target_power);      // + scarica, - carica
    p.pw_command_source = SOURCES[s.command_source] || 9;
    if (!SOURCES[s.command_source]) unknown.push("command_source=" + s.command_source);
    p.pw_max_charge_w = Number(s.max_charge_power);
    p.pw_inst_max_charge_w = Number(s.instantaneous_max_charge_power);
    p.pw_full_pack_wh = Number(s.nominal_full_pack_energy);
    p.pw_remaining_wh = Number(s.nominal_energy_remaining);
    p.pw_blocks = Number(s.available_blocks);
    p.pw_charge_clamped = blocks.some(b => b.charge_power_clamped) ? 1 : 0;
    p.pw_backup_ready = blocks.length && blocks.every(b => b.backup_ready) ? 1 : 0;
    p.pw_disabled_reasons = blocks.reduce((a, b) => a + (Array.isArray(b.disabled_reasons) ? b.disabled_reasons.length : 0), 0);
    p.pw_energy_charged_kwh = sum("energy_charged") / 1000;
    p.pw_energy_discharged_kwh = sum("energy_discharged") / 1000;
}
const f = body("faults", true);
if (f) {
    p.pw_grid_faults = f.length;
    p.pw_last_fault_ts = f.length ? Math.round(Math.max.apply(null, f.map(x => Number(x.timestamp) || 0)) / 1000) : 0;
}
const u = body("update");
if (u) {
    const st = String(u.state || "");
    p.pw_update_state = /fail/.test(st) ? 2 : (/succeeded/.test(st) || st === "" ? 0 : 1);
    if (p.pw_update_state === 1) unknown.push("update.state=" + st);
}

// Testi nuovi (modalità, sorgente del comando, aggiornamento): una sola volta nella barra di debug
const seen = context.get("seen") || {};
unknown.forEach(t => { if (!seen[t]) { seen[t] = true; node.warn("TESLA-STATUS: valore nuovo " + t); } });
context.set("seen", seen);

for (const k in p) if (typeof p[k] !== "number" || !isFinite(p[k])) delete p[k];
const missing = ["operation", "grid", "sitemaster", "system", "faults", "update"].filter(k => !r[k] || r[k].code !== 200);
node.status({fill: missing.length ? "yellow" : "green", shape: "dot",
    text: Object.keys(p).length + " valori" + (missing.length ? ", mancano " + missing.join(",") : "") +
        (s ? ", target " + p.pw_target_power + " W" : "")});
if (!Object.keys(p).length) return null;
return {payload: p};
