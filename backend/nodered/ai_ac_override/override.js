// AI-CLIMATE-AC-OVERRIDE.1 (2026-10-23): manual priority of the air conditioner with an end time
// (docs/AI_MODE/COMANDI_A_TEMPO.md, phase T4). Input zara/interface/climate/ac/override/cmd:
// {"mode": "off|heat|cool|dry", "setpoint": n, "until": "<ISO with time zone>"} (at most 6 h ahead,
// user's decision) or "auto"; a tick every 60 s. The AI Climate function applies it (flow ac_override,
// file store) with the compressor timers; a battery protection closes it (flow ac_override_closed).
// Outputs: [retained state, alert for the phone (ntfy chain of HOUSE-ALERTS.1)].
const now = Date.now();
const STATE = 'zara/interface/climate/ac/override/stat';
const MAX_MS = 6 * 3600000;
// Setpoint range and default per mode: the codes of the IR dictionary (eco16heat..eco22heat, 22..28, 23dry..25dry).
const RANGES = {heat: [16, 22, 20], cool: [22, 28, 24], dry: [23, 25, 24]};
const NAMES = {off: 'spento', heat: 'caldo', cool: 'raffrescamento', dry: 'deumidifica'};
let ov = flow.get('ac_override', 'file') || null;
const out = [null, null];
const label = iso => { const d = new Date(Date.parse(iso)); return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0'); };
const state = extra => ({topic: STATE, retain: true, payload: JSON.stringify(ov
    ? {schema: 'domopi.ac_override.v1', active: true, mode: ov.mode, setpoint: ov.setpoint, until: ov.until, label: ov.label}
    : Object.assign({schema: 'domopi.ac_override.v1', active: false}, extra || {}))});
const code = (mode, setpoint) => mode === 'off' ? 'OFF' : mode === 'heat' ? 'eco' + setpoint + 'heat'
    : mode === 'dry' ? setpoint + 'dry' : String(setpoint);
const end = () => { ov = null; flow.set('ac_override', null, 'file'); node.status({fill: 'grey', shape: 'ring', text: 'automatico'}); };

if (msg.topic === 'tick') {
    const closed = flow.get('ac_override_closed', 'file');
    if (closed && !closed.notified) {
        flow.set('ac_override_closed', Object.assign({}, closed, {notified: true}), 'file');
        end();
        out[0] = state({closed_reason: closed.reason});
        out[1] = {payload: {title: 'Condizionatore: comando manuale chiuso', tags: ['warning'], priority: 4,
            message: 'Protezione della batteria (' + closed.reason + '): il condizionatore ' + NAMES[closed.mode]
                + ' è stato chiuso e AI Climate ha ripreso il controllo.'}};
        return out;
    }
    if (ov && now >= Date.parse(ov.until)) { end(); out[0] = state(); }
    else if (msg.refresh) out[0] = state();
    return out;
}
let body = msg.payload;
if (typeof body === 'string') { try { body = JSON.parse(body); } catch (e) { body = body.trim(); } }
if (body === 'auto') { end(); out[0] = state(); return out; }
const mode = body && body.mode, until = body && Date.parse(body.until);
const range = RANGES[mode];
let setpoint = range ? (body.setpoint === undefined || body.setpoint === null ? range[2] : Number(body.setpoint)) : null;
if (!body || !(mode === 'off' || range) || (range && (!Number.isInteger(setpoint) || setpoint < range[0] || setpoint > range[1]))
        || typeof body.until !== 'string' || !/[+-]\d\d:\d\d$|Z$/.test(body.until) || !Number.isFinite(until)
        || until <= now || until > now + MAX_MS) {
    node.warn('priorità condizionatore non valida: ' + JSON.stringify(msg.payload).slice(0, 160));
    return out;
}
ov = {mode, setpoint, code: code(mode, setpoint), until: body.until, label: label(body.until), set_at: new Date(now).toISOString()};
flow.set('ac_override', ov, 'file');
flow.set('ac_override_closed', null, 'file');
flow.set('ac_override_inviato', null, 'file');      // the AI Climate function sends the code at its next cycle
node.status({fill: 'blue', shape: 'dot', text: NAMES[mode] + (setpoint ? ' ' + setpoint + '°' : '') + ' fino alle ' + ov.label});
out[0] = state();
return out;
