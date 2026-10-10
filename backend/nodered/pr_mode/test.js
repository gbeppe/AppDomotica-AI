// Offline tests for PR-MODE.1: node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const after = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const before = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));
const ROUTER = '5a7c1d360710ca21', ADAPTER = 'c67ee62775ea71f8';
const pick = (list, id) => list.find(n => n.id === id);

function runner(node, store = {}) {
  const context = {get: k => store[k], set: (k, v) => { store[k] = v; }};
  const fn = new Function('msg', 'context', 'node', 'env', node.func);
  return msg => fn(msg, context, {id: node.id, warn: () => {}, error: () => {}}, {get: () => undefined});
}
const opState = (extra = {}) => ({topic: 'pr/operational_state', payload: {
  schema: 'domopi.predictive_reserve.operational_state.v1', control_enabled: false,
  operating_mode: 'ACTIVE', ready: true, ...extra}});
const appMsgs = out => (out && out[1]) || [];

test('only function bodies change; wiring, outputs and names are identical', () => {
  for (const id of [ROUTER, ADAPTER]) {
    const b = pick(before, id), u = pick(after, id);
    for (const k of Object.keys(b)) if (k !== 'func') a.deepEqual(u[k], b[k], id + ' ' + k);
    a.notEqual(u.func, b.func);
    new Function('msg', 'context', 'node', 'env', u.func);
  }
});

test('adapter publishes control, mode and readiness retained on the same output', () => {
  const out = runner(pick(after, ADAPTER))(opState());
  a.equal(out.length, 1);
  a.deepEqual(out[0].map(m => [m.topic, m.payload, m.retain, m.qos]), [
    ['casa/clima/stat/predictive_reserve_control_enabled', false, true, 1],
    ['casa/clima/stat/predictive_reserve_operating_mode', 'ACTIVE', true, 1],
    ['casa/clima/stat/predictive_reserve_ready', true, true, 1],
  ]);
});

test('adapter keeps control_enabled identical to the baseline and drops invalid extras', () => {
  const base = runner(pick(before, ADAPTER))(opState({control_enabled: true}));
  const out = runner(pick(after, ADAPTER))(opState({control_enabled: true, operating_mode: 'MAYBE', ready: 'yes'}));
  a.deepEqual(out[0], [base]);
  a.equal(runner(pick(after, ADAPTER))({payload: {operating_mode: 'ACTIVE'}}), null);
});

test('router maps the new read-only states and still maps control_enabled', () => {
  const route = runner(pick(after, ROUTER));
  const stat = (topic, payload) => appMsgs(route({topic, payload})).map(m => [m.topic, m.payload]);
  a.deepEqual(stat('casa/clima/stat/predictive_reserve_operating_mode', 'SHADOW'),
    [['zara/interface/predictive_reserve/operating_mode/stat', 'SHADOW']]);
  a.deepEqual(stat('casa/clima/stat/predictive_reserve_ready', 'false'),
    [['zara/interface/predictive_reserve/ready/stat', false]]);
  a.deepEqual(stat('casa/clima/stat/predictive_reserve_operating_mode', 'garbage'), []);
  a.deepEqual(stat('casa/clima/stat/predictive_reserve_control_enabled', 'true'),
    [['zara/interface/predictive_reserve/control_enabled/stat', true]]);
});

test('the new states have no command path', () => {
  const route = runner(pick(after, ROUTER));
  for (const t of ['zara/interface/predictive_reserve/operating_mode/cmd', 'zara/interface/predictive_reserve/ready/cmd']) {
    const out = route({topic: t, payload: 'ACTIVE'});
    a.ok(!out || out[0] === null, t);
  }
});

test('v118 guard rebuilds the routing tables cached by the previous Router', () => {
  const store = {};
  // Previous Router initialised the node context (as in production today).
  runner(pick(before, ROUTER), store)({topic: 'casa/clima/stat/predictive_reserve_control_enabled', payload: 'true'});
  a.equal(store.router_initialized_v117, true);
  a.equal(store.mapLegacyToStat['casa/clima/stat/predictive_reserve_operating_mode'], undefined);
  // Patched Router on the same context must rebuild and route the new topic.
  const out = runner(pick(after, ROUTER), store)({topic: 'casa/clima/stat/predictive_reserve_operating_mode', payload: 'ACTIVE'});
  a.deepEqual(appMsgs(out).map(m => m.topic), ['zara/interface/predictive_reserve/operating_mode/stat']);
  a.equal(store.router_initialized_v118, true);
});
