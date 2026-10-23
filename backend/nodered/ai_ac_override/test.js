// Offline tests for AI-CLIMATE-AC-OVERRIDE.1: TZ=Europe/Rome node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const src = fs.readFileSync(__dirname + '/override.js', 'utf8');
const [ai, ...added] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const [before] = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));
let clock = 0;
class FakeDate extends Date { constructor(...x) { super(...(x.length ? x : [clock])); } static now() { return clock; } }
const at = s => { clock = new Date(s).getTime(); };
function store() {
  const mem = {}, file = {};
  return {mem, file, get: (k, s) => (s === 'file' ? file : mem)[k], set: (k, v, s) => { (s === 'file' ? file : mem)[k] = v; }};
}
function run(flow, msg) {
  const out = new Function('msg', 'flow', 'node', 'Date', src)(msg, flow, {warn() {}, status() {}}, FakeDate);
  return {state: out[0] && JSON.parse(out[0].payload), alert: out[1] && out[1].payload};
}
const cmd = (flow, body) => run(flow, {topic: 'zara/interface/climate/ac/override/cmd',
  payload: typeof body === 'string' ? body : JSON.stringify(body)});

test('override node: modes, codes, defaults and the 6 hour limit', () => {
  at('2026-10-23T16:00:00+02:00'); const f = store();
  const r = cmd(f, {mode: 'heat', setpoint: 20, until: '2026-10-23T22:00:00+02:00'});
  a.deepEqual(r.state, {schema: 'domopi.ac_override.v1', active: true, mode: 'heat', setpoint: 20,
                        until: '2026-10-23T22:00:00+02:00', label: '22:00'});
  a.equal(f.file.ac_override.code, 'eco20heat');
  a.equal(cmd(f, {mode: 'cool', until: '2026-10-23T18:00:00+02:00'}) && f.file.ac_override.code, '24');
  a.equal(cmd(f, {mode: 'dry', setpoint: 23, until: '2026-10-23T18:00:00+02:00'}) && f.file.ac_override.code, '23dry');
  cmd(f, {mode: 'off', until: '2026-10-23T18:00:00+02:00'});
  a.deepEqual([f.file.ac_override.code, f.file.ac_override.setpoint], ['OFF', null]);
  for (const bad of [{mode: 'heat', setpoint: 25, until: '2026-10-23T18:00:00+02:00'},     // out of range
                     {mode: 'cool', setpoint: 21, until: '2026-10-23T18:00:00+02:00'},
                     {mode: 'fan', until: '2026-10-23T18:00:00+02:00'},
                     {mode: 'off', until: '2026-10-23T22:00:01+02:00'},                     // over 6 hours
                     {mode: 'off', until: '2026-10-23T15:00:00+02:00'},                     // past
                     {mode: 'off', until: '2026-10-23T18:00:00'}, 'boh'])                   // no time zone
    a.deepEqual(cmd(f, bad), {state: null, alert: null});
  a.equal(cmd(f, 'auto').state.active, false);
  a.equal(f.file.ac_override, null);
});

test('override node: ends by itself; a battery protection is announced once on the phone', () => {
  at('2026-10-23T16:00:00+02:00'); const f = store();
  cmd(f, {mode: 'cool', setpoint: 25, until: '2026-10-23T17:00:00+02:00'});
  at('2026-10-23T16:59:00+02:00'); a.equal(run(f, {topic: 'tick'}).state, null);
  at('2026-10-23T17:00:30+02:00'); a.equal(run(f, {topic: 'tick'}).state.active, false);
  a.equal(f.file.ac_override, null);
  cmd(f, {mode: 'cool', setpoint: 25, until: '2026-10-23T19:00:00+02:00'});
  f.file.ac_override = null;                                       // closed by the AI Climate block
  f.file.ac_override_closed = {reason: 'PEAK_SHAVING_ATTIVO (Scarica: 2700W)', mode: 'cool', at: clock, notified: false};
  const r = run(f, {topic: 'tick'});
  a.equal(r.state.closed_reason, 'PEAK_SHAVING_ATTIVO (Scarica: 2700W)');
  a.match(r.alert.message, /raffrescamento è stato chiuso/);
  a.equal(run(f, {topic: 'tick'}).alert, null);                    // only once
});

