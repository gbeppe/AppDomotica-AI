// ANNOUNCER.1 (2026-10-31): voice announcements of the house, SHADOW phase (docs/AI_MODE/ANNUNCI_VOCALI.md): it
// decides what it would say and only writes the log, nothing is spoken. Table and rules agreed with the user.
// Inputs: MQTT state topics, the alerts of HOUSE-ALERTS.1 (msg.alert), a tick every 60 s.
// Output: retained log zara/interface/announcer/log/stat.
const MIN = 60000, HOUR = 60 * MIN, PAUSE = 2 * HOUR, CAP_H = 3, CAP_D = 8;
const PUFFER = {high: 60, back: 58, confirm: MIN}, STALE_PEER = 15 * MIN;
const CRITICAL = /^(service:|http:|broker$|internet$)/;
const CALM = /^(puffer|battery|profile)/;   // alerts of HOUSE-ALERTS.1 not announced: puffer here, the others are phone only
const MODES = {heat: 'caldo', cool: 'raffrescamento', dry: 'deumidifica', standby: 'standby', auto: 'automatico'};
const BY = {user: 'manuale', ai: 'automatica', watchdog: 'dal vigilante'};
const now = new Date(), ms = now.getTime();
const p2 = n => String(n).padStart(2, '0');
const day = now.getFullYear() + '-' + p2(now.getMonth() + 1) + '-' + p2(now.getDate());
const hhmm = p2(now.getHours()) + ':' + p2(now.getMinutes());
const weekend = now.getDay() === 0 || now.getDay() === 6;
const active = hhmm >= (weekend ? '09:30' : '08:30') && hhmm < '21:30';
const a = flow.get('announcer', 'file') || {log: [], last: {}, hours: [], date: null, count: 0, kwh: 0, kwh_at: 0, alerts_today: 0,
    ac: null, fire: null, peer: {}, bad: {}, puffer_since: 0, puffer_on: false, summary: null, seen: {}};
