// HEATING-PROFILE.1 (2026-10-16): heating profile (Spento / Ridotto / Pieno, automatic from the
// 72 h outdoor mean or forced) and thermostat timers. Inside a time slot the thermostat gets its
// max, outside its min. Setpoints are written only on events (slot start/end, profile change,
// min/max change), so a manual or spoken setpoint lasts until the next event. In "shadow" mode
// nothing is sent: decisions are only published and logged. See docs/AI_MODE/PROFILO_RISCALDAMENTO.md.
// HEATING-PROFILE.2 (2026-10-16): the plan also gives each thermostat's next change (for the app)
// and the last user corrections are published for the app's "what it learned" section.
// Outputs: [setpoint commands, retained states, JSONL log lines].
const S = 'file';
const P = 'zara/interface/';
const THERMOSTATS = ['thermostat_living', 'thermostat_bath'];
const ALL = [0, 1, 2, 3, 4, 5, 6], WEEKDAYS = [1, 2, 3, 4, 5], WEEKEND = [0, 6];
// [start minute, end minute, days (0 = Sunday)]
const SCHEDULES = {
    spento: {thermostat_living: [], thermostat_bath: []},
    ridotto: {thermostat_living: [[1020, 1260, ALL]], thermostat_bath: [[1020, 1260, ALL]]},
    pieno: {thermostat_living: [[720, 1200, ALL]],
            thermostat_bath: [[1020, 1320, ALL], [305, 420, WEEKDAYS], [450, 570, WEEKEND]]}
};
// EmonCMS 2017/18-2024/25: below 10 °C heating on 80-100% of days, above 16 °C almost never.
const T = {pieno_below: 10, pieno_exit_above: 11, spento_above: 16, spento_exit_below: 15};
const MIN_HOURS = 48, WINDOW_HOURS = 72, ECHO_MS = 120000;

const now = Date.now();
const local = new Date(now);
const minute = local.getHours() * 60 + local.getMinutes();
const day = local.getDay();
const pad = n => String(n).padStart(2, '0');
const today = local.getFullYear() + '-' + pad(local.getMonth() + 1) + '-' + pad(local.getDate());
const get = (k, d) => { const v = context.get(k, S); return v === undefined ? d : v; };
const set = (k, v) => context.set(k, v, S);
const cmds = [], states = [], logs = [];
const log = (event, data) => logs.push({filename: '/home/pi/AI_climate/heating-' + today + '.jsonl',
    payload: JSON.stringify(Object.assign({at: new Date(now).toISOString(), event}, data))});
const number = v => { const n = parseFloat(v); return Number.isFinite(n) ? n : null; };

let mode = get('mode', 'auto');                 // auto | spento | ridotto | pieno
let timerMode = get('timerMode', 'shadow');     // shadow | active
let autoProfile = get('autoProfile', 'ridotto');
let hourly = get('hourly', {});                 // "YYYY-MM-DDTHH" -> [sum, count]
let limits = get('limits', {});                 // thermostat -> {min, max}
let inside = get('inside', {});                 // thermostat -> bool at the last evaluation
let targets = get('targets', {});               // thermostat -> last setpoint seen
let sent = get('sent', {});                     // thermostat -> {value, at} last command of the timer
let lastProfile = get('lastProfile', null);
let corrections = get('corrections', []);       // last user setpoint changes, newest first
const changed = new Set();                      // thermostats whose min/max changed now
let reason = null;

function outdoorMean() {
    const cutoff = now - WINDOW_HOURS * 3600000;
    const means = Object.entries(hourly).filter(([h]) => Date.parse(h + ':00:00') >= cutoff)
        .map(([, [s, n]]) => s / n);
    return {hours: means.length, mean: means.length ? means.reduce((a, b) => a + b, 0) / means.length : null};
}
function slotOf(profile, t) {
    return SCHEDULES[profile][t].find(([a, b, days]) => days.includes(day) && minute >= a && minute < b) || null;
}
function insideAt(profile, t, d, m) {
    return SCHEDULES[profile][t].some(([a, b, days]) => days.includes(d) && m >= a && m < b);
}
// First slot boundary after now (within a week) where the thermostat changes inside/outside.
function nextChange(profile, t, isInside) {
    const marks = [...new Set(SCHEDULES[profile][t].flatMap(([a, b]) => [a, b]))].sort((x, y) => x - y);
    for (let off = 0; off <= 7; off++) {
        const d = (day + off) % 7;
        for (const m of marks) {
            if (off === 0 && m <= minute) continue;
            if (insideAt(profile, t, d, m) !== isInside) return {day_offset: off, at: pad(Math.floor(m / 60)) + ':' + pad(m % 60), inside: !isInside};
        }
    }
    return null;
}

