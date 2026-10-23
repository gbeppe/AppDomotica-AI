// RULES-ENGINE.1 (2026-10-16): natural-language rules, phase H1. See docs/AI_MODE/REGOLE_NATURALI.md.
// A rule = one trigger (threshold on an allowed measure, or a time of day), up to 2 conditions, one
// allowed action, a lifetime (once by default), a cooldown. Every new rule is "in test" for 24 h:
// it logs and announces what it would do, then becomes active. The engine itself starts in
// "shadow": no rule sends commands until the engine is switched to "active".
// Inputs: measures (retained /stat topics), zara/interface/rules/<verb>/cmd, a tick every 30 s.
// Outputs: [commands, retained states, JSONL log lines, events for notifications].
// RULES-ENGINE.3 (2026-10-18): timed commands ("accendi X fino alle 19", docs/AI_MODE/COMANDI_A_TEMPO.md):
// trigger {kind: 'at', at: ISO} = one moment with its date (from 15 min ago to 48 h ahead), always once;
// "start_active": true (only with 'at') skips the test day; a late 'at' (> 15 min) closes as missed;
// closed 'at' rules leave the list after 24 h.
// RULES-ENGINE.4 (2026-10-18): VMC actions (phase T2): set_vmc_speed (plain speed 1-4), set_vmc_override
// (manual priority until a moment, AI-CLIMATE-VMC-OVERRIDE.1), set_vmc_auto (back to AI Climate).
// RULES-ENGINE.5 (2026-10-23): air conditioner actions (phase T4): set_ac_override (off, or heat/cool/dry
// at a setpoint, until a moment at most 6 hours after the start; AI-CLIMATE-AC-OVERRIDE.1), set_ac_auto.
const S = 'file';
const P = 'zara/interface/';
const MAX_ACTIVE = 10, TEST_MS = 24 * 3600000, DEFAULT_COOLDOWN_MIN = 30;
const AT_PAST_MS = 15 * 60000, AT_AHEAD_MS = 48 * 3600000, AT_KEEP_MS = 24 * 3600000;
// measure -> [topic, min, max, stale after ms, re-arm margin]
const MEASURES = {
    puffer_top: ['heating/puffer/top_temperature/stat', 10, 95, 600000, 0.5],
    puffer_bottom: ['heating/puffer/bottom_temperature/stat', 10, 95, 600000, 0.5],
    outdoor: ['env/outdoor/temperature/stat', -20, 45, 900000, 0.5],
    living: ['env/living/temperature/stat', 5, 35, 900000, 0.5],
    bedroom: ['env/bedroom/temperature/stat', 5, 35, 900000, 0.5],
    kids_room: ['env/kids_room/temperature/stat', 5, 35, 900000, 0.5],
    service_bathroom: ['env/service_bathroom/temperature/stat', 5, 35, 900000, 0.5],
    battery_soc: ['energy/battery/soc/stat', 0, 100, 300000, 2],
    solar_power: ['energy/solar/power/stat', 0, 15000, 300000, 200],
    home_power: ['energy/home/consumption/stat', 0, 15000, 300000, 200],
};
const BY_TOPIC = Object.fromEntries(Object.entries(MEASURES).map(([k, v]) => [P + v[0], k]));
const LIGHTS = ['lights_living', 'lights_libreria', 'lights_tv', 'lights_reading', 'lights_laundry',
    'lights_porch_entrance', 'lights_service_entrance', 'lights_prolunga'];
const SCENES = ['tv', 'sleep', 'all_on', 'all_off'];
const THERMOSTATS = ['thermostat_living', 'thermostat_bath'];
const FIREPLACE_MODES = ['Disattivato', 'Riscaldamento', 'Integrazione Caldaia', 'Acqua Sanitaria', 'Manuale'];
const PROFILE_MODES = ['auto', 'spento', 'ridotto', 'pieno'];
// Setpoint range per air conditioner mode: the codes of the IR dictionary (AI-CLIMATE-AC-OVERRIDE.1).
const AC_RANGES = {heat: [16, 22], cool: [22, 28], dry: [23, 25]};
const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;

const now = Date.now();
const local = new Date(now);
const pad = n => String(n).padStart(2, '0');
const today = local.getFullYear() + '-' + pad(local.getMonth() + 1) + '-' + pad(local.getDate());
const hhmm = pad(local.getHours()) + ':' + pad(local.getMinutes());
const get = (k, d) => { const v = context.get(k, S); return v === undefined ? d : v; };
const set = (k, v) => context.set(k, v, S);
const cmds = [], states = [], logs = [], events = [];
const log = (event, data) => logs.push({filename: '/home/pi/AI_climate/rules-' + today + '.jsonl',
    payload: JSON.stringify(Object.assign({at: new Date(now).toISOString(), event}, data))});
