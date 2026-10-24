"""Run on .15. Modes: prepare | apply BACKUP_DIR | rollback BACKUP_DIR.

TESLA-STATUS.1 on .15 (emonpi): adds 8 nodes to the "Tesla Powerwall" tab (status of the Powerwall
gateway every 5 minutes -> pw_* inputs of the existing Emoncms Push, node TeslaPowerwall). No existing
node is modified. Node-RED Admin API v2 on http://localhost:1880, token from
~/.node-red/.admin-token.json (created by the user, never printed), full backup before deploy,
'nodes' deployment, verification after deploy. Rollback removes the 8 nodes.
"""
import copy, datetime, hashlib, json, os, pathlib, shutil, sys, urllib.request

os.umask(0o077)
root = pathlib.Path(__file__).resolve().parent
token = json.loads((pathlib.Path.home() / '.node-red/.admin-token.json').read_text())['access_token']
url = 'http://localhost:1880'
headers = {'Authorization': 'Bearer ' + token, 'Node-RED-API-Version': 'v2'}

TAB = 'ab593fb1.208fc'          # Tesla Powerwall
TLS = '355c8d70.949f62'         # TLS config of the existing Tesla requests
PUSH = '9f03278f.1e0008'        # Emoncms Push, node TeslaPowerwall
ID = {k: f'a7c3e10{i}.5f0a0{i}' for i, k in enumerate(
    ['comment', 'inject', 'request', 'http', 'wrap', 'join', 'convert', 'debug'], 1)}
Y = 1100


def code(name):
    return (root / name).read_text()


def new_nodes():
    base = {'z': TAB}
    return [
        {**base, 'id': ID['comment'], 'type': 'comment', 'name': 'TESLA-STATUS.1: stato del gateway ogni 5 min -> pw_* (EmonCMS)',
         'info': 'Modalità, riserva, potenza chiesta alla batteria, sorgente del comando, cloud Tesla, servizi di rete,\n'
                 'limiti di carica, salute e contatori della batteria, avvisi di rete, stato aggiornamento.\n'
                 'Repository: backend/nodered/tesla_status/', 'x': 260, 'y': Y - 40, 'wires': []},
        {**base, 'id': ID['inject'], 'type': 'inject', 'name': 'ogni 5 min',
         'props': [{'p': 'payload'}, {'p': 'topic', 'vt': 'str'}], 'repeat': '300', 'crontab': '', 'once': True,
         'onceDelay': '30', 'topic': '', 'payload': '', 'payloadType': 'date', 'x': 130, 'y': Y, 'wires': [[ID['request']]]},
        {**base, 'id': ID['request'], 'type': 'function', 'name': 'PW richieste stato', 'func': code('request.js'),
         'outputs': 1, 'noerr': 0, 'initialize': '', 'finalize': '', 'x': 340, 'y': Y, 'wires': [[ID['http']]]},
        {**base, 'id': ID['http'], 'type': 'http request', 'name': 'PW GET stato', 'method': 'GET', 'ret': 'obj',
         'paytoqs': 'ignore', 'url': '', 'tls': TLS, 'persist': False, 'proxy': '', 'authType': '',
         'x': 540, 'y': Y, 'wires': [[ID['wrap']]]},
        {**base, 'id': ID['wrap'], 'type': 'function', 'name': 'PW codice + risposta', 'func': code('wrap.js'),
         'outputs': 1, 'noerr': 0, 'initialize': '', 'finalize': '', 'x': 740, 'y': Y, 'wires': [[ID['join']]]},
        {**base, 'id': ID['join'], 'type': 'join', 'name': 'PW unisci 6 risposte', 'mode': 'custom', 'build': 'object',
         'property': 'payload', 'propertyType': 'msg', 'key': 'topic', 'joiner': '\\n', 'joinerType': 'str',
         'accumulate': False, 'timeout': '20', 'count': '6', 'reduceRight': False, 'reduceExp': '', 'reduceInit': '',
         'reduceInitType': '', 'reduceFixup': '', 'x': 950, 'y': Y, 'wires': [[ID['convert']]]},
        {**base, 'id': ID['convert'], 'type': 'function', 'name': 'PW stato -> pw_*', 'func': code('convert.js'),
         'outputs': 1, 'noerr': 0, 'initialize': '', 'finalize': '', 'x': 1160, 'y': Y, 'wires': [[PUSH, ID['debug']]]},
        {**base, 'id': ID['debug'], 'type': 'debug', 'name': 'PW pw_*', 'active': False, 'tosidebar': True,
         'console': False, 'tostatus': False, 'complete': 'payload', 'targetType': 'msg', 'statusVal': '',
         'statusType': 'auto', 'x': 1380, 'y': Y + 40, 'wires': []},
    ]


def api(body=None):
    h = dict(headers)
    if body is not None:
        h.update({'Content-Type': 'application/json', 'Node-RED-Deployment-Type': 'nodes'})
    req = urllib.request.Request(url + '/flows', data=None if body is None else json.dumps(body).encode(), headers=h)
    with urllib.request.urlopen(req, timeout=60) as response:
        return json.load(response)


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True).encode()).hexdigest()


def check_base(runtime, present):
    live = {n['id']: n for n in runtime['flows']}
    assert live.get(TAB, {}).get('label') == 'Tesla Powerwall', 'tab missing'
    assert live.get(TLS, {}).get('type') == 'tls-config', 'TLS config missing'
    assert live.get(PUSH, {}).get('nodegroup') == 'TeslaPowerwall', 'Emoncms Push missing'
    for node_id in ID.values():
        assert (node_id in live) == present, f'{node_id} {"missing" if present else "already present"}'


def deploy(backup, planned, label):
    result = api(planned)
    actual = api()
    assert digest(actual['flows']) == digest(planned['flows']), 'Post-deploy mismatch; inspect runtime'
    (backup / f'runtime-after-{label}.json').write_text(json.dumps(actual, indent=2))
    print(json.dumps({'result': 'verified', 'mode': label, 'revision': result['rev'], 'backup': str(backup),
                      'nodes': len(ID)}))


mode = sys.argv[1] if len(sys.argv) > 1 else ''
if mode == 'prepare':
    live = api()
    check_base(live, False)
    backup = pathlib.Path.home() / 'AI_backups/nodered' / datetime.datetime.now(datetime.timezone.utc).strftime(
        '%Y%m%dT%H%M%SZ-before-tesla-status1')
    backup.mkdir(parents=True, mode=0o700)
    (backup / 'runtime-before.json').write_text(json.dumps(live, indent=2))
    project = pathlib.Path.home() / '.node-red/projects/emonpi'
    for name in ('flows_emonpi.json', 'flows_emonpi_cred.json'):
        if (project / name).exists():
            shutil.copy2(project / name, backup / name)
    print(backup)
    print(f'Runtime verified; {len(ID)} nodes to add to the Tesla Powerwall tab; full backup saved.')
elif mode == 'apply':
    backup = pathlib.Path(sys.argv[2])
    live = api()
    assert live == json.loads((backup / 'runtime-before.json').read_text()), 'Runtime changed after preparation; stop'
    planned = copy.deepcopy(live)
    planned['flows'] += new_nodes()
    deploy(backup, planned, 'apply')
elif mode == 'rollback':
    backup = pathlib.Path(sys.argv[2])
    live = api()
    check_base(live, True)
    planned = copy.deepcopy(live)
    planned['flows'] = [n for n in planned['flows'] if n['id'] not in ID.values()]
    deploy(backup, planned, 'rollback')
else:
    raise SystemExit('Use: prepare | apply BACKUP_DIR | rollback BACKUP_DIR')
