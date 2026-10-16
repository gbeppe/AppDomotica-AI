// Offline tests for FLOORPUMP-REPEAT.2 (.15): node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const [after] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const [before] = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));

function make(node, g) {
  const c = {};
  const global = {get: k => g[k], set: (k, v) => { g[k] = v; }};
  const context = {get: k => c[k], set: (k, v) => { c[k] = v; }};
  const fn = new Function('msg', 'global', 'context', 'node', node.func);
  return t => { const out = fn({topic: 'emon/resolDL2/sensor3', payload: t}, global, context, {});
                return out === null ? null : out.payload; };
}

test('live bug: enabled pump, no fireplace stop time after a restart -> TypeError', () => {
  const puffer = make(before, {pompapavimentoenabled: 1});
  a.throws(() => puffer(40.3), /split/);
});

test('without the stop time the pump logic works', () => {
  const puffer = make(after, {pompapavimentoenabled: 1});
  a.deepEqual([40.3, 33, 29].map(puffer), [1, 1, 0]);
});

test('with the stop time nothing changes', () => {
  const puffer = make(after, {pompapavimentoenabled: 1, palazzettiOraSpegnimento: '19:00', palazzettiOraAvvio: '17:00'});
  a.deepEqual([36, 34, 29].map(puffer), [1, 1, 0]);
});

test('only that line changes', () => {
  a.deepEqual({...after, func: before.func}, before);
  a.equal(after.func.split('\n').length, before.func.split('\n').length + 1);
});
