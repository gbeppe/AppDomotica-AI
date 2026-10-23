// Offline tests for LIGHTS-ECHO-GUARD.2: node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const byId = file => Object.fromEntries(JSON.parse(fs.readFileSync(__dirname + '/' + file)).map(n => [n.id, n]));
const after = byId('update.json'), before = byId('rollback.json');
const F17 = 'ea8307d48995ca93';
const TV = 'zara/domotics/lights/tvlamp', LIVING = 'zara/domotics/lights/livinglamp';
const publisher = (nodes, word, payload) => Object.values(nodes).find(n =>
  n.func.includes(`msg.payload = '${payload}'`) && n.func.includes(word) && n.id !== F17);
let clock = 0;

function run(node, ctx, glob, msg) {
  const store = s => ({get: k => s[k], set: (k, v) => { s[k] = v; }});
  const fn = new Function('msg', 'context', 'global', 'node', 'Date', node.func);
  return fn(msg, store(ctx), store(glob), {id: node.id, warn: () => {}}, {now: () => clock});
}
const app = (on, topic = TV) => ({topic, payload: on ? 'on' : 'off', _semantic_command: {
  schema: 'domopi.semantic_command.v1', actor: 'utente', stage: 'routed', routed_topic: topic,
  requested_state: on, command_id: 'c' + clock, entity_id: 'lights.tv.power'}});
const mqtt = (payload, topic = TV) => ({topic, payload});
// TV chain outputs of "function 17": 5 = on, 6 = off.
const chain = out => out === null ? null : out[5] ? 'on' : out[6] ? 'off' : null;
// A Broadlink chain ran and its publisher returned the state message to publish.
function published(nodes, glob, payload, word = 'lights.tv.power') {
  const out = run(publisher(nodes, word, payload), {}, glob, {payload: {status: 'OK'}});
  const msg = Array.isArray(out) ? out[0] : out;
  a.equal(msg.payload, payload);
  return mqtt(payload, word === 'lights.tv.power' ? TV : LIVING);
}

test('live bug (.1): a dashboard press is sent twice', () => {
  const ctx = {}, glob = {};
  clock = 0; const echo = published(before, glob, 'on');          // dashboard -> chain -> state
  clock = 70; a.equal(chain(run(before[F17], ctx, glob, echo)), 'on'); // routed again: 2nd "3on"
});

test('dashboard, Alexa or schedules: the echo is consumed, one send only', () => {
  const ctx = {}, glob = {};
  clock = 0; const echo = published(after, glob, 'on');
  clock = 70; a.equal(run(after[F17], ctx, glob, echo), null);
  clock = 5000; a.equal(chain(run(after[F17], ctx, glob, app(false))), 'off'); // app off still works
});

test('app command: one chain, its echo consumed', () => {
  const ctx = {}, glob = {};
  clock = 0; a.equal(chain(run(after[F17], ctx, glob, app(true))), 'on');
  clock = 60; const echo = published(after, glob, 'on');
  clock = 70; a.equal(run(after[F17], ctx, glob, echo), null);
  a.deepEqual(glob.lightsPendingEchoes[TV], []);
});

test('15/10 double tap: two opposite chains, then silence', () => {
  const ctx = {}, glob = {};
  clock = 0; a.equal(chain(run(after[F17], ctx, glob, app(true))), 'on');
  clock = 800; a.equal(chain(run(after[F17], ctx, glob, app(false))), 'off');
  clock = 900; const on = published(after, glob, 'on');
  clock = 1000; const off = published(after, glob, 'off');
  clock = 1100; a.equal(run(after[F17], ctx, glob, on), null);
  clock = 1200; a.equal(run(after[F17], ctx, glob, off), null);
  clock = 30000; a.equal(chain(run(after[F17], ctx, glob, app(true))), 'on');
});

test('an MQTT command with no announcement, or after 10 s, is routed', () => {
  const ctx = {}, glob = {};
  clock = 0; a.equal(chain(run(after[F17], ctx, glob, mqtt('on'))), 'on');
  clock = 1000; published(after, glob, 'off');
  clock = 12000; a.equal(chain(run(after[F17], ctx, glob, mqtt('off'))), 'off'); // stale announcement
});

test('app commands are never taken for echoes', () => {
  const ctx = {}, glob = {};
  clock = 0; published(after, glob, 'on');
  clock = 10; a.equal(chain(run(after[F17], ctx, glob, app(true))), 'on');
});

test('announcements are per topic', () => {
  const ctx = {}, glob = {};
  clock = 0; published(after, glob, 'on', 'lights.living.power');
  clock = 10; a.equal(chain(run(after[F17], ctx, glob, mqtt('on'))), 'on'); // tv: not an echo
});

test('only the function code changes; every publisher announces its own topic', () => {
  a.deepEqual(Object.keys(after).sort(), Object.keys(before).sort());
  a.equal(Object.keys(after).length, 19);
  for (const id of Object.keys(after)) {
    a.deepEqual({...after[id], func: before[id].func}, before[id]);
    a.equal((after[id].func.match(/LIGHTS-ECHO-GUARD\.2/g) || []).length, 1);
  }
  a.ok(!after[F17].func.includes('pendingEchoes'));
});