const topic = msg.topic || '';
if (topic === P + 'env/outdoor/temperature/stat') {
    const v = number(msg.payload);
    if (v !== null && v > -30 && v < 50) {
        const key = local.getFullYear() + '-' + pad(local.getMonth() + 1) + '-' + pad(local.getDate()) + 'T' + pad(local.getHours());
        const cell = hourly[key] || [0, 0];
        hourly[key] = [cell[0] + v, cell[1] + 1];
        const keep = now - (WINDOW_HOURS + 12) * 3600000;
        for (const h of Object.keys(hourly)) if (Date.parse(h + ':00:00') < keep) delete hourly[h];
        set('hourly', hourly);
    }
} else if (topic === P + 'heating/profile/mode/cmd') {
    const m = String(msg.payload).trim().toLowerCase();
    if (['auto', 'spento', 'ridotto', 'pieno'].includes(m) && m !== mode) {
        mode = m; set('mode', mode); reason = 'profile_mode_' + m;
        log('profile_mode', {mode, outdoor_mean_72h: outdoorMean().mean});
    }
} else if (topic === P + 'heating/timer/mode/cmd') {
    const m = String(msg.payload).trim().toLowerCase();
    if (['shadow', 'active'].includes(m) && m !== timerMode) {
        timerMode = m; set('timerMode', timerMode); log('timer_mode', {timer_mode: timerMode});
    }
} else {
    const match = topic.match(/^zara\/interface\/climate\/(thermostat_living|thermostat_bath)\/(min|max|target)_temperature\/stat$/);
    const v = match ? number(msg.payload) : null;
    if (match && v !== null && v >= 5 && v <= 30) {
        const [, t, kind] = match;
        if (kind === 'target') {
            const echo = sent[t] && sent[t].value === v && now - sent[t].at <= ECHO_MS;
            if (targets[t] !== undefined && targets[t] !== v && !echo) {
                const l = limits[t] || {};
                const expected = inside[t] === undefined ? null : (inside[t] ? l.max : l.min);
                const change = {thermostat: t, value: v, previous: targets[t], timer_value: expected,
                    profile: lastProfile, outdoor_mean_72h: outdoorMean().mean};
                log('setpoint_change', change);
                const mean = change.outdoor_mean_72h;
                corrections = [Object.assign({at: new Date(now).toISOString()}, change,
                    {outdoor_mean_72h: mean === null ? null : Math.round(mean * 10) / 10})].concat(corrections).slice(0, 10);
                set('corrections', corrections);
            }
            targets[t] = v; set('targets', targets);
        } else {
            const l = limits[t] || {};
            if (l[kind] !== v) {
                const first = l[kind] === undefined;
                l[kind] = v; limits[t] = l; set('limits', limits);
                if (!first) changed.add(t);
            }
        }
    }
}

// Automatic profile with 1 °C hysteresis; held with less than 48 h of outdoor data.
const outdoor = outdoorMean();
if (outdoor.hours >= MIN_HOURS) {
    const m = outdoor.mean;
    let next = autoProfile;
    if (autoProfile === 'pieno' && m > T.pieno_exit_above) next = m > T.spento_above ? 'spento' : 'ridotto';
    else if (autoProfile === 'spento' && m < T.spento_exit_below) next = m < T.pieno_below ? 'pieno' : 'ridotto';
    else if (autoProfile === 'ridotto') next = m < T.pieno_below ? 'pieno' : m > T.spento_above ? 'spento' : 'ridotto';
    if (next !== autoProfile) {
        log('auto_profile', {from: autoProfile, to: next, outdoor_mean_72h: Math.round(m * 10) / 10, hours: outdoor.hours});
        autoProfile = next; set('autoProfile', autoProfile);
    }
}
const profile = mode === 'auto' ? autoProfile : mode;
const profileChanged = lastProfile !== null && profile !== lastProfile;
if (profileChanged && !reason) reason = 'profile_' + profile;

const plan = {};
for (const t of THERMOSTATS) {
    const slot = slotOf(profile, t);
    const isInside = slot !== null;
    const l = limits[t] || {};
    const value = isInside ? l.max : l.min;
    const boundary = inside[t] !== undefined && inside[t] !== isInside;
    const next = nextChange(profile, t, isInside);
    if (next) {
        const nv = next.inside ? l.max : l.min;
        next.value = nv === undefined ? null : nv;
    }
    plan[t] = {inside: isInside, value: value === undefined ? null : value,
               slot: slot ? `${pad(Math.floor(slot[0] / 60))}:${pad(slot[0] % 60)}-${pad(Math.floor(slot[1] / 60))}:${pad(slot[1] % 60)}` : null,
               min: l.min === undefined ? null : l.min, max: l.max === undefined ? null : l.max, next};
    const why = boundary ? (isInside ? 'slot_start' : 'slot_end') : profileChanged ? reason : changed.has(t) ? 'limits_changed' : null;
    if (why && value !== undefined) {
        if (timerMode === 'active') {
            cmds.push({topic: P + 'climate/' + t + '/target_temperature/cmd', payload: value.toFixed(1)});
            sent[t] = {value, at: now};
        }
        log(timerMode === 'active' ? 'setpoint_sent' : 'setpoint_shadow',
            {thermostat: t, value, inside: isInside, reason: why, profile, outdoor_mean_72h: outdoor.mean});
    }
    inside[t] = isInside;
}
set('inside', inside); set('sent', sent);
if (lastProfile !== profile) { lastProfile = profile; set('lastProfile', profile); }

const state = {schema: 'domopi.heating_profile.v1', profile, mode, auto_profile: autoProfile,
    outdoor_mean_72h: outdoor.mean === null ? null : Math.round(outdoor.mean * 10) / 10,
    hours_of_data: outdoor.hours, thresholds: T, timer_mode: timerMode};
const planState = {schema: 'domopi.heating_timer_plan.v1', profile, timer_mode: timerMode, thermostats: plan};
const learning = {schema: 'domopi.heating_learning.v1', learning_active: false, corrections};
const text = JSON.stringify([state, planState, learning]);
const lastState = context.get('lastState');   // memory: republished after every restart
if (text !== lastState || minute % 60 === 0 && msg.tick) {
    context.set('lastState', text);
    states.push({topic: P + 'heating/profile/stat', payload: JSON.stringify(state), retain: true});
    states.push({topic: P + 'heating/timer/plan/stat', payload: JSON.stringify(planState), retain: true});
    states.push({topic: P + 'heating/profile/mode/stat', payload: mode, retain: true});
    states.push({topic: P + 'heating/timer/mode/stat', payload: timerMode, retain: true});
    states.push({topic: P + 'heating/profile/learning/stat', payload: JSON.stringify(learning), retain: true});
}
return [cmds, states, logs];
