// TASMOTA-TIMERS-OFF.1: node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
test('only the bathroom timerswitch is disabled, nothing else changes', () => {
  const [after] = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
  const [before] = JSON.parse(fs.readFileSync(__dirname + '/rollback.json'));
  a.equal(before.type, 'timerswitch'); a.equal(before.ontopic, 'bathroom_schedule'); a.ok(!before.d);
  a.equal(after.d, true);
  a.deepEqual({...after, d: undefined}, {...before, d: undefined});
});
