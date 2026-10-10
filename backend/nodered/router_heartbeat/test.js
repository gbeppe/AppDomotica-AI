// Offline tests for ROUTER-HEARTBEAT.1: node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const [after] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const [before] = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));

function router(node) {
  const store = {};
  const context = {get: k => store[k], set: (k, v) => { store[k] = v; }};
  const fn = new Function('msg', 'context', 'node', 'env', node.func);
  let now = 1_000_000;
  const realNow = Date.now;
  return {
    at(ms) { now = ms; return this; },
    run(msg) { Date.now = () => now; try { return fn(msg, context, {id: node.id, warn: () => {}}, {get: () => undefined}); } finally { Date.now = realNow; } },
  };
}
const SOLAR = 'zara/interface/energy/solar/power/stat';
const solar = w => ({topic: 'TeslaPowerwall/solar_instant_power', payload: String(w)});
const app = out => (out && out[1]) || [];

test('only the Router body changes', () => {
  for (const k of Object.keys(before)) if (k !== 'func') a.deepEqual(after[k], before[k], k);
  new Function('msg', 'context', 'node', 'env', after.func);
});

test('baseline: a steady reading inside the 15 W deadband is never republished', () => {
  const r = router(before);
  a.equal(app(r.at(1_000_000).run(solar(0)))[0].topic, SOLAR);
  a.deepEqual(app(r.at(1_120_000).run(solar(3))), []);   // two minutes later: still silent
});

test('steady energy reading is republished as heartbeat every 60 s', () => {
  const r = router(after);
  r.at(1_000_000).run(solar(0));
  a.deepEqual(app(r.at(1_059_999).run(solar(3))), []);
  const beat = app(r.at(1_060_000).run(solar(4)));
  a.deepEqual(beat.map(m => [m.topic, m.payload, m.retain, m.qos]), [[SOLAR, 0, true, 1]]);
  a.deepEqual(app(r.at(1_090_000).run(solar(2))), []);    // next heartbeat only after another 60 s
  a.equal(app(r.at(1_120_000).run(solar(1)))[0].payload, 0);
});

test('real changes still pass immediately and keep the last published value', () => {
  const r = router(after);
  r.at(1_000_000).run(solar(3000));
  a.equal(app(r.at(1_000_100).run(solar(3200)))[0].payload, 3200);
  a.equal(app(r.at(1_070_000).run(solar(3205)))[0].payload, 3200);   // heartbeat repeats 3200, not 3205
});

test('no heartbeat for non-energy states or before a first publication', () => {
  const r = router(after);
  a.deepEqual(app(r.at(1_000_000).run({topic: 'casa/clima/stat/predictive_reserve_operating_mode', payload: 'bogus'})), []);
  r.at(1_000_000).run({topic: 'casa/clima/stat/predictive_reserve_operating_mode', payload: 'ACTIVE'});
  a.deepEqual(app(r.at(1_200_000).run({topic: 'casa/clima/stat/predictive_reserve_operating_mode', payload: 'bogus'})), []);
});

test('a silent source produces nothing: the app can detect stale data', () => {
  const r = router(after);
  r.at(1_000_000).run(solar(0));
  const other = app(r.at(1_300_000).run({topic: 'TeslaPowerwall/load_instant_power', payload: '900'}));
  a.ok(!other.some(m => m.topic === SOLAR));
});
