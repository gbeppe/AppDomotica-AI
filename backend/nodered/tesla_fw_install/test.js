// TESLA-FW-INSTALL.1 tests, plain Node (the .15 has Node 12): node test.js
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const code = fs.readFileSync(path.join(__dirname, 'gw.js'), 'utf8');

function run(payload, store, now) {
    const realNow = Date.now;
    Date.now = () => now * 1000;
    const flow = {get: (k, s) => (assert.strictEqual(s, 'file'), store[k]), set: (k, v, s) => (assert.strictEqual(s, 'file'), store[k] = v)};
    try { return new Function('msg', 'flow', code)({payload}, flow).payload; } finally { Date.now = realNow; }
}
const status = (version, up) => ({version, up_time_seconds: up, is_new: false, cellular_disabled: false, can_reboot: true, commission_count: 0});
const store = {};
const t0 = 1793088000;                                  // 26/10/2026

// first run: version 26.34.0, up 464h13m -> installed = last boot, not exact
let p = run(status('26.34.0 7d707328', '464h13m20s'), store, t0);
assert.strictEqual(p.gw_fw_version, 263400);
assert.strictEqual(p.gw_fw_installed, t0 - (464 * 3600 + 13 * 60 + 20));
assert.strictEqual(p.gw_fw_installed_exact, 0);
const firstInstalled = p.gw_fw_installed;

// same version, later, and after a plain gateway reboot: the date does not move
p = run(status('26.34.0 7d707328', '10m'), store, t0 + 86400);
assert.strictEqual(p.gw_fw_installed, firstInstalled);
assert.strictEqual(p.gw_start_time, t0 + 86400 - 600);

// new version seen one hour after its reboot -> exact date = that reboot
p = run(status('26.42.1 abcdef01', '1h0m0s'), store, t0 + 10 * 86400);
assert.strictEqual(p.gw_fw_version, 264201);
assert.strictEqual(p.gw_fw_installed, t0 + 10 * 86400 - 3600);
assert.strictEqual(p.gw_fw_installed_exact, 1);

// unreadable answer: nothing invented, the stored date is still sent
p = run({version: 'x', up_time_seconds: ''}, store, t0 + 11 * 86400);
assert.ok(!('gw_fw_version' in p));
assert.strictEqual(p.gw_fw_installed, t0 + 10 * 86400 - 3600);

// empty memory and unreadable answer: no installed date at all
p = run({version: 'x'}, {}, t0);
assert.ok(!('gw_fw_installed' in p) && !('gw_fw_installed_exact' in p));
console.log('TESLA-FW-INSTALL.1: 5 cases passed');
