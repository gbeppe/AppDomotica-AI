"""Generate ROUTER-CLEANUP.2 from a Node-RED export: bump the router init guard.

ROUTER-CLEANUP.1 changed registry rules, but the router keeps its routing tables in
node context and rebuilds them only when the init guard key changes (v118). Bumping
to v119 rebuilds the tables with the new rules on the next message.

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

ROUTER_ID = '5a7c1d360710ca21'
root = pathlib.Path(__file__).resolve().parent


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    router = nodes[ROUTER_ID]
    assert 'ROUTER-CLEANUP.1' in router['func'], 'ROUTER-CLEANUP.1 must be the base'
    assert router['func'].count('router_initialized_v118') == 2, 'unexpected init guard'
    patched = copy.deepcopy(router)
    patched['func'] = router['func'].replace('router_initialized_v118', 'router_initialized_v119')
    for name, obj in (('update.json', [patched]), ('rollback.json', [router])):
        (root / name).write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')
    digest = lambda n: hashlib.sha256(json.dumps(n, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'ROUTER-CLEANUP.2', 'modified_ids': [ROUTER_ID], 'added_ids': [], 'removed_ids': [],
        'node_sha256_before': {ROUTER_ID: digest(router)}, 'node_sha256_after': {ROUTER_ID: digest(patched)},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
