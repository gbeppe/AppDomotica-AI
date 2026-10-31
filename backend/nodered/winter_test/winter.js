// WINTER-TEST.1 (2026-10-31): winter field test of AI Climate (docs/AI_MODE/AI_CLIMATE_INVERNO.md section 8).
// Off until enabled. On suitable days (FV forecast at 08:30, outside temperature and fireplace at 10:30) test and
// control days alternate. Test day, 10:30-15:00: air conditioner heat 20 "solar only" (AI-CLIMATE-AC-OVERRIDE.2) and
// living thermostat 1 degree under the plan of the heating timers, set again if a timer changes it; at the end
// the thermostat goes back to the plan. The state is in the file store: a restart resumes or closes the run.
// Input zara/interface/climate/winter_test/cmd: {"action":"on|off"} or {"action":"run","until":"<ISO>"} (trial).
// Outputs: [MQTT commands and retained state, alert for the phone].
const PREFIX = 'zara/interface/climate/winter_test/';
const TH = 'zara/interface/climate/thermostat_living/target_temperature/';
const AC_CMD = 'zara/interface/climate/ac/override/cmd';
const MIN_KWH = 12, TEMP_MIN = 5, TEMP_MAX = 15, COEFF = {eclipse: 0.12, viessmann: 0.195};
const START = '10:30', LAST_START = '11:00', END = '15:00', NOTICE = '08:30', FLOOR = 16, MAX_MS = 6 * 3600000;

const now = new Date(), nowMs = now.getTime();
const p2 = n => String(Math.floor(Math.abs(n))).padStart(2, '0');
const day = now.getFullYear() + '-' + p2(now.getMonth() + 1) + '-' + p2(now.getDate());
const hhmm = p2(now.getHours()) + ':' + p2(now.getMinutes());
const iso = ms => { const d = new Date(ms), o = -d.getTimezoneOffset();
    return d.getFullYear() + '-' + p2(d.getMonth() + 1) + '-' + p2(d.getDate()) + 'T' + p2(d.getHours()) + ':' + p2(d.getMinutes())
        + ':' + p2(d.getSeconds()) + (o >= 0 ? '+' : '-') + p2(o / 60) + ':' + p2(o % 60); };
const cmds = [], alerts = [];
const send = (topic, payload, retain) => cmds.push({topic, payload, retain: !!retain});
const alert = (title, message) => alerts.push({payload: {title, tags: ['thermometer'], priority: 3, message}});
const wt = flow.get('winter_test', 'file') || {enabled: false, last_type: null, day: null, run: null, journal: []};
const planValue = () => flow.get('wt_plan') === undefined ? null : flow.get('wt_plan');
const log = entry => { wt.journal.push(Object.assign({day}, entry)); wt.journal = wt.journal.slice(-150); };
const finish = (result) => {
    const run = wt.run;
    wt.run = null;
    const back = planValue() !== null ? planValue() : run.base;
    if (back !== null) send(TH + 'cmd', String(back));
    if (result !== 'fine') send(AC_CMD, 'auto');
    const e = wt.journal.slice().reverse().find(x => x.started === run.started);
    if (e) { e.ended = iso(nowMs); e.result = result; }
    if (result !== 'fine') alert('Prova invernale chiusa', 'Motivo: ' + result + '. Termostato del living riportato al piano.');
};
const startRun = (type, untilMs) => {
    const base = planValue() !== null ? planValue() : flow.get('wt_target');
    wt.run = {type, started: iso(nowMs), until: untilMs, base: base === undefined ? null : base, ac_seen: false};
    log({type, started: wt.run.started, base: wt.run.base});
    send(AC_CMD, JSON.stringify({mode: 'heat', setpoint: 20, until: iso(untilMs), solar_only: true}));
};
const atToday = t => new Date(day + 'T' + t + ':00').getTime();

