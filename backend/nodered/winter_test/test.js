// Offline tests for WINTER-TEST.1: TZ=Europe/Rome node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const src = fs.readFileSync(__dirname + '/winter.js', 'utf8');
let clock = 0;
class FakeDate extends Date { constructor(...x) { super(...(x.length ? x : [clock])); } static now() { return clock; } }
const at = s => { clock = new Date(s).getTime(); };
function store() {
  const mem = {}, file = {};
  return {mem, file, get: (k, s) => (s === 'file' ? file : mem)[k], set: (k, v, s) => { (s === 'file' ? file : mem)[k] = v; }};
}
const run = (f, topic, payload) => {
  const [c, al] = new Function('msg', 'flow', 'node', 'Date', src)({topic, payload}, f, {warn() {}}, FakeDate);
  return {cmds: c.filter(m => !m.topic.endsWith('winter_test/stat')), alerts: al};
};
const tick = f => run(f, 'tick');
const TH = 'zara/interface/climate/thermostat_living/target_temperature/';
const feed = (f, o = {}) => {
  run(f, 'zara/interface/heating/timer/plan/stat', JSON.stringify({thermostats: {thermostat_living: {value: o.plan ?? 18}}}));
  run(f, 'zara/interface/energy/solar_forecast/stat', JSON.stringify({faces: {eclipse: {today_kwh: o.e ?? 60}, viessmann: {today_kwh: o.v ?? 40}}}));
  run(f, 'zigbee2mqtt/Sonoff AirGuard TH esterno cucina', JSON.stringify({temperature: o.out ?? 9}));
  run(f, TH + 'stat', String(o.plan ?? 18));
  run(f, 'zara/interface/fireplace/main/power/stat', o.fire ? 'true' : 'false');
};
const on = f => run(f, 'zara/interface/climate/winter_test/cmd', '{"action":"on"}');

test('off by default: no notice, no command', () => {
  at('2026-12-10T08:30:00+01:00'); const f = store(); feed(f);
  a.deepEqual(tick(f), {cmds: [], alerts: []});
  at('2026-12-10T10:30:00+01:00');
  a.deepEqual(tick(f), {cmds: [], alerts: []});
});

test('a test day: notice, start, thermostat held 1 degree under the plan, back to the plan at 15', () => {
  at('2026-12-10T08:30:00+01:00'); const f = store(); on(f); feed(f);
  let r = tick(f);
  a.match(r.alerts[0].payload.title, /giorno prova/); a.equal(f.file.winter_test.day.kwh, 15);
  at('2026-12-10T10:30:00+01:00');
  r = tick(f);
  const c = JSON.parse(r.cmds[0].payload);
  a.deepEqual(c, {mode: 'heat', setpoint: 20, until: '2026-12-10T15:00:00+01:00', solar_only: true});
  a.deepEqual([r.cmds[1].topic, r.cmds[1].payload], [TH + 'cmd', '17']);
  run(f, TH + 'stat', '17');
  at('2026-12-10T10:31:00+01:00');
  a.equal(tick(f).cmds.length, 0);
  run(f, 'zara/interface/heating/timer/plan/stat', JSON.stringify({thermostats: {thermostat_living: {value: 19}}}));
  run(f, TH + 'stat', '19');                                       // a heating timer changed it
  at('2026-12-10T12:00:00+01:00');
  a.equal(tick(f).cmds[0].payload, '18');
  run(f, TH + 'stat', '18');
  at('2026-12-10T15:00:00+01:00');
  r = tick(f);
  a.deepEqual(r.cmds.map(m => [m.topic, m.payload]), [[TH + 'cmd', '19']]);
  a.equal(f.file.winter_test.run, null);
  a.equal(f.file.winter_test.journal[0].result, 'fine'); a.equal(f.file.winter_test.journal[0].type, 'prova');
});

