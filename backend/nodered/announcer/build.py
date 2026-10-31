"""Generate ANNOUNCER.1: voice announcements in shadow, it only logs (docs/AI_MODE/ANNUNCI_VOCALI.md).

New tab "Annunci (DomoPi)" with announcer.js. The function "avvisi" of HOUSE-ALERTS.1 also feeds it, and its puffer
alarm goes from 85/80 to 60/58 degrees (user's decision of 31/10/2026). Nothing is spoken; the Alexa Speak tab stays disabled.

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

root = pathlib.Path(__file__).resolve().parent
ALERTS = '3916025424d2ee99'
HOME = '3ed6302a.5af928'
TOPICS = ['zara/interface/climate/ac/state/stat', 'zara/interface/fireplace/main/power/stat',
          'zara/interface/fireplace/main/mode/stat', 'zara/interface/fireplace/main/start_time/stat',
          'zara/interface/fireplace/main/stop_time/stat', 'zara/interface/heating/puffer/top_temperature/stat',
          'zara/interface/energy/battery/soc/stat', 'zara/system/health/20/stat', 'zara/system/health/15/stat']
TAP = "msg.alert = true;\nreturn msg;\n"


def nid(name):
    return hashlib.sha256(('ANNOUNCER.1/' + name).encode()).hexdigest()[:16]


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    flows = data['flows'] if isinstance(data, dict) else data
    nodes = {n['id']: n for n in flows}
    alerts = nodes[ALERTS]
    tab, logic, out, tick, tap = nid('tab'), nid('logic'), nid('out'), nid('tick'), nid('tap')
    assert tab not in nodes, 'patch already applied'
    a = 'const PUFFER = {high: 85, back: 80}'
    assert alerts['func'].count(a) == 1, 'unexpected base'
    new = copy.deepcopy(alerts)
    new['func'] = alerts['func'].replace(a, 'const PUFFER = {high: 60, back: 58}')
    new['wires'] = [alerts['wires'][0] + [tap]]
    solar = [n for n in flows if n['type'] == 'mqtt in' and n.get('topic') == 'TeslaPowerwall/solar_instant_power' and not n.get('d')]
    assert solar, 'no solar input'
    broker = solar[0]['broker']
    added = [{'id': tab, 'type': 'tab', 'label': 'Annunci (DomoPi)', 'disabled': False, 'info': ''}]
    for i, t in enumerate(TOPICS + ['TeslaPowerwall/solar_instant_power']):
        added.append({'id': nid('in%d' % i), 'type': 'mqtt in', 'z': tab, 'name': t.split('/')[-2] if t.endswith('stat') else 'solare', 'topic': t,
                      'qos': '1', 'datatype': 'utf8', 'broker': broker if t.startswith('Tesla') else HOME, 'nl': False, 'rap': True, 'rh': 0,
                      'inputs': 0, 'x': 190, 'y': 60 + i * 45, 'wires': [[logic]]})
    added += [
        {'id': tick, 'type': 'inject', 'z': tab, 'name': 'ogni 60 s', 'props': [{'p': 'topic', 'vt': 'str'}], 'repeat': '60', 'crontab': '',
         'once': True, 'onceDelay': '30', 'topic': 'tick', 'x': 150, 'y': 540, 'wires': [[logic]]},
        {'id': tap, 'type': 'function', 'z': ALERTS and alerts['z'], 'name': 'copia verso annunci', 'func': TAP, 'outputs': 1, 'timeout': 0, 'noerr': 0,
         'initialize': '', 'finalize': '', 'libs': [], 'x': alerts['x'] + 40, 'y': alerts['y'] + 70, 'wires': [[nid('link')]]},
        {'id': nid('link'), 'type': 'link out', 'z': alerts['z'], 'name': '→ annunci', 'mode': 'link', 'links': [nid('linkin')], 'x': alerts['x'] + 240, 'y': alerts['y'] + 70},
        {'id': nid('linkin'), 'type': 'link in', 'z': tab, 'name': 'avvisi', 'links': [nid('link')], 'x': 170, 'y': 600, 'wires': [[logic]]},
        {'id': logic, 'type': 'function', 'z': tab, 'name': 'Annunciatore (ombra)', 'func': (root / 'announcer.js').read_text(), 'outputs': 1,
         'timeout': 0, 'noerr': 0, 'initialize': '', 'finalize': '', 'libs': [], 'x': 520, 'y': 300, 'wires': [[out]]},
        {'id': out, 'type': 'mqtt out', 'z': tab, 'name': 'registro', 'topic': '', 'qos': '1', 'retain': 'true', 'respTopic': '', 'contentType': '',
         'userProps': '', 'correl': '', 'expiry': '', 'broker': HOME, 'x': 760, 'y': 300, 'wires': []},
    ]
    (root / 'update.json').write_text(json.dumps([new] + added, ensure_ascii=False, indent=2) + '\n')
    (root / 'rollback.json').write_text(json.dumps([alerts], ensure_ascii=False, indent=2) + '\n')
    digest = lambda v: hashlib.sha256(json.dumps(v, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'ANNOUNCER.1', 'modified_ids': [ALERTS], 'removed_ids': [], 'added_ids': [n['id'] for n in added],
        'node_sha256_before': {ALERTS: digest(alerts)}, 'node_sha256_after': {v['id']: digest(v) for v in [new] + added},
    }, indent=2) + '\n')
    print(f'{len(added)} nodes added, 1 modified')


if __name__ == '__main__':
    main(sys.argv[1])
