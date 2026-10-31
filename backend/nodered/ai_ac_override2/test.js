// Offline tests for AI-CLIMATE-AC-OVERRIDE.2: TZ=Europe/Rome node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const [ai, ovNode] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const [before] = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));
let clock = 0;
class FakeDate extends Date { constructor(...x) { super(...(x.length ? x : [clock])); } static now() { return clock; } }
const at = s => { clock = new Date(s).getTime(); };
function store() {
  const mem = {}, file = {};
  return {mem, file, get: (k, s) => (s === 'file' ? file : mem)[k], set: (k, v, s) => { (s === 'file' ? file : mem)[k] = v; }};
}
const fn = ai.func;

test('override node: solar_only accepted, published, never on off; a closure has its own message', () => {
  const run = (f, msg) => new Function('msg', 'flow', 'node', 'Date', ovNode.func)(msg, f, {warn() {}, status() {}}, FakeDate);
  at('2026-12-10T10:30:00+01:00'); const f = store();
  const cmd = b => run(f, {topic: 'zara/interface/climate/ac/override/cmd', payload: JSON.stringify(b)});
  let r = cmd({mode: 'heat', setpoint: 20, until: '2026-12-10T12:30:00+01:00', solar_only: true});
  a.equal(f.file.ac_override.solar_only, true); a.equal(JSON.parse(r[0].payload).solar_only, true);
  cmd({mode: 'heat', setpoint: 20, until: '2026-12-10T12:30:00+01:00'});
  a.equal(f.file.ac_override.solar_only, false);
  cmd({mode: 'off', until: '2026-12-10T12:30:00+01:00', solar_only: true});
  a.equal(f.file.ac_override.solar_only, false);
  cmd({mode: 'heat', setpoint: 20, until: '2026-12-10T12:30:00+01:00', solar_only: true});
  f.file.ac_override = null;
  f.file.ac_override_closed = {reason: 'SOLO_SOLE_PRELIEVO_15_MIN', mode: 'heat', at: clock, notified: false};
  r = run(f, {topic: 'tick'});
  a.match(r[1].payload.message, /solo sole/); a.equal(JSON.parse(r[0].payload).closed_reason, 'SOLO_SOLE_PRELIEVO_15_MIN');
});

// The solar only part of block B, run with the variables of one cycle.
const s = fn.indexOf('    let acSoloSoleChiude = false;'), e = fn.indexOf('    } else if (acOverrideAttivo && !bypassRoutine) {');
const solar = new Function('flow', 'global', 'v', `let {acOverrideAttivo, bypassRoutine, acOverride, batteryDischargeW, gridImportW,
  adesso, statoAttuale, comandoIR, msgPrPrestart, motivoAc, acStatoInizio, acUltimoAvvioInizio, acUltimoSpegnimentoInizio} = v;
  ${fn.slice(s, e)}
  }
  return {acSoloSoleChiude, statoAttuale, comandoIR, motivoAc};`);
const cyc = (f, g, o) => solar(f, g, Object.assign({acOverrideAttivo: true, bypassRoutine: false, batteryDischargeW: 0, gridImportW: 0,
  acOverride: {mode: 'heat', solar_only: true}, statoAttuale: 'MANUALE_CALDO', comandoIR: 'x', msgPrPrestart: null, motivoAc: 'y',
  acStatoInizio: 'MANUALE_CALDO', acUltimoAvvioInizio: 1, acUltimoSpegnimentoInizio: 2}, o));

test('solar only: closed after 15 minutes in a row, the count restarts when the draw stops', () => {
  const f = store(), g = store(), m = 60000, t = 1e12;
  a.equal(cyc(f, g, {adesso: t, gridImportW: 500}).acSoloSoleChiude, false);
  a.equal(cyc(f, g, {adesso: t + 14 * m, batteryDischargeW: 500}).acSoloSoleChiude, false);
  cyc(f, g, {adesso: t + 14.5 * m});                               // no draw: count reset
  a.equal(cyc(f, g, {adesso: t + 15 * m, gridImportW: 500}).acSoloSoleChiude, false);
  a.equal(cyc(f, g, {adesso: t + 29 * m, gridImportW: 90}).acSoloSoleChiude, false);    // under 100 W: reset too
  cyc(f, g, {adesso: t + 30 * m, gridImportW: 500});
  const r = cyc(f, g, {adesso: t + 45 * m, gridImportW: 500});
  a.deepEqual([r.acSoloSoleChiude, r.comandoIR, r.motivoAc, r.statoAttuale], [true, null, 'SOLO_SOLE_CHIUDE_MANUALE', 'MANUALE_CALDO']);
  a.equal(f.file.ac_override, null); a.equal(f.file.ac_override_closed.reason, 'SOLO_SOLE_PRELIEVO_15_MIN');
});

test('solar only: plain priorities, off and watchdog cycles are not touched', () => {
  const f = store(), g = store(), t = 1e12, m = 60000;
  for (const o of [{acOverride: {mode: 'heat', solar_only: false}}, {acOverride: {mode: 'off', solar_only: true}}, {bypassRoutine: true}]) {
    cyc(f, g, Object.assign({adesso: t, gridImportW: 900}, o));
    a.equal(cyc(f, g, Object.assign({adesso: t + 20 * m, gridImportW: 900}, o)).acSoloSoleChiude, false);
  }
  a.equal(f.mem.ac_solo_sole_da, undefined);
});

// The master switch: which branch runs.
const head = fn.slice(fn.indexOf('const acManualeInizio'), fn.indexOf('if (!aiAbilitata && !acManualeVale) {\n    if (!systemWasDisabled)'));
const manuale = new Function('flow', 'statoAttuale', 'Date', head + 'return acManualeVale;');

test('with AI Climate off the manual branch runs for a live priority or a state still to be closed', () => {
  at('2026-12-10T11:00:00+01:00'); const f = store();
  a.equal(manuale(f, 'OFF', FakeDate), false);
  f.file.ac_override = {until: '2026-12-10T12:00:00+01:00'};
  a.equal(manuale(f, 'OFF', FakeDate), true);
  f.file.ac_override = {until: '2026-12-10T10:00:00+01:00'};
  a.equal(manuale(f, 'OFF', FakeDate), false);
  a.equal(manuale(f, 'MANUALE_CALDO', FakeDate), true);            // expired: block A still has to switch it off
});

test('only the planned changes; the AI logic stays gated on aiAbilitata; nodes keep id and wires', () => {
  a.equal(fn.split('if (!bypassRoutine && aiAbilitata) {').length, 2);
  a.match(fn, /if \(!aiAbilitata\) \{ velocitaVmc = 1; motivoVmc = "SISTEMA_DISABILITATO"; \}/);
  new Function('msg', 'flow', 'global', 'node', 'env', 'context', fn);
  a.deepEqual([ai.id, ai.outputs, ai.wires], [before.id, before.outputs, before.wires]);
  a.ok(fn.includes('"SOLAR_ONLY_CLOSED"'));
});
