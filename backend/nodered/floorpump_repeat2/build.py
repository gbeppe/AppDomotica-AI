"""Generate FLOORPUMP-REPEAT.2 from the .15 Node-RED export (base: FLOORPUMP-REPEAT.1).

Test of 16/10/2026: with the pump enabled the function threw "Cannot read property 'split' of
undefined" at every puffer reading and never sent a command. It splits the fireplace stop time
(palazzettiOraSpegnimento), a global recorded only while the pump is enabled; after a .15 restart
with the pump disabled it does not exist. The computed time (stopTime) is not used (the code that
used it is commented out). A missing stop time no longer stops the function.

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

FN_ID = '1572e937.c23a67'
root = pathlib.Path(__file__).resolve().parent
OLD = "let [hours,minutes,seconds] = palazzettiOraSpegnimento.split(':');"
NEW = ("// FLOORPUMP-REPEAT.2: the stop time may be missing after a restart; stopTime is not used.\n"
       "let [hours,minutes,seconds] = String(palazzettiOraSpegnimento || '00:00:00').split(':');")


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    fn = nodes[FN_ID]
    func = fn['func']
    assert 'FLOORPUMP-REPEAT.1' in func, 'FLOORPUMP-REPEAT.1 must be the base'
    assert 'FLOORPUMP-REPEAT.2' not in func, 'already patched'
    assert func.count(OLD) == 1, OLD
    patched = {**copy.deepcopy(fn), 'func': func.replace(OLD, NEW)}
    for name, obj in (('update.json', [patched]), ('rollback.json', [fn])):
        (root / name).write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')
    digest = lambda n: hashlib.sha256(json.dumps(n, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'FLOORPUMP-REPEAT.2', 'host': '192.168.1.15', 'modified_ids': [FN_ID], 'added_ids': [],
        'removed_ids': [], 'node_sha256_before': {FN_ID: digest(fn)}, 'node_sha256_after': {FN_ID: digest(patched)},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
