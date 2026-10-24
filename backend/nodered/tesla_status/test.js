// TESLA-STATUS.1 tests, plain Node (the .15 has Node 12): node test.js
const assert = require('assert');
const fs = require('fs');
const path = require('path');

function run(file, msg, flowStore = {}, ctxStore = {}) {
    const statuses = [], warnings = [];
    const node = {status: s => statuses.push(s), warn: w => warnings.push(w)};
    const store = o => ({get: k => o[k], set: (k, v) => { o[k] = v; }});
    const fn = new Function('msg', 'node', 'flow', 'context', fs.readFileSync(path.join(__dirname, file), 'utf8'));
    return {out: fn(msg, node, store(flowStore), store(ctxStore)), statuses, warnings, flowStore, ctxStore};
}

const ok = body => ({code: 200, body});
const live = {   // shapes read from the gateway on 24/10/2026 (fw 26.34.0), serials left out
    operation: ok({real_mode: 'self_consumption', backup_reserve_percent: 5}),
    grid: ok({grid_status: 'SystemGridConnected', grid_services_active: false}),
    sitemaster: ok({status: 'StatusUp', running: true, connected_to_tesla: false, power_supply_mode: false, can_reboot: 'Yes'}),
    system: ok({command_source: 'Configuration', battery_target_power: 261.75, nominal_full_pack_energy: 14107,
        nominal_energy_remaining: 1690, max_charge_power: 5000, instantaneous_max_charge_power: 7000, available_blocks: 1,
        battery_blocks: [{disabled_reasons: [], charge_power_clamped: false, backup_ready: true,
            energy_charged: 9357210, energy_discharged: 8220222}]}),
    faults: ok([{timestamp: 1791721328830}, {timestamp: 1791721211310}]),
    update: ok({state: '/update_succeeded', version: '26.34.0 7d707328'}),
};

// 1. request: no cookie -> nothing; with cookie -> six GETs with the cookie
let r = run('request.js', {});
assert.strictEqual(r.out, null);
r = run('request.js', {}, {TeslaCookie: ['AuthCookie=x']});
assert.strictEqual(r.out[0].length, 6);
assert.deepStrictEqual(r.out[0].map(m => m.topic), ['operation', 'grid', 'sitemaster', 'system', 'faults', 'update']);
assert.ok(r.out[0].every(m => m.url.startsWith('https://192.168.1.46/api/') && m.headers.Cookie[0] === 'AuthCookie=x'));

// 2. wrap: keeps the code; 401 drops the cookie so the main flow logs in again
r = run('wrap.js', {statusCode: 200, payload: {a: 1}}, {TeslaCookie: ['c']});
assert.deepStrictEqual(r.out.payload, {code: 200, body: {a: 1}});
assert.deepStrictEqual(r.flowStore.TeslaCookie, ['c']);
r = run('wrap.js', {statusCode: 401, payload: 'no'}, {TeslaCookie: ['c']});
assert.strictEqual(r.flowStore.TeslaCookie, null);

// 3. convert: live values
r = run('convert.js', {payload: live});
assert.deepStrictEqual(r.out.payload, {
    pw_mode: 1, pw_reserve_pct: 5, pw_grid_connected: 1, pw_grid_services: 0, pw_cloud_connected: 0, pw_running: 1,
    pw_target_power: 261.75, pw_command_source: 1, pw_max_charge_w: 5000, pw_inst_max_charge_w: 7000,
    pw_full_pack_wh: 14107, pw_remaining_wh: 1690, pw_blocks: 1, pw_charge_clamped: 0, pw_backup_ready: 1,
    pw_disabled_reasons: 0, pw_energy_charged_kwh: 9357.21, pw_energy_discharged_kwh: 8220.222,
    pw_grid_faults: 2, pw_last_fault_ts: 1791721329, pw_update_state: 0});
assert.strictEqual(r.warnings.length, 0);
assert.strictEqual(r.statuses[0].fill, 'green');

// 4. convert: new texts become codes 0/9/1 and are reported once
const odd = JSON.parse(JSON.stringify(live));
odd.operation.body.real_mode = 'autonomous_new';
odd.system.body.command_source = 'GridServices';
odd.update.body.state = '/download';
const ctx = {};
r = run('convert.js', {payload: odd}, {}, ctx);
assert.strictEqual(r.out.payload.pw_mode, 0);
assert.strictEqual(r.out.payload.pw_command_source, 9);
assert.strictEqual(r.out.payload.pw_update_state, 1);
assert.strictEqual(r.warnings.length, 3);
r = run('convert.js', {payload: odd}, {}, ctx);
assert.strictEqual(r.warnings.length, 0);

// 5. convert: partial answers (timeout of the join, 401, text body) -> only what is valid
r = run('convert.js', {payload: {operation: live.operation, system: {code: 401, body: 'Unauthorized'}, faults: ok('x')}});
assert.deepStrictEqual(r.out.payload, {pw_mode: 1, pw_reserve_pct: 5});
assert.strictEqual(r.statuses[0].fill, 'yellow');
r = run('convert.js', {payload: {}});
assert.strictEqual(r.out, null);

console.log('TESLA-STATUS.1: 5 test groups passed');