// The two blocks inside the AI Climate function, run in a small model of one cycle.
const fn = ai.func;
const blockA = fn.slice(fn.indexOf('    // AI-CLIMATE-AC-OVERRIDE.1 (2026-10-23)'), fn.indexOf('    if (!bypassRoutine && aiAbilitata) {'));
const blockB = fn.slice(fn.indexOf('    // AI-CLIMATE-AC-OVERRIDE.1: while'), fn.indexOf('    // ==================================================\n    // B.3 LOGICA VMC '));
const cycle = new Function('flow', 'global', 'Date', 'v', 'aiDecision', `
  let {statoAttuale, comandoIR, motivoAc, causaTransizioneAc, bypassRoutine, bloccoSpegnimentoEmergenza,
       minMancantiAccensione, minMancantiSpegnimento, msgPrPrestart, adesso} = v;
  ${blockA}
  if (!bypassRoutine) ({statoAttuale, comandoIR, motivoAc, bloccoSpegnimentoEmergenza, msgPrPrestart} =
      aiDecision({statoAttuale, comandoIR, motivoAc, bloccoSpegnimentoEmergenza, msgPrPrestart}, flow, global, adesso));
  ${blockB}
  return {statoAttuale, comandoIR, motivoAc, causaTransizioneAc, msgPrPrestart};`);
const base = (o = {}) => Object.assign({statoAttuale: 'OFF', comandoIR: null, motivoAc: 'LOGICA_INVERNALE_ATTIVA',
  causaTransizioneAc: null, bypassRoutine: false, bloccoSpegnimentoEmergenza: false, minMancantiAccensione: 0,
  minMancantiSpegnimento: 0, msgPrPrestart: null, adesso: clock}, o);
const quiet = s => s;                                              // the AI changes nothing this cycle
function aiStarts(s, flow, global, adesso) {                       // the AI starts heating this cycle
  flow.set('condizionatore_stato', 'HEAT_DIURNO', 'file'); global.set('ultimo_avvio', adesso, 'file');
  return Object.assign({}, s, {statoAttuale: 'HEAT_DIURNO', comandoIR: 'eco19heat', motivoAc: 'LOGICA_INVERNALE_ATTIVA',
                               msgPrPrestart: {topic: 'pr'}});
}
const priority = (f, mode, code, until = '2026-10-23T20:00:00+02:00') =>
  { f.file.ac_override = {mode, setpoint: 20, code, until, label: until.slice(11, 16)}; };

test('manual heat: sent once, the AI decision of the cycle undone, no PR pre-start', () => {
  at('2026-10-23T17:00:00+02:00'); const f = store(), g = store();
  g.file.ultimo_avvio = 1; g.file.ultimo_spegnimento = 2; priority(f, 'heat', 'eco20heat');
  let r = cycle(f, g, FakeDate, base(), aiStarts);
  a.deepEqual([r.statoAttuale, r.comandoIR, r.motivoAc, r.msgPrPrestart],
              ['MANUALE_CALDO', 'eco20heat', 'MANUALE_CALDO_FINO_ALLE_20:00', null]);
  a.equal(g.file.ultimo_avvio, clock); a.equal(g.file.ultimo_spegnimento, 2); a.equal(f.file.condizionatore_stato, 'MANUALE_CALDO');
  r = cycle(f, g, FakeDate, base({statoAttuale: 'MANUALE_CALDO', minMancantiSpegnimento: 10}), quiet);
  a.deepEqual([r.statoAttuale, r.comandoIR], ['MANUALE_CALDO', null]);  // already sent: no repeat
});

test('compressor timers: manual on waits after a recent stop, manual off waits the minimum run', () => {
  at('2026-10-23T17:00:00+02:00'); const f = store(), g = store();
  priority(f, 'cool', '24');
  let r = cycle(f, g, FakeDate, base({minMancantiAccensione: 7}), quiet);
  a.deepEqual([r.statoAttuale, r.comandoIR, r.motivoAc], ['OFF', null, 'MANUALE_IN_ATTESA_COMPRESSORE_7_MIN']);
  priority(f, 'off', 'OFF');
  r = cycle(f, g, FakeDate, base({statoAttuale: 'COOLING_ON', minMancantiSpegnimento: 4}), quiet);
  a.deepEqual([r.statoAttuale, r.comandoIR, r.motivoAc], ['COOLING_ON', null, 'MANUALE_IN_ATTESA_COMPRESSORE_4_MIN']);
  r = cycle(f, g, FakeDate, base({statoAttuale: 'COOLING_ON'}), quiet);
  a.deepEqual([r.statoAttuale, r.comandoIR, r.motivoAc], ['OFF', 'OFF', 'MANUALE_SPENTO_FINO_ALLE_20:00']);
  r = cycle(f, g, FakeDate, base(), aiStarts);                     // manual off: the AI cannot start it
  a.deepEqual([r.statoAttuale, r.comandoIR], ['OFF', null]);
});

