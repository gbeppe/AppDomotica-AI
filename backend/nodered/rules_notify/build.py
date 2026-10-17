"""Generate RULES-ENGINE.2 from a Node-RED export (base: RULES-ENGINE.1).

Adds to the "Regole (DomoPi)" tab the phone notifications through ntfy (backend/ntfy, NTFY.1):
the engine's event output also feeds "notifica ntfy" (ntfy.js) -> http request to 127.0.0.1:2586.
The Node-RED publish token is read from /home/pi/.config/domopi/ntfy_nodered.token at start and
every hour (never in the flow). The only modified node is the engine (one extra wire).

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

root = pathlib.Path(__file__).resolve().parent
old = lambda name: hashlib.sha256(('RULES-ENGINE.1/' + name).encode()).hexdigest()[:16]
nid = lambda name: hashlib.sha256(('RULES-ENGINE.2/' + name).encode()).hexdigest()[:16]
TOKEN_FILE = '/home/pi/.config/domopi/ntfy_nodered.token'


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    tab, logic = old('tab'), nodes[old('logic')]
    assert 'RULES-ENGINE.1' in logic['func'] and len(logic['wires']) == 4, 'unexpected base'
    n = {k: nid(k) for k in ('tok_inject', 'tok_file', 'tok_store', 'format', 'http', 'result')}
    assert not set(n.values()) & set(nodes), 'patch already applied'
    patched = copy.deepcopy(logic)
    patched['wires'][3] = logic['wires'][3] + [n['format']]
    added = [
        {'id': n['tok_inject'], 'type': 'inject', 'z': tab, 'name': 'carica token ntfy (avvio, ogni ora)', 'props': [],
         'repeat': '3600', 'crontab': '', 'once': True, 'onceDelay': '3', 'topic': '', 'x': 200, 'y': 440,
         'wires': [[n['tok_file']]]},
        {'id': n['tok_file'], 'type': 'file in', 'z': tab, 'name': 'ntfy_nodered.token', 'filename': TOKEN_FILE,
         'filenameType': 'str', 'format': 'utf8', 'chunk': False, 'sendError': False, 'encoding': 'none',
         'allProps': False, 'x': 450, 'y': 440, 'wires': [[n['tok_store']]]},
        {'id': n['tok_store'], 'type': 'function', 'z': tab, 'name': 'memorizza token',
         'func': "const t = String(msg.payload || '').trim();\nif (/^tk_[A-Za-z0-9]+$/.test(t)) flow.set('ntfyToken', t);\nelse node.warn('ntfy: token non valido');\nreturn null;",
         'outputs': 1, 'timeout': 0, 'noerr': 0, 'initialize': '', 'finalize': '', 'libs': [], 'x': 660, 'y': 440, 'wires': [[]]},
        {'id': n['format'], 'type': 'function', 'z': tab, 'name': 'notifica ntfy', 'func': (root / 'ntfy.js').read_text(),
         'outputs': 1, 'timeout': 0, 'noerr': 0, 'initialize': '', 'finalize': '', 'libs': [], 'x': 640, 'y': 380,
         'wires': [[n['http']]]},
        {'id': n['http'], 'type': 'http request', 'z': tab, 'name': 'ntfy (domopi-regole)', 'method': 'use', 'ret': 'txt',
         'paytoqs': 'ignore', 'url': '', 'tls': '', 'persist': False, 'proxy': '', 'insecureHTTPParser': False,
         'authType': '', 'senderr': False, 'headers': [], 'x': 850, 'y': 380, 'wires': [[n['result']]]},
        {'id': n['result'], 'type': 'function', 'z': tab, 'name': 'esito ntfy',
         'func': "if (msg.statusCode !== 200) node.warn('ntfy: notifica non consegnata, HTTP ' + msg.statusCode);\nreturn null;",
         'outputs': 1, 'timeout': 0, 'noerr': 0, 'initialize': '', 'finalize': '', 'libs': [], 'x': 1040, 'y': 380, 'wires': [[]]},
    ]
    (root / 'update.json').write_text(json.dumps([patched] + added, ensure_ascii=False, indent=2) + '\n')
    (root / 'rollback.json').write_text(json.dumps([logic], ensure_ascii=False, indent=2) + '\n')
    digest = lambda x: hashlib.sha256(json.dumps(x, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'RULES-ENGINE.2', 'modified_ids': [logic['id']], 'removed_ids': [], 'added_ids': [x['id'] for x in added],
        'node_sha256_before': {logic['id']: digest(logic)},
        'node_sha256_after': {x['id']: digest(x) for x in [patched] + added},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
