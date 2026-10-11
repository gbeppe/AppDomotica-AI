// Offline checks for AI-CLIMATE-CLEANUP.1: node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const removed = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));
const manifest = JSON.parse(fs.readFileSync(__dirname + '/manifest.json'));

test('only the 6 unwired copies are removed, nothing modified or added', () => {
  a.equal(removed.length, 6);
  a.deepEqual(manifest.modified_ids, []); a.deepEqual(manifest.added_ids, []);
  for (const n of removed) {
    a.ok(n.name.startsWith('Machine Learning domotico'));
    a.ok((n.wires || []).every(w => w.length === 0), n.id + ' had outputs');
  }
  a.ok(!manifest.removed_ids.includes('61992c8879c11f66'));   // the working copy stays
});
