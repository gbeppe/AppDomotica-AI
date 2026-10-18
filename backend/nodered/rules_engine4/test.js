// RULES-ENGINE.4: node --test test.js (engine tests live in ../rules_engine)
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
test('only the func of the engine changes', () => {
  const [after] = JSON.parse(fs.readFileSync(__dirname + '/update.json')), [before] = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));
  a.deepEqual({...after, func: ''}, {...before, func: ''});
  a.equal(after.func, fs.readFileSync(__dirname + '/../rules_engine/rules.js', 'utf8'));
});
