"""Generate HEATING-PROFILE.2 from a Node-RED export (base: HEATING-PROFILE.1, shadow).

The function "profilo e timer" is replaced by ../heating_profile/heating.js (the single source):
the plan gives each thermostat's min, max and next change, and the last 10 user setpoint
corrections are published retained on zara/interface/heating/profile/learning/stat for the app.
Decisions, events and shadow mode are unchanged. Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

root = pathlib.Path(__file__).resolve().parent
FN_ID = hashlib.sha256(b'HEATING-PROFILE.1/logic').hexdigest()[:16]


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    fn = nodes[FN_ID]
    assert 'HEATING-PROFILE.1' in fn['func'] and 'HEATING-PROFILE.2' not in fn['func'], 'unexpected base'
    patched = {**copy.deepcopy(fn), 'func': (root.parent / 'heating_profile' / 'heating.js').read_text()}
    for name, obj in (('update.json', [patched]), ('rollback.json', [fn])):
        (root / name).write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')
    digest = lambda n: hashlib.sha256(json.dumps(n, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'HEATING-PROFILE.2', 'modified_ids': [FN_ID], 'added_ids': [], 'removed_ids': [],
        'node_sha256_before': {FN_ID: digest(fn)}, 'node_sha256_after': {FN_ID: digest(patched)},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
