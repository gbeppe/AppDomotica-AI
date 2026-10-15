"""Generate LIGHTS-ECHO-GUARD.1 from a Node-RED export ("function 17", Digital Twin Bridge).

"function 17" listens to zara/domotics/lights/+ and routes on/off to the Broadlink IR chains.
Each chain (first code, 5 s delay, second code) publishes the final state on that same topic,
so the function took its own echo for a new command. Its anti-loop only dropped a payload equal
to the previous one: with an "on" and an "off" in flight within ~6 s (a double tap, 15/10/2026
21:27:58) the two echoes kept re-triggering each other (85 IR sends in 2 minutes, stopped by
switching the emitter off).

Fix: every routed command is remembered for 10 s as an expected echo; a message without
_semantic_command (it came over MQTT, not from the router) matching one is consumed as that
echo and not routed again. Router commands from the app always pass.

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

FN_ID = 'ea8307d48995ca93'
root = pathlib.Path(__file__).resolve().parent
GUARD_OLD = '''// Validazione payload
if (payload !== "on" && payload !== "off") {
    return null;
}
'''
GUARD_NEW = GUARD_OLD + '''
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
ROUTE_OLD = '''    outputs[targetIndex] = {
        topic: topic,
        payload: payload
    };
'''
ROUTE_NEW = ROUTE_OLD + '''    expected.push({payload: payload, at: now});
    pendingEchoes[topic] = expected;
    context.set("pendingEchoes", pendingEchoes);
'''


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    fn = nodes[FN_ID]
    func = fn['func']
    assert fn['name'] == 'function 17' and 'LIGHTS-ECHO-GUARD' not in func, 'already patched'
    for old, new in ((GUARD_OLD, GUARD_NEW), (ROUTE_OLD, ROUTE_NEW)):
        assert func.count(old) == 1, old
        func = func.replace(old, new)
    patched = {**copy.deepcopy(fn), 'func': func}
    for name, obj in (('update.json', [patched]), ('rollback.json', [fn])):
        (root / name).write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')
    digest = lambda n: hashlib.sha256(json.dumps(n, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'LIGHTS-ECHO-GUARD.1', 'modified_ids': [FN_ID], 'added_ids': [], 'removed_ids': [],
        'node_sha256_before': {FN_ID: digest(fn)}, 'node_sha256_after': {FN_ID: digest(patched)},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
