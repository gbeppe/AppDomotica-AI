// Offline tests for IRRIGATION.1: node --test test.js
const {test} = require('node:test'), a = require('node:assert/strict'), fs = require('node:fs');
const nodes = JSON.parse(fs.readFileSync(__dirname + '/update.json'));
const byName = name => nodes.find(n => n.name === name);
const PW = '0123456789abcdef0123456789abcdef';

function fn(name, flow, logs = []) {
  const body = new Function('msg', 'flow', 'node', byName(name).func);
  const node = {warn: m => logs.push(m), status: () => {}, error: m => logs.push(m)};
  return msg => body(msg, flow, node);
}
function flowStore(init = {}) { const s = {...init}; return {get: k => s[k], set: (k, v) => { s[k] = v; }, s}; }

// /ja shaped like the real controller (2026-10-11): 8 stations, S06-S08 disabled, 4 programs.
function ja({running = [], remaining = 0, en = 1} = {}) {
  const sn = [0, 0, 0, 0, 0, 0, 0, 0], ps = sn.map(() => [0, 0, 0, 0]);
  for (const i of running) { sn[i] = 1; ps[i] = [99, remaining, 0, 0]; }
  return {statusCode: 200, kind: 'poll', payload: {
    settings: {en, rd: 0, rdst: 0, ps, mqtt: {pass: 'secret'}},
    status: {sn, nstations: 8},
    stations: {snames: ['retro piazzola', 'retro cucina', 'ingresso secondario', 'fronte ingresso',
                        'fronte garage', 'S06', 'S07', 'S08'], stn_dis: [224]},
    programs: {nprogs: 4, pd: [[1, 0, 0, [], [], 'Vacanza'], [0, 0, 0, [], [], 'Semina'],
                              [0, 0, 0, [], [], 'alba fronte'], [0, 0, 0, [], [], 'alba retro']]},
  }};
}

test('password file: only a 32-hex MD5 is kept', () => {
  const f = flowStore(), logs = [];
  fn('memorizza password', f, logs)({payload: 'not a hash'});
  a.equal(f.get('os_pw'), undefined); a.equal(logs.length, 1);
  fn('memorizza password', f)({payload: PW + '\n'});
  a.equal(f.get('os_pw'), PW);
});

test('state: enabled zones and programs, no secrets, published retained', () => {
  const f = flowStore();
  const [app, , , emon] = fn('stato, esito, EmonCMS', f)(ja({running: [1], remaining: 300}));
  a.equal(app.topic, 'zara/interface/irrigation/state/stat'); a.equal(app.retain, true);
  a.deepEqual(app.payload.zones.map(z => z.name), ['retro piazzola', 'retro cucina', 'ingresso secondario',
                                                   'fronte ingresso', 'fronte garage']);
  a.deepEqual(app.payload.zones[1], {id: 2, name: 'retro cucina', running: true, remaining_s: 300});
  a.deepEqual(app.payload.programs[0], {id: 1, name: 'Vacanza', enabled: true});
  a.equal(app.payload.controller_enabled, true);
  a.ok(!JSON.stringify(app.payload).includes('secret'));
  a.deepEqual(emon.map(m => [m.topic, m.payload]).slice(0, 2), [['emon/irrigazione/zona_1', 0], ['emon/irrigazione/zona_2', 1]]);
});

test('unchanged state is not republished before the 60 s heartbeat', () => {
  const f = flowStore(), r = fn('stato, esito, EmonCMS', f);
  a.ok(r(ja())[0]);
  a.equal(r(ja())[0], null);
  a.ok(r(ja({running: [0]}))[0]);   // a change is published at once
});

test('EmonCMS gets the measured duration when a zone stops', () => {
  const f = flowStore(), r = fn('stato, esito, EmonCMS', f);
  const realNow = Date.now;
  try {
    Date.now = () => 1_000_000; r(ja({running: [3]}));
    Date.now = () => 1_000_000 + 480_000;
    const emon = r(ja())[3];
    a.deepEqual(emon.find(m => m.topic === 'emon/irrigazione/zona_4_durata_s').payload, 480);
  } finally { Date.now = realNow; }
});

