// ALEXA-HUE.1: the patch only adds the disable flags, on exactly the expected nodes. node --test test.js
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const read = f => JSON.parse(fs.readFileSync(path.join(__dirname, f), 'utf8'));
const before = read('rollback.json'), after = read('update.json'), manifest = read('manifest.json');

test('same nodes, same order, ids in the manifest', () => {
  assert.deepStrictEqual(after.map(n => n.id), before.map(n => n.id));
  assert.deepStrictEqual(manifest.modified_ids, before.map(n => n.id));
});

test('11 devices disabled, the Speak tab and the account switched off, nothing else changed', () => {
  const devices = after.filter(n => n.type === 'amazon-echo-device');
  assert.strictEqual(devices.length, 11);
  assert.ok(devices.every(n => n.d === true));
  const names = devices.map(n => n.name);
  for (const keep of ['luci', 'caminetto', 'livello caminetto', 'ventilazione', 'luce acqua piscina', 'luci pedana della piscina', 'skimmer'])
    assert.ok(!names.includes(keep), keep + ' must stay enabled');
  assert.strictEqual(after.find(n => n.type === 'tab').disabled, true);
  assert.strictEqual(after.find(n => n.type === 'alexa-remote-account').d, true);
  before.forEach((b, i) => {
    const a = Object.assign({}, after[i]); delete a.d; delete a.disabled;
    const o = Object.assign({}, b); delete o.d; delete o.disabled;
    assert.deepStrictEqual(a, o);
  });
});
