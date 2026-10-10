"""Generate the Router dedup patch from a Node-RED export (list of nodes or {"flows": [...]}).

Usage: python3 build.py <flows-export.json>
Writes update.json / rollback.json / manifest.json next to this file.
"""
import copy, hashlib, json, pathlib, sys

ROUTER_ID = '5a7c1d360710ca21'
HEARTBEAT_MS = 60000
root = pathlib.Path(__file__).resolve().parent

OLD = '''function emitState(config, value) {
    const lastVal = lastSensorValues[config.topic || config.int_stat];
    const outPayload = transformValue(config, value, lastVal);

    if (outPayload === undefined || outPayload === null) {
        return;
    }

    const topic = config.topic || config.int_stat;
    lastSensorValues[topic] = outPayload;

    outMsgsApp.push({'''

NEW = '''// ROUTER-DEDUP.1: publish a Digital Twin state only when its value changes, or as a
// heartbeat at most every STATE_HEARTBEAT_MS. The retained value on the broker is
// unchanged; lastSensorValues (used by transform_func) is still updated every time.
const STATE_HEARTBEAT_MS = %d;
const lastPublishedStates = context.get("lastPublishedStates") || {};

function emitState(config, value) {
    const lastVal = lastSensorValues[config.topic || config.int_stat];
    const outPayload = transformValue(config, value, lastVal);

    if (outPayload === undefined || outPayload === null) {
        return;
    }

    const topic = config.topic || config.int_stat;
    lastSensorValues[topic] = outPayload;

    const nowMs = Date.now();
    const serialized = JSON.stringify(outPayload);
    const previous = lastPublishedStates[topic];
    if (previous && previous.payload === serialized && nowMs - previous.at < STATE_HEARTBEAT_MS) {
        return;
    }
    lastPublishedStates[topic] = { payload: serialized, at: nowMs };
    context.set("lastPublishedStates", lastPublishedStates);

    outMsgsApp.push({''' % HEARTBEAT_MS


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = data['flows'] if isinstance(data, dict) else data
    router = next(n for n in nodes if n['id'] == ROUTER_ID)
    assert router['type'] == 'function' and router['name'] == 'Digital Twin Router'
    assert router['func'].count(OLD) == 1, 'emitState block not found exactly once: base drift'
    assert 'lastPublishedStates' not in router['func'], 'patch already applied'
    patched = copy.deepcopy(router)
    patched['func'] = router['func'].replace(OLD, NEW)
    for name, obj in (('update.json', [patched]), ('rollback.json', [router])):
        (root / name).write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')
    digest = lambda n: hashlib.sha256(n['func'].encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'ROUTER-DEDUP.1', 'modified_ids': [ROUTER_ID], 'added_ids': [],
        'heartbeat_ms': HEARTBEAT_MS,
        'func_sha256_before': digest(router), 'func_sha256_after': digest(patched),
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
