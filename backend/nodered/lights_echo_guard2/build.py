"""Generate LIGHTS-ECHO-GUARD.2 from a Node-RED export ("function 17" and the IR state publishers).

"function 17" (Digital Twin Bridge) listens to zara/domotics/lights/+ and routes on/off to the
Broadlink chains. After sending, each chain publishes the final state on that same topic.
LIGHTS-ECHO-GUARD.1 (15/10/2026) remembered as expected echo only the commands routed by
"function 17" itself. A command started elsewhere (Node-RED dashboard, Alexa, schedules) went
straight to its chain, so its state echo was taken for a new command and routed again: the
dashboard sent "3on" twice for one press (23/10/2026, 09:39:16 and 09:39:18).

Fix: every state publisher announces the echo it is about to publish in a global registry
(topic, payload, time); "function 17" consumes a matching announcement, whatever started the
command, before its anti-loop, and records the state in the anti-loop memory. Router commands
(with _semantic_command) are never taken for echoes. The .1 bookkeeping is removed.

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, re, sys

FN_ID = 'ea8307d48995ca93'
DEVICES = ('shelflamp', 'livinglamp', 'tvlamp', 'readinglight', 'prolunga',
           'poolpump', 'poolskimmer', 'poolfloor', 'poollight')
root = pathlib.Path(__file__).resolve().parent

RBE_OLD = '''// 1. Gestione Anti-Loop (RBE in memoria per singolo topic)
let lastStates = context.get("lastStates") || {};
'''
RBE_NEW = '''// LIGHTS-ECHO-GUARD.2: the state publishers after the Broadlink chains announce, in a global
// registry, the echo they are about to publish on this topic. A message without
// _semantic_command (MQTT, not the router) matching an announcement of the last 10 s is that
// echo, whoever started the command (app, dashboard, Alexa, schedules): consume it and only
// record the state (dashboard press sent "3on" twice, 23/10/2026).
const ECHO_MS = 10000;
const now = Date.now();
let lastStates = context.get("lastStates") || {};
if (!msg._semantic_command) {
    const registry = global.get("lightsPendingEchoes") || {};
    const pending = (registry[topic] || []).filter(e => now - e.at <= ECHO_MS);
    const echo = pending.findIndex(e => e.payload === payload);
    if (echo >= 0) {
        pending.splice(echo, 1);
        registry[topic] = pending;
        global.set("lightsPendingEchoes", registry);
        lastStates[topic] = payload;
        context.set("lastStates", lastStates);
        return null;
    }
}

// 1. Gestione Anti-Loop (RBE in memoria per singolo topic)
'''
GUARD1 = '''
// LIGHTS-ECHO-GUARD.1: the IR chains publish their final state on this same topic. A message
// without _semantic_command (MQTT, not the router) matching a command routed in the last 10 s
// is that echo: consume it instead of routing it again (on/off ping-pong of 15/10/2026).
const ECHO_MS = 10000;
const now = Date.now();
let pendingEchoes = context.get("pendingEchoes") || {};
let expected = (pendingEchoes[topic] || []).filter(e => now - e.at <= ECHO_MS);
if (!msg._semantic_command) {
    const echo = expected.findIndex(e => e.payload === payload);
    if (echo >= 0) {
        expected.splice(echo, 1);
        pendingEchoes[topic] = expected;
        context.set("pendingEchoes", pendingEchoes);
        return null;
    }
}
'''
PUSH1 = '''    expected.push({payload: payload, at: now});
    pendingEchoes[topic] = expected;
    context.set("pendingEchoes", pendingEchoes);
'''


def announce(topic):
    return ('// LIGHTS-ECHO-GUARD.2: announce the state echo published below, so "function 17" does not\n'
            '// take it for a new command.\n'
            '{\n'
            '    const registry = global.get("lightsPendingEchoes") || {}, at = Date.now();\n'
            f'    const pending = (registry["{topic}"] || []).filter(e => at - e.at <= 10000);\n'
            '    pending.push({payload: msg.payload, at: at});\n'
            f'    registry["{topic}"] = pending;\n'
            '    global.set("lightsPendingEchoes", registry);\n'
            '}\n')


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    flows = data['flows'] if isinstance(data, dict) else data
    nodes = {n['id']: n for n in flows}
    fn = nodes[FN_ID]
    func = fn['func']
    assert fn['name'] == 'function 17' and 'LIGHTS-ECHO-GUARD.1' in func and 'GUARD.2' not in func
    for old, new in ((RBE_OLD, RBE_NEW), (GUARD1, ''), (PUSH1, '')):
        assert func.count(old) == 1, old[:60]
        func = func.replace(old, new)
    patched = {FN_ID: {**copy.deepcopy(fn), 'func': func}}
    originals = {FN_ID: fn}
    for device in DEVICES:
        topic = f'zara/domotics/lights/{device}'
        outs = [n for n in flows if n.get('type') == 'mqtt out' and n.get('topic') == topic]
        assert len(outs) == 1, topic
        publishers = [n for n in flows if n.get('type') == 'function' and outs[0]['id'] in n['wires'][0]]
        assert len(publishers) == 2, topic
        for p in publishers:
            assert 'GUARD.2' not in p['func']
            last = [m.start() for m in re.finditer(r'^return [^\n]*$', p['func'], re.M)][-1]
            patched[p['id']] = {**copy.deepcopy(p), 'func': p['func'][:last] + announce(topic) + p['func'][last:]}
            originals[p['id']] = p
    ids = sorted(patched)
    for name, store in (('update.json', patched), ('rollback.json', originals)):
        (root / name).write_text(json.dumps([store[i] for i in ids], ensure_ascii=False, indent=2) + '\n')
    digest = lambda n: hashlib.sha256(json.dumps(n, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'LIGHTS-ECHO-GUARD.2', 'modified_ids': ids, 'added_ids': [], 'removed_ids': [],
        'node_sha256_before': {i: digest(originals[i]) for i in ids},
        'node_sha256_after': {i: digest(patched[i]) for i in ids},
    }, indent=2) + '\n')
    print(f'{len(ids)} nodes patched')


if __name__ == '__main__':
    main(sys.argv[1])
