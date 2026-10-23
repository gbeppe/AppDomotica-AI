"""Generate SOLAR-TILTED.1 (phases F2 and F3 of backend/solar/README.md) from the .20 Node-RED export.

Tab "start local scripts":
F2. remove the disabled "Irradiance=" branch (mqtt in emon/weather/tomorrowSolarIrradiance, its
    function, two delays and two exec nodes): it never worked (solarEnergy.php reads only the first
    argument and the value was in flow, not msg). The clear-sky chain stays as it is.
F3. add the Open-Meteo forecast on the tilted faces (tilted.js): hourly fetch for the two faces, a
    300 s tick publishing W of light per face to EmonCMS (emoncms broker, retained) and a retained
    summary zara/interface/energy/solar_forecast/stat (domopi broker). SOLAR-FORECAST.1 untouched.

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

root = pathlib.Path(__file__).resolve().parent
TAB = 'bb7b2455ed68c7d8'
HOME_BROKER, EMON_BROKER = '3ed6302a.5af928', 'cfd9898e.1367b8'
REMOVED = ['08d69022888b30e4', '7fcb34dde7c4bb44', 'cfe8a968f3e3d484', '71316610605ed7d2',
           'f7d9ed4550d3c448', 'e0139363b5a64510']


def nid(name):
    return hashlib.sha256(('SOLAR-TILTED.1/' + name).encode()).hexdigest()[:16]


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    flows = data['flows'] if isinstance(data, dict) else data
    nodes = {n['id']: n for n in flows}
    old = [nodes[i] for i in REMOVED]
    assert all(n['z'] == TAB for n in old) and nodes['08d69022888b30e4'].get('d') is True
    assert [n.get('command') for n in old if n['type'] == 'exec'] == [
        'php /home/pi/solarEnergy.php panelModel=Eclipse Irradiance=', 'php /home/pi/solarEnergy.php panelModel=Viessmann Irradiance=']
    inside = set(REMOVED)
    for n in flows:   # nothing outside the branch is wired into it
        if n['id'] not in inside:
            assert not inside & {w for ws in n.get('wires', []) for w in ws}, n['id']
    n = {k: nid(k) for k in ('fetch', 'tick', 'logic', 'http', 'emon', 'home')}
    assert not set(n.values()) & set(nodes), 'patch already applied'
    x, y = 170, 420
    added = [
        {'id': n['fetch'], 'type': 'inject', 'z': TAB, 'name': 'previsione: ogni ora',
         'props': [{'p': 'topic', 'vt': 'str'}], 'repeat': '3600', 'crontab': '', 'once': True, 'onceDelay': '30',
         'topic': 'fetch', 'x': x, 'y': y, 'wires': [[n['logic']]]},
        {'id': n['tick'], 'type': 'inject', 'z': TAB, 'name': 'previsione: ogni 300 s',
         'props': [{'p': 'topic', 'vt': 'str'}], 'repeat': '300', 'crontab': '', 'once': True, 'onceDelay': '90',
         'topic': 'tick', 'x': x, 'y': y + 50, 'wires': [[n['logic']]]},
        {'id': n['logic'], 'type': 'function', 'z': TAB, 'name': 'Fotovoltaico: previsione sul piano inclinato',
         'func': (root / 'tilted.js').read_text(), 'outputs': 3, 'timeout': 0, 'noerr': 0, 'initialize': '',
         'finalize': '', 'libs': [], 'x': x + 300, 'y': y + 20,
         'wires': [[n['http']], [n['emon']], [n['home']]]},
        {'id': n['http'], 'type': 'http request', 'z': TAB, 'name': 'Open-Meteo piano inclinato', 'method': 'GET',
         'ret': 'obj', 'paytoqs': 'ignore', 'url': '', 'tls': '', 'persist': False, 'proxy': '',
         'insecureHTTPParser': False, 'authType': '', 'senderr': False, 'headers': [],
         'x': x + 300, 'y': y + 90, 'wires': [[n['logic']]]},
        {'id': n['emon'], 'type': 'mqtt out', 'z': TAB, 'name': 'EmonCMS forecast_smodule', 'topic': '', 'qos': '0',
         'retain': 'true', 'respTopic': '', 'contentType': '', 'userProps': '', 'correl': '', 'expiry': '',
         'broker': EMON_BROKER, 'x': x + 600, 'y': y, 'wires': []},
        {'id': n['home'], 'type': 'mqtt out', 'z': TAB, 'name': 'riepilogo previsione', 'topic': '', 'qos': '1',
         'retain': 'true', 'respTopic': '', 'contentType': '', 'userProps': '', 'correl': '', 'expiry': '',
         'broker': HOME_BROKER, 'x': x + 600, 'y': y + 50, 'wires': []},
    ]
    (root / 'update.json').write_text(json.dumps(added, ensure_ascii=False, indent=2) + '\n')
    (root / 'rollback.json').write_text(json.dumps(old, ensure_ascii=False, indent=2) + '\n')
    digest = lambda v: hashlib.sha256(json.dumps(v, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'SOLAR-TILTED.1', 'modified_ids': [], 'removed_ids': REMOVED, 'added_ids': [v['id'] for v in added],
        'node_sha256_before': {v['id']: digest(v) for v in old}, 'node_sha256_after': {v['id']: digest(v) for v in added},
    }, indent=2) + '\n')
    print(f'{len(added)} nodes added, {len(REMOVED)} removed')


if __name__ == '__main__':
    main(sys.argv[1])