test('switching between two "on" modes is immediate (no compressor restart)', () => {
  at('2026-10-23T17:00:00+02:00'); const f = store(), g = store();
  priority(f, 'dry', '24dry');
  const r = cycle(f, g, FakeDate, base({statoAttuale: 'COOLING_ON', minMancantiSpegnimento: 9, minMancantiAccensione: 9}), quiet);
  a.deepEqual([r.statoAttuale, r.comandoIR], ['MANUALE_DEUMIDIFICA', '24dry']);
});

test('a battery protection closes a manual "on" priority; the AI decision stands', () => {
  at('2026-07-20T22:30:00+02:00'); const f = store(), g = store();
  priority(f, 'cool', '24', '2026-07-21T01:00:00+02:00');
  const protect = s => Object.assign({}, s, {statoAttuale: 'OFF', comandoIR: 'OFF', bloccoSpegnimentoEmergenza: true,
                                             motivoAc: 'SOPRAVVIVENZA_NOTTE_ATTIVA (Cusc: 1.0 < Riq: 2.0kWh)'});
  const r = cycle(f, g, FakeDate, base({statoAttuale: 'MANUALE_RAFFRESCAMENTO'}), protect);
  a.deepEqual([r.statoAttuale, r.comandoIR], ['OFF', 'OFF']);
  a.match(r.motivoAc, /^PROTEZIONE_BATTERIA_CHIUDE_MANUALE \(SOPRAVVIVENZA_NOTTE_ATTIVA/);
  a.equal(f.file.ac_override, null); a.equal(f.file.ac_override_closed.mode, 'cool');
});

test('after the end of a manual "on": off once the minimum run is over, the AI waits until then', () => {
  at('2026-10-23T20:00:30+02:00'); const f = store(), g = store();
  priority(f, 'heat', 'eco20heat');                                // expired at 20:00
  let r = cycle(f, g, FakeDate, base({statoAttuale: 'MANUALE_CALDO', minMancantiSpegnimento: 3}), aiStarts);
  a.deepEqual([r.statoAttuale, r.comandoIR, r.motivoAc], ['MANUALE_CALDO', null, 'FINE_MANUALE_ATTESA_COMPRESSORE_3_MIN']);
  r = cycle(f, g, FakeDate, base({statoAttuale: 'MANUALE_CALDO'}), aiStarts);
  a.deepEqual([r.statoAttuale, r.comandoIR, r.motivoAc], ['OFF', 'OFF', 'FINE_MANUALE_SPEGNIMENTO']);
  a.equal(g.file.ultimo_spegnimento, clock);
  r = cycle(f, g, FakeDate, base(), aiStarts);                     // next cycle: back to the AI
  a.equal(r.statoAttuale, 'HEAT_DIURNO');
});

test('watchdog bypass or AI disabled: the priority does nothing in that cycle', () => {
  at('2026-10-23T17:00:00+02:00'); const f = store(), g = store();
  priority(f, 'heat', 'eco20heat');
  const r = cycle(f, g, FakeDate, base({bypassRoutine: true, comandoIR: 'OFF', motivoAc: 'WATCHDOG_FALSO_OFF'}), quiet);
  a.deepEqual([r.comandoIR, r.motivoAc], ['OFF', 'WATCHDOG_FALSO_OFF']);
});

test('only the two blocks are inserted; new nodes are wired as planned', () => {
  a.equal(ai.func.replace(blockA, '').replace(blockB, ''), before.func);
  a.deepEqual(added.map(n => n.type), ['mqtt in', 'inject', 'function', 'mqtt out', 'link out', 'link in']);
  a.equal(added[0].topic, 'zara/interface/climate/ac/override/cmd');
  a.deepEqual(added[5].wires, [['a880a8a1ebba64c6']]);
  a.deepEqual(added[4].links, [added[5].id]); a.deepEqual(added[5].links, [added[4].id]);
});
