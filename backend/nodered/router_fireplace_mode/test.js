// Offline tests for ROUTER-FIREPLACE-MODE.1: node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const [after] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const [before] = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));
const CMD = 'zara/interface/fireplace/main/mode/cmd', STAT = 'zara/interface/fireplace/main/mode/stat';
const LEG = 'zara/domotics/palazzetti/modalita';

function run(node, store, msg) {
  const context = {get: k => store[k], set: (k, v) => { store[k] = v; }};
  const fn = new Function('msg', 'context', 'node', 'env', node.func);
  return fn(msg, context, {id: node.id, warn: () => {}, error: () => {}}, {get: () => undefined});
}
const legacy = out => out && out[0];
const stat = out => ((out && out[1]) || []).filter(m => m.topic === STAT).map(m => m.payload);

test('live bug: label forwarded untranslated to the .20 broker', () => {
  const out = legacy(run(before, {}, {topic: CMD, payload: 'Acqua Sanitaria'}));
  a.equal(out.topic, LEG); a.equal(out.payload, 'Acqua Sanitaria'); a.equal(out.broker, 'home');
});

test('every app label reaches .15 as the value its fireplace logic expects', () => {
  const expected = {'Riscaldamento': 'inverno', 'Integrazione Caldaia': 'conCaldaia',
    'Acqua Sanitaria': 'sanitaria', 'Disattivato': 'disattivato'};
  for (const [label, value] of Object.entries(expected)) {
    const out = legacy(run(after, {}, {topic: CMD, payload: label}));
    a.deepEqual([out.topic, out.payload, out.broker], [LEG, value, 'emon'], label);
  }
});

test('state from .15 is shown with the app label; unknown values pass through', () => {
  const store = {};
  a.deepEqual(stat(run(after, store, {topic: LEG, payload: 'sanitaria'})), ['Acqua Sanitaria']);
  a.deepEqual(stat(run(after, store, {topic: LEG, payload: 'conCaldaia'})), ['Integrazione Caldaia']);
  a.deepEqual(stat(run(after, store, {topic: LEG, payload: 'qualcosa'})), ['qualcosa']);
});

test('tables cached by the old code are rebuilt (init guard v119 -> v120)', () => {
  const store = {};
  run(before, store, {topic: 'zara/domotics/luciECO', payload: 'true'});   // old code initialises context
  const out = legacy(run(after, store, {topic: CMD, payload: 'Riscaldamento'}));
  a.deepEqual([out.payload, out.broker], ['inverno', 'emon']);
});

test('only the mode entry and the init guard change', () => {
  const guard = f => f.replaceAll(/router_initialized_v1\d\d/g, 'GUARD');
  const afterEntry = /\{ id: "fireplace\.main\.mode"[^\n]*\n[^\n]*\n[^\n]*String\(p\) \},/;
  const beforeEntry = /\{ id: "fireplace\.main\.mode"[^\n]*\},/;
  a.match(after.func, afterEntry); a.match(before.func, beforeEntry);
  a.equal(guard(after.func.replace(afterEntry, 'ENTRY')), guard(before.func.replace(beforeEntry, 'ENTRY')));
  a.equal((after.func.match(/router_initialized_v120/g) || []).length, 2);
  for (const k of Object.keys(before)) if (k !== 'func') a.deepEqual(after[k], before[k], k);
});
