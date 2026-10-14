// Offline tests for ROUTER-FIREPLACE-RETAIN.1: node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const [after, afterOut] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const [before, beforeOut] = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));
const P = 'zara/interface/fireplace/main/';

function run(node, store, msg) {
  const context = {get: k => store[k], set: (k, v) => { store[k] = v; }};
  const fn = new Function('msg', 'context', 'node', 'env', node.func);
  return fn(msg, context, {id: node.id, warn: () => {}, error: () => {}}, {get: () => undefined});
}
const legacy = out => out && out[0];
// Same rule as Node-RED 5 mqtt out (10-mqtt.js): the node setting wins over msg.retain.
const published = (outNode, msg) => {
  const r = outNode.retain || msg.retain || false;
  return r === true || r === 'true';
};

test('live bug: settings reached .15 without retain', () => {
  const out = legacy(run(before, {}, {topic: P + 'start_time/cmd', payload: '16:30'}));
  a.equal(published(beforeOut, out), false);
});

test('mode, times and auto power are retained on the .15 broker', () => {
  for (const [t, p, topic, v] of [
    ['mode', 'Riscaldamento', 'zara/domotics/palazzetti/modalita', 'inverno'],
    ['start_time', '16:30', 'zara/domotics/palazzetti/oraavvio', '16:30'],
    ['stop_time', '17:30', 'zara/domotics/palazzetti/oraspegnimento', '17:30'],
    ['auto_power', 'true', 'zara/domotics/controlloAutomaticoPotenzaCaminetto', 'true']]) {
    const out = legacy(run(after, {}, {topic: P + t + '/cmd', payload: p}));
    a.deepEqual([out.topic, out.payload, out.broker, out.retain], [topic, v, 'emon', true]);
    a.equal(published(afterOut, out), true);
  }
});

test('manual on/off and level stay one-shot commands', () => {
  for (const [t, p, v] of [['power', 'true', 'on'], ['power', 'false', 'off'], ['level', '4', '4']]) {
    const out = legacy(run(after, {}, {topic: P + t + '/cmd', payload: p}));
    a.deepEqual([out.topic, out.payload, out.broker], ['alexa/caminetto', v, 'emon']);
    a.equal(out.retain, undefined);
    a.equal(published(afterOut, out), false);
  }
});

test('home broker routes are unchanged', () => {
  const out = legacy(run(after, {}, {topic: 'zara/interface/settings/eco_lights/cmd', payload: 'true'}));
  if (out) a.equal(out.retain, undefined);
  const floor = legacy(run(after, {}, {topic: 'zara/interface/heating/floor_pump/enabled/cmd', payload: 'true'}));
  a.deepEqual([floor.broker, floor.retain], ['home', undefined]);
});

test('tables cached by v121 are rebuilt (v122)', () => {
  const store = {};
  run(before, store, {topic: P + 'start_time/cmd', payload: '17:00'});
  a.equal(legacy(run(after, store, {topic: P + 'start_time/cmd', payload: '17:00'})).retain, true);
  a.equal((after.func.match(/router_initialized_v122/g) || []).length, 2);
});

test('only the retain setting of the emon output changes', () => {
  a.deepEqual({...afterOut, retain: 'false'}, beforeOut);
  a.equal(afterOut.retain, '');
});