const t = msg.topic;
if (t === 'zara/interface/heating/timer/plan/stat') {
    try { const v = JSON.parse(msg.payload).thermostats.thermostat_living.value;
        if (Number.isFinite(v)) flow.set('wt_plan', v); } catch (e) { node.warn('piano non leggibile'); }
} else if (t === 'zara/interface/energy/solar_forecast/stat') {
    try { const f = JSON.parse(msg.payload).faces;
        flow.set('wt_kwh', f.eclipse.today_kwh * COEFF.eclipse + f.viessmann.today_kwh * COEFF.viessmann); } catch (e) { node.warn('previsione non leggibile'); }
} else if (t === TH + 'stat') {
    flow.set('wt_target', parseFloat(msg.payload));
} else if (t === 'zara/interface/fireplace/main/power/stat') {
    flow.set('wt_fire', String(msg.payload).trim() === 'true');
} else if (t === 'zara/interface/climate/ac/override/stat') {
    try { const o = JSON.parse(msg.payload);
        if (wt.run) { if (o.active) wt.run.ac_seen = true; else if (wt.run.ac_seen && nowMs < wt.run.until - 90000) finish(o.closed_reason ? 'solo sole' : 'comando annullato'); } } catch (e) { node.warn('stato priorità non leggibile'); }
} else if (t && t.startsWith('zigbee2mqtt/')) {
    try { const v = JSON.parse(msg.payload).temperature; if (Number.isFinite(v)) flow.set('wt_out', v); } catch (e) { node.warn('esterno non leggibile'); }
} else if (t === PREFIX + 'cmd') {
    let c = msg.payload; try { c = JSON.parse(c); } catch (e) { c = null; }
    const a = c && c.action;
    if (a === 'on') wt.enabled = true;
    else if (a === 'off') { wt.enabled = false; if (wt.run) finish('spenta a mano'); }
    else if (a === 'run') {
        const u = Date.parse(c.until);
        if (wt.run || !Number.isFinite(u) || u <= nowMs || u > nowMs + MAX_MS || !/[+-]\d\d:\d\d$|Z$/.test(c.until)) node.warn('collaudo non valido');
        else startRun('collaudo', u);
    } else node.warn('comando non valido');
} else if (t === 'tick') {
    if (wt.enabled && hhmm >= NOTICE && hhmm < START && (!wt.day || wt.day.date !== day)) {
        const kwh = flow.get('wt_kwh');
        wt.day = {date: day, suitable: Number.isFinite(kwh) && kwh >= MIN_KWH, kwh: Number.isFinite(kwh) ? Math.round(kwh * 10) / 10 : null, done: false};
        if (wt.day.suitable) {
            const next = wt.last_type === 'prova' ? 'confronto' : 'prova';
            alert('Prova invernale: giorno ' + next, next === 'prova'
                ? 'Oggi FV previsto ' + wt.day.kwh + ' kWh: se l\'esterno va bene, alle 10:30 parte la prova. Caminetto spento dalle 10:30 alle 15.'
                : 'Oggi FV previsto ' + wt.day.kwh + ' kWh: giorno di confronto, tutto normale ma caminetto spento dalle 10:30 alle 15.');
        }
    }
    if (wt.enabled && wt.day && wt.day.date === day && wt.day.suitable && !wt.day.done && hhmm >= START && hhmm < LAST_START) {
        wt.day.done = true;
        const out = flow.get('wt_out'), type = wt.last_type === 'prova' ? 'confronto' : 'prova';
        const why = !Number.isFinite(out) ? 'esterno sconosciuto' : out < TEMP_MIN || out > TEMP_MAX ? 'esterno ' + out + ' °C'
            : flow.get('wt_fire') ? 'caminetto acceso' : null;
        if (why) log({type: 'saltato', reason: why, kwh: wt.day.kwh});
        else {
            wt.last_type = type;
            if (type === 'prova') startRun('prova', atToday(END));
            else log({type: 'confronto', kwh: wt.day.kwh, temp: out});
        }
    }
    if (wt.run) {
        if (nowMs >= wt.run.until) finish('fine');
        else if (flow.get('wt_fire') && wt.run.type === 'prova') finish('caminetto acceso');
        else {
            const base = planValue() !== null ? planValue() : wt.run.base;
            const want = base === null ? null : Math.max(FLOOR, base - 1);
            if (want !== null && flow.get('wt_target') !== want) send(TH + 'cmd', String(want));
        }
    }
}
flow.set('winter_test', wt, 'file');
send(PREFIX + 'stat', JSON.stringify({schema: 'domopi.winter_test.v1', enabled: wt.enabled, last_type: wt.last_type, day: wt.day,
    run: wt.run && {type: wt.run.type, started: wt.run.started, until: iso(wt.run.until), base: wt.run.base}, journal: wt.journal}), true);
return [cmds.map(m => m), alerts];
