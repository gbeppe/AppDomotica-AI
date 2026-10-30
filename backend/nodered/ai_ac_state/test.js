// Offline tests for AI-CLIMATE-AC-STATE.1: TZ=Europe/Rome node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const [ai, out] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const [before] = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));
const fn = ai.func;
const at = s => new Date(s).getTime();
function store() {
  const mem = {}, file = {};
  return {mem, file, get: (k, s) => (s === 'file' ? file : mem)[k], set: (k, v, s) => { (s === 'file' ? file : mem)[k] = v; }};
}

// The summary block, run with the variables of one AI Climate cycle.
const block = fn.slice(fn.indexOf('// AI-CLIMATE-AC-STATE.1 (2026-10-30): summary'), fn.indexOf('let msgBroadlink = '));
const VARS = ['statoAttuale', 'comandoIR', 'motivoAc', 'causaTransizioneAc', 'aiAbilitata', 'acRichiestaAi', 'acWatchdogCiclo',
  'adesso', 'acPower', 'tempImpostata', 'surplus', 'soc', 'temp', 'humidex', 'velocitaVmc',
  'MIN_RUN_TIME', 'MIN_OFF_TIME', 'MIN_RUN_MINUTI', 'MIN_OFF_MINUTI'];
const summary = new Function('flow', 'global', 'v', `let {${VARS.join(', ')}} = v;\n${block}\nreturn msgAcState;`);
const cycle = (f, g, o) => {
  const m = summary(f, g, Object.assign({statoAttuale: 'OFF', comandoIR: null, motivoAc: 'LOGICA_INVERNALE_ATTIVA',
    causaTransizioneAc: null, aiAbilitata: true, acRichiestaAi: null, acWatchdogCiclo: false, acPower: 14, tempImpostata: 0,
    surplus: 850, soc: 99, temp: 18.8, humidex: 20.3, velocitaVmc: 1,
    MIN_RUN_TIME: 25 * 60000, MIN_OFF_TIME: 20 * 60000, MIN_RUN_MINUTI: 25, MIN_OFF_MINUTI: 20}, o));
  return m && Object.assign({retain: m.retain, topic: m.topic}, JSON.parse(m.payload));
};

test('the test of 30/10 replayed: manual heat, watchdog, manual off held by the 25 minute run', () => {
  const f = store(), g = store();
  let t = at('2026-10-30T13:38:44+01:00');
  g.file.ultimo_avvio = at('2026-10-30T13:38:23+01:00');
  f.file.ac_override = {mode: 'heat', setpoint: 20, code: 'eco20heat', until: '2026-10-30T15:38:35+01:00', label: '15:38'};
  let s = cycle(f, g, {adesso: t, statoAttuale: 'MANUALE_CALDO', comandoIR: 'eco20heat', causaTransizioneAc: 'MANUALE',
                       motivoAc: 'MANUALE_CALDO_FINO_ALLE_15:38', acPower: 24, tempImpostata: 20});
  a.equal(s.topic, 'zara/interface/climate/ac/state/stat'); a.equal(s.retain, true);
  a.deepEqual(s.current, {mode: 'heat', setpoint: 20, code: 'eco20heat', since: '2026-10-30T13:38:44+01:00', by: 'user'});
  a.deepEqual([s.confirm, s.target, s.guard, s.alarms], [{status: 'starting', power_w: 24}, null, null, []]);
  a.deepEqual(s.context, {surplus_w: 850, soc: 99, temp: 18.8, humidex: 20.3, vmc: 1});
  // 13:44:24 the watchdog restarts it after the remote control
  t = at('2026-10-30T13:44:24+01:00'); g.file.ultimo_avvio = t;
  s = cycle(f, g, {adesso: t, statoAttuale: 'MANUALE_CALDO', comandoIR: 'eco20heat', causaTransizioneAc: 'MANUALE',
                   acWatchdogCiclo: true, motivoAc: 'MANUALE_CALDO_FINO_ALLE_15:38', acPower: 5, tempImpostata: 20});
  a.equal(s.current.by, 'watchdog'); a.deepEqual(s.alarms, ['WATCHDOG_RESTART']);
  // 14:04:30 manual off requested at 13:53:56, held until 14:09:24
  t = at('2026-10-30T14:04:30+01:00');
  f.file.ac_override = {mode: 'off', setpoint: null, code: 'OFF', until: '2026-10-30T15:53:56+01:00', label: '15:53'};
  s = cycle(f, g, {adesso: t, statoAttuale: 'MANUALE_CALDO', motivoAc: 'MANUALE_IN_ATTESA_COMPRESSORE_5_MIN', acPower: 1075, tempImpostata: 20});
  a.deepEqual(s.current, {mode: 'heat', setpoint: 20, code: 'eco20heat', since: '2026-10-30T13:38:44+01:00', by: 'watchdog'});
  a.deepEqual(s.confirm, {status: 'running', power_w: 1075});
  a.deepEqual(s.target, {mode: 'off', setpoint: null, by: 'user', until: '2026-10-30T15:53:56+01:00'});
  a.deepEqual(s.guard, {type: 'min_run', until: '2026-10-30T14:09:24+01:00', minutes: 25});
  // 14:09:25 off; 14:15 the watchdog alarm is still shown (30 minutes), then it goes
  t = at('2026-10-30T14:09:25+01:00'); g.file.ultimo_spegnimento = t;
  s = cycle(f, g, {adesso: t, statoAttuale: 'OFF', comandoIR: 'OFF', causaTransizioneAc: 'MANUALE', motivoAc: 'MANUALE_SPENTO_FINO_ALLE_15:53', acPower: 900});
  a.deepEqual([s.current.mode, s.current.setpoint, s.current.code, s.current.by, s.confirm.status, s.target, s.guard],
              ['off', null, 'OFF', 'user', 'stopping', null, null]);
  s = cycle(f, g, {adesso: at('2026-10-30T14:10:00+01:00'), statoAttuale: 'OFF', motivoAc: 'MANUALE_SPENTO_FINO_ALLE_15:53', acPower: 14});
  a.equal(s.confirm.status, 'off');
  s = cycle(f, g, {adesso: at('2026-10-30T14:15:00+01:00'), statoAttuale: 'OFF', motivoAc: 'MANUALE_SPENTO_FINO_ALLE_15:53', acPower: 14, surplus: 2000});
  a.deepEqual(s.alarms, []);
});

