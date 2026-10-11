"""Generate ROUTER-CLEANUP.1 from a Node-RED export (list of nodes or {"flows": [...]}).

Usage: python3 build.py <flows-export.json>
Writes update.json (modified nodes), rollback.json (original modified + removed nodes)
and manifest.json next to this file.
"""
import copy, hashlib, json, pathlib, re, sys

TAB = '294638a5b3d82009'                 # Digital Twin Bridge
ROUTER_ID = '5a7c1d360710ca21'
OBSERVER_ID = '8f540759dc824270'
REMOVED = {
    'd6fd86d961bc3efc': 'old Digital Twin Router copy, unwired',
    '24b7b3975c0c176c': 'disabled duplicate mqtt in zara/interface/#',
    '2318745f8140e724': 'disabled duplicate mqtt in cmnd/#',
    '113e119c925982a1': 'semantic observe home stat/tasmota_86AD14/POWER (HiFi lamp removed)',
    '50a951da85765fdd': 'semantic observe emon stat/tasmota_86AD14/POWER (HiFi lamp removed)',
    '62211616279959e0': 'evidence broker home for the HiFi lamp',
    '2fbdf038a8a48d30': 'evidence broker emon for the HiFi lamp',
}
REMOVED_ENTRIES = ('lights.hifi.power', 'heating.heat_pump.enabled', 'heating.heat_pump.solar_divert',
                   'heating.heat_pump.target_temperature')
BOOL_OLD = '(p === "1" || p === "true" || p === "ON" || p === true)'
BOOL_NEW = '(p === "1" || p === 1 || p === "true" || p === "ON" || p === true)'
BOOL_OLD_ML = 'p === "1" ||\n            p === "true" ||'
BOOL_NEW_ML = 'p === "1" ||\n            p === 1 ||\n            p === "true" ||'
root = pathlib.Path(__file__).resolve().parent


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    flows = data['flows'] if isinstance(data, dict) else data
    nodes = {n['id']: n for n in flows}
    router, observer = nodes[ROUTER_ID], nodes[OBSERVER_ID]
    assert router['name'] == 'Digital Twin Router' and 'ROUTER-HEARTBEAT.1' in router['func']
    assert 'ROUTER-CLEANUP.1' not in router['func'], 'patch already applied'
    for node_id in REMOVED:
        assert node_id in nodes and nodes[node_id]['z'] == TAB, node_id
    referenced = {w for n in flows if n['id'] not in REMOVED for ws in n.get('wires', []) for w in ws}
    assert not referenced & set(REMOVED), 'a removed node is still wired from a kept node'

    func = router['func']
    # 1. Numeric 1 (mqtt-in auto-detect turns "1" into a number) is "on" too.
    assert func.count(BOOL_OLD) == 16 and func.count(BOOL_OLD_ML) == 1, (func.count(BOOL_OLD), func.count(BOOL_OLD_ML))
    func = func.replace(BOOL_OLD, BOOL_NEW).replace(BOOL_OLD_ML, BOOL_NEW_ML)
    # 2. Devices that no longer exist (user, 2026-10-11): HiFi lamp, heat pump.
    lines = func.split('\n')
    for entry in REMOVED_ENTRIES:
        hits = [i for i, l in enumerate(lines) if l.lstrip().startswith('{ id: "%s"' % entry)]
        assert len(hits) == 1 and lines[hits[0]].rstrip().endswith('},'), entry
        lines[hits[0]] = '    // ROUTER-CLEANUP.1 (2026-10-11): removed "%s" (device no longer installed)' % entry
    func = '\n'.join(lines)
    new_router = copy.deepcopy(router)
    new_router['func'] = func

    hifi_line = " 'stat/tasmota_86AD14/POWER':'lights.hifi.power',\n"
    assert observer['func'].count(hifi_line) == 1
    new_observer = copy.deepcopy(observer)
    new_observer['func'] = observer['func'].replace(hifi_line, '')

    # 3. Link-outs named after the Broadlink action they trigger, not "Trigger Shelf Lamp".
    renamed = []
    for n in flows:
        if n['type'] == 'link out' and n.get('z') == TAB and n.get('name', '').startswith('Trigger Shelf Lamp'):
            targets = [nodes[t].get('name') for t in n.get('links', []) if t in nodes]
            assert len(targets) == 1 and targets[0], n['id']
            new = copy.deepcopy(n)
            new['name'] = '→ ' + targets[0]
            renamed.append((n, new))
    assert len(renamed) == 20, len(renamed)

    before = [router, observer] + [old for old, _ in renamed]
    after = [new_router, new_observer] + [new for _, new in renamed]
    (root / 'update.json').write_text(json.dumps(after, ensure_ascii=False, indent=2) + '\n')
    (root / 'rollback.json').write_text(json.dumps(before + [nodes[i] for i in REMOVED], ensure_ascii=False, indent=2) + '\n')
    digest = lambda n: hashlib.sha256(json.dumps(n, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'ROUTER-CLEANUP.1', 'modified_ids': [n['id'] for n in after], 'added_ids': [],
        'removed_ids': list(REMOVED), 'removed_reasons': REMOVED,
        'renamed': {new['id']: new['name'] for _, new in renamed},
        'node_sha256_before': {n['id']: digest(n) for n in before + [nodes[i] for i in REMOVED]},
        'node_sha256_after': {n['id']: digest(n) for n in after},
    }, ensure_ascii=False, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
