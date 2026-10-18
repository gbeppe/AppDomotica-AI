// RULES-ENGINE.3: node --test test.js (engine and formatter tests live in ../rules_engine and ../rules_notify)
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
test('only the func of the engine and of the formatter change', () => {
  const after = JSON.parse(fs.readFileSync(__dirname + '/update.json')), before = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));
  a.equal(after.length, 2);
  after.forEach((n, i) => { a.deepEqual({...n, func: ''}, {...before[i], func: ''}); a.notEqual(n.func, before[i].func); });
  a.equal(after[0].func, fs.readFileSync(__dirname + '/../rules_engine/rules.js', 'utf8'));
  a.equal(after[1].func, fs.readFileSync(__dirname + '/../rules_notify/ntfy.js', 'utf8'));
});