test('days alternate; a control day sends nothing but is logged', () => {
  at('2026-12-10T08:30:00+01:00'); const f = store(); on(f); feed(f);
  tick(f); at('2026-12-10T10:30:00+01:00'); tick(f); at('2026-12-10T15:00:00+01:00'); tick(f);
  at('2026-12-11T08:30:00+01:00');
  a.match(tick(f).alerts[0].payload.title, /confronto/);
  at('2026-12-11T10:30:00+01:00');
  a.equal(tick(f).cmds.length, 0);
  a.equal(f.file.winter_test.journal[1].type, 'confronto');
  at('2026-12-12T08:30:00+01:00'); tick(f); at('2026-12-12T10:30:00+01:00');
  a.equal(JSON.parse(tick(f).cmds[0].payload).mode, 'heat');
});

test('unsuitable days: little sun, cold or warm outside, fireplace on; the alternation does not move', () => {
  at('2026-12-10T08:30:00+01:00'); const f = store(); on(f); feed(f, {e: 30, v: 10});
  a.equal(tick(f).alerts.length, 0); at('2026-12-10T10:30:00+01:00'); a.equal(tick(f).cmds.length, 0);
  for (const [d, o, why] of [['11', {out: 3}, /esterno/], ['12', {out: 16}, /esterno/], ['13', {fire: true}, /caminetto/]]) {
    at(`2026-12-${d}T08:30:00+01:00`); feed(f, o); tick(f); at(`2026-12-${d}T10:30:00+01:00`);
    a.equal(tick(f).cmds.length, 0);
    a.match(f.file.winter_test.journal.at(-1).reason, why);
  }
  a.equal(f.file.winter_test.last_type, null);
});

test('closures: solar only, fireplace, switched off; thermostat back, AC given back', () => {
  for (const close of [f => run(f, 'zara/interface/climate/ac/override/stat', '{"active":false,"closed_reason":"SOLO_SOLE_PRELIEVO_15_MIN"}'),
                       f => { run(f, 'zara/interface/fireplace/main/power/stat', 'true'); return tick(f); },
                       f => run(f, 'zara/interface/climate/winter_test/cmd', '{"action":"off"}')]) {
    at('2026-12-10T08:30:00+01:00'); const f = store(); on(f); feed(f); tick(f); at('2026-12-10T10:30:00+01:00'); tick(f);
    run(f, 'zara/interface/climate/ac/override/stat', '{"active":true}');
    at('2026-12-10T11:00:00+01:00');
    const r = close(f);
    a.equal(f.file.winter_test.run, null);
    a.deepEqual(r.cmds.map(m => m.payload), ['18', 'auto']);
    a.equal(r.alerts.length, 1);
  }
});

test('a restart resumes from the file store; past the end the thermostat is restored', () => {
  at('2026-12-10T08:30:00+01:00'); const f = store(); on(f); feed(f); tick(f); at('2026-12-10T10:30:00+01:00'); tick(f);
  const g = store(); g.file.winter_test = f.file.winter_test;      // memory lost
  at('2026-12-10T16:00:00+01:00');
  a.deepEqual(tick(g).cmds.map(m => m.payload), ['18']);           // plan unknown: saved base
});

test('trial run works with the test off; bad commands are refused', () => {
  at('2026-11-01T11:00:00+01:00'); const f = store(); feed(f);
  const r = run(f, 'zara/interface/climate/winter_test/cmd', '{"action":"run","until":"2026-11-01T12:00:00+01:00"}');
  a.equal(JSON.parse(r.cmds[0].payload).until, '2026-11-01T12:00:00+01:00');
  a.equal(f.file.winter_test.journal[0].type, 'collaudo');
  at('2026-11-01T11:01:00+01:00'); a.equal(tick(f).cmds[0].payload, '17');
  for (const b of ['{"action":"run","until":"2026-11-01T20:00:00+01:00"}', 'boh', '{"action":"x"}'])
    a.equal(run(f, 'zara/interface/climate/winter_test/cmd', b).cmds.length, 0);
});

test('nodes: new tab, alert link both ways', () => {
  const [al, ...added] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
  const link = added.find(n => n.type === 'link out');
  a.ok(al.links.includes(link.id)); a.deepEqual(link.links, [al.id]);
  a.equal(added.filter(n => n.type === 'mqtt in').length, 7);
  a.equal(added.find(n => n.type === 'function').wires.length, 2);
});
