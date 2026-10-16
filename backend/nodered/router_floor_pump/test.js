// Offline tests for ROUTER-FLOOR-PUMP.1: node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const [after] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const [before] = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));
const CMD = 'zara/interface/heating/floor_pump/enabled/cmd', STAT = 'zara/interface/heating/floor_pump/enabled/stat';
const LEG = 'zara/domotics/pompapavimento/abilitata';

function run(node, store, msg) {
  const context = {get: k => store[k], set: (k, v) => { store[k] = v; }};
  const fn = new Function('msg', 'context', 'node', 'env', node.func);
  return fn(msg, context, {id: node.id, warn: () => {}, error: () => {}}, {get: () => undefined});
}
const legacy = out => out && out[0];
const stat = out => ((out && out[1]) || []).filter(m => m.topic === STAT).map(m => m.payload);

test('live bug: the enable went to the .20 broker, where .15 does not listen', () => {
  const out = legacy(run(before, {}, {topic: CMD, payload: 'true'}));
  a.deepEqual([out.topic, out.payload, out.broker], [LEG, '1', 'home']);
});

test('enable and disable reach .15 on its broker, retained', () => {
  for (const [p, v] of [['true', '1'], ['false', '0']]) {
    const out = legacy(run(after, {}, {topic: CMD, payload: p}));
    a.deepEqual([out.topic, out.payload, out.broker, out.retain], [LEG, v, 'emon', true]);
  }
});

test('the app shows the value held on .15', () => {
  const store = {};
  a.deepEqual(stat(run(after, store, {topic: LEG, payload: '0'})), [false]);
  a.deepEqual(stat(run(after, store, {topic: LEG, payload: '1'})), [true]);
});

test('fireplace routes are unchanged', () => {
  const out = legacy(run(after, {}, {topic: 'zara/interface/fireplace/main/mode/cmd', payload: 'Riscaldamento'}));
  a.deepEqual([out.topic, out.payload, out.broker, out.retain], ['zara/domotics/palazzetti/modalita', 'inverno', 'emon', true]);
});

test('tables cached by v122 are rebuilt (v123), only the floor-pump entry changes', () => {
  const store = {};
  run(before, store, {topic: CMD, payload: 'true'});
  a.equal(legacy(run(after, store, {topic: CMD, payload: 'true'})).broker, 'emon');
  a.equal((after.func.match(/router_initialized_v123/g) || []).length, 2);
  const OLD = 'leg_stat: "zara/domotics/pompapavimento/abilitata", broker: "home",';
  const NEW = 'leg_stat: "zara/domotics/pompapavimento/abilitata", broker: "emon", leg_retain: true, /* ROUTER-FLOOR-PUMP.1: the pump logic is on .15 */';
  a.equal(after.func, before.func.replace(OLD, NEW).replace(/router_initialized_v122/g, 'router_initialized_v123'));
});
