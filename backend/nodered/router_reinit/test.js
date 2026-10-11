// Offline tests for ROUTER-CLEANUP.2: node --test test.js
// Reproduces the live case: context already initialised by the previous router code.
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const [after] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const [before] = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));
const cleanup1Base = JSON.parse(fs.readFileSync(__dirname + '/../router_cleanup/rollback.json'))
  .find(n => n.id === before.id);   // router before ROUTER-CLEANUP.1 (v118, old rules)
const FLOOR = 'zara/interface/heating/floor_pump/enabled/stat';

function run(node, store, msg) {
  const context = {get: k => store[k], set: (k, v) => { store[k] = v; }};
  const fn = new Function('msg', 'context', 'node', 'env', node.func);
  return fn(msg, context, {id: node.id, warn: () => {}, error: () => {}}, {get: () => undefined});
}
const stat = out => ((out && out[1]) || []).filter(m => m.topic === FLOOR).map(m => m.payload);
const echo = {topic: 'zara/domotics/pompapavimento/abilitata', payload: 1};

test('live bug: CLEANUP.1 code on an already initialised context keeps the old rule', () => {
  const store = {};
  run(cleanup1Base, store, {topic: 'zara/domotics/luciECO', payload: 'true'});  // old code initialises
  a.deepEqual(stat(run(before, store, echo)), [false]);
});

test('CLEANUP.2 rebuilds the tables: numeric 1 is now true', () => {
  const store = {};
  run(cleanup1Base, store, {topic: 'zara/domotics/luciECO', payload: 'true'});
  a.deepEqual(stat(run(after, store, echo)), [true]);
  const hifi = run(after, store, {topic: 'zara/interface/lights/hifi/power/cmd', payload: 'true'});
  a.ok(!hifi || !hifi[0]);   // retired HiFi lamp no longer routed
});

test('only the init guard changes', () => {
  a.equal(after.func, before.func.replaceAll('router_initialized_v118', 'router_initialized_v119'));
  for (const k of Object.keys(before)) if (k !== 'func') a.deepEqual(after[k], before[k], k);
});
