// Offline tests for HEATING-PROFILE.1: TZ=Europe/Rome node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const nodes = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const fn = {func: fs.readFileSync(__dirname + '/heating.js', 'utf8')};   // current source (update.json = deployed build)
const P = 'zara/interface/';
let clock = 0;
class FakeDate extends Date {
  constructor(...args) { super(...(args.length ? args : [clock])); }
  static now() { return clock; }
}
const at = s => { clock = new Date(s).getTime(); };   // local time, e.g. '2026-10-19T16:59'

function run(store, msg) {
  const context = {get: k => store[k], set: (k, v) => { store[k] = v; }};
  const f = new Function('msg', 'context', 'node', 'Date', fn.func);
  const [cmds, states, logs] = f(msg, context, {warn() {}}, FakeDate);
  return {cmds, states, logs: logs.map(l => JSON.parse(l.payload))};
}
const tick = store => run(store, {tick: true});
const stat = (store, t, kind, v) => run(store, {topic: `${P}climate/${t}/${kind}_temperature/stat`, payload: String(v)});
const setup = (mode = 'shadow') => {
  const store = {};
  for (const [t, min, max] of [['thermostat_living', 17.5, 18.5], ['thermostat_bath', 17, 18]]) {
    stat(store, t, 'min', min); stat(store, t, 'max', max); stat(store, t, 'target', min);
  }
  run(store, {topic: P + 'heating/timer/mode/cmd', payload: mode});
  return store;
};
// 72 hourly outdoor samples at a given temperature, ending at the current clock.
function outdoor(store, celsius, hours = 72) {
  clock += hours * 3600000;                            // a new window, not mixed with the previous one
  const end = clock;
  for (let h = hours - 1; h >= 0; h--) {
    clock = end - h * 3600000;
    run(store, {topic: P + 'env/outdoor/temperature/stat', payload: String(celsius)});
  }
  clock = end;
}

test('automatic profile from the 72 h outdoor mean, held under 48 h of data', () => {
  at('2026-10-19T10:00'); const store = setup();
  outdoor(store, 5, 40);
  a.equal(JSON.parse(tick(store).states[0].payload).profile, 'ridotto');   // 40 h: held
  outdoor(store, 5);
  a.equal(store.autoProfile, 'pieno');
  const state = JSON.parse(tick(store).states.find(s => s.topic.endsWith('profile/stat'))?.payload ?? '{}');
  a.equal(state.profile ?? store.lastProfile, 'pieno');
});

test('hysteresis: Pieno until above 11 °C, Spento until below 15 °C', () => {
  at('2026-10-19T10:00'); const store = setup();
  outdoor(store, 9); a.equal(store.autoProfile, 'pieno');
  outdoor(store, 10.5); a.equal(store.autoProfile, 'pieno');
  outdoor(store, 11.5); a.equal(store.autoProfile, 'ridotto');
  outdoor(store, 16.5); a.equal(store.autoProfile, 'spento');
  outdoor(store, 15.5); a.equal(store.autoProfile, 'spento');
  outdoor(store, 14.5); a.equal(store.autoProfile, 'ridotto');
});

test('shadow: slot start is logged and planned, nothing is sent', () => {
  at('2026-10-19T16:59'); const store = setup(); tick(store);   // Ridotto, Monday
  at('2026-10-19T17:00'); const r = tick(store);
  a.deepEqual(r.cmds, []);
  const shadow = r.logs.filter(l => l.event === 'setpoint_shadow');
  a.deepEqual(shadow.map(l => [l.thermostat, l.value, l.reason]).sort(),
              [['thermostat_bath', 18, 'slot_start'], ['thermostat_living', 18.5, 'slot_start']]);
  const plan = JSON.parse(r.states.find(s => s.topic.endsWith('timer/plan/stat')).payload);
  a.equal(plan.thermostats.thermostat_living.slot, '17:00-21:00');
  a.equal(plan.timer_mode, 'shadow');
});

test('active: max inside the slot, min outside, only on events', () => {
  at('2026-10-19T16:59'); const store = setup('active'); tick(store);
  at('2026-10-19T17:00');
  a.deepEqual(tick(store).cmds.map(c => [c.topic, c.payload]).sort(), [
    [P + 'climate/thermostat_bath/target_temperature/cmd', '18.0'],
    [P + 'climate/thermostat_living/target_temperature/cmd', '18.5']]);
  at('2026-10-19T17:01'); a.deepEqual(tick(store).cmds, []);      // no repeat
  at('2026-10-19T21:00');
  a.deepEqual(tick(store).cmds.map(c => c.payload).sort(), ['17.0', '17.5']);
});

