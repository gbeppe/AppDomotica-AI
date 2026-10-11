// Offline tests for ROUTER-CLEANUP.1: node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const after = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const before = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));
const manifest = JSON.parse(fs.readFileSync(__dirname + '/manifest.json'));
const ROUTER = '5a7c1d360710ca21';
const node = (list, id) => list.find(n => n.id === id);

function router(n) {
  const store = {};
  const context = {get: k => store[k], set: (k, v) => { store[k] = v; }};
  const fn = new Function('msg', 'context', 'node', 'env', n.func);
  return msg => fn(msg, context, {id: n.id, warn: () => {}, error: () => {}}, {get: () => undefined});
}
const app = out => (out && out[1]) || [];
const legacy = out => (out ? [out[0]].flat() : []).filter(Boolean);  // output 0: legacy commands
const stat = (out, topic) => app(out).filter(m => m.topic === topic).map(m => m.payload);
const FLOOR = 'zara/interface/heating/floor_pump/enabled/stat';

test('baseline bug: numeric 1 from the legacy echo became false', () => {
  const r = router(node(before, ROUTER));
  a.deepEqual(stat(r({topic: 'zara/domotics/pompapavimento/abilitata', payload: 1}), FLOOR), [false]);
});

test('numeric 1 and text "1" are on, 0 is off', () => {
  const r = router(node(after, ROUTER));
  a.deepEqual(stat(r({topic: 'zara/domotics/pompapavimento/abilitata', payload: 1}), FLOOR), [true]);
  a.deepEqual(stat(r({topic: 'zara/domotics/pompapavimento/abilitata', payload: 0}), FLOOR), [false]);
  a.deepEqual(stat(r({topic: 'zara/domotics/pompapavimento/abilitata', payload: '1'}), FLOOR), [true]);
});

test('text true/false sources (settings) are unchanged', () => {
  const r = router(node(after, ROUTER));
  const t = 'zara/interface/settings/eco_lights/stat';
  a.deepEqual(stat(r({topic: 'zara/domotics/luciECO', payload: 'true'}), t), [true]);
  a.deepEqual(stat(r({topic: 'zara/domotics/luciECO', payload: 'false'}), t), [false]);
});

test('floor pump command still goes to the legacy topic as "1"', () => {
  const r = router(node(after, ROUTER));
  const out = legacy(r({topic: 'zara/interface/heating/floor_pump/enabled/cmd', payload: 'true'}));
  a.deepEqual(out.filter(m => m.topic === 'zara/domotics/pompapavimento/abilitata').map(m => String(m.payload)), ['1']);
});

test('HiFi lamp and heat pump are gone from the router', () => {
  const r = router(node(after, ROUTER));
  a.deepEqual(legacy(r({topic: 'zara/interface/lights/hifi/power/cmd', payload: 'true'})), []);
  a.deepEqual(legacy(r({topic: 'zara/interface/heating/heat_pump/enabled/cmd', payload: 'true'})), []);
  a.deepEqual(app(r({topic: 'stat/tasmota_86AD14/POWER', payload: 'ON'})), []);
  const b = router(node(before, ROUTER));
  a.ok(legacy(b({topic: 'zara/interface/lights/hifi/power/cmd', payload: 'true'})).length > 0);  // baseline had it
});

test('only names change on link-outs; removed nodes are not wired from kept ones', () => {
  for (const [id, name] of Object.entries(manifest.renamed)) {
    const o = node(before, id), n = node(after, id);
    for (const k of Object.keys(o)) if (k !== 'name') a.deepEqual(n[k], o[k], id + ' ' + k);
    a.equal(n.name, name);
  }
  for (const id of manifest.removed_ids) a.ok(node(before, id) && !node(after, id));
  for (const n of after) for (const ws of n.wires || []) for (const w of ws) a.ok(!manifest.removed_ids.includes(w));
});
