// Offline tests for ANNOUNCER.1: TZ=Europe/Rome node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const src = fs.readFileSync(__dirname + '/announcer.js', 'utf8');
let clock = 0;
class FakeDate extends Date { constructor(...x) { super(...(x.length ? x : [clock])); } static now() { return clock; } }
const at = s => { clock = new Date(s).getTime(); };
function store() { const file = {}; return {file, get: (k) => file[k], set: (k, v) => { file[k] = v; }}; }
const run = (f, msg) => new Function('msg', 'flow', 'node', 'Date', src)(msg, f, {warn() {}}, FakeDate);
const last = f => f.file.announcer.log.at(-1);
const AC = 'zara/interface/climate/ac/state/stat', FP = 'zara/interface/fireplace/main/';
const ac = (f, mode, setpoint, by) => run(f, {topic: AC, payload: JSON.stringify({current: {mode, setpoint, by}})});
const hl = (f, id, checks) => run(f, {topic: 'zara/system/health/' + id + '/stat', payload: JSON.stringify({checks})});

test('air conditioner: first state silent, then manual and automatic with readable words; not in the cap', () => {
  at('2026-11-02T10:00:00+01:00'); const f = store();
  ac(f, 'off', null, 'ai'); a.equal(f.file.announcer.log.length, 0);
  const r = ac(f, 'heat', 20, 'user');
  a.equal(last(f).text, 'Condizionatore acceso in caldo a 20 gradi, manuale');
  a.equal(JSON.parse(r.payload).mode, 'shadow'); a.equal(r.retain, true);
  ac(f, 'off', null, 'ai'); a.equal(last(f).text, 'Condizionatore spento, automatica');
  a.equal(f.file.announcer.count, 0);
});

test('fireplace: automatic with hours, manual without', () => {
  at('2026-11-02T17:00:00+01:00'); const f = store();
  run(f, {topic: FP + 'power/stat', payload: 'false'});
  run(f, {topic: FP + 'mode/stat', payload: 'Acqua Sanitaria'}); run(f, {topic: FP + 'start_time/stat', payload: '17:00'}); run(f, {topic: FP + 'stop_time/stat', payload: '19:00'});
  run(f, {topic: FP + 'power/stat', payload: 'true'});
  a.equal(last(f).text, 'Caminetto acceso in modalità automatica dalle 17:00 alle 19:00');
  run(f, {topic: FP + 'power/stat', payload: 'false'}); a.equal(last(f).text, 'Caminetto spento');
  run(f, {topic: FP + 'mode/stat', payload: 'Manuale'}); run(f, {topic: FP + 'power/stat', payload: 'true'});
  a.equal(last(f).text, 'Caminetto acceso in modalità manuale');
});

test('quiet hours: informative silent, urgent at low volume in living and bedroom; weekend starts later', () => {
  at('2026-11-02T23:00:00+01:00'); const f = store();
  ac(f, 'off', null, 'ai'); ac(f, 'heat', 17, 'ai');
  a.equal(last(f).result, 'tace: ore tranquille');
  hl(f, '20', [{id: 'internet', title: 'Internet', ok: false}]);
  a.deepEqual([last(f).result, last(f).volume, last(f).echo], ['direbbe', 'basso', 'living e camera']);
  at('2026-11-07T09:00:00+01:00'); ac(f, 'off', null, 'ai'); a.equal(last(f).result, 'tace: ore tranquille');
  at('2026-11-07T09:30:00+01:00'); ac(f, 'heat', 19, 'ai'); a.equal(last(f).result, 'direbbe');
});

test('puffer: one minute of confirmation, once, rearmed under 58', () => {
  at('2026-11-02T12:00:00+01:00'); const f = store(), P = 'zara/interface/heating/puffer/top_temperature/stat';
  run(f, {topic: P, payload: '60.5'}); run(f, {topic: 'tick'});
  a.equal(f.file.announcer.log.length, 0);
  run(f, {topic: P, payload: '57'}); at('2026-11-02T12:02:00+01:00'); run(f, {topic: P, payload: '61'}); run(f, {topic: 'tick'});
  a.equal(f.file.announcer.log.length, 0);
  at('2026-11-02T12:03:00+01:00'); run(f, {topic: 'tick'});
  a.equal(last(f).text, 'Attenzione: puffer a 61 gradi');
  at('2026-11-02T12:10:00+01:00'); run(f, {topic: 'tick'}); a.equal(f.file.announcer.log.length, 1);
});

test('alerts: pause of 2 hours, 3 an hour and 8 a day; back-to-normal and phone-only alerts ignored', () => {
  at('2026-11-02T10:00:00+01:00'); const f = store();
  const al = (key, title = key) => run(f, {alert: true, payload: {key, title}});
  al('battery_low'); al('stale_living_ok'); al('profile'); a.equal((f.file.announcer || {log: []}).log.length, 0);
  al('board_x'); al('board_x'); a.equal(last(f).result, 'tace: già detto da meno di 2 ore');
  al('b'); al('c'); al('d'); a.equal(last(f).result, 'tace: tetto orario');
  for (let h = 11; h < 15; h++) { at(`2026-11-02T${h}:30:00+01:00`); al('e' + h); al('g' + h); al('h' + h); }
  a.equal(last(f).result, 'tace: tetto giornaliero'); a.equal(f.file.announcer.count, 8);
});

test('health: transition only; the other server silent 15 minutes; powerwall informative', () => {
  at('2026-11-02T10:00:00+01:00'); const f = store();
  hl(f, '15', [{id: 'service:nodered', title: 'Node-RED', ok: false}]);
  a.equal(last(f).level, 'urgente'); const n = f.file.announcer.log.length;
  hl(f, '15', [{id: 'service:nodered', title: 'Node-RED', ok: false}]); a.equal(f.file.announcer.log.length, n);
  hl(f, '20', [{id: 'powerwall_charge', title: 'Powerwall', ok: false}]); a.equal(last(f).text, 'La batteria non si sta caricando');
  at('2026-11-02T10:20:00+01:00'); run(f, {topic: 'tick'});
  a.equal(last(f).text, 'Il server punto 15 non risponde');
  at('2026-11-02T10:21:00+01:00'); run(f, {topic: 'tick'}); a.notEqual(f.file.announcer.log.at(-2).key, 'peer15');
});

test('summary at 21 once a day', () => {
  at('2026-11-02T11:00:00+01:00'); const f = store(), S = 'TeslaPowerwall/solar_instant_power';
  run(f, {topic: S, payload: '6000'}); at('2026-11-02T11:05:00+01:00'); run(f, {topic: S, payload: '6000'});
  run(f, {topic: 'zara/interface/energy/battery/soc/stat', payload: '96'});
  at('2026-11-02T21:00:00+01:00'); run(f, {topic: 'tick'});
  a.equal(last(f).text, 'Oggi 1 chilowattora, batteria 96 percento, impianto regolare');
  const n = f.file.announcer.log.length; at('2026-11-02T21:02:00+01:00'); run(f, {topic: 'tick'}); a.equal(f.file.announcer.log.length, n);
});

test('nodes: only logs; alert chain keeps ntfy; puffer alarm at 60', () => {
  const [al, ...added] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
  a.ok(al.func.includes('const PUFFER = {high: 60, back: 58}')); a.equal(al.wires[0][0], 'a880a8a1ebba64c6'); a.equal(al.wires[0].length, 2);
  const out = added.filter(n => n.type === 'mqtt out'); a.equal(out.length, 1);
  a.ok(!src.includes('homeassistant_voice') && !src.includes('alexa'));
});