test('Pieno: bath mornings on weekdays and weekends, living from noon', () => {
  at('2026-10-19T05:00'); const store = setup('active');
  run(store, {topic: P + 'heating/profile/mode/cmd', payload: 'pieno'});
  at('2026-10-19T05:04'); tick(store);
  at('2026-10-19T05:05'); a.deepEqual(tick(store).cmds.map(c => c.topic.split('/')[3]), ['thermostat_bath']);
  at('2026-10-24T07:29'); tick(store);                             // Saturday
  at('2026-10-24T07:30'); a.deepEqual(tick(store).cmds.map(c => c.topic.split('/')[3]), ['thermostat_bath']);
  at('2026-10-24T09:30'); a.deepEqual(tick(store).cmds.map(c => [c.topic.split('/')[3], c.payload]), [['thermostat_bath', '17.0']]);
  at('2026-10-24T12:00'); a.deepEqual(tick(store).cmds.map(c => [c.topic.split('/')[3], c.payload]), [['thermostat_living', '18.5']]);
});

test('forced profile change and min/max change re-send; invalid mode ignored', () => {
  at('2026-10-19T13:00'); const store = setup('active'); tick(store);  // Ridotto: both outside
  let r = run(store, {topic: P + 'heating/profile/mode/cmd', payload: 'pieno'});   // living inside now
  a.deepEqual(r.cmds.map(c => [c.topic.split('/')[3], c.payload]).sort(),
              [['thermostat_bath', '17.0'], ['thermostat_living', '18.5']]);
  r = stat(store, 'thermostat_living', 'max', 19);
  a.deepEqual(r.cmds.map(c => [c.topic.split('/')[3], c.payload]), [['thermostat_living', '19.0']]);
  r = run(store, {topic: P + 'heating/profile/mode/cmd', payload: 'turbo'});
  a.deepEqual(r.cmds, []); a.equal(store.mode, 'pieno');
});

test('user setpoint changes are logged, the timer echo is not', () => {
  at('2026-10-19T16:59'); const store = setup('active'); tick(store);
  at('2026-10-19T17:00'); tick(store);
  let r = stat(store, 'thermostat_living', 'target', 18.5);        // echo of the timer
  a.deepEqual(r.logs.filter(l => l.event === 'setpoint_change'), []);
  at('2026-10-19T18:00');
  r = stat(store, 'thermostat_living', 'target', 20);
  const change = r.logs.find(l => l.event === 'setpoint_change');
  a.deepEqual([change.thermostat, change.value, change.timer_value, change.profile], ['thermostat_living', 20, 18.5, 'ridotto']);
  a.deepEqual(r.cmds, []);                                          // never fights the user
});

test('the plan gives the next change; corrections are published newest first', () => {
  at('2026-10-19T15:00'); const store = setup(); tick(store);
  let plan;
  at('2026-10-19T15:01');
  stat(store, 'thermostat_living', 'target', 20);
  at('2026-10-19T15:02');
  const r = stat(store, 'thermostat_bath', 'target', 19);
  plan = JSON.parse(r.states.find(s => s.topic.endsWith('timer/plan/stat')).payload);
  a.deepEqual(plan.thermostats.thermostat_living.next, {day_offset: 0, at: '17:00', inside: true, value: 18.5});
  a.deepEqual([plan.thermostats.thermostat_bath.min, plan.thermostats.thermostat_bath.max], [17, 18]);
  const learning = JSON.parse(r.states.find(s => s.topic.endsWith('profile/learning/stat')).payload);
  a.deepEqual(learning.corrections.map(c => [c.thermostat, c.value]), [['thermostat_bath', 19], ['thermostat_living', 20]]);
  a.equal(learning.learning_active, false);
});

test('Pieno bath next change crosses to the next morning', () => {
  at('2026-10-19T22:30'); const store = setup();
  const r = run(store, {topic: P + 'heating/profile/mode/cmd', payload: 'pieno'});
  const plan = JSON.parse(r.states.find(s => s.topic.endsWith('timer/plan/stat')).payload);
  a.deepEqual(plan.thermostats.thermostat_bath.next, {day_offset: 1, at: '05:05', inside: true, value: 18});
});

test('without min/max nothing is sent', () => {
  at('2026-10-19T16:59'); const store = {};
  run(store, {topic: P + 'heating/timer/mode/cmd', payload: 'active'}); tick(store);
  at('2026-10-19T17:00'); a.deepEqual(tick(store).cmds, []);
});

test('the tab only adds nodes, outputs go to MQTT and the JSONL log', () => {
  a.equal(nodes.filter(n => n.type === 'tab').length, 1);
  const outs = nodes.filter(n => n.type === 'mqtt out');
  a.ok(outs.every(o => o.retain === '' && o.topic === ''));
  const file = nodes.find(n => n.type === 'file');
  a.deepEqual([file.filenameType, file.filename, file.appendNewline], ['msg', 'filename', true]);
  a.equal(JSON.parse(fs.readFileSync(__dirname + '/rollback.json')).length, 0);
});
