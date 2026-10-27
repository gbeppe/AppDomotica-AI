"""Generate TESLA-FW-INSTALL.1 from the .15 Node-RED runtime export (tab "Tesla Powerwall").

The user's function after "Get Tesla Status" (5811d15d.e9acb8) is rewritten from gw.js: same inputs as
before plus gw_fw_installed (Unix time) and gw_fw_installed_exact (1 = seen changing, 0 = last known boot),
kept in the "file" context store. Nothing else changes.
Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

FN_ID = '5811d15d.e9acb8'
root = pathlib.Path(__file__).resolve().parent


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    fn = nodes[FN_ID]
    assert 'gw_fw_version' in fn['func'] and 'gw_fw_installed' not in fn['func'], 'unexpected base function'
    patched = {**copy.deepcopy(fn), 'name': 'Tesla status -> gw_*', 'func': (root / 'gw.js').read_text()}
    for name, obj in (('update.json', [patched]), ('rollback.json', [fn])):
        (root / name).write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')
    digest = lambda n: hashlib.sha256(json.dumps(n, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'TESLA-FW-INSTALL.1', 'host': '192.168.1.15', 'modified_ids': [FN_ID], 'added_ids': [],
        'removed_ids': [], 'node_sha256_before': {FN_ID: digest(fn)}, 'node_sha256_after': {FN_ID: digest(patched)},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
