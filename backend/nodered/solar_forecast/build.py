"""Generate SOLAR-FORECAST.1 from a Node-RED export (list of nodes or {"flows": [...]}).

Usage: python3 build.py <flows-export.json>
Writes update.json / rollback.json / manifest.json next to this file.
"""
import copy, hashlib, json, pathlib, sys

INJECT_ID = '6916979d5a52f89e'     # "alle 23:00 ogni giono"
FUNCTION_ID = 'e987e367138f30ef'   # "function 22"
OLD_CRON = '00 23 * * *'
NEW_CRON = '*/30 23,0-5 * * *'     # 23:00, 23:30, ... 05:30; function skips when already fresh
root = pathlib.Path(__file__).resolve().parent


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    inject, function = nodes[INJECT_ID], nodes[FUNCTION_ID]
    assert inject['type'] == 'inject' and inject['crontab'] == OLD_CRON and inject['wires'] == [['6ba4fd927988c70b']]
    assert function['type'] == 'function' and function['name'] == 'function 22'
    assert 'SOLAR-FORECAST.1' not in function['func'], 'patch already applied'
    assert 'flow.set("previsione_kwh_domani", kwhStimatiDomani)' in function['func']
    new_inject = copy.deepcopy(inject)
    new_inject['crontab'] = NEW_CRON
    new_inject['name'] = 'alle 23:00 e ogni 30 min fino alle 05:30'
    new_function = copy.deepcopy(function)
    new_function['func'] = (root / 'function22.js').read_text()
    for name, obj in (('update.json', [new_inject, new_function]), ('rollback.json', [inject, function])):
        (root / name).write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')
    digest = lambda n: hashlib.sha256(json.dumps(n, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'SOLAR-FORECAST.1', 'modified_ids': [INJECT_ID, FUNCTION_ID], 'added_ids': [],
        'node_sha256_before': {n['id']: digest(n) for n in (inject, function)},
        'node_sha256_after': {n['id']: digest(n) for n in (new_inject, new_function)},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
