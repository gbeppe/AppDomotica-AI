// Offline tests for RULES-ENGINE.1: TZ=Europe/Rome node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const src = fs.readFileSync(__dirname + '/rules.js', 'utf8');
const P = 'zara/interface/';
let clock = 0;
class FakeDate extends Date {
  constructor(...args) { super(...(args.length ? args : [clock])); }
  static now() { return clock; }
}
const at = s => { clock = new Date(s).getTime(); };
const later = min => { clock += min * 60000; };

function run(store, msg) {
  const context = {get: k => store[k], set: (k, v) => { store[k] = v; }};
  const f = new Function('msg', 'context', 'node', 'Date', src);
  const [cmds, states, logs, events] = f(msg, context, {warn() {}}, FakeDate);
  return {cmds, states, logs: logs.map(l => JSON.parse(l.payload)), events: events.map(e => JSON.parse(e.payload))};
}
const tick = store => run(store, {tick: true});
const measure = (store, topic, v) => run(store, {topic: P + topic, payload: String(v)});
const puffer = (store, v) => measure(store, 'heating/puffer/top_temperature/stat', v);
const cmd = (store, verb, body) => run(store, {topic: P + 'rules/' + verb + '/cmd', payload: typeof body === 'string' ? body : JSON.stringify(body)});
const fireplace = (store, mode, power) => {
  run(store, {topic: P + 'fireplace/main/mode/stat', payload: mode});
  run(store, {topic: P + 'fireplace/main/power/stat', payload: String(power)});
};
// The user's example of 16/10/2026.
const PUFFER_RULE = {id: 'r-puffer', text: 'quando il puffer alto supera 42 °C spegni il caminetto',
  trigger: {kind: 'threshold', metric: 'puffer_top', op: 'above', value: 42}, action: {tool: 'set_fireplace_power', state: 'off'}};
const fresh = (mode = 'active') => { const s = {}; if (mode) cmd(s, 'engine', mode); return s; };

test('a new rule is in test for 24 h: it announces, never sends, "once" is not consumed', () => {
  at('2026-10-17T10:00'); const s = fresh(); fireplace(s, 'Manuale', true);
  const created = cmd(s, 'create', PUFFER_RULE);
  a.equal(created.events[0].kind, 'created');
  puffer(s, 41); tick(s);
  later(5); const r = puffer(s, 42.5);
  a.deepEqual(r.cmds, []);
  a.equal(r.events[0].kind, 'would_fire');
  a.deepEqual(r.events[0].command, {topic: P + 'fireplace/main/power/cmd', payload: 'false'});
  a.equal(s.ruleState['r-puffer'].status, 'test');
});

test('after the test day it acts once, then expires (default lifetime once)', () => {
  at('2026-10-17T10:00'); const s = fresh(); fireplace(s, 'Manuale', true);
  cmd(s, 'create', PUFFER_RULE); puffer(s, 40);
  later(24 * 60 + 1); const act = puffer(s, 40);          // every message re-evaluates the rules
  a.equal(act.events[0].kind, 'activated');
  later(1); const r = puffer(s, 43);
  a.deepEqual(r.cmds, [{topic: P + 'fireplace/main/power/cmd', payload: 'false'}]);
  a.equal(r.events.find(e => e.kind === 'fired').rule_id, 'r-puffer');
  a.equal(s.ruleState['r-puffer'].status, 'expired');
  later(60); puffer(s, 40); later(60); a.deepEqual(puffer(s, 44).cmds, []);
});

test('condition already true at creation acts at once (after the test day: re-evaluated)', () => {
  at('2026-10-17T10:00'); const s = fresh(); fireplace(s, 'Manuale', true); puffer(s, 43);
  const created = cmd(s, 'create', PUFFER_RULE);
  a.deepEqual(created.events.map(e => e.kind), ['created', 'would_fire']);   // already true: announced at once
  const r = cmd(s, 'activate', 'r-puffer');                // active and still above 42: acts at once
  a.deepEqual(r.cmds.map(c => c.payload), ['false']);
});

test('the engine in shadow never sends, even for an active rule', () => {
  at('2026-10-17T10:00'); const s = fresh(null); fireplace(s, 'Manuale', true);
  cmd(s, 'create', PUFFER_RULE); cmd(s, 'activate', 'r-puffer');
  later(1); const r = puffer(s, 45);
  a.deepEqual(r.cmds, []);
  a.equal(r.events[0].kind, 'would_fire');
});

test('re-arm only after the margin and the cooldown ("always" rule)', () => {
  at('2026-10-17T10:00'); const s = fresh(); fireplace(s, 'Manuale', true);
  cmd(s, 'create', {...PUFFER_RULE, lifetime: {kind: 'always'}, cooldown_min: 30}); cmd(s, 'activate', 'r-puffer');
  later(1); a.equal(puffer(s, 43).cmds.length, 1);
  later(1); puffer(s, 41.8); later(1); a.equal(puffer(s, 43).cmds.length, 0);  // margin 0.5: not re-armed
  later(1); puffer(s, 41.4); later(1); a.equal(puffer(s, 43).cmds.length, 0);  // re-armed but cooldown
  later(30); a.equal(puffer(s, 43).cmds.length, 1);
});

