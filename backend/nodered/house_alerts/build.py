"""Generate HOUSE-ALERTS.1: a new, separate Node-RED tab "Avvisi (DomoPi)" on .20.

House alerts on the phone through ntfy (backend/ntfy, NTFY.1), channel domopi-casa, chosen by the
user on 17/10/2026: puffer top above 85 °C, fireplace / floor pump boards offline for 5 minutes,
living / bath temperature sensors silent for 30 minutes, outdoor temperature silent for 2 hours,
heating profile changed, battery below 20 %. No existing node is modified.

Usage: python3 build.py <flows-export.json>
"""
import hashlib, json, pathlib, sys

root = pathlib.Path(__file__).resolve().parent
HOME_BROKER = '3ed6302a.5af928'    # "domopi mqtt" 192.168.1.20
EMON_BROKER = 'cfd9898e.1367b8'    # "emoncms mqtt" 192.168.1.15
TOKEN_FILE = '/home/pi/.config/domopi/ntfy_nodered.token'
HOME_TOPICS = ['zara/interface/heating/puffer/top_temperature/stat', 'zara/interface/energy/battery/soc/stat',
               'zara/interface/heating/profile/stat']
EMON_TOPICS = ['/esp8266/palazzetti/online', '/esp8266/floorPump/online', 'emon/emonth5/temperature_calibrated',
               'emon/bagnoServizio/temperature', 'emon/weather/extTemp']


def nid(name):
    return hashlib.sha256(('HOUSE-ALERTS.1/' + name).encode()).hexdigest()[:16]


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    flows = data['flows'] if isinstance(data, dict) else data
    ids = {n['id'] for n in flows}
    for broker in (HOME_BROKER, EMON_BROKER):
        assert any(n['id'] == broker and n['type'] == 'mqtt-broker' for n in flows), broker
    tab = nid('tab')
    assert tab not in ids, 'patch already applied'
    logic, fmt, http, result = nid('logic'), nid('format'), nid('http'), nid('result')
    tok_inject, tok_file, tok_store = nid('tok_inject'), nid('tok_file'), nid('tok_store')

    def mqtt_in(topic, broker, y):
        return {'id': nid('in/' + topic), 'type': 'mqtt in', 'z': tab, 'name': topic, 'topic': topic, 'qos': '0',
                'datatype': 'utf8', 'broker': broker, 'nl': False, 'rap': True, 'rh': 0, 'inputs': 0,
                'x': 230, 'y': y, 'wires': [[logic]]}

    func = lambda i, name, code, x, y, wires: {
        'id': i, 'type': 'function', 'z': tab, 'name': name, 'func': code, 'outputs': 1, 'timeout': 0, 'noerr': 0,
        'initialize': '', 'finalize': '', 'libs': [], 'x': x, 'y': y, 'wires': wires}
    inputs = [mqtt_in(t, HOME_BROKER, 80 + 40 * i) for i, t in enumerate(HOME_TOPICS)]
    inputs += [mqtt_in(t, EMON_BROKER, 80 + 40 * (i + len(HOME_TOPICS))) for i, t in enumerate(EMON_TOPICS)]
    nodes = [
        {'id': tab, 'type': 'tab', 'label': 'Avvisi (DomoPi)', 'disabled': False,
         'info': 'HOUSE-ALERTS.1 — avvisi della casa sul telefono (ntfy, canale domopi-casa). '
                 'Sorgente: backend/nodered/house_alerts.'},
        {'id': nid('comment'), 'type': 'comment', 'z': tab, 'name': 'HOUSE-ALERTS.1 — vedi backend/nodered/house_alerts/README.md',
         'info': '', 'x': 260, 'y': 40, 'wires': []},
        *inputs,
        {'id': nid('tick'), 'type': 'inject', 'z': tab, 'name': 'ogni 60 s', 'props': [{'p': 'topic', 'vt': 'str'}],
         'repeat': '60', 'crontab': '', 'once': True, 'onceDelay': '20', 'topic': 'tick', 'x': 200, 'y': 440,
         'wires': [[logic]]},
        func(logic, 'avvisi', (root / 'alerts.js').read_text(), 560, 240, [[fmt]]),
        func(fmt, 'notifica ntfy', (root / 'ntfy.js').read_text(), 760, 240, [[http]]),
        {'id': http, 'type': 'http request', 'z': tab, 'name': 'ntfy (domopi-casa)', 'method': 'use', 'ret': 'txt',
         'paytoqs': 'ignore', 'url': '', 'tls': '', 'persist': False, 'proxy': '', 'insecureHTTPParser': False,
         'authType': '', 'senderr': False, 'headers': [], 'x': 970, 'y': 240, 'wires': [[result]]},
        func(result, 'esito ntfy',
             "if (msg.statusCode !== 200) node.warn('ntfy: avviso non consegnato, HTTP ' + msg.statusCode);\nreturn null;",
             1160, 240, [[]]),
        {'id': tok_inject, 'type': 'inject', 'z': tab, 'name': 'carica token ntfy (avvio, ogni ora)', 'props': [],
         'repeat': '3600', 'crontab': '', 'once': True, 'onceDelay': '3', 'topic': '', 'x': 220, 'y': 500,
         'wires': [[tok_file]]},
        {'id': tok_file, 'type': 'file in', 'z': tab, 'name': 'ntfy_nodered.token', 'filename': TOKEN_FILE,
         'filenameType': 'str', 'format': 'utf8', 'chunk': False, 'sendError': False, 'encoding': 'none',
         'allProps': False, 'x': 470, 'y': 500, 'wires': [[tok_store]]},
        func(tok_store, 'memorizza token',
             "const t = String(msg.payload || '').trim();\nif (/^tk_[A-Za-z0-9]+$/.test(t)) flow.set('ntfyToken', t);\nelse node.warn('ntfy: token non valido');\nreturn null;",
             680, 500, [[]]),
    ]
    (root / 'update.json').write_text(json.dumps(nodes, ensure_ascii=False, indent=2) + '\n')
    (root / 'rollback.json').write_text('[]\n')
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'HOUSE-ALERTS.1', 'modified_ids': [], 'removed_ids': [], 'added_ids': [x['id'] for x in nodes],
        'node_sha256_after': {x['id']: hashlib.sha256(json.dumps(x, sort_keys=True).encode()).hexdigest() for x in nodes},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
