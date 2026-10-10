// Offline tests for ROUTER-DEDUP.1: node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const [after] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const [before] = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));
const manifest = JSON.parse(fs.readFileSync(__dirname + '/manifest.json'));

// Minimal Node-RED Function runtime: persistent node context, controllable clock.
function router(node) {
  const store = {}, warnings = [];
  const context = {get: k => store[k], set: (k, v) => { store[k] = v; }};
  const nodeApi = {id: node.id, warn: w => warnings.push(w), error: w => warnings.push(w)};
  const fn = new Function('msg', 'context', 'node', 'env', node.func);
  let now = 1_000_000;
  const realNow = Date.now;
  return {
    warnings,
    at(ms) { now = ms; return this; },
    run(msg) {
      Date.now = () => now;
      try { return fn(msg, context, nodeApi, {get: () => undefined}); } finally { Date.now = realNow; }
    },
  };
}
const SOGLIA = 'zara/interface/logica_controllo/soglia_temperatura_avvio_cooling_c/stat';
const composite = (soglia, ts) => ({topic: 'casa/clima/stato_completo',
  payload: JSON.stringify({timestamp: ts, logica_controllo: {soglia_temperatura_avvio_cooling_c: soglia}})});
const appTopics = out => (out && out[1] ? out[1].map(m => m.topic) : []);

test('only the Router function body changes, wiring and outputs are identical', () => {
  a.equal(after.id, before.id);
  a.equal(after.id, manifest.modified_ids[0]);
  for (const k of Object.keys(before)) if (k !== 'func') a.deepEqual(after[k], before[k], k);
  a.notEqual(after.func, before.func);
  new Function('msg', 'context', 'node', 'env', after.func);
});

test('identical composite state is published once, changes pass immediately', () => {
  const r = router(after);
  const first = appTopics(r.at(1_000_000).run(composite(26.5, 1)));
  a.ok(first.includes(SOGLIA));
  a.ok(first.includes('zara/interface/climate/full_state/timestamp/stat'));
  // Same values 50 ms later (controller re-run): nothing to republish except the changed timestamp.
  const second = appTopics(r.at(1_000_050).run(composite(26.5, 2)));
  a.deepEqual(second.filter(t => !t.includes('full_state')), []);
  a.ok(second.includes('zara/interface/climate/full_state/timestamp/stat'));
  // A real change is published at once.
  const third = appTopics(r.at(1_000_100).run(composite(27, 3)));
  a.ok(third.includes(SOGLIA));
});

test('unchanged values are republished as heartbeat after 60 s', () => {
  const r = router(after);
  r.at(1_000_000).run(composite(26.5, 1));
  a.ok(!appTopics(r.at(1_059_999).run(composite(26.5, 1))).includes(SOGLIA));
  const beat = r.at(1_060_000).run(composite(26.5, 1));
  a.ok(appTopics(beat).includes(SOGLIA));
  const msg = beat[1].find(m => m.topic === SOGLIA);
  a.equal(msg.retain, true); a.equal(msg.qos, 1); a.equal(msg.payload, 26.5);
});

test('baseline Router republished every identical state (the defect being fixed)', () => {
  const r = router(before);
  r.at(1_000_000).run(composite(26.5, 1));
  a.ok(appTopics(r.at(1_000_050).run(composite(26.5, 1))).includes(SOGLIA));
});

test('point legacy topics are deduplicated the same way', () => {
  for (const node of [before, after]) {
    const r = router(node);
    const t = 'zara/interface/heating/gas_boiler/flame/stat';
    a.ok(appTopics(r.at(1_000_000).run({topic: 'emon/OTGcaldaia/flame', payload: '1'})).includes(t));
    const repeat = appTopics(r.at(1_000_100).run({topic: 'emon/OTGcaldaia/flame', payload: '1'}));
    a.equal(repeat.includes(t), node === before);
    a.ok(appTopics(r.at(1_000_200).run({topic: 'emon/OTGcaldaia/flame', payload: '0'})).includes(t));
  }
});

test('existing per-entity deadbands (e.g. solar 15 W) keep working before the dedup', () => {
  const r = router(after);
  const t = 'zara/interface/energy/solar/power/stat';
  a.ok(appTopics(r.at(1_000_000).run({topic: 'TeslaPowerwall/solar_instant_power', payload: '3500'})).includes(t));
  a.ok(!appTopics(r.at(1_000_100).run({topic: 'TeslaPowerwall/solar_instant_power', payload: '3510'})).includes(t));
  a.ok(appTopics(r.at(1_000_200).run({topic: 'TeslaPowerwall/solar_instant_power', payload: '3600'})).includes(t));
});

test('commands and scene bridge are untouched by the patch', () => {
  for (const node of [before, after]) {
    const r = router(node);
    const scene = r.run({topic: 'zara/interface/lights/scene/cmd', payload: 'tv'});
    a.deepEqual(scene.slice(0, 3), [null, null, null]);
    a.equal(scene[3].payload, 'tv');
  }
  const outBefore = router(before).at(1_000_000).run({topic: 'zara/interface/ai/system_enabled/cmd', payload: 'true'});
  const outAfter = router(after).at(1_000_000).run({topic: 'zara/interface/ai/system_enabled/cmd', payload: 'true'});
  a.deepEqual(outAfter[0], outBefore[0]);
});
