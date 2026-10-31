"""Generate WINTER-TEST.1: tool 3 of the winter field test (docs/AI_MODE/AI_CLIMATE_INVERNO.md section 8).

New tab "Prova invernale (DomoPi)" with the function winter.js, disabled until
zara/interface/climate/winter_test/cmd {"action":"on"}; requires AI-CLIMATE-AC-OVERRIDE.2. The existing link-in
"avvisi da altri tab" of HOUSE-ALERTS.1 gains the link from the new tab. Nothing else changes.

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

root = pathlib.Path(__file__).resolve().parent
BROKER = '3ed6302a.5af928'
ALERT_IN = hashlib.sha256(b'AI-CLIMATE-AC-OVERRIDE.1/alert_in').hexdigest()[:16]
TOPICS = ['zara/interface/heating/timer/plan/stat', 'zara/interface/energy/solar_forecast/stat',
          'zara/interface/climate/thermostat_living/target_temperature/stat', 'zara/interface/fireplace/main/power/stat',
          'zara/interface/climate/ac/override/stat', 'zigbee2mqtt/Sonoff AirGuard TH esterno cucina',
          'zara/interface/climate/winter_test/cmd']


def nid(name):
    return hashlib.sha256(('WINTER-TEST.1/' + name).encode()).hexdigest()[:16]


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    assert 'OVERRIDE.2' in nodes['61992c8879c11f66']['func'], 'requires AI-CLIMATE-AC-OVERRIDE.2'
    tab, logic, out, link, tick = nid('tab'), nid('logic'), nid('out'), nid('link'), nid('tick')
    assert tab not in nodes, 'patch already applied'
    al = nodes[ALERT_IN]
    new_al = copy.deepcopy(al)
    new_al['links'] = al['links'] + [link]
    added = [{'id': tab, 'type': 'tab', 'label': 'Prova invernale (DomoPi)', 'disabled': False, 'info': ''}]
    for i, topic in enumerate(TOPICS):
        added.append({'id': nid('in%d' % i), 'type': 'mqtt in', 'z': tab, 'name': topic.split('/')[-2] if topic.endswith('stat') or topic.endswith('cmd') else 'esterno',
                      'topic': topic, 'qos': '1', 'datatype': 'utf8', 'broker': BROKER, 'nl': False, 'rap': True, 'rh': 0,
                      'inputs': 0, 'x': 180, 'y': 60 + i * 50, 'wires': [[logic]]})
    added += [
        {'id': tick, 'type': 'inject', 'z': tab, 'name': 'ogni 60 s', 'props': [{'p': 'topic', 'vt': 'str'}],
         'repeat': '60', 'crontab': '', 'once': True, 'onceDelay': '30', 'topic': 'tick', 'x': 150, 'y': 420, 'wires': [[logic]]},
        {'id': logic, 'type': 'function', 'z': tab, 'name': 'Prova invernale', 'func': (root / 'winter.js').read_text(),
         'outputs': 2, 'timeout': 0, 'noerr': 0, 'initialize': '', 'finalize': '', 'libs': [], 'x': 520, 'y': 240, 'wires': [[out], [link]]},
        {'id': out, 'type': 'mqtt out', 'z': tab, 'name': 'comandi e stato', 'topic': '', 'qos': '1', 'retain': '', 'respTopic': '',
         'contentType': '', 'userProps': '', 'correl': '', 'expiry': '', 'broker': BROKER, 'x': 780, 'y': 220, 'wires': []},
        {'id': link, 'type': 'link out', 'z': tab, 'name': '→ avviso ntfy', 'mode': 'link', 'links': [ALERT_IN], 'x': 760, 'y': 280},
    ]
    (root / 'update.json').write_text(json.dumps([new_al] + added, ensure_ascii=False, indent=2) + '\n')
    (root / 'rollback.json').write_text(json.dumps([al], ensure_ascii=False, indent=2) + '\n')
    digest = lambda v: hashlib.sha256(json.dumps(v, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'WINTER-TEST.1', 'modified_ids': [ALERT_IN], 'removed_ids': [], 'added_ids': [n['id'] for n in added],
        'node_sha256_before': {ALERT_IN: digest(al)}, 'node_sha256_after': {v['id']: digest(v) for v in [new_al] + added},
    }, indent=2) + '\n')
    print(f'{len(added)} nodes added, 1 modified')


if __name__ == '__main__':
    main(sys.argv[1])