const announce = (kind, rule, extra) => {
    const e = Object.assign({schema: 'domopi.rule_event.v1', at: new Date(now).toISOString(), kind,
        rule_id: rule.id, text: rule.text}, extra || {});
    events.push({topic: P + 'rules/events', payload: JSON.stringify(e)});
    states.push({topic: P + 'rules/last_event/stat', payload: JSON.stringify(e), retain: true});
};
const num = v => { const n = parseFloat(v); return Number.isFinite(n) ? n : null; };

let engineMode = get('engineMode', 'shadow');   // shadow | active
let rules = get('rules', {});                    // id -> rule (validated)
let st = get('ruleState', {});                   // id -> {armed, lastFired, lastFiredDay, testUntil, status}
let devices = get('devices', {});                // fireplace mode / power, for action guards
let measures = context.get('measures') || {};    // memory: refilled from retained topics
let changed = false;

// ---------------------------------------------------------------- validation
function validMeasure(m) { return Object.prototype.hasOwnProperty.call(MEASURES, m); }
function validThreshold(t) {
    if (!t || typeof t !== 'object') return false;
    if (t.kind === 'threshold') {
        const v = num(t.value), lim = MEASURES[t.metric];
        return validMeasure(t.metric) && (t.op === 'above' || t.op === 'below') && v !== null && v >= lim[1] && v <= lim[2];
    }
    if (t.kind === 'time') return TIME.test(t.at || '') && Array.isArray(t.days) && t.days.length > 0 &&
        t.days.every(d => Number.isInteger(d) && d >= 0 && d <= 6);
    return false;
}
function validAt(t) {
    if (!t || t.kind !== 'at' || typeof t.at !== 'string' || !/[+-]\d\d:\d\d$|Z$/.test(t.at)) return false;
    const when = Date.parse(t.at);
    return Number.isFinite(when) && when >= now - AT_PAST_MS && when <= now + AT_AHEAD_MS;
}
function validCondition(c) {
    if (!c || typeof c !== 'object') return false;
    if (c.kind === 'threshold') return validThreshold(c);
    if (c.kind === 'time_window') return TIME.test(c.from || '') && TIME.test(c.to || '') && c.from !== c.to;
    if (c.kind === 'fireplace_mode') return FIREPLACE_MODES.includes(c.value);
    return false;
}
function validAction(a) {
    if (!a || typeof a !== 'object') return false;
    switch (a.tool) {
        case 'set_light_state': return LIGHTS.includes(a.light) && (a.state === 'on' || a.state === 'off');
        case 'set_light_scene': return SCENES.includes(a.scene);
        case 'set_thermostat_target': {
            const v = num(a.target_c);
            return THERMOSTATS.includes(a.thermostat) && v !== null && v >= 16 && v <= 22 && Math.round(v * 2) === v * 2;
        }
        case 'set_fireplace_power': return a.state === 'on' || a.state === 'off';
        case 'set_fireplace_level': return Number.isInteger(a.level) && a.level >= 1 && a.level <= 6;
        case 'set_fireplace_mode': return FIREPLACE_MODES.includes(a.mode);
        case 'set_heating_profile': return PROFILE_MODES.includes(a.mode);
        case 'set_vmc_speed': return Number.isInteger(a.speed) && a.speed >= 1 && a.speed <= 4;
        case 'set_vmc_override': return Number.isInteger(a.speed) && a.speed >= 1 && a.speed <= 4 &&
            typeof a.until === 'string' && /[+-]\d\d:\d\d$|Z$/.test(a.until) && Date.parse(a.until) > now &&
            Date.parse(a.until) <= now + AT_AHEAD_MS + 4 * 3600000;
        case 'set_vmc_auto': return true;
        case 'set_ac_override': {
            const range = AC_RANGES[a.mode];
            return (a.mode === 'off' ? a.setpoint === undefined || a.setpoint === null
                    : !!range && Number.isInteger(a.setpoint) && a.setpoint >= range[0] && a.setpoint <= range[1]) &&
                typeof a.until === 'string' && /[+-]\d\d:\d\d$|Z$/.test(a.until) && Date.parse(a.until) > now &&
                Date.parse(a.until) <= now + AT_AHEAD_MS + 6 * 3600000;
        }
        case 'set_ac_auto': return true;
        default: return false;
    }
}
function validate(r) {
    if (!r || typeof r !== 'object') return 'regola non valida';
    if (typeof r.text !== 'string' || !r.text.trim() || r.text.length > 300) return 'manca il testo della regola';
    const timed = r.trigger && r.trigger.kind === 'at';
    if (timed ? !validAt(r.trigger) : !validThreshold(r.trigger)) return timed ? 'orario non valido' : 'trigger non ammesso';
    if (r.start_active !== undefined && (r.start_active !== true || !timed)) return 'attivazione immediata solo per i comandi a tempo';
    const conds = r.conditions || [];
    if (!Array.isArray(conds) || conds.length > 2 || !conds.every(validCondition)) return 'condizioni non ammesse';
    if (!validAction(r.action)) return 'azione non ammessa';
    const life = r.lifetime || {kind: 'once'};
    if (!['once', 'until', 'always'].includes(life.kind) || (timed && life.kind !== 'once')) return 'durata non ammessa';
    if (life.kind === 'until' && !(Date.parse(life.until) > now)) return 'data di scadenza non valida';
    const cd = r.cooldown_min === undefined ? DEFAULT_COOLDOWN_MIN : r.cooldown_min;
    if (!Number.isInteger(cd) || cd < 1 || cd > 1440) return 'attesa minima non valida';
    return null;
}

