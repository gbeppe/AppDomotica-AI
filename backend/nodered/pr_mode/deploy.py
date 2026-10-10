"""Run on .20. Modes: prepare | apply BACKUP_DIR | bootstrap | rollback BACKUP_DIR.

Node-RED Admin API v2 over the verified Tailscale TLS endpoint, token from
~/.node-red/.cli-config.json (never printed), full backup before deploy, drift
checks on every modified node, 'nodes' deployment. 'bootstrap' presses the
existing read-only inject "PR Operational State Bootstrap" (same as at startup).
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
assert set(IDS) == set(before) == set(after) and manifest['added_ids'] == []
FIELDS = ('type', 'z', 'func', 'outputs', 'wires')


def call(path, body=None, method=None, deploy=False):
    h = dict(headers)
    if body is not None:
        h['Content-Type'] = 'application/json'
    if deploy:
        h['Node-RED-Deployment-Type'] = 'nodes'
    req = urllib.request.Request(url + path, data=None if body is None else json.dumps(body).encode(),
                                 headers=h, method=method)
    with urllib.request.urlopen(req, timeout=60) as response:
        text = response.read()
        return json.loads(text) if text.strip() else None


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True).encode()).hexdigest()


def by_id(runtime):
    return {n['id']: n for n in runtime['flows']}


def check(runtime, expected):
    live = by_id(runtime)
    for node_id in IDS:
        for field in FIELDS:
            assert live[node_id].get(field) == expected[node_id].get(field), \
                'Runtime drift on %s %s; stop' % (node_id, field)


def planned_from(runtime, target):
    planned = copy.deepcopy(runtime)
    live = by_id(planned)
    for node_id in IDS:
        live[node_id]['func'] = target[node_id]['func']
    return planned


def deploy(backup, expected, target, label):
    current = call('/flows')
    check(current, expected)
    planned = planned_from(current, target)
    (backup / f'runtime-before-{label}.json').write_text(json.dumps(current, indent=2))
    result = call('/flows', planned, deploy=True)
    actual = call('/flows')
    assert digest(actual['flows']) == digest(planned['flows']), 'Post-deploy mismatch; inspect runtime'
    (backup / f'runtime-after-{label}.json').write_text(json.dumps(actual, indent=2))
    print(json.dumps({'result': 'verified', 'mode': label, 'revision': result['rev'], 'backup': str(backup),
                      'modified_nodes': len(IDS), 'added_nodes': 0}))


mode = sys.argv[1] if len(sys.argv) > 1 else ''
if mode == 'prepare':
    live = call('/flows')
    check(live, before)
    backup = pathlib.Path.home() / 'AI_backups/nodered' / datetime.datetime.now(datetime.timezone.utc).strftime(
        '%Y%m%dT%H%M%SZ-before-pr-mode')
    backup.mkdir(parents=True, mode=0o700)
    (backup / 'runtime-before.json').write_text(json.dumps(live, indent=2))
    (backup / 'runtime-planned.json').write_text(json.dumps(planned_from(live, after), indent=2))
    project = pathlib.Path.home() / '.node-red/projects/domopi'
    for name in ('flows_domopi.json', 'flows_domopi_cred.json'):
        if (project / name).exists():
            shutil.copy2(project / name, backup / name)
    (backup / 'plan.json').write_text(json.dumps({'patch': manifest['patch'], 'modified_ids': IDS,
                                                  'before_hash': digest(live)}, indent=2))
    print(backup)
    print('Runtime verified; %d changes planned (Router, PR Control State Adapter); full backup saved.' % len(IDS))
elif mode == 'apply':
    backup = pathlib.Path(sys.argv[2])
    assert call('/flows') == json.loads((backup / 'runtime-before.json').read_text()), \
        'Runtime changed after preparation; stop'
    deploy(backup, before, after, 'apply')
elif mode == 'bootstrap':
    check(call('/flows'), after)
    call('/inject/' + manifest['bootstrap_inject_id'], {}, method='POST')
    print(json.dumps({'result': 'injected', 'node': manifest['bootstrap_inject_id']}))
elif mode == 'rollback':
    deploy(pathlib.Path(sys.argv[2]), after, before, 'rollback')
else:
    raise SystemExit('Use: prepare | apply BACKUP_DIR | bootstrap | rollback BACKUP_DIR')
