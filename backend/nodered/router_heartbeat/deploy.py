"""Run on .20. Modes: prepare | apply BACKUP_DIR | rollback BACKUP_DIR.

Same procedure as semantic_bridge/actor_policy: Node-RED Admin API v2 over the
verified Tailscale TLS endpoint, token from ~/.node-red/.cli-config.json (never
printed), full backup before deploy, runtime drift checks, 'nodes' deployment of
the single Router node only.
"""
import copy, datetime, hashlib, json, os, pathlib, shutil, sys, urllib.request

os.umask(0o077)
root = pathlib.Path(__file__).resolve().parent
cfg = json.loads((pathlib.Path.home() / '.node-red/.cli-config.json').read_text())
url = 'https://domopi.tailf30ba8.ts.net:1880'
assert cfg['target'].rstrip('/') == url
headers = {'Authorization': 'Bearer ' + cfg['tokens']['access_token'], 'Node-RED-API-Version': 'v2'}
manifest = json.loads((root / 'manifest.json').read_text())
before_node = json.loads((root / 'rollback.json').read_text())[0]
after_node = json.loads((root / 'update.json').read_text())[0]
ROUTER_ID = manifest['modified_ids'][0]
assert before_node['id'] == after_node['id'] == ROUTER_ID and manifest['added_ids'] == []


def api(body=None):
    h = dict(headers)
    if body is not None:
        h.update({'Content-Type': 'application/json', 'Node-RED-Deployment-Type': 'nodes'})
    req = urllib.request.Request(url + '/flows', data=None if body is None else json.dumps(body).encode(), headers=h)
    with urllib.request.urlopen(req, timeout=60) as response:
        return json.load(response)


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True).encode()).hexdigest()


def router_of(runtime):
    return next(n for n in runtime['flows'] if n['id'] == ROUTER_ID)


def with_router_func(runtime, func):
    planned = copy.deepcopy(runtime)
    router_of(planned)['func'] = func
    return planned


def deploy(backup, expected_router, target_func, label):
    current = api()
    for field in ('type', 'z', 'func', 'outputs', 'wires'):
        assert router_of(current).get(field) == expected_router.get(field), 'Runtime drift on Router ' + field + '; stop'
    planned = with_router_func(current, target_func)
    (backup / f'runtime-before-{label}.json').write_text(json.dumps(current, indent=2))
    result = api(planned)
    actual = api()
    assert digest(actual['flows']) == digest(planned['flows']), 'Post-deploy mismatch; inspect runtime'
    (backup / f'runtime-after-{label}.json').write_text(json.dumps(actual, indent=2))
    print(json.dumps({'result': 'verified', 'mode': label, 'revision': result['rev'], 'backup': str(backup),
                      'modified_nodes': 1, 'added_nodes': 0}))


mode = sys.argv[1] if len(sys.argv) > 1 else ''
if mode == 'prepare':
    live = api()
    router = router_of(live)
    for field in ('type', 'z', 'func', 'outputs', 'wires'):
        assert router.get(field) == before_node.get(field), 'Runtime drift on Router ' + field + '; rebuild the patch'
    backup = pathlib.Path.home() / 'AI_backups/nodered' / datetime.datetime.now(datetime.timezone.utc).strftime(
        '%Y%m%dT%H%M%SZ-before-router-heartbeat')
    backup.mkdir(parents=True, mode=0o700)
    (backup / 'runtime-before.json').write_text(json.dumps(live, indent=2))
    (backup / 'runtime-planned.json').write_text(json.dumps(with_router_func(live, after_node['func']), indent=2))
    project = pathlib.Path.home() / '.node-red/projects/domopi'
    for name in ('flows_domopi.json', 'flows_domopi_cred.json'):
        src = project / name
        if src.exists():
            shutil.copy2(src, backup / name)
    (backup / 'plan.json').write_text(json.dumps({'patch': manifest['patch'], 'modified_ids': [ROUTER_ID],
                                                  'before_hash': digest(live)}, indent=2))
    print(backup)
    print('Runtime verified; 1 change planned (Digital Twin Router, ' + manifest['patch'] + '); full backup saved.')
elif mode == 'apply':
    backup = pathlib.Path(sys.argv[2])
    assert api() == json.loads((backup / 'runtime-before.json').read_text()), 'Runtime changed after preparation; stop'
    deploy(backup, before_node, after_node['func'], 'apply')
elif mode == 'rollback':
    deploy(pathlib.Path(sys.argv[2]), after_node, before_node['func'], 'rollback')
else:
    raise SystemExit('Use: prepare | apply BACKUP_DIR | rollback BACKUP_DIR')
