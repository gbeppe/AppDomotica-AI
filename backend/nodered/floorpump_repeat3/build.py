"""Generate FLOORPUMP-REPEAT.3 from the .15 Node-RED export (base: FLOORPUMP-REPEAT.2).

The floor-pump function (tab "Sheet 1") is rewritten from pump.js: the decision is unchanged
(enabled + puffer top, 35/30 °C, last command repeated in the band) but the fireplace times
and the unused stop-time computation are gone. The fireplace mode is still recorded only while
the pump is enabled, as before (the old thermostat timers read it). Nothing else changes.

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

FN_ID = '1572e937.c23a67'
root = pathlib.Path(__file__).resolve().parent


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    fn = nodes[FN_ID]
    assert 'FLOORPUMP-REPEAT.2' in fn['func'], 'FLOORPUMP-REPEAT.2 must be the base'
    patched = {**copy.deepcopy(fn), 'func': (root / 'pump.js').read_text()}
    for name, obj in (('update.json', [patched]), ('rollback.json', [fn])):
        (root / name).write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')
    digest = lambda n: hashlib.sha256(json.dumps(n, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'FLOORPUMP-REPEAT.3', 'host': '192.168.1.15', 'modified_ids': [FN_ID], 'added_ids': [],
        'removed_ids': [], 'node_sha256_before': {FN_ID: digest(fn)}, 'node_sha256_after': {FN_ID: digest(patched)},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
