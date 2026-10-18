// Offline tests for AI-CLIMATE-VMC-OVERRIDE.1: TZ=Europe/Rome node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const src = fs.readFileSync(__dirname + '/override.js', 'utf8');
let clock = 0;
class FakeDate extends Date { constructor(...x) { super(...(x.length ? x : [clock])); } static now() { return clock; } }
const at = s => { clock = new Date(s).getTime(); };
function flowStore() {
  const mem = {}, file = {};
  return {mem, file, get: (k, s) => (s === 'file' ? file : mem)[k], set: (k, v, s) => { (s === 'file' ? file : mem)[k] = v; }};
}
function run(flow, msg) {
  const status = [];
  const out = new Function('msg', 'flow', 'node', 'Date', src)(msg, flow, {warn() {}, status: s => status.push(s)}, FakeDate);
  return {cmd: out[0], state: out[1] && JSON.parse(out[1].payload)};
}
const cmd = (flow, body) => run(flow, {topic: 'zara/interface/ventilation/vmc/override/cmd', payload: typeof body === 'string' ? body : JSON.stringify(body)});

test('a priority sends the speed at once, blocks the AI resend, ends by itself', () => {
  at('2026-10-18T17:45:00+02:00'); const f = flowStore();
  const r = cmd(f, {speed: 3, until: '2026-10-18T20:00:00+02:00'});
  a.deepEqual(r.cmd, {topic: 'zara/interface/ventilation/vmc/speed/cmd', payload: '3'});
  a.deepEqual(r.state, {schema: 'domopi.vmc_override.v1', active: true, speed: 3, until: '2026-10-18T20:00:00+02:00', label: '20:00'});
  a.equal(f.mem.vmc_velocita_inviata, 3);
  at('2026-10-18T19:59:00+02:00'); a.equal(run(f, {topic: 'tick'}).state, null);
  at('2026-10-18T20:00:30+02:00'); const end = run(f, {topic: 'tick'});
  a.deepEqual(end.state, {schema: 'domopi.vmc_override.v1', active: false});
  a.equal(f.file.vmc_override, null); a.equal(f.mem.vmc_ultimo_cambio_ms, 0);
});

test('"auto" ends the priority; invalid requests are ignored', () => {
  at('2026-10-18T17:45:00+02:00'); const f = flowStore();
  cmd(f, {speed: 2, until: '2026-10-18T19:45:00+02:00'});
  a.equal(cmd(f, 'auto').state.active, false);
  for (const bad of [{speed: 5, until: '2026-10-18T19:00:00+02:00'}, {speed: 2, until: '2026-10-18T17:00:00+02:00'},
                     {speed: 2, until: '2026-10-18T19:00:00'}, {speed: 2, until: '2026-10-21T19:00:00+02:00'}, 'boh'])
    a.deepEqual(cmd(f, bad), {cmd: null, state: null});
  a.equal(f.file.vmc_override, null);
});

test('the AI Climate block: manual speed wins during the priority, anti-bounce skipped; normal after', () => {
  const [ai] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
  const start = ai.func.indexOf('// AI-CLIMATE-VMC-OVERRIDE.1'), end = ai.func.indexOf('flow.set("vmc_fanspeed", velocitaVmc);');
  a.ok(start > 0 && end > start);
  const block = new Function('flow', 'Date', 'velocitaVmc', 'motivoVmc',
    ai.func.slice(start, end) + '\nreturn [velocitaVmc, motivoVmc];');
  at('2026-10-18T18:00:00+02:00'); const f = flowStore();
  f.file.vmc_override = {speed: 3, until: '2026-10-18T20:00:00+02:00', label: '20:00'};
  f.mem.vmc_velocita_inviata = 1; f.mem.vmc_ultimo_cambio_ms = clock - 60000;    // inside the 5 min anti-bounce
  a.deepEqual(block(f, FakeDate, 1, 'COMFORT_INVERNALE_BASE'), [3, 'MANUALE_FINO_ALLE_20:00']);
  at('2026-10-18T20:01:00+02:00'); f.mem.vmc_velocita_inviata = 3; f.mem.vmc_ultimo_cambio_ms = 0;
  a.deepEqual(block(f, FakeDate, 1, 'COMFORT_INVERNALE_BASE'), [1, 'COMFORT_INVERNALE_BASE']);
  const [before] = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));
  a.equal(ai.func.length - before.func.length, ai.func.indexOf('let currentTime') - start + ' && !vmcOverrideAttivo'.length);
});
