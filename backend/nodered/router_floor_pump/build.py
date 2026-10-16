"""Generate ROUTER-FLOOR-PUMP.1 from a Node-RED export (base: ROUTER-FIREPLACE-RETAIN.1, v122).

The floor-pump enable from the app (zara/domotics/pompapavimento/abilitata) was routed to the
.20 broker, while the pump logic on .15 subscribes to its own broker: the app switch did
nothing and showed only the echo of its command. The entry now uses the emon broker and is
retained there (like the fireplace settings), so .15 receives it, keeps it after a restart, and
the app state is the value on .15. The running state (floorpumpstate) already comes from .15.
Init guard v122 -> v123 (routing tables are cached in node context). Deploying sends nothing.

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

ROUTER_ID = '5a7c1d360710ca21'
root = pathlib.Path(__file__).resolve().parent
OLD = ('leg_cmd: "zara/domotics/pompapavimento/abilitata", leg_stat: "zara/domotics/pompapavimento/abilitata", '
       'broker: "home",')
NEW = ('leg_cmd: "zara/domotics/pompapavimento/abilitata", leg_stat: "zara/domotics/pompapavimento/abilitata", '
       'broker: "emon", leg_retain: true, /* ROUTER-FLOOR-PUMP.1: the pump logic is on .15 */')


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    router = nodes[ROUTER_ID]
    func = router['func']
    assert 'ROUTER-FIREPLACE-RETAIN.1' in func, 'ROUTER-FIREPLACE-RETAIN.1 must be the base'
    assert 'ROUTER-FLOOR-PUMP.1' not in func, 'already patched'
    assert func.count('router_initialized_v122') == 2, 'unexpected init guard'
    assert func.count(OLD) == 1, OLD
    patched = copy.deepcopy(router)
    patched['func'] = func.replace(OLD, NEW).replace('router_initialized_v122', 'router_initialized_v123')
    for name, obj in (('update.json', [patched]), ('rollback.json', [router])):
        (root / name).write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')
    digest = lambda n: hashlib.sha256(json.dumps(n, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'ROUTER-FLOOR-PUMP.1', 'modified_ids': [ROUTER_ID], 'added_ids': [], 'removed_ids': [],
        'node_sha256_before': {ROUTER_ID: digest(router)}, 'node_sha256_after': {ROUTER_ID: digest(patched)},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
