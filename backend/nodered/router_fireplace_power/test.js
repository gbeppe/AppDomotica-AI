// Offline tests for ROUTER-FIREPLACE-MODE.2: node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const [after] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const [before] = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));
const P = 'zara/interface/fireplace/main/';

function run(node, store, msg) {
  const context = {get: k => store[k], set: (k, v) => { store[k] = v; }};
  const fn = new Function('msg', 'context', 'node', 'env', node.func);
  return fn(msg, context, {id: node.id, warn: () => {}, error: () => {}}, {get: () => undefined});
}
const legacy = out => out && out[0];
const stat = (out, t) => ((out && out[1]) || []).filter(m => m.topic === P + t).map(m => m.payload);

test('live bug: the power switch wrote 1/0 to a state topic on the .20 broker', () => {
  const out = legacy(run(before, {}, {topic: P + 'power/cmd', payload: 'true'}));
  a.deepEqual([out.topic, out.payload, out.broker], ['zara/domotics/palazzetti/acceso', '1', 'home']);
});

test('power switch sends the .15 manual command alexa/caminetto on/off', () => {
  for (const [p, v] of [['true', 'on'], ['false', 'off']]) {
    const out = legacy(run(after, {}, {topic: P + 'power/cmd', payload: p}));
    a.deepEqual([out.topic, out.payload, out.broker], ['alexa/caminetto', v, 'emon']);
  }
});

test('power state accepts the lowercase on/off written by .15', () => {
  const store = {};
  a.deepEqual(stat(run(after, store, {topic: 'zara/domotics/palazzetti/acceso', payload: 'on'}), 'power/stat'), [true]);
  a.deepEqual(stat(run(after, store, {topic: 'zara/domotics/palazzetti/acceso', payload: 'off'}), 'power/stat'), [false]);
});

test('Manuale <-> manuale, other modes unchanged', () => {
  const out = legacy(run(after, {}, {topic: P + 'mode/cmd', payload: 'Manuale'}));
  a.deepEqual([out.payload, out.broker], ['manuale', 'emon']);
  a.equal(legacy(run(after, {}, {topic: P + 'mode/cmd', payload: 'Riscaldamento'})).payload, 'inverno');
  a.deepEqual(stat(run(after, {}, {topic: 'zara/domotics/palazzetti/modalita', payload: 'manuale'}), 'mode/stat'), ['Manuale']);
});

test('tables cached by v120 are rebuilt (v121)', () => {
  const store = {};
  run(before, store, {topic: 'zara/domotics/luciECO', payload: 'true'});
  a.equal(legacy(run(after, store, {topic: P + 'power/cmd', payload: 'true'})).topic, 'alexa/caminetto');
  a.equal((after.func.match(/router_initialized_v121/g) || []).length, 2);
});

test('level command is untouched', () => {
  const out = legacy(run(after, {}, {topic: P + 'level/cmd', payload: '4'}));
  a.deepEqual([out.topic, out.payload, out.broker], ['alexa/caminetto', '4', 'emon']);
});
