"""Generate RULES-ENGINE.1: a new, separate Node-RED tab running the natural-language rules
(phase H1, docs/AI_MODE/REGOLE_NATURALI.md). The engine starts in shadow mode.

Usage: python3 build.py <flows-export.json>
Writes update.json (the added nodes), rollback.json (empty) and manifest.json. The Digital Twin
Router is not touched.
"""
import hashlib, json, pathlib, sys

root = pathlib.Path(__file__).resolve().parent
HOME_BROKER = '3ed6302a.5af928'    # "domopi mqtt" 192.168.1.20


def nid(name):
    return hashlib.sha256(('RULES-ENGINE.1/' + name).encode()).hexdigest()[:16]


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    flows = data['flows'] if isinstance(data, dict) else data
    ids = {n['id'] for n in flows}
    assert any(n['id'] == HOME_BROKER and n['type'] == 'mqtt-broker' for n in flows), HOME_BROKER
    tab = nid('tab')
    n = {k: nid(k) for k in ('comment', 'tick', 'in_puffer', 'in_env', 'in_energy', 'in_fireplace', 'in_cmd',
                             'logic', 'out_cmd', 'out_state', 'log_file')}
    mqtt_in = lambda key, name, topic, y: {
        'id': n[key], 'type': 'mqtt in', 'z': tab, 'name': name, 'topic': topic, 'qos': '1', 'datatype': 'utf8',
        'broker': HOME_BROKER, 'nl': False, 'rap': True, 'rh': 0, 'inputs': 0, 'x': 190, 'y': y,
        'wires': [[n['logic']]]}
    mqtt_out = lambda key, name, y: {
        'id': n[key], 'type': 'mqtt out', 'z': tab, 'name': name, 'topic': '', 'qos': '', 'retain': '',
        'respTopic': '', 'contentType': '', 'userProps': '', 'correl': '', 'expiry': '', 'broker': HOME_BROKER,
        'x': 800, 'y': y, 'wires': []}
    nodes = [
        {'id': tab, 'type': 'tab', 'label': 'Regole (DomoPi)', 'disabled': False,
         'info': 'RULES-ENGINE.1 (2026-10-16): regole naturali, fase H1. Trigger a soglia/orario, una azione '
                 'ammessa, durata, attesa minima; ogni regola nuova in prova 24 h; motore in shadow finché non si '
                 'pubblica "active" su zara/interface/rules/engine/cmd. Repository: backend/nodered/rules_engine/.'},
        {'id': n['comment'], 'type': 'comment', 'z': tab, 'name': 'RULES-ENGINE.1 — vedi docs/AI_MODE/REGOLE_NATURALI.md',
         'info': '', 'x': 290, 'y': 40, 'wires': []},
        {'id': n['tick'], 'type': 'inject', 'z': tab, 'name': 'ogni 30 s',
         'props': [{'p': 'tick', 'v': 'true', 'vt': 'bool'}], 'repeat': '30', 'crontab': '', 'once': True,
         'onceDelay': '20', 'topic': '', 'x': 160, 'y': 100, 'wires': [[n['logic']]]},
        mqtt_in('in_puffer', 'puffer', 'zara/interface/heating/puffer/+/stat', 160),
        mqtt_in('in_env', 'temperature ambienti ed esterna', 'zara/interface/env/+/temperature/stat', 210),
        mqtt_in('in_energy', 'batteria, FV, consumo', 'zara/interface/energy/+/+/stat', 260),
        mqtt_in('in_fireplace', 'stato caminetto', 'zara/interface/fireplace/main/+/stat', 310),
        mqtt_in('in_cmd', 'comandi regole', 'zara/interface/rules/+/cmd', 360),
        {'id': n['logic'], 'type': 'function', 'z': tab, 'name': 'motore regole',
         'func': (root / 'rules.js').read_text(), 'outputs': 4, 'timeout': 0, 'noerr': 0,
         'initialize': '', 'finalize': '', 'libs': [], 'x': 500, 'y': 230,
         'wires': [[n['out_cmd']], [n['out_state']], [n['log_file']], [n['out_state']]]},
        mqtt_out('out_cmd', 'comandi (solo regole attive, motore active)', 180),
        mqtt_out('out_state', 'elenco, ultimo evento, eventi', 240),
        {'id': n['log_file'], 'type': 'file', 'z': tab, 'name': 'registro rules-YYYY-MM-DD.jsonl',
         'filename': 'filename', 'filenameType': 'msg', 'appendNewline': True, 'createDir': True,
         'overwriteFile': 'false', 'encoding': 'none', 'x': 810, 'y': 300, 'wires': [[]]},
    ]
    assert not {x['id'] for x in nodes} & ids, 'patch already applied'
    (root / 'update.json').write_text(json.dumps(nodes, ensure_ascii=False, indent=2) + '\n')
    (root / 'rollback.json').write_text('[]\n')
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'RULES-ENGINE.1', 'modified_ids': [], 'removed_ids': [], 'added_ids': [x['id'] for x in nodes],
        'node_sha256_after': {x['id']: hashlib.sha256(json.dumps(x, sort_keys=True).encode()).hexdigest() for x in nodes},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
