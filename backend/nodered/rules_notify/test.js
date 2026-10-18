// Offline tests for RULES-ENGINE.2: node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const src = fs.readFileSync(__dirname + '/ntfy.js', 'utf8');
function run(event, token = 'tk_abc') {
  const flow = {get: k => (k === 'ntfyToken' ? token : undefined)};
  const warnings = [];
  const out = new Function('msg', 'flow', 'node', src)({payload: JSON.stringify(event)}, flow, {warn: w => warnings.push(w)});
  return {out, warnings};
}
test('fired, would_fire and blocked become notifications with the right priority', () => {
  const fired = run({kind: 'fired', text: 'quando il puffer alto supera 42 gradi spengo il caminetto, una volta'}).out;
  a.equal(fired.url, 'http://127.0.0.1:2586/');
  a.equal(fired.headers.Authorization, 'Bearer tk_abc');
  a.deepEqual(JSON.parse(fired.payload), {topic: 'domopi-regole', title: 'Regola intervenuta',
    message: 'quando il puffer alto supera 42 gradi spengo il caminetto, una volta. Comando inviato.', tags: ['fire'], priority: 4});
  const test = JSON.parse(run({kind: 'would_fire', text: 'alle 22:00 ogni giorno spengo tutte le luci, ogni volta'}).out.payload);
  a.equal(test.message, 'Avrebbe agito: alle 22:00 ogni giorno spengo tutte le luci, ogni volta. Nessun comando inviato.');
  const blocked = JSON.parse(run({kind: 'blocked', text: 'x', reason: 'caminetto non in Manuale', testing: true}).out.payload);
  a.equal(blocked.message, 'x: caminetto non in Manuale (in prova).');
});
test('a timed command created active is announced as programmed', () => {
  const m = JSON.parse(run({kind: 'created', text: 'alle 19:00 (oggi) spengo il caminetto', active: true}).out.payload);
  a.equal(m.title, 'Comando programmato');
  a.equal(m.message, 'alle 19:00 (oggi) spengo il caminetto. Attivo subito.');
});
test('without token or with an unknown event nothing is sent', () => {
  const r = run({kind: 'fired', text: 'x'}, null);
  a.equal(r.out, null);
  a.match(r.warnings[0], /token/);
  a.equal(run({kind: 'other'}).out, null);
  a.equal(new Function('msg', 'flow', 'node', src)({payload: 'not json'}, {get: () => 'tk'}, {}), null);
});
test('the engine keeps its outputs and gains one wire', () => {
  const [engine, ...added] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
  const [before] = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));
  a.deepEqual({...engine, wires: before.wires}, before);
  a.equal(engine.wires[3].length, before.wires[3].length + 1);
  a.ok(added.every(n => !/tk_[A-Za-z0-9]{20,}/.test(JSON.stringify(n))));
});
