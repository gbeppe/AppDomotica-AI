// AI-CLIMATE-VMC-OVERRIDE.1 (2026-10-18): manual priority of the VMC speed with an end time
// (docs/AI_MODE/COMANDI_A_TEMPO.md, phase T2). Input zara/interface/ventilation/vmc/override/cmd:
// {"speed": 1..4, "until": "<ISO with time zone>"} (at most 48 h ahead) or "auto"; a tick every 60 s.
// While the priority lasts the AI Climate function uses this speed (flow vmc_override, file store);
// at the end, or with "auto", AI Climate takes over again at its next cycle.
// Outputs: [speed command for the router, retained state].
const now = Date.now();
const STATE = 'zara/interface/ventilation/vmc/override/stat';
let ov = flow.get('vmc_override', 'file') || null;
const out = [null, null];
const label = iso => { const d = new Date(Date.parse(iso)); return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0'); };
const state = () => ({topic: STATE, retain: true, payload: JSON.stringify(ov
    ? {schema: 'domopi.vmc_override.v1', active: true, speed: ov.speed, until: ov.until, label: ov.label}
    : {schema: 'domopi.vmc_override.v1', active: false})});
const resume = () => { ov = null; flow.set('vmc_override', null, 'file'); flow.set('vmc_ultimo_cambio_ms', 0); };

if (msg.topic === 'tick') {
    if (ov && now >= Date.parse(ov.until)) { resume(); node.status({fill: 'grey', shape: 'ring', text: 'automatico'}); out[1] = state(); }
    else if (msg.refresh) out[1] = state();
    return out;
}
let body = msg.payload;
if (typeof body === 'string') { try { body = JSON.parse(body); } catch (e) { body = body.trim(); } }
if (body === 'auto') {
    resume();
    node.status({fill: 'grey', shape: 'ring', text: 'automatico'});
    out[1] = state();
    return out;
}
const speed = body && Number(body.speed), until = body && Date.parse(body.until);
if (!body || !Number.isInteger(speed) || speed < 1 || speed > 4 || typeof body.until !== 'string'
        || !/[+-]\d\d:\d\d$|Z$/.test(body.until) || !Number.isFinite(until) || until <= now || until > now + 48 * 3600000) {
    node.warn('priorità VMC non valida: ' + JSON.stringify(msg.payload).slice(0, 120));
    return out;
}
ov = {speed, until: body.until, label: label(body.until), set_at: new Date(now).toISOString()};
flow.set('vmc_override', ov, 'file');
flow.set('vmc_velocita_inviata', speed);          // AI Climate does not resend it while the priority lasts
flow.set('vmc_ultimo_cambio_ms', now);
node.status({fill: 'blue', shape: 'dot', text: 'velocità ' + speed + ' fino alle ' + ov.label});
out[0] = {topic: 'zara/interface/ventilation/vmc/speed/cmd', payload: String(speed)};
out[1] = state();
return out;
