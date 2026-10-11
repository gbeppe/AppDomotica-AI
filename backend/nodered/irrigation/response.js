// IRRIGATION.1: OpenSprinkler HTTP response.
// Outputs: 1 = app state (retained JSON), 2 = command result for the app, 3 = poll now,
//          4 = EmonCMS (emon/irrigazione/zona_N every poll, zona_N_durata_s at the end of a run).
const now = Date.now();
if (msg.kind === "command") {
    const result = msg.payload && typeof msg.payload === "object" ? msg.payload.result : null;
    const ok = msg.statusCode === 200 && result === 1;
    if (!ok) node.warn("Irrigazione: OpenSprinkler ha rifiutato " + msg.command + " (HTTP " + msg.statusCode + ", result " + result + ")");
    return [null, {topic: "zara/interface/irrigation/last_command/stat", retain: false,
                   payload: {ok: ok, command: msg.command, result: result, at_ms: now}},
            {kind: "poll-now"}, null];
}

const d = msg.payload;
if (msg.statusCode !== 200 || !d || typeof d !== "object" || !d.settings || !d.status || !d.stations || !d.programs) {
    node.warn("Irrigazione: lettura stato OpenSprinkler non riuscita (HTTP " + msg.statusCode + ")");
    return null;
}
const names = d.stations.snames || [];
const disabled = d.stations.stn_dis || [];
const sn = d.status.sn || [];
const ps = d.settings.ps || [];
const zones = [];
names.forEach((name, i) => {
    if ((disabled[i >> 3] >> (i & 7)) & 1) return;   // station disabled in OpenSprinkler
    zones.push({id: i + 1, name: String(name), running: sn[i] === 1,
                remaining_s: Array.isArray(ps[i]) ? Number(ps[i][1]) || 0 : 0});
});
const programs = (d.programs.pd || []).map((p, i) => ({id: i + 1, name: String(p[5]), enabled: (p[0] & 1) === 1}));
const state = {schema: "domopi.irrigation.v1", controller_enabled: d.settings.en === 1,
               rain_delay: d.settings.rd === 1, rain_delay_until_s: Number(d.settings.rdst) || 0,
               zones: zones, programs: programs};
flow.set("irrigation_state", state);

// EmonCMS: running state every poll, measured duration when a zone stops.
const starts = flow.get("irrigation_starts") || {};
const emon = [];
for (const z of zones) {
    emon.push({topic: "emon/irrigazione/zona_" + z.id, payload: z.running ? 1 : 0});
    if (z.running && !starts[z.id]) starts[z.id] = now;
    if (!z.running && starts[z.id]) {
        emon.push({topic: "emon/irrigazione/zona_" + z.id + "_durata_s", payload: Math.round((now - starts[z.id]) / 1000)});
        delete starts[z.id];
    }
}
flow.set("irrigation_starts", starts);

// App: publish on change, or every 60 s as heartbeat.
const key = JSON.stringify(state);
const last = flow.get("irrigation_last") || {};
let app = null;
if (key !== last.key || now - (last.at || 0) >= 60000) {
    flow.set("irrigation_last", {key: key, at: now});
    app = {topic: "zara/interface/irrigation/state/stat", retain: true, qos: 1,
           payload: Object.assign({}, state, {updated_at_ms: now})};
}
return [app, null, null, emon];