// ---------------------------------------------------------------- evaluation
function reading(m) {
    const r = measures[m];
    if (!r) return {value: null, stale: true};
    return {value: r.value, stale: now - r.at > MEASURES[m][3]};
}
function thresholdTrue(t) {
    const r = reading(t.metric);
    if (r.stale) return null;                    // unknown: never fire on old or missing data
    return t.op === 'above' ? r.value > t.value : r.value < t.value;
}
function rearmed(t) {
    const r = reading(t.metric);
    if (r.stale) return false;
    const margin = MEASURES[t.metric][4];
    return t.op === 'above' ? r.value <= t.value - margin : r.value >= t.value + margin;
}
function inWindow(from, to) { return from < to ? hhmm >= from && hhmm < to : hhmm >= from || hhmm < to; }
function conditionsTrue(r) {
    return (r.conditions || []).every(c => {
        if (c.kind === 'threshold') return thresholdTrue(c) === true;
        if (c.kind === 'time_window') return inWindow(c.from, c.to);
        if (c.kind === 'fireplace_mode') return devices.fireplaceMode === c.value;
        return false;
    });
}
// The same limits AI Smart applies today; null = allowed.
function actionBlocked(a) {
    if (a.tool === 'set_vmc_override' && !(Date.parse(a.until) > now)) return 'fine della priorità VMC già passata';
    if (a.tool === 'set_ac_override' && !(Date.parse(a.until) > now)) return 'fine del comando del condizionatore già passata';
    if (a.tool === 'set_ac_override' && Date.parse(a.until) > now + 6 * 3600000) return 'comando del condizionatore oltre 6 ore';
    if (a.tool === 'set_fireplace_power' && devices.fireplaceMode !== 'Manuale') return 'caminetto non in Manuale';
    if (a.tool === 'set_fireplace_level' && (devices.fireplaceMode !== 'Manuale' || devices.fireplacePower !== true))
        return 'livello solo in Manuale a caminetto acceso';
    return null;
}
function commandOf(a) {
    switch (a.tool) {
        case 'set_light_state': return {topic: P + 'lights/' + a.light.replace(/^lights_/, '') + '/power/cmd', payload: String(a.state === 'on')};
        case 'set_light_scene': return {topic: P + 'lights/scene/cmd', payload: a.scene};
        case 'set_thermostat_target': return {topic: P + 'climate/' + a.thermostat + '/target_temperature/cmd', payload: num(a.target_c).toFixed(1)};
        case 'set_fireplace_power': return {topic: P + 'fireplace/main/power/cmd', payload: String(a.state === 'on')};
        case 'set_fireplace_level': return {topic: P + 'fireplace/main/level/cmd', payload: String(a.level)};
        case 'set_fireplace_mode': return {topic: P + 'fireplace/main/mode/cmd', payload: a.mode};
        case 'set_heating_profile': return {topic: P + 'heating/profile/mode/cmd', payload: a.mode};
        case 'set_vmc_speed': return {topic: P + 'ventilation/vmc/speed/cmd', payload: String(a.speed)};
        case 'set_vmc_override': return {topic: P + 'ventilation/vmc/override/cmd', payload: JSON.stringify({speed: a.speed, until: a.until})};
        case 'set_vmc_auto': return {topic: P + 'ventilation/vmc/override/cmd', payload: 'auto'};
        case 'set_ac_override': return {topic: P + 'climate/ac/override/cmd', payload: JSON.stringify(
            a.mode === 'off' ? {mode: 'off', until: a.until} : {mode: a.mode, setpoint: a.setpoint, until: a.until})};
        case 'set_ac_auto': return {topic: P + 'climate/ac/override/cmd', payload: 'auto'};
    }
    return null;
}
function activeCount() { return Object.keys(rules).filter(id => st[id] && st[id].status !== 'expired').length; }
function expire(id, why) {
    st[id].status = 'expired'; changed = true;
    log('rule_expired', {rule_id: id, reason: why});
}
function fire(id, r, s) {
    const testing = engineMode !== 'active' || now < s.testUntil;
    const blocked = actionBlocked(r.action);
    const cmd = commandOf(r.action);
    s.armed = false; s.lastFired = now; s.lastFiredDay = today; changed = true;
    if (blocked) {
        log('rule_blocked', {rule_id: id, reason: blocked, testing});
        announce('blocked', r, {reason: blocked, testing});
        return;
    }
    if (testing) {
        log('rule_would_fire', {rule_id: id, command: cmd, measures: snapshot(r)});
        announce('would_fire', r, {command: cmd});
        return;                                   // in test nothing is sent and "once" is not consumed
    }
    cmds.push(cmd);
    log('rule_fired', {rule_id: id, command: cmd, measures: snapshot(r)});
    announce('fired', r, {command: cmd});
    if ((r.lifetime || {}).kind === 'once' || !(r.lifetime)) expire(id, 'once');
}
function snapshot(r) {
    const ms = [r.trigger, ...(r.conditions || [])].filter(t => t.kind === 'threshold').map(t => t.metric);
    return Object.fromEntries(ms.map(m => [m, measures[m] ? measures[m].value : null]));
}

