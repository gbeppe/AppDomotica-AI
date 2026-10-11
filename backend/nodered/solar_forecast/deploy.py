"""Run on .20. Modes: prepare | apply BACKUP_DIR | rollback BACKUP_DIR.

Same procedure as router_heartbeat: Node-RED Admin API v2 over the verified
Tailscale TLS endpoint, token from ~/.node-red/.cli-config.json (never printed),
full backup before deploy, runtime drift checks on every modified node, 'nodes'
deployment of the two forecast nodes only.
"""
import copy, datetime, hashlib, json, os, pathlib, shutil, sys, urllib.request

os.umask(0o077)
root = pathlib.Path(__file__).resolve().parent
cfg = json.loads((pathlib.Path.home() / '.node-red/.cli-config.json').read_text())
url = 'https://domopi.tailf30ba8.ts.net:1880'
assert cfg['target'].rstrip('/') == url
headers = {'Authorization': 'Bearer ' + cfg['tokens']['access_token'], 'Node-RED-API-Version': 'v2'}
manifest = json.loads((root / 'manifest.json').read_text())
before = {n['id']: n for n in json.loads((root / 'rollback.json').read_text())}
after = {n['id']: n for n in json.loads((root / 'update.json').read_text())}
IDS = manifest['modified_ids']
assert set(before) == set(after) == set(IDS) and manifest['added_ids'] == []


def api(body=None):
    h = dict(headers)
    if body is not None:
        h.update({'Content-Type': 'application/json', 'Node-RED-Deployment-Type': 'nodes'})
    req = urllib.request.Request(url + '/flows', data=None if body is None else json.dumps(body).encode(), headers=h)
    with urllib.request.urlopen(req, timeout=60) as response:
        return json.load(response)


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True).encode()).hexdigest()


def check(runtime, expected, what):
    live = {n['id']: n for n in runtime['flows'] if n['id'] in IDS}
    for node_id in IDS:
        assert live.get(node_id) == expected[node_id], f'Runtime drift on {node_id}; {what}'


def replaced(runtime, target):
    planned = copy.deepcopy(runtime)
    planned['flows'] = [copy.deepcopy(target[n['id']]) if n['id'] in target else n for n in planned['flows']]
    return planned


def deploy(backup, expected, target, label):
    current = api()
    check(current, expected, 'stop')
    planned = replaced(current, target)
    (backup / f'runtime-before-{label}.json').write_text(json.dumps(current, indent=2))
    result = api(planned)
    actual = api()
    assert digest(actual['flows']) == digest(planned['flows']), 'Post-deploy mismatch; inspect runtime'
    (backup / f'runtime-after-{label}.json').write_text(json.dumps(actual, indent=2))
    print(json.dumps({'result': 'verified', 'mode': label, 'revision': result['rev'], 'backup': str(backup),
                      'modified_nodes': len(IDS), 'added_nodes': 0}))


mode = sys.argv[1] if len(sys.argv) > 1 else ''
if mode == 'prepare':
    live = api()
    check(live, before, 'rebuild the patch')
    backup = pathlib.Path.home() / 'AI_backups/nodered' / datetime.datetime.now(datetime.timezone.utc).strftime(
        '%Y%m%dT%H%M%SZ-before-solar-forecast')
    backup.mkdir(parents=True, mode=0o700)
    (backup / 'runtime-before.json').write_text(json.dumps(live, indent=2))
    (backup / 'runtime-planned.json').write_text(json.dumps(replaced(live, after), indent=2))
    project = pathlib.Path.home() / '.node-red/projects/domopi'
    for name in ('flows_domopi.json', 'flows_domopi_cred.json'):
        src = project / name
        if src.exists():
            shutil.copy2(src, backup / name)
    (backup / 'plan.json').write_text(json.dumps({'patch': manifest['patch'], 'modified_ids': IDS,
                                                  'before_hash': digest(live)}, indent=2))
    print(backup)
    print(f'Runtime verified; {len(IDS)} changes planned ({manifest["patch"]}); full backup saved.')
elif mode == 'apply':
    backup = pathlib.Path(sys.argv[2])
    assert api() == json.loads((backup / 'runtime-before.json').read_text()), 'Runtime changed after preparation; stop'
    deploy(backup, before, after, 'apply')
elif mode == 'rollback':
    deploy(pathlib.Path(sys.argv[2]), after, before, 'rollback')
else:
    raise SystemExit('Use: prepare | apply BACKUP_DIR | rollback BACKUP_DIR')