test('power reading: fan only, compressor, no power after 6 minutes', () => {
  const f = store(), g = store(), t = at('2026-10-30T12:00:00+01:00');
  g.file.ultimo_avvio = t - 5 * 60000;
  const st = (p, x = t) => cycle(f, g, {adesso: x, statoAttuale: 'HEAT_DIURNO', acPower: p, tempImpostata: 19, motivoAc: 'X' + p + x});
  a.equal(st(3).confirm.status, 'starting');
  a.equal(st(150).confirm.status, 'starting');
  a.equal(st(200).confirm.status, 'running');
  const s = st(3, t + 2 * 60000);
  a.deepEqual([s.confirm.status, s.alarms], ['no_power', ['ON_NO_POWER']]);
});

test('the AI request held back by a timer is shown only without a manual priority', () => {
  const f = store(), g = store(), t = at('2026-07-20T15:00:00+02:00');
  g.file.ultimo_avvio = t - 10 * 60000; g.file.ultimo_spegnimento = t - 60 * 60000;
  let s = cycle(f, g, {adesso: t, statoAttuale: 'COOLING_ON', acPower: 900, tempImpostata: 23, motivoAc: 'COMFORT_TEMP_BASSA_ATTESA_TIMER',
                       acRichiestaAi: {mode: 'off', setpoint: null, guard: 'min_run'}});
  a.deepEqual(s.target, {mode: 'off', setpoint: null, by: 'ai', until: null});
  a.deepEqual(s.guard, {type: 'min_run', until: '2026-07-20T15:15:00+02:00', minutes: 25});
  s = cycle(f, g, {adesso: t, statoAttuale: 'OFF', motivoAc: 'BLOCCO_TIMER', acRichiestaAi: {mode: 'cool', setpoint: 24, guard: 'min_off'}});
  a.deepEqual(s.guard, {type: 'min_off', until: '2026-07-20T14:20:00+02:00', minutes: 20});
  f.file.ac_override = {mode: 'off', code: 'OFF', until: '2026-07-20T17:00:00+02:00'};
  s = cycle(f, g, {adesso: t, statoAttuale: 'OFF', motivoAc: 'MANUALE_SPENTO_FINO_ALLE_17:00', acRichiestaAi: {mode: 'cool', setpoint: 24, guard: 'min_off'}});
  a.deepEqual([s.target, s.guard], [null, null]);
  f.file.ac_override = null;                                       // end of a manual "on": off after the run
  s = cycle(f, g, {adesso: t, statoAttuale: 'MANUALE_RAFFRESCAMENTO', motivoAc: 'FINE_MANUALE_ATTESA_COMPRESSORE_15_MIN', acPower: 800, tempImpostata: 24});
  a.deepEqual([s.target.mode, s.target.by, s.guard.type], ['off', 'ai', 'min_run']);
});

test('alarms: priority closed by the battery, watchdog suspension until AI Climate is enabled again', () => {
  const f = store(), g = store(), t = at('2026-07-20T22:30:00+02:00');
  f.file.ac_override_closed = {reason: 'SOPRAVVIVENZA', mode: 'cool', at: t - 10 * 60000, notified: true};
  a.deepEqual(cycle(f, g, {adesso: t}).alarms, ['BATTERY_CLOSED_MANUAL']);
  f.file.ac_override_closed = null;
  let s = cycle(f, g, {adesso: t, aiAbilitata: false, acWatchdogCiclo: true, comandoIR: 'OFF', motivoAc: 'WATCHDOG_SOSPENSIONE_SISTEMA_GUASTO'});
  a.deepEqual([s.ai_enabled, s.current.by, s.alarms], [false, 'watchdog', ['WATCHDOG_RESTART', 'WATCHDOG_SUSPENDED']]);
  s = cycle(f, g, {adesso: t + 60 * 60000, aiAbilitata: false, motivoAc: 'SISTEMA_DISABILITATO_DA_UTENTE'});
  a.deepEqual(s.alarms, ['WATCHDOG_SUSPENDED']);
  s = cycle(f, g, {adesso: t + 61 * 60000, motivoAc: 'LOGICA_ESTIVA'});
  a.deepEqual(s.alarms, []);
});

