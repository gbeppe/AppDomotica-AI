"""Generate ROUTER-HEARTBEAT.1 from a Node-RED export (list of nodes or {"flows": [...]}).

Usage: python3 build.py <flows-export.json>
Writes update.json / rollback.json / manifest.json next to this file.
"""
import copy, hashlib, json, pathlib, sys

ROUTER_ID = '5a7c1d360710ca21'
root = pathlib.Path(__file__).resolve().parent

OLD = '''    const outPayload = transformValue(config, value, lastVal);

    if (outPayload === undefined || outPayload === null) {
        return;
    }
'''

NEW = '''    const outPayload = transformValue(config, value, lastVal);

    if (outPayload === undefined || outPayload === null) {
        // ROUTER-HEARTBEAT.1: an energy reading inside its per-entity deadband still proves
        // the source is alive. Republish the last published value at most every
        // STATE_HEARTBEAT_MS so the app can tell "steady" from "no data" (energy only).
        const hbTopic = config.topic || config.int_stat;
        const previousState = lastPublishedStates[hbTopic];
        if (previousState && hbTopic.indexOf("zara/interface/energy/") === 0 &&
            Date.now() - previousState.at >= STATE_HEARTBEAT_MS) {
            previousState.at = Date.now();
            context.set("lastPublishedStates", lastPublishedStates);
            outMsgsApp.push({
                topic: hbTopic,
                payload: JSON.parse(previousState.payload),
                qos: 1,
                retain: true
            });
        }
        return;
    }
'''


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    router = nodes[ROUTER_ID]
    assert router['name'] == 'Digital Twin Router'
    assert 'lastPublishedStates' in router['func'] and 'router_initialized_v118' in router['func'], \
        'ROUTER-DEDUP.1 and PR-MODE.1 must be the base'
    assert router['func'].count(OLD) == 1, 'emitState null branch not found exactly once'
    assert 'ROUTER-HEARTBEAT.1' not in router['func'], 'patch already applied'
    patched = copy.deepcopy(router)
    patched['func'] = router['func'].replace(OLD, NEW)
    for name, obj in (('update.json', [patched]), ('rollback.json', [router])):
        (root / name).write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')
    digest = lambda n: hashlib.sha256(n['func'].encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'ROUTER-HEARTBEAT.1', 'modified_ids': [ROUTER_ID], 'added_ids': [],
        'func_sha256_before': digest(router), 'func_sha256_after': digest(patched),
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
