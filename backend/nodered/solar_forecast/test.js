// Offline tests for SOLAR-FORECAST.1: node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const [injectAfter, after] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const [injectBefore, before] = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));

// Runs a function node body at a fixed instant with fake flow context and node logger.
function runner(node, store = {}) {
  const logs = [];
  return {
    store, logs,
    run(msg, isoNow) {
      const fixed = Date.parse(isoNow);
      class FakeDate extends Date {
        constructor(...args) { super(...(args.length ? args : [fixed])); }
        static now() { return fixed; }
      }
      const flow = {get: k => store[k], set: (k, v) => { store[k] = v; }};
      const nodeApi = {warn: m => logs.push(['warn', m]), error: m => logs.push(['error', m])};
      const script = new vm.Script('(function(msg, flow, node){' + node.func + '\n})');
      return script.runInNewContext({Date: FakeDate})(msg, flow, nodeApi);
    },
  };
}

// Open-Meteo style payload: 48 hourly values starting at `day`.
function meteo(day, value = 300) {
  const start = Date.parse(day + 'T00:00:00Z'), time = [], rad = [];
  for (let h = 0; h < 48; h++) {
    time.push(new Date(start + h * 3600e3).toISOString().slice(0, 16));
    rad.push(value);
  }
  return {statusCode: 200, payload: {hourly: {time, shortwave_radiation: rad}}};
}

test('only crontab/name of the inject and the function body change', () => {
  for (const k of Object.keys(injectBefore)) if (!['crontab', 'name'].includes(k)) a.deepEqual(injectAfter[k], injectBefore[k], k);
  a.equal(injectAfter.crontab, '*/30 23,0-5 * * *');
  for (const k of Object.keys(before)) if (k !== 'func') a.deepEqual(after[k], before[k], k);
});

test('23:00 Rome computes tomorrow and logs one real forecast', () => {
  const r = runner(after);
  const out = r.run(meteo('2026-10-11'), '2026-10-11T21:00:00Z');   // 23:00 CEST
  a.equal(out.payload.data_previsione, '2026-10-12');
  a.equal(out.payload.ore_elaborate, 24);
  a.equal(out.payload.kwh_stimati_impianto_reali, Math.round(34 * 0.12 * 7.2 * 0.85));
  a.equal(r.store.previsione_data_domani, '2026-10-12');
  a.equal(r.store.previsione_kwh_domani, out.payload.kwh_stimati_impianto_reali);
});

test('after midnight a retry targets the current Rome day, not the UTC one', () => {
  const r = runner(after);
  const out = r.run(meteo('2026-10-12'), '2026-10-11T22:30:00Z');   // 00:30 CEST on 12/10
  a.equal(out.payload.data_previsione, '2026-10-12');
});

test('once fresh, later night runs do nothing', () => {
  const r = runner(after);
  r.run(meteo('2026-10-11'), '2026-10-11T21:00:00Z');
  a.equal(r.run(meteo('2026-10-11', 999), '2026-10-11T21:30:00Z'), null);
  a.equal(r.run(meteo('2026-10-12', 999), '2026-10-12T03:30:00Z'), null);   // 05:30 CEST, still fresh
});

test('a forecast computed in the afternoon is refreshed at 23:00', () => {
  const r = runner(after);
  r.run(meteo('2026-10-11', 100), '2026-10-11T12:00:00Z');            // 14:00 CEST (deploy)
  const out = r.run(meteo('2026-10-11', 400), '2026-10-11T21:00:00Z');
  a.ok(out && out.payload.kwh_stimati_impianto_reali > 0);
});

test('API error without hourly data is logged and keeps the old values', () => {
  const r = runner(after, {previsione_kwh_domani: 11, previsione_data_domani: '2026-10-10'});
  const out = r.run({statusCode: 503, payload: {error: true, reason: 'Service unavailable'}}, '2026-10-11T21:00:00Z');
  a.equal(out, null);
  a.deepEqual(r.store, {previsione_kwh_domani: 11, previsione_data_domani: '2026-10-10'});
  a.match(r.logs[0][1], /HTTP 503, motivo: Service unavailable/);
});

test('baseline was silent on the same error', () => {
  const r = runner(before);
  a.equal(r.run({statusCode: 503, payload: {error: true, reason: 'x'}}, '2026-10-11T21:00:00Z'), null);
  a.deepEqual(r.logs, []);
});

test('unreadable response keeps the historical 12 kWh fallback but no fake log line', () => {
  const r = runner(after, {previsione_data_domani: '2026-10-10'});
  a.equal(r.run({statusCode: 502, payload: '<html>bad gateway</html>'}, '2026-10-11T21:00:00Z'), null);
  a.equal(r.store.previsione_kwh_domani, 12);
  a.equal(r.store.previsione_data_domani, '2026-10-10');
  a.equal(r.logs[0][0], 'error');
});

test('incomplete day is not turned into a forecast', () => {
  const r = runner(after);
  const partial = meteo('2026-10-11');
  partial.payload.hourly.time = partial.payload.hourly.time.slice(0, 30);   // tomorrow has 6 hours only
  a.equal(r.run(partial, '2026-10-11T21:00:00Z'), null);
  a.equal(r.store.previsione_kwh_domani, undefined);
  a.match(r.logs[0][1], /dati incompleti per 2026-10-12 \(6 ore\)/);
});
