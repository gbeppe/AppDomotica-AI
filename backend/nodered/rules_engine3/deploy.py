"""Run on .20. Modes: prepare | apply BACKUP_DIR | rollback BACKUP_DIR.

Same procedure as solar_forecast: Node-RED Admin API v2 over the verified
Tailscale TLS endpoint, token from ~/.node-red/.cli-config.json (never printed),
full backup before deploy, runtime drift checks on every modified and removed node,
'nodes' deployment. Removed nodes are dropped from the flow list; rollback restores
them from rollback.json.
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
REMOVED = manifest['removed_ids']
ADDED = manifest['added_ids']
assert set(after) == set(IDS) | set(ADDED) and set(before) == set(IDS) | set(REMOVED)
before.update({node_id: None for node_id in ADDED})   # added nodes must not exist before apply


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
    """expected: id -> node that must be live, or None for a node that must be absent."""
    live = {n['id']: n for n in runtime['flows']}
    for node_id, node in expected.items():
        assert live.get(node_id) == node, f'Runtime drift on {node_id}; {what}'


def replaced(runtime, target):
    """Apply target (id -> node, or None to remove); missing nodes are appended (rollback)."""
    planned = copy.deepcopy(runtime)
    present = {n['id'] for n in planned['flows']}
    planned['flows'] = [copy.deepcopy(target[n['id']]) if n['id'] in target else n
                        for n in planned['flows'] if not (n['id'] in target and target[n['id']] is None)]
    planned['flows'] += [copy.deepcopy(node) for node_id, node in target.items()
                         if node is not None and node_id not in present]
    return planned


APPLIED = {**after, **{node_id: None for node_id in REMOVED}}
ROLLBACK = {**{k: v for k, v in before.items()}, **{node_id: None for node_id in ADDED}}


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
                      'modified_nodes': len(IDS), 'added_nodes': len(ADDED) if label == 'apply' else 0,
                      'removed_nodes': len(REMOVED) if label == 'apply' else len(ADDED),
                      'restored_nodes': len(REMOVED) if label == 'rollback' else 0}))


mode = sys.argv[1] if len(sys.argv) > 1 else ''
if mode == 'prepare':
    live = api()
    check(live, before, 'rebuild the patch')
    backup = pathlib.Path.home() / 'AI_backups/nodered' / datetime.datetime.now(datetime.timezone.utc).strftime(
        '%Y%m%dT%H%M%SZ-before-rules-engine3')
    backup.mkdir(parents=True, mode=0o700)
    (backup / 'runtime-before.json').write_text(json.dumps(live, indent=2))
    (backup / 'runtime-planned.json').write_text(json.dumps(replaced(live, APPLIED), indent=2))
    project = pathlib.Path.home() / '.node-red/projects/domopi'
    for name in ('flows_domopi.json', 'flows_domopi_cred.json'):
        src = project / name
        if src.exists():
            shutil.copy2(src, backup / name)
    (backup / 'plan.json').write_text(json.dumps({'patch': manifest['patch'], 'modified_ids': IDS, 'removed_ids': REMOVED, 'added_ids': ADDED,
                                                  'before_hash': digest(live)}, indent=2))
    print(backup)
    print(f'Runtime verified; {len(ADDED)} added, {len(IDS)} modified, {len(REMOVED)} removed nodes planned ({manifest["patch"]}); full backup saved.')
elif mode == 'apply':
    backup = pathlib.Path(sys.argv[2])
    assert api() == json.loads((backup / 'runtime-before.json').read_text()), 'Runtime changed after preparation; stop'
    deploy(backup, before, APPLIED, 'apply')
elif mode == 'rollback':
    deploy(pathlib.Path(sys.argv[2]), APPLIED, ROLLBACK, 'rollback')
else:
    raise SystemExit('Use: prepare | apply BACKUP_DIR | rollback BACKUP_DIR')