test('sent on change; power and room values at most every 30 s', () => {
  const f = store(), g = store(), t = at('2026-10-30T12:00:00+01:00');
  g.file.ultimo_avvio = t - 30 * 60000;
  const st = (dt, o) => cycle(f, g, Object.assign({adesso: t + dt * 1000, statoAttuale: 'HEAT_DIURNO', acPower: 900, tempImpostata: 19,
                                                   motivoAc: 'LOGICA_INVERNALE_ATTIVA'}, o));
  a.ok(st(0));
  a.equal(st(5), null);                                            // nothing changed
  a.equal(st(10, {acPower: 1000, surplus: 2000}), null);           // changed, but within 30 s
  a.ok(st(31, {acPower: 1000}));
  a.equal(st(62, {acPower: 1020, surplus: 900}), null);            // under 50 W and 100 W
  a.ok(st(63, {motivoAc: 'MANTENIMENTO'}));                        // the reason is a change: at once
});

// The winter decisions with the new "else" branches, run on their own.
const winter = fn.slice(fn.indexOf('        if (MODALITA_STAGIONE === "INVERNO") {'), fn.indexOf('        else if (MODALITA_STAGIONE === "ESTATE") {'));
const W = ['statoAttuale', 'esNotteFascia', 'surplus', 'temp', 'soc', 'kwhDomani', 'consumoStoricoAttuale', 'minMancantiAccensione',
  'minMancantiSpegnimento', 'adesso'];
const runWinter = new Function('flow', 'global', 'v', `const MODALITA_STAGIONE = "INVERNO"; let {${W.join(', ')}} = v;
  let sogliaAttivazioneLogica, socMinimoLogico, motivoAc, comandoIR = null, acRichiestaAi = null;
  ${winter}
  return {statoAttuale, comandoIR, acRichiestaAi};`);

test('winter: the AI request is recorded only when a compressor timer holds it back', () => {
  const base = o => Object.assign({statoAttuale: 'OFF', esNotteFascia: false, surplus: 2000, temp: 18, soc: 90, kwhDomani: 10,
    consumoStoricoAttuale: 500, minMancantiAccensione: 0, minMancantiSpegnimento: 0, adesso: 1}, o);
  a.deepEqual(runWinter(store(), store(), base()), {statoAttuale: 'HEAT_DIURNO', comandoIR: 'eco19heat', acRichiestaAi: null});
  a.deepEqual(runWinter(store(), store(), base({minMancantiAccensione: 7})).acRichiestaAi, {mode: 'heat', setpoint: 19, guard: 'min_off'});
  a.deepEqual(runWinter(store(), store(), base({esNotteFascia: true, temp: 16, minMancantiAccensione: 7})).acRichiestaAi,
              {mode: 'heat', setpoint: 17, guard: 'min_off'});
  a.deepEqual(runWinter(store(), store(), base({statoAttuale: 'HEAT_DIURNO', temp: 20, minMancantiSpegnimento: 4})),
              {statoAttuale: 'HEAT_DIURNO', comandoIR: null, acRichiestaAi: {mode: 'off', setpoint: null, guard: 'min_run'}});
  a.equal(runWinter(store(), store(), base({statoAttuale: 'HEAT_DIURNO', temp: 20})).comandoIR, 'OFF');
  a.equal(runWinter(store(), store(), base({temp: 20, minMancantiAccensione: 7})).acRichiestaAi, null);   // no request
});

test('only additions: the decisions are unchanged; 7th output wired to a retained mqtt out', () => {
  let rest = fn.replace(block, '').replace('    msgPrPrestart,\n    msgAcState\n];', '    msgPrPrestart\n];')
    .replace('    acWatchdogCiclo = String(motivoAc).startsWith("WATCHDOG_");   // AI-CLIMATE-AC-STATE.1\n\n', '')
    .replace(/\/\/ AI-CLIMATE-AC-STATE\.1 \(2026-10-30\): what the AI[^]*?let acWatchdogCiclo = false;\n/, '')
    .replace(/(\n\s*| )else (if \([^)]*\) )?acRichiestaAi = \{[^}]*\};/g, '');
  a.equal(rest, before.func);
  new Function('msg', 'flow', 'global', 'node', 'env', 'context', fn);   // compiles
  a.equal(ai.outputs, 7); a.deepEqual(ai.wires.slice(0, 6), before.wires); a.deepEqual(ai.wires[6], [out.id]);
  a.deepEqual([out.type, out.retain, out.qos, out.broker], ['mqtt out', 'true', '1', '3ed6302a.5af928']);
});
