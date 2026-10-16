"""Generate HEATING-PROFILE.1: a new, separate Node-RED tab with the heating profile and the
thermostat timers (shadow mode by default). See docs/AI_MODE/PROFILO_RISCALDAMENTO.md.

Usage: python3 build.py <flows-export.json>
Writes update.json (the added nodes), rollback.json (empty: nothing is modified) and
manifest.json. The Digital Twin Router is not touched.
"""
import hashlib, json, pathlib, sys

root = pathlib.Path(__file__).resolve().parent
HOME_BROKER = '3ed6302a.5af928'    # "domopi mqtt" 192.168.1.20


def nid(name):
    return hashlib.sha256(('HEATING-PROFILE.1/' + name).encode()).hexdigest()[:16]


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    flows = data['flows'] if isinstance(data, dict) else data
    ids = {n['id'] for n in flows}
    assert any(n['id'] == HOME_BROKER and n['type'] == 'mqtt-broker' for n in flows), HOME_BROKER
    tab = nid('tab')
    n = {k: nid(k) for k in ('comment', 'tick', 'in_outdoor', 'in_climate', 'in_cmd', 'logic', 'out_cmd',
                             'out_state', 'log_file')}
    mqtt_in = lambda key, name, topic, y: {
        'id': n[key], 'type': 'mqtt in', 'z': tab, 'name': name, 'topic': topic, 'qos': '1', 'datatype': 'utf8',
        'broker': HOME_BROKER, 'nl': False, 'rap': True, 'rh': 0, 'inputs': 0, 'x': 180, 'y': y,
        'wires': [[n['logic']]]}
    mqtt_out = lambda key, name, y: {
        'id': n[key], 'type': 'mqtt out', 'z': tab, 'name': name, 'topic': '', 'qos': '', 'retain': '',
        'respTopic': '', 'contentType': '', 'userProps': '', 'correl': '', 'expiry': '', 'broker': HOME_BROKER,
        'x': 760, 'y': y, 'wires': []}
    nodes = [
        {'id': tab, 'type': 'tab', 'label': 'Profilo riscaldamento (DomoPi)', 'disabled': False,
         'info': 'HEATING-PROFILE.1 (2026-10-16): profilo Spento/Ridotto/Pieno (automatico dalla media esterna '
                 'di 72 h: Pieno < 10 °C, Spento > 16 °C, isteresi 1 °C) e timer dei termostati (in fascia il '
                 'massimo, fuori il minimo). Parte in shadow. Repository: backend/nodered/heating_profile/.'},
        {'id': n['comment'], 'type': 'comment', 'z': tab, 'name': 'HEATING-PROFILE.1 — vedi docs/AI_MODE/PROFILO_RISCALDAMENTO.md',
         'info': '', 'x': 290, 'y': 40, 'wires': []},
        {'id': n['tick'], 'type': 'inject', 'z': tab, 'name': 'ogni minuto',
         'props': [{'p': 'tick', 'v': 'true', 'vt': 'bool'}], 'repeat': '60', 'crontab': '', 'once': True,
         'onceDelay': '20', 'topic': '', 'x': 160, 'y': 100, 'wires': [[n['logic']]]},
        mqtt_in('in_outdoor', 'temperatura esterna', 'zara/interface/env/outdoor/temperature/stat', 160),
        mqtt_in('in_climate', 'termostati (setpoint, min, max)', 'zara/interface/climate/+/+/stat', 220),
        mqtt_in('in_cmd', 'comandi profilo e timer', 'zara/interface/heating/+/mode/cmd', 280),
        {'id': n['logic'], 'type': 'function', 'z': tab, 'name': 'profilo e timer',
         'func': (root / 'heating.js').read_text(), 'outputs': 3, 'timeout': 0, 'noerr': 0,
         'initialize': '', 'finalize': '', 'libs': [], 'x': 470, 'y': 200,
         'wires': [[n['out_cmd']], [n['out_state']], [n['log_file']]]},
        mqtt_out('out_cmd', 'setpoint termostati (solo active)', 160),
        mqtt_out('out_state', 'stato profilo e piano (retained)', 220),
        {'id': n['log_file'], 'type': 'file', 'z': tab, 'name': 'registro heating-YYYY-MM-DD.jsonl',
         'filename': 'filename', 'filenameType': 'msg', 'appendNewline': True, 'createDir': True,
         'overwriteFile': 'false', 'encoding': 'none', 'x': 780, 'y': 280, 'wires': [[]]},
    ]
    assert not {x['id'] for x in nodes} & ids, 'patch already applied'
    (root / 'update.json').write_text(json.dumps(nodes, ensure_ascii=False, indent=2) + '\n')
    (root / 'rollback.json').write_text('[]\n')
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'HEATING-PROFILE.1', 'modified_ids': [], 'removed_ids': [], 'added_ids': [x['id'] for x in nodes],
        'node_sha256_after': {x['id']: hashlib.sha256(json.dumps(x, sort_keys=True).encode()).hexdigest() for x in nodes},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
