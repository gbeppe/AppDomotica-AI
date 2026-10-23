// Offline tests for SOLAR-TILTED.1: TZ=Europe/Rome node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const src = fs.readFileSync(__dirname + '/tilted.js', 'utf8');
const added = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const removed = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));
let clock = 0;
class FakeDate extends Date { constructor(...x) { super(...(x.length ? x : [clock])); } static now() { return clock; } }
const at = s => { clock = Date.parse(s); };
function store() { const file = {}; return {file, get: (k) => file[k], set: (k, v) => { file[k] = v; }}; }
const warns = [];
const run = (flow, msg) => new Function('msg', 'flow', 'node', 'Date', src)(msg, flow, {warn: w => warns.push(w), status() {}}, FakeDate);
// Two days of hourly labels from 2026-10-23T00:00Z; 100 W/m² from 07 to 15 UTC, 0 otherwise.
function reply(face, value = 100, status = 200) {
  const time = [], gti = [];
  for (let i = 0; i < 48; i++) {
    const d = new Date(Date.UTC(2026, 9, 23, i)); const hour = d.getUTCHours();
    time.push(d.toISOString().slice(0, 16)); gti.push(hour >= 8 && hour <= 15 ? value : 0);
  }
  return {face, statusCode: status, payload: {hourly: {time, global_tilted_irradiance: gti}}};
}

test('fetch asks one tilted request per face', () => {
  const [reqs] = run(store(), {topic: 'fetch'});
  a.deepEqual(reqs.map(r => r.face), ['eclipse', 'viessmann']);
  a.match(reqs[0].url, /global_tilted_irradiance.*&tilt=40&azimuth=-37.5$/);
  a.match(reqs[1].url, /&tilt=15&azimuth=-37.5$/);
});

test('tick publishes W per face for the current hour and the daily kWh of light', () => {
  const f = store(); at('2026-10-23T10:20:00Z');
  run(f, reply('eclipse')); run(f, reply('viessmann', 50));
  const [, values, summary] = run(f, {topic: 'tick'});
  a.deepEqual(values, [{topic: 'emon/planetary/forecast_smodule', payload: '1966'},
                       {topic: 'emon/planetary/forecast_smodule2', payload: '976'}]);
  const s = JSON.parse(summary.payload);
  a.equal(summary.topic, 'zara/interface/energy/solar_forecast/stat');
  a.equal(s.faces.eclipse.today_kwh, 15.7);           // 8 hours × 100 W/m² × 19.66 m²
  a.equal(s.faces.viessmann.tomorrow_kwh, 7.8);
  a.equal(s.today_kwh, 23.5);
});

test('bad replies keep the old data; old or missing data are not published', () => {
  const f = store(); at('2026-10-23T10:20:00Z');
  a.equal(run(f, {topic: 'tick'}), null);
  run(f, reply('eclipse'));
  a.equal(run(f, reply('eclipse', 100, 500)), null);
  a.equal(run(f, {face: 'eclipse', statusCode: 200, payload: {hourly: {time: ['x'], global_tilted_irradiance: []}}}), null);
  a.equal(warns.length, 2);
  a.equal(run(f, {topic: 'tick'})[1].length, 1);       // only eclipse
  at('2026-10-23T16:30:01Z');                          // 6 h after the fetch
  a.equal(run(f, {topic: 'tick'}), null);
});

test('the patch removes only the broken Irradiance branch and adds 6 nodes', () => {
  a.equal(removed.length, 6);
  a.ok(removed.every(n => !JSON.stringify(n).includes('solarEnergy.php panelModel=Eclipse"')));
  a.equal(removed.filter(n => n.type === 'exec' && n.command.endsWith('Irradiance=')).length, 2);
  a.deepEqual(added.map(n => n.type).sort(), ['function', 'http request', 'inject', 'inject', 'mqtt out', 'mqtt out']);
  const outs = added.filter(n => n.type === 'mqtt out');
  a.deepEqual(outs.map(o => [o.broker, o.retain]), [['cfd9898e.1367b8', 'true'], ['3ed6302a.5af928', 'true']]);
});
