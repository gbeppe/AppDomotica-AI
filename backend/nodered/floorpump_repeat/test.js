// Offline tests for FLOORPUMP-REPEAT.1 (.15): node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const [after] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const [before] = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));

function make(node, enabled) {
  const g = {pompapavimentoenabled: enabled, palazzettiOraSpegnimento: '19:00', palazzettiOraAvvio: '17:00'};
  const c = {};
  const global = {get: k => g[k], set: (k, v) => { g[k] = v; }};
  const context = {get: k => c[k], set: (k, v) => { c[k] = v; }};
  const fn = new Function('msg', 'global', 'context', 'node', node.func);
  const puffer = t => { const out = fn({topic: 'emon/resolDL2/sensor3', payload: t}, global, context, {});
                        return out === null ? null : out.payload; };
  return {puffer, g};
}

test('live behaviour: nothing is sent in the 30-35 °C band', () => {
  const {puffer} = make(before, 1);
  a.deepEqual([36, 34, 31, 29].map(puffer), [1, null, null, 0]);
});

test('the last command is repeated in the band, thresholds unchanged', () => {
  const {puffer} = make(after, 1);
  a.equal(puffer(33), null);                         // no command yet after a restart
  a.deepEqual([36, 34, 31, 29, 32, 34, 35.5].map(puffer), [1, 1, 1, 0, 0, 0, 1]);
});

test('disabled still sends 0 and is remembered', () => {
  const p = make(after, 0);
  a.deepEqual([40, 33].map(p.puffer), [0, 0]);
});

test('the fireplace globals are still recorded', () => {
  const {puffer, g} = make(after, 1);
  puffer(36);
  a.equal(g.tempPufferSup, 36);
});

test('only the function code changes', () => {
  a.deepEqual({...after, func: before.func}, before);
});
