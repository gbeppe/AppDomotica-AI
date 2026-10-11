"""Generate IRRIGATION.1: a new, separate Node-RED tab for OpenSprinkler.

Usage: python3 build.py <flows-export.json>
Writes update.json (the added nodes), rollback.json (empty: nothing is modified) and
manifest.json. The Digital Twin Router is not touched.
"""
import hashlib, json, pathlib, sys

root = pathlib.Path(__file__).resolve().parent
HOME_BROKER = '3ed6302a.5af928'    # "domopi mqtt" 192.168.1.20
EMON_BROKER = 'cfd9898e.1367b8'    # "emoncms mqtt" 192.168.1.15
PASSWORD_FILE = '/home/pi/.config/domopi/opensprinkler.md5'


def nid(name):
    return hashlib.sha256(('IRRIGATION.1/' + name).encode()).hexdigest()[:16]


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    flows = data['flows'] if isinstance(data, dict) else data
    ids = {n['id'] for n in flows}
    for broker in (HOME_BROKER, EMON_BROKER):
        assert any(n['id'] == broker and n['type'] == 'mqtt-broker' for n in flows), broker
    tab = nid('tab')
    func = lambda name: (root / name).read_text()
    n = {k: nid(k) for k in ('pw_inject', 'pw_file', 'pw_store', 'poll_inject', 'poll', 'http',
                             'response', 'cmd_in', 'command', 'out_home', 'out_emon', 'comment')}
    nodes = [
        {'id': tab, 'type': 'tab', 'label': 'Irrigazione OpenSprinkler (DomoPi)', 'disabled': False,
         'info': 'IRRIGATION.1 (2026-10-11): comandi app -> API OpenSprinkler 192.168.1.3:9003, stato ogni 10 s '
                 'su zara/interface/irrigation/state/stat, registrazione EmonCMS emon/irrigazione/zona_N. '
                 'Password: ' + PASSWORD_FILE + '. Repository: backend/nodered/irrigation/.'},
        {'id': n['comment'], 'type': 'comment', 'z': tab, 'name': 'IRRIGATION.1 — vedi backend/nodered/irrigation/README.md',
         'info': '', 'x': 250, 'y': 40, 'wires': []},
        {'id': n['pw_inject'], 'type': 'inject', 'z': tab, 'name': 'carica password (avvio, ogni ora)',
         'props': [], 'repeat': '3600', 'crontab': '', 'once': True, 'onceDelay': '2', 'topic': '',
         'x': 190, 'y': 100, 'wires': [[n['pw_file']]]},
        {'id': n['pw_file'], 'type': 'file in', 'z': tab, 'name': 'opensprinkler.md5', 'filename': PASSWORD_FILE,
         'filenameType': 'str', 'format': 'utf8', 'chunk': False, 'sendError': False, 'encoding': 'none',
         'allProps': False, 'x': 430, 'y': 100, 'wires': [[n['pw_store']]]},
        {'id': n['pw_store'], 'type': 'function', 'z': tab, 'name': 'memorizza password', 'func': func('load_password.js'),
         'outputs': 1, 'timeout': 0, 'noerr': 0, 'initialize': '', 'finalize': '', 'libs': [],
         'x': 650, 'y': 100, 'wires': [[]]},
        {'id': n['poll_inject'], 'type': 'inject', 'z': tab, 'name': 'ogni 10 s', 'props': [], 'repeat': '10',
         'crontab': '', 'once': True, 'onceDelay': '5', 'topic': '', 'x': 160, 'y': 180, 'wires': [[n['poll']]]},
        {'id': n['poll'], 'type': 'function', 'z': tab, 'name': 'lettura stato', 'func': func('poll.js'),
         'outputs': 1, 'timeout': 0, 'noerr': 0, 'initialize': '', 'finalize': '', 'libs': [],
         'x': 360, 'y': 180, 'wires': [[n['http']]]},
        {'id': n['http'], 'type': 'http request', 'z': tab, 'name': 'OpenSprinkler API', 'method': 'GET',
         'ret': 'obj', 'paytoqs': 'ignore', 'url': '', 'tls': '', 'persist': False, 'proxy': '',
         'insecureHTTPParser': False, 'authType': '', 'senderr': False, 'headers': [],
         'x': 580, 'y': 220, 'wires': [[n['response']]]},
        {'id': n['response'], 'type': 'function', 'z': tab, 'name': 'stato, esito, EmonCMS', 'func': func('response.js'),
         'outputs': 4, 'timeout': 0, 'noerr': 0, 'initialize': '', 'finalize': '', 'libs': [],
         'x': 810, 'y': 220, 'wires': [[n['out_home']], [n['out_home']], [n['poll']], [n['out_emon']]]},
        {'id': n['cmd_in'], 'type': 'mqtt in', 'z': tab, 'name': 'comandi app', 'topic': 'zara/interface/irrigation/#',
         'qos': '1', 'datatype': 'utf8', 'broker': HOME_BROKER, 'nl': False, 'rap': True, 'rh': 0, 'inputs': 0,
         'x': 160, 'y': 280, 'wires': [[n['command']]]},
        {'id': n['command'], 'type': 'function', 'z': tab, 'name': 'valida comando', 'func': func('command.js'),
         'outputs': 2, 'timeout': 0, 'noerr': 0, 'initialize': '', 'finalize': '', 'libs': [],
         'x': 360, 'y': 280, 'wires': [[n['http']], [n['out_home']]]},
        {'id': n['out_home'], 'type': 'mqtt out', 'z': tab, 'name': 'stato app (Home)', 'topic': '', 'qos': '',
         'retain': '', 'respTopic': '', 'contentType': '', 'userProps': '', 'correl': '', 'expiry': '',
         'broker': HOME_BROKER, 'x': 1060, 'y': 200, 'wires': []},
        {'id': n['out_emon'], 'type': 'mqtt out', 'z': tab, 'name': 'EmonCMS (Emon)', 'topic': '', 'qos': '',
         'retain': '', 'respTopic': '', 'contentType': '', 'userProps': '', 'correl': '', 'expiry': '',
         'broker': EMON_BROKER, 'x': 1060, 'y': 260, 'wires': []},
    ]
    assert not {x['id'] for x in nodes} & ids, 'patch already applied'
    (root / 'update.json').write_text(json.dumps(nodes, ensure_ascii=False, indent=2) + '\n')
    (root / 'rollback.json').write_text('[]\n')
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'IRRIGATION.1', 'modified_ids': [], 'removed_ids': [], 'added_ids': [x['id'] for x in nodes],
        'node_sha256_after': {x['id']: hashlib.sha256(json.dumps(x, sort_keys=True).encode()).hexdigest() for x in nodes},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
