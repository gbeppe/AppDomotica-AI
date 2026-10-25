"""Generate ALEXA-HUE.1 from the .20 Node-RED flows (export or ~/.node-red/projects/domopi/flows_domopi.json).

Alexa on the .20 is kept only for lights, scenes, fireplace, VMC and pool skimmer (user decision 25/10/2026):
  - the amazon-echo-device nodes for the pool pump, air conditioner, heat pump, hot water and
    the five camera positions get disabled ("d": true): the hub stops exposing them, nothing deleted;
  - the "Alexa Speak" tab gets disabled and so does the alexa-remote-account config node: the
    announcements were already broken (account not initialised since 19/10) and its login proxy on
    port 3456 closes.
The emulated Hue bridge itself moves behind Apache on port 80 (conf domopi-alexa-hue, outside Node-RED).
Usage: python3 build.py <flows.json>
"""
import copy, hashlib, json, pathlib, sys

DISABLE_DEVICES = ['pompa piscina', 'condizionatore', 'temperatura condizionatore', 'pompa calore',
                   'temperatura pompa calore', 'acqua calda', 'telecamera uno', 'telecamera due',
                   'telecamera tre', 'telecamera quattro', 'telecamera cinque']
SPEAK_TAB = 'Alexa Speak'
root = pathlib.Path(__file__).resolve().parent


def main(path):
    data = json.loads(pathlib.Path(path).read_text())
    flows = data['flows'] if isinstance(data, dict) else data
    devices = {n['name']: n for n in flows if n.get('type') == 'amazon-echo-device'}
    assert len(devices) == 32, 'expected the 32 Alexa devices of 25/10/2026, found %d' % len(devices)
    missing = [name for name in DISABLE_DEVICES if name not in devices]
    assert not missing, missing
    tab = [n for n in flows if n.get('type') == 'tab' and n.get('label') == SPEAK_TAB]
    accounts = [n for n in flows if n.get('type') == 'alexa-remote-account']
    assert len(tab) == 1 and len(accounts) == 1
    before = [devices[name] for name in DISABLE_DEVICES] + tab + accounts
    after = [dict(copy.deepcopy(n), d=True) for n in before[:len(DISABLE_DEVICES)]]
    after.append(dict(copy.deepcopy(tab[0]), disabled=True))
    after.append(dict(copy.deepcopy(accounts[0]), d=True))
    digest = lambda n: hashlib.sha256(json.dumps(n, sort_keys=True).encode()).hexdigest()
    for name, obj in (('update.json', after), ('rollback.json', before)):
        (root / name).write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'ALEXA-HUE.1', 'host': '192.168.1.20', 'modified_ids': [n['id'] for n in before],
        'added_ids': [], 'removed_ids': [],
        'node_sha256_before': {n['id']: digest(n) for n in before},
        'node_sha256_after': {n['id']: digest(n) for n in after},
    }, indent=2) + '\n')
    kept = sorted(name for name in devices if name not in DISABLE_DEVICES)
    print('disabled %d devices, kept %d: %s' % (len(DISABLE_DEVICES), len(kept), ', '.join(kept)))


if __name__ == '__main__':
    main(sys.argv[1])
