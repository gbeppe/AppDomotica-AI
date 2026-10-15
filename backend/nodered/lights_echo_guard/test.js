// Offline tests for LIGHTS-ECHO-GUARD.1: node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const [after] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const [before] = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));
const T = 'zara/domotics/lights/livinglamp';
let clock = 0;

function run(node, store, msg) {
  const context = {get: k => store[k], set: (k, v) => { store[k] = v; }};
  const fn = new Function('msg', 'context', 'node', 'Date', node.func);
  return fn(msg, context, {id: node.id, warn: () => {}}, {now: () => clock});
}
const app = (on, topic = T) => ({topic, payload: on ? 'on' : 'off', _semantic_command: {
  schema: 'domopi.semantic_command.v1', actor: 'utente', stage: 'routed', routed_topic: topic,
  requested_state: on, command_id: 'c' + clock, entity_id: 'lights.living.power'}});
const mqtt = (payload, topic = T) => ({topic, payload});
// Which IR chain a message starts: output 3 = living on, 4 = living off.
const chain = out => out === null ? null : out[3] ? 'on' : out[4] ? 'off' : null;

test('live bug: echoes of two opposite chains re-trigger each other', () => {
  const store = {};
  clock = 0; a.equal(chain(run(before, store, app(true))), 'on');
  clock = 800; a.equal(chain(run(before, store, app(false))), 'off');
  clock = 6000; a.equal(chain(run(before, store, mqtt('on'))), 'on');   // echo of the on chain
  clock = 6800; a.equal(chain(run(before, store, mqtt('off'))), 'off'); // echo of the off chain
  clock = 12000; a.equal(chain(run(before, store, mqtt('on'))), 'on');  // ... forever
});

test('echoes are consumed: two IR chains, then silence; last command wins', () => {
  const store = {};
  clock = 0; a.equal(chain(run(after, store, app(true))), 'on');
  clock = 800; a.equal(chain(run(after, store, app(false))), 'off');
  clock = 6000; a.equal(run(after, store, mqtt('on')), null);
  clock = 6800; a.equal(run(after, store, mqtt('off')), null);
  clock = 30000; a.equal(chain(run(after, store, app(true))), 'on'); // next real command works
});

test('a single command and its echo: one chain only', () => {
  const store = {};
  clock = 0; a.equal(chain(run(after, store, app(false))), 'off');
  clock = 6000; a.equal(run(after, store, mqtt('off')), null);
});

test('app commands are never taken for echoes', () => {
  const store = {};
  clock = 0; a.equal(chain(run(after, store, app(true))), 'on');
  clock = 1000; a.equal(chain(run(after, store, app(false))), 'off');
  clock = 2000; a.equal(chain(run(after, store, app(true))), 'on');
});

test('an MQTT command with no pending echo, or after 10 s, is routed', () => {
  const store = {};
  clock = 0; a.equal(chain(run(after, store, mqtt('on'))), 'on');
  clock = 20000; a.equal(chain(run(after, store, mqtt('off'))), 'off');
  clock = 31000; a.equal(chain(run(after, store, mqtt('on'))), 'on'); // the off echo never came
});

test('echoes are tracked per light', () => {
  const store = {}, tv = 'zara/domotics/lights/tvlamp';
  clock = 0; run(after, store, app(true));
  clock = 500; a.ok(run(after, store, mqtt('on', tv))[5]); // tv on: output 5, not an echo
});

test('only the function code changes', () => {
  a.deepEqual({...after, func: before.func}, before);
  a.equal((after.func.match(/LIGHTS-ECHO-GUARD\.1/g) || []).length, 1);
});