test('fireplace on/off and level obey the AI Smart limits: blocked outside Manuale', () => {
  at('2026-10-17T10:00'); const s = fresh(); fireplace(s, 'Riscaldamento', true);
  cmd(s, 'create', PUFFER_RULE); cmd(s, 'activate', 'r-puffer');
  later(1); const r = puffer(s, 43);
  a.deepEqual(r.cmds, []);
  a.equal(r.events[0].kind, 'blocked');
  a.equal(r.events[0].reason, 'caminetto non in Manuale');
});

test('stale or missing measures never fire', () => {
  at('2026-10-17T10:00'); const s = fresh(); fireplace(s, 'Manuale', true);
  cmd(s, 'create', PUFFER_RULE); cmd(s, 'activate', 'r-puffer');
  puffer(s, 44);               // fires now? it is fresh: yes, so use another rule for staleness
  const t = fresh(); fireplace(t, 'Manuale', true);
  cmd(t, 'create', {...PUFFER_RULE, id: 'r-old'}); cmd(t, 'activate', 'r-old');
  later(-20); measure(t, 'heating/puffer/top_temperature/stat', 45); later(20);   // reading 20 min old
  a.deepEqual(tick(t).cmds, []);
});

test('time trigger with days and a condition', () => {
  at('2026-10-19T21:59'); const s = fresh();       // Monday
  cmd(s, 'create', {id: 'r-sera', text: 'alle 22 nei giorni feriali spegni tutte le luci se fuori ci sono meno di 15 gradi',
    trigger: {kind: 'time', at: '22:00', days: [1, 2, 3, 4, 5]}, action: {tool: 'set_light_scene', scene: 'all_off'},
    conditions: [{kind: 'threshold', metric: 'outdoor', op: 'below', value: 15}], lifetime: {kind: 'always'}});
  cmd(s, 'activate', 'r-sera'); measure(s, 'env/outdoor/temperature/stat', 12);
  a.deepEqual(tick(s).cmds, []);
  at('2026-10-19T22:00');
  a.deepEqual(measure(s, 'env/outdoor/temperature/stat', 12).cmds, [{topic: P + 'lights/scene/cmd', payload: 'all_off'}]);
  at('2026-10-19T22:00:30'); a.deepEqual(tick(s).cmds, []);          // once per day
  at('2026-10-25T22:00');
  a.deepEqual(measure(s, 'env/outdoor/temperature/stat', 12).cmds, []);    // Sunday
});

test('validation: only allowed measures, actions and limits; max 10 rules', () => {
  at('2026-10-17T10:00'); const s = fresh();
  for (const bad of [
    {...PUFFER_RULE, id: 'b1', action: {tool: 'opensprinkler', zone: 1}},
    {...PUFFER_RULE, id: 'b2', trigger: {kind: 'threshold', metric: 'garage', op: 'above', value: 1}},
    {...PUFFER_RULE, id: 'b3', action: {tool: 'set_thermostat_target', thermostat: 'thermostat_bath', target_c: 25}},
    {...PUFFER_RULE, id: 'b4', text: ''},
    {...PUFFER_RULE, id: 'b5', conditions: [{kind: 'x'}]},
  ]) {
    const r = cmd(s, 'create', bad);
    a.equal(r.events[0].kind, 'rejected', bad.id);
  }
  for (let i = 0; i < 10; i++) a.equal(cmd(s, 'create', {...PUFFER_RULE, id: 'r-' + i}).events[0].kind, 'created');
  a.match(cmd(s, 'create', {...PUFFER_RULE, id: 'r-10'}).events[0].reason, /10 regole/);
});

test('delete, pause and the retained list', () => {
  at('2026-10-17T10:00'); const s = fresh();
  cmd(s, 'create', PUFFER_RULE);
  let list = JSON.parse(cmd(s, 'pause', 'r-puffer').states.find(x => x.topic.endsWith('rules/list/stat')).payload);
  a.equal(list.rules[0].status, 'paused');
  a.equal(list.engine_mode, 'active');
  list = JSON.parse(cmd(s, 'delete', 'r-puffer').states.find(x => x.topic.endsWith('rules/list/stat')).payload);
  a.deepEqual(list.rules, []);
});

test('the tab only adds nodes; four outputs wired', () => {
  const nodes = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
  const fn = nodes.find(n => n.type === 'function');
  a.equal(fn.outputs, 4);
  a.equal(fn.func, src);
  a.equal(JSON.parse(fs.readFileSync(__dirname + '/rollback.json')).length, 0);
});
