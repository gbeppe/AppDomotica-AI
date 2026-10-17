// Offline tests for HOUSE-ALERTS.1: node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const src = fs.readFileSync(__dirname + '/alerts.js', 'utf8');
const MIN = 60000;
function engine(t0 = 1e12) {
  const store = {};
  const context = {get: (k) => store[k], set: (k, v) => { store[k] = JSON.parse(JSON.stringify(v)); }};
  let now = t0;
  const send = (topic, payload) => new Function('msg', 'context', src)({topic, payload, now}, context)[0].map(m => m.payload);
  return {send, tick: () => send('tick', ''), at: (m) => { now = t0 + m * MIN; }};
}
test('puffer: alert above 85, once, back below 80, repeat waits 2 hours', () => {
  const e = engine();
  a.equal(e.send('zara/interface/heating/puffer/top_temperature/stat', '84.9').length, 0);
  const [hot] = e.send('zara/interface/heating/puffer/top_temperature/stat', '86.2');
  a.deepEqual(hot, {key: 'puffer_hot', title: 'Puffer alto a 86,2 °C', message: 'Sopra 85 °C: controlla caminetto e pompe.', tags: ['fire', 'warning'], priority: 4});
  a.equal(e.send('zara/interface/heating/puffer/top_temperature/stat', '87').length, 0);
  a.equal(e.send('zara/interface/heating/puffer/top_temperature/stat', '82').length, 0);
  e.at(10);
  a.equal(e.send('zara/interface/heating/puffer/top_temperature/stat', '79.5')[0].message, 'Puffer alto tornato a 79,5 °C.');
  e.at(20);
  a.equal(e.send('zara/interface/heating/puffer/top_temperature/stat', '86').length, 0);   // within 2 h: quiet
  a.equal(e.send('zara/interface/heating/puffer/top_temperature/stat', '70').length, 0);   // quiet alert: no "ok"
  e.at(200);
  a.equal(e.send('zara/interface/heating/puffer/top_temperature/stat', '86').length, 1);
});
test('board offline for 5 minutes, then online again', () => {
  const e = engine();
  a.equal(e.send('/esp8266/floorPump/online', 'offline').length, 0);
  e.at(4); a.equal(e.tick().length, 0);
  e.at(5); a.deepEqual(e.tick().map(x => x.message), ['La scheda della pompa pavimento è scollegata da 5 minuti.']);
  e.at(6); a.equal(e.tick().length, 0);
  a.deepEqual(e.send('/esp8266/floorPump/online', 'online').map(x => x.message), ['La scheda della pompa pavimento è di nuovo collegata.']);
  a.equal(e.send('/esp8266/palazzetti/online', 'offline').length, 0);
  e.at(8); a.equal(e.send('/esp8266/palazzetti/online', 'online').length, 0);              // short drop: silent
});
test('stale data after 30 minutes (outdoor 2 hours) and back', () => {
  const e = engine();
  e.send('emon/emonth5/temperature_calibrated', '21.1'); e.send('emon/bagnoServizio/temperature', '21'); e.send('emon/weather/extTemp', '12');
  e.at(25); e.send('emon/bagnoServizio/temperature', '21');
  e.at(31);
  a.deepEqual(e.tick().map(x => x.message), ['Nessun valore (sensore di temperatura del soggiorno) da 31 minuti.']);
  e.at(121);
  a.deepEqual(e.tick().map(x => x.key), ['stale_bath', 'stale_outdoor']);
  a.equal(e.tick().length, 0);
  a.equal(e.send('emon/weather/extTemp', '11')[0].message, 'Temperatura esterna: valori di nuovo presenti.');
});
test('battery and profile change', () => {
  const e = engine();
  a.equal(e.send('zara/interface/energy/battery/soc/stat', '19.4')[0].title, 'Batteria al 19 %');
  a.equal(e.send('zara/interface/energy/battery/soc/stat', '24').length, 0);
  a.equal(e.send('zara/interface/energy/battery/soc/stat', '26')[0].message, 'Batteria al 26 %.');
  const prof = (p, m) => JSON.stringify({profile: p, mode: 'auto', outdoor_mean_72h: m, timer_mode: 'shadow'});
  a.equal(e.send('zara/interface/heating/profile/stat', prof('ridotto', 13)).length, 0);   // first value: no alert
  a.equal(e.send('zara/interface/heating/profile/stat', prof('ridotto', 12)).length, 0);
  a.deepEqual(e.send('zara/interface/heating/profile/stat', prof('pieno', 9.6))[0], {key: 'profile',
    title: 'Profilo di riscaldamento: Pieno', message: "L'automatico è passato da Ridotto a Pieno (media esterna 72 ore 9,6 °C). Il timer è in prova: i termostati non cambiano.",
    tags: ['thermometer'], priority: 3});
});
test('the formatter publishes on domopi-casa only with a token', () => {
  const f = fs.readFileSync(__dirname + '/ntfy.js', 'utf8');
  const run = (tok) => new Function('msg', 'flow', 'node', f)({payload: {title: 'T', message: 'M', tags: ['x'], priority: 3}}, {get: () => tok}, {warn: () => {}});
  a.equal(JSON.parse(run('tk_a').payload).topic, 'domopi-casa');
  a.equal(run(undefined), null);
});
test('the generated tab adds nodes only and carries no token', () => {
  const nodes = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
  a.equal(nodes[0].type, 'tab');
  a.ok(nodes.every(n => !/tk_[A-Za-z0-9]{20,}/.test(JSON.stringify(n))));
  a.deepEqual(JSON.parse(fs.readFileSync(__dirname + '/rollback.json')), []);
  a.equal(nodes.filter(n => n.type === 'mqtt in').length, 8);
});