// ---------------------------------------------------------------- inputs
const topic = msg.topic || '';
if (BY_TOPIC[topic]) {
    const v = num(msg.payload), m = BY_TOPIC[topic], lim = MEASURES[m];
    if (v !== null && v >= lim[1] - 10 && v <= lim[2] + 10) { measures[m] = {value: v, at: now}; context.set('measures', measures); }
} else if (topic === P + 'fireplace/main/mode/stat') {
    devices.fireplaceMode = String(msg.payload); set('devices', devices);
} else if (topic === P + 'fireplace/main/power/stat') {
    devices.fireplacePower = String(msg.payload) === 'true'; set('devices', devices);
} else if (topic.startsWith(P + 'rules/') && topic.endsWith('/cmd')) {
    const verb = topic.slice((P + 'rules/').length, -'/cmd'.length);
    let body = msg.payload;
    if (typeof body === 'string') { try { body = JSON.parse(body); } catch (e) { body = body.trim(); } }
    if (verb === 'engine') {
        if (['shadow', 'active'].includes(body) && body !== engineMode) {
            engineMode = body; set('engineMode', engineMode); log('engine_mode', {mode: engineMode}); changed = true;
        }
    } else if (verb === 'create') {
        const why = validate(body);
        const id = body && typeof body.id === 'string' && /^[a-z0-9-]{3,40}$/.test(body.id) ? body.id : 'r-' + now.toString(36);
        if (!why && activeCount() >= MAX_ACTIVE) {
            log('rule_rejected', {reason: 'max_rules'});
            events.push({topic: P + 'rules/events', payload: JSON.stringify({schema: 'domopi.rule_event.v1', kind: 'rejected', reason: 'Ci sono già 10 regole attive: cancellane una.'})});
        } else if (why || rules[id]) {
            log('rule_rejected', {reason: why || 'id esistente'});
            events.push({topic: P + 'rules/events', payload: JSON.stringify({schema: 'domopi.rule_event.v1', kind: 'rejected', reason: why || 'regola già esistente'})});
        } else {
            const active = body.start_active === true;
            const rule = {id, text: body.text.trim(), trigger: body.trigger, conditions: body.conditions || [],
                action: body.action, lifetime: body.lifetime || {kind: 'once'}, start_active: active,
                cooldown_min: body.cooldown_min === undefined ? DEFAULT_COOLDOWN_MIN : body.cooldown_min,
                created_at: new Date(now).toISOString()};
            rules[id] = rule;
            // Decision of 16/10/2026: a condition already true at creation acts at once (armed).
            // Timed commands confirmed by the user start active (decision of 18/10/2026).
            st[id] = active ? {armed: true, lastFired: null, lastFiredDay: null, testUntil: now, status: 'active'}
                            : {armed: true, lastFired: null, lastFiredDay: null, testUntil: now + TEST_MS, status: 'test'};
            changed = true;
            log('rule_created', {rule});
            announce('created', rule, active ? {active: true, fires_at: rule.trigger.at} : {test_until: new Date(now + TEST_MS).toISOString()});
        }
    } else if ((verb === 'delete' || verb === 'activate' || verb === 'pause' || verb === 'resume') && rules[body]) {
        const id = body;
        if (verb === 'delete') { log('rule_deleted', {rule_id: id}); announce('deleted', rules[id]); delete rules[id]; delete st[id]; }
        else if (verb === 'activate') {          // a would-fire in test does not delay the first real action
            Object.assign(st[id], {testUntil: now, status: 'active', armed: true, lastFired: null, lastFiredDay: null});
            log('rule_activated', {rule_id: id});
        }
        else if (verb === 'pause') { st[id].status = 'paused'; log('rule_paused', {rule_id: id}); }
        else if (verb === 'resume' && st[id].status === 'paused') { st[id].status = 'test'; log('rule_resumed', {rule_id: id}); }
        changed = true;
    }
}