test('commands: zone start/stop, program, stop all, controller; password appended, never echoed', () => {
  const f = flowStore({os_pw: PW});
  fn('stato, esito, EmonCMS', f)(ja());
  const c = fn('valida comando', f);
  const url = (topic, payload) => c({topic: 'zara/interface/irrigation/' + topic + '/cmd', payload})[0].url;
  a.equal(url('zone/3/start', '15'), `http://192.168.1.3:9003/cm?sid=2&en=1&t=900&pw=${PW}`);
  a.equal(url('zone/3/stop', 'true'), `http://192.168.1.3:9003/cm?sid=2&en=0&pw=${PW}`);
  a.equal(url('program/1/start', 'true'), `http://192.168.1.3:9003/mp?pid=0&uwt=0&pw=${PW}`);
  a.equal(url('all/stop', 'true'), `http://192.168.1.3:9003/cv?rsn=1&pw=${PW}`);
  a.equal(url('controller/enabled', 'false'), `http://192.168.1.3:9003/cv?en=0&pw=${PW}`);
});

test('invalid commands are rejected with a reason and never reach OpenSprinkler', () => {
  const f = flowStore({os_pw: PW});
  fn('stato, esito, EmonCMS', f)(ja());
  const c = fn('valida comando', f, []);
  for (const [topic, payload, reason] of [['zone/6/start', '5', 'zona sconosciuta'], ['zone/1/start', '61', 'minuti non validi'],
       ['zone/1/start', '0', 'minuti non validi'], ['zone/1/start', '2.5', 'minuti non validi'],
       ['program/9/start', 'true', 'programma sconosciuto'], ['all/stop', 'false', 'valore non valido'],
       ['controller/enabled', 'maybe', 'valore non valido'], ['zone/1/explode', '1', 'azione sconosciuta']]) {
    const [http, ack] = c({topic: 'zara/interface/irrigation/' + topic + '/cmd', payload});
    a.equal(http, null, topic); a.equal(ack.payload.ok, false); a.equal(ack.payload.reason, reason);
    a.ok(!JSON.stringify(ack).includes(PW));
  }
  a.equal(c({topic: 'zara/interface/irrigation/state/stat', payload: '{}'}), null);   // own state ignored
});

test('no password or no state yet: commands refused, polling idle', () => {
  a.equal(fn('valida comando', flowStore())({topic: 'zara/interface/irrigation/all/stop/cmd', payload: 'true'})[1].payload.reason,
          'password OpenSprinkler non caricata');
  a.equal(fn('valida comando', flowStore({os_pw: PW}))({topic: 'zara/interface/irrigation/all/stop/cmd', payload: 'true'})[1].payload.reason,
          'stato OpenSprinkler non ancora letto');
  a.equal(fn('lettura stato', flowStore())({}), null);
  a.equal(fn('lettura stato', flowStore({os_pw: PW}))({}).url, `http://192.168.1.3:9003/ja?pw=${PW}`);
});

test('command result: OpenSprinkler result 1 is ok, anything else is reported, then poll now', () => {
  const r = fn('stato, esito, EmonCMS', flowStore(), []);
  const ok = r({kind: 'command', command: 'all/stop', statusCode: 200, payload: {result: 1}});
  a.equal(ok[1].payload.ok, true); a.deepEqual(ok[2], {kind: 'poll-now'});
  const ko = r({kind: 'command', command: 'zone/1/start', statusCode: 200, payload: {result: 2}});
  a.equal(ko[1].payload.ok, false); a.equal(ko[1].payload.result, 2);
});

test('wiring: commands and polls share one HTTP node; nothing touches the router', () => {
  const http = byName('OpenSprinkler API');
  a.deepEqual(byName('valida comando').wires[0], [http.id]);
  a.deepEqual(byName('lettura stato').wires[0], [http.id]);
  a.deepEqual(byName('stato, esito, EmonCMS').wires[2], [byName('lettura stato').id]);
  a.ok(nodes.every(n => n.type === 'tab' || n.z === nodes[0].id));
});