if (a.date !== day) { a.date = day; a.count = 0; a.kwh = 0; a.alerts_today = 0; }
let wrote = false;
const log = (level, key, text, echo, result, volume) => {
    a.log.push({ts: new Date(ms).toISOString(), level, key, text, echo, volume, result});
    a.log = a.log.slice(-1000);
    wrote = true;
};
// command = air conditioner and fireplace: they do not count in the cap and have no pause
function say(level, key, text, echo, command) {
    if (level === 'urgente') return log(level, key, text, active ? echo : 'living e camera', 'direbbe', active ? 'normale' : 'basso');
    if (!active) return log(level, key, text, echo, 'tace: ore tranquille');
    if (!command) {
        if (ms - (a.last[key] || 0) < PAUSE) return log(level, key, text, echo, 'tace: già detto da meno di 2 ore');
        a.hours = a.hours.filter(t => ms - t < HOUR);
        if (a.hours.length >= CAP_H) return log(level, key, text, echo, 'tace: tetto orario');
        if (a.count >= CAP_D) return log(level, key, text, echo, 'tace: tetto giornaliero');
        a.hours.push(ms); a.count++; a.last[key] = ms; a.alerts_today++;
    }
    log(level, key, text, echo, 'direbbe', 'normale');
}
const parse = v => { try { return JSON.parse(v); } catch (e) { return null; } };
const t = msg.topic;
if (msg.alert) {
    const al = msg.payload || {};
    if (al.key && !CALM.test(al.key) && !al.key.endsWith('_ok')) say('informativo', al.key, al.title || al.message, 'living', false);
} else if (t === 'zara/interface/climate/ac/state/stat') {
    const s = parse(msg.payload);
    if (s && s.current) {
        const c = s.current, on = c.mode !== 'off', key = c.mode + '/' + c.setpoint;
        if (a.ac !== null && a.ac !== key) {
            const how = BY[c.by] || 'automatica';
            say('informativo', 'ac', on ? 'Condizionatore acceso in ' + MODES[c.mode] + (c.setpoint ? ' a ' + c.setpoint + ' gradi' : '') + ', ' + how
                : 'Condizionatore spento, ' + how, 'living', true);
        }
        a.ac = key;
    }
} else if (t === 'zara/interface/fireplace/main/mode/stat') { a.seen.mode = String(msg.payload);
} else if (t === 'zara/interface/fireplace/main/start_time/stat') { a.seen.start = String(msg.payload);
} else if (t === 'zara/interface/fireplace/main/stop_time/stat') { a.seen.stop = String(msg.payload);
} else if (t === 'zara/interface/fireplace/main/power/stat') {
    const on = String(msg.payload).trim() === 'true';
    if (a.fire !== null && a.fire !== on) {
        const m = a.seen.mode || '', auto = !/manuale/i.test(m);
        say('informativo', 'fire', on ? 'Caminetto acceso in modalità ' + (auto ? 'automatica dalle ' + a.seen.start + ' alle ' + a.seen.stop : 'manuale') : 'Caminetto spento', 'living', true);
    }
    a.fire = on;
} else if (t === 'zara/interface/heating/puffer/top_temperature/stat') {
    const v = parseFloat(msg.payload);
    if (v >= PUFFER.high) { if (!a.puffer_since) a.puffer_since = ms; a.puffer_v = v; }
    else { a.puffer_since = 0; if (v < PUFFER.back) a.puffer_on = false; }
} else if (t === 'TeslaPowerwall/solar_instant_power') {
    const v = parseFloat(msg.payload);
    if (a.kwh_at && ms - a.kwh_at < 10 * MIN && v > 0) a.kwh += v * (ms - a.kwh_at) / 3.6e9;
    a.kwh_at = ms;
} else if (t === 'zara/interface/energy/battery/soc/stat') { a.soc = parseFloat(msg.payload);
} else if (t && t.startsWith('zara/system/health/')) {
    const h = parse(msg.payload), id = t.split('/')[3];
    if (h && h.checks) {
        a.peer[id] = ms;
        const bad = h.checks.filter(c => !c.ok).map(c => c.id), was = a.bad[id] || [];
        for (const c of h.checks) {
            if (c.ok || was.includes(c.id)) continue;
            if (CRITICAL.test(c.id)) say('urgente', id + ':' + c.id, c.title + ' non funziona sul punto ' + id, 'living e camera');
            else if (c.id === 'powerwall_charge') say('informativo', 'powerwall', 'La batteria non si sta caricando', 'living', false);
        }
        a.bad[id] = bad;
    }
} else if (t === 'tick') {
    if (a.puffer_since && !a.puffer_on && ms - a.puffer_since >= PUFFER.confirm) {
        a.puffer_on = true;
        say('urgente', 'puffer', 'Attenzione: puffer a ' + Math.round(a.puffer_v) + ' gradi', 'living e camera');
    }
    if (a.peer['15'] && ms - a.peer['15'] > STALE_PEER && !a.peer_down) {
        a.peer_down = true;
        say('urgente', 'peer15', 'Il server punto 15 non risponde', 'living e camera');
    } else if (a.peer['15'] && ms - a.peer['15'] <= STALE_PEER) a.peer_down = false;
    if (hhmm >= '21:00' && hhmm < '21:05' && a.summary !== day) {
        a.summary = day;
        say('riepilogo', 'summary', 'Oggi ' + Math.round(a.kwh) + ' chilowattora, batteria ' + Math.round(a.soc || 0) + ' percento, '
            + (a.alerts_today ? a.alerts_today + ' avvisi' : 'impianto regolare'), 'living', true);
    }
}
flow.set('announcer', a, 'file');
return wrote ? {topic: 'zara/interface/announcer/log/stat', retain: true,
    payload: JSON.stringify({schema: 'domopi.announcer.v1', mode: 'shadow', log: a.log.slice(-100)})} : null;