// ---------------------------------------------------------------- evaluate every rule
for (const [id, r] of Object.entries(rules)) {
    const s = st[id];
    if (s && s.status === 'expired' && r.trigger.kind === 'at' && now - Date.parse(r.trigger.at) > AT_KEEP_MS) {
        delete rules[id]; delete st[id]; changed = true; log('rule_removed', {rule_id: id}); continue;
    }
    if (!s || s.status === 'expired' || s.status === 'paused') continue;
    const life = r.lifetime || {kind: 'once'};
    if (life.kind === 'until' && now > Date.parse(life.until)) { expire(id, 'until'); continue; }
    if (s.status === 'test' && now >= s.testUntil) {
        Object.assign(s, {status: 'active', armed: true, lastFired: null, lastFiredDay: null});   // re-evaluate from scratch
        changed = true;
        log('rule_test_ended', {rule_id: id});
        announce('activated', r);
    }
    const cooled = !s.lastFired || now - s.lastFired >= r.cooldown_min * 60000;
    if (r.trigger.kind === 'threshold') {
        if (!s.armed && rearmed(r.trigger)) { s.armed = true; changed = true; }
        if (s.armed && cooled && thresholdTrue(r.trigger) === true && conditionsTrue(r)) fire(id, r, s);
    } else if (r.trigger.kind === 'at') {
        const when = Date.parse(r.trigger.at);
        if (now >= when && !s.lastFired) {
            if (now - when > AT_PAST_MS) {
                s.lastFired = now; expire(id, 'missed');
                announce('blocked', r, {reason: 'orario superato di oltre 15 minuti, comando non inviato'});
            } else if (conditionsTrue(r)) fire(id, r, s);
        }
    } else if (r.trigger.kind === 'time') {
        const due = hhmm === r.trigger.at && r.trigger.days.includes(local.getDay()) && s.lastFiredDay !== today;
        if (due && conditionsTrue(r)) fire(id, r, s);
    }
}

if (changed) { set('rules', rules); set('ruleState', st); }
const list = {schema: 'domopi.rules.v1', engine_mode: engineMode, max_active: MAX_ACTIVE,
    rules: Object.values(rules).map(r => {
        const s = st[r.id] || {};
        return {id: r.id, text: r.text, status: s.status, armed: s.armed,
            test_until: s.testUntil ? new Date(s.testUntil).toISOString() : null,
            last_fired: s.lastFired ? new Date(s.lastFired).toISOString() : null,
            trigger: r.trigger, conditions: r.conditions, action: r.action, lifetime: r.lifetime,
            start_active: r.start_active === true};
    })};
const text = JSON.stringify(list);
if (text !== context.get('lastList') || (msg.tick && local.getMinutes() === 0 && local.getSeconds() < 30)) {
    context.set('lastList', text);
    states.push({topic: P + 'rules/list/stat', payload: text, retain: true});
}
return [cmds, states, logs, events];
