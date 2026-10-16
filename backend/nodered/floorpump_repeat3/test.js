// Offline tests for FLOORPUMP-REPEAT.3 (.15): node --test test.js
// The new function must decide exactly like the old one for every input sequence.
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const [after] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const [before] = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));

function make(node, g) {
  const c = {};
  const global = {get: k => g[k], set: (k, v) => { g[k] = v; }};
  const context = {get: k => c[k], set: (k, v) => { c[k] = v; }};
  const fn = new Function('msg', 'global', 'context', 'node', node.func);
  return msg => { const out = fn({...msg}, global, context, {}); return out === null ? null : out.payload; };
}
const P = t => ({topic: 'emon/resolDL2/sensor3', payload: t});
const MODE = m => ({topic: 'zara/domotics/palazzetti/modalita', payload: m});
const STOP = s => ({topic: 'zara/domotics/palazzetti/oraspegnimento', payload: s});

test('same decisions as REPEAT.2 on a long mixed sequence', () => {
  const seq = [P(33), P(36), STOP('19:00'), P(34), MODE('inverno'), P(31), P(29), P(32), P(35.5), P(30), P(40)];
  for (const enabled of [0, 1]) {
    const old = make(before, {pompapavimentoenabled: enabled}), neu = make(after, {pompapavimentoenabled: enabled});
    a.deepEqual(seq.map(neu), seq.map(old), 'enabled=' + enabled);
  }
});

test('fireplace mode recorded only with the pump enabled, as before', () => {
  for (const enabled of [0, 1]) {
    const go = {pompapavimentoenabled: enabled}, gn = {pompapavimentoenabled: enabled};
    make(before, go)(MODE('inverno')); make(after, gn)(MODE('inverno'));
    a.equal(gn.palazzettiModalita, go.palazzettiModalita);
  }
});

test('fireplace times are no longer read or stored', () => {
  const g = {pompapavimentoenabled: 1};
  make(after, g)(STOP('19:00'));
  a.equal(g.palazzettiOraSpegnimento, undefined);
  a.ok(!/palazzettiOra/.test(after.func));
});

test('only the function code changes', () => {
  a.deepEqual({...after, func: before.func}, before);
});
