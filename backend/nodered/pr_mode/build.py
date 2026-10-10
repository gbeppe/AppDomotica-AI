"""Generate PR-MODE.1 from a Node-RED export (list of nodes or {"flows": [...]}).

Usage: python3 build.py <flows-export.json>
Writes update.json / rollback.json / manifest.json next to this file.
"""
import copy, hashlib, json, pathlib, sys

ROUTER_ID = '5a7c1d360710ca21'
ADAPTER_ID = 'c67ee62775ea71f8'
BOOTSTRAP_INJECT_ID = '8a5df72f1a69144b'
root = pathlib.Path(__file__).resolve().parent

ADAPTER_OLD_TAIL = '''msg.topic =
    "casa/clima/stat/predictive_reserve_control_enabled";

msg.payload =
    msg.payload.control_enabled;

msg.qos = 1;
msg.retain = true;

return msg;'''

ADAPTER_NEW_TAIL = '''const state = msg.payload;

msg.topic =
    "casa/clima/stat/predictive_reserve_control_enabled";

msg.payload =
    state.control_enabled;

msg.qos = 1;
msg.retain = true;

// PR-MODE.1: expose (read-only) the operating mode and model readiness that
// PR Operational State already computes. No command, no actuator path.
const out = [msg];

if (state.operating_mode === "SHADOW" || state.operating_mode === "ACTIVE") {
    out.push({
        topic: "casa/clima/stat/predictive_reserve_operating_mode",
        payload: state.operating_mode,
        qos: 1,
        retain: true
    });
}

if (typeof state.ready === "boolean") {
    out.push({
        topic: "casa/clima/stat/predictive_reserve_ready",
        payload: state.ready,
        qos: 1,
        retain: true
    });
}

return [out];'''

ROUTER_ANCHOR = '''        stat_trans_func: (p) =>
        (
            p === "1" ||
            p === "true" ||
            p === "ON" ||
            p === true
        )
    },
'''

ROUTER_ENTRIES = '''
    // PR-MODE.1: read-only Predictive Reserve state (no int_cmd).
    {
        id: "predictive_reserve.operating_mode",
        int_stat: "zara/interface/predictive_reserve/operating_mode/stat",
        leg_stat: "casa/clima/stat/predictive_reserve_operating_mode",
        broker: "home",
        stat_trans_func: (p) => (p === "SHADOW" || p === "ACTIVE") ? p : null
    },

    {
        id: "predictive_reserve.ready",
        int_stat: "zara/interface/predictive_reserve/ready/stat",
        leg_stat: "casa/clima/stat/predictive_reserve_ready",
        broker: "home",
        stat_trans_func: (p) =>
            (p === true || p === "true" || p === "1") ? true :
            (p === false || p === "false" || p === "0") ? false : null
    },
'''


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    router, adapter = nodes[ROUTER_ID], nodes[ADAPTER_ID]
    assert router['name'] == 'Digital Twin Router' and adapter['name'] == 'PR Control State Adapter'
    assert 'lastPublishedStates' in router['func'], 'ROUTER-DEDUP.1 must be the base'
    assert router['func'].count(ROUTER_ANCHOR) == 1, 'control_enabled entry not found exactly once'
    assert router['func'].count('router_initialized_v117') == 2, 'unexpected init guard'
    assert 'predictive_reserve.operating_mode' not in router['func'], 'patch already applied'
    assert adapter['func'].count(ADAPTER_OLD_TAIL) == 1, 'adapter body drift'

    new_router = copy.deepcopy(router)
    new_router['func'] = (router['func']
                          .replace(ROUTER_ANCHOR, ROUTER_ANCHOR + ROUTER_ENTRIES)
                          .replace('router_initialized_v117', 'router_initialized_v118'))
    new_adapter = copy.deepcopy(adapter)
    new_adapter['func'] = adapter['func'].replace(ADAPTER_OLD_TAIL, ADAPTER_NEW_TAIL)

    for name, obj in (('update.json', [new_router, new_adapter]), ('rollback.json', [router, adapter])):
        (root / name).write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')
    digest = lambda n: hashlib.sha256(n['func'].encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'PR-MODE.1', 'modified_ids': [ROUTER_ID, ADAPTER_ID], 'added_ids': [],
        'bootstrap_inject_id': BOOTSTRAP_INJECT_ID,
        'func_sha256_before': {n['id']: digest(n) for n in (router, adapter)},
        'func_sha256_after': {n['id']: digest(n) for n in (new_router, new_adapter)},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
