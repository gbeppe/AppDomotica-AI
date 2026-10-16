"""Generate FLOORPUMP-REPEAT.1 from the .15 Node-RED export (project emonpi, tab "Sheet 1").

The floor-pump function sends 1 above 35 °C and 0 below 30 °C (puffer top) and nothing in the
30-35 °C band. floorpump_v2 switches the pump off after 15 minutes without commands (user's
choice, 16/10/2026): in the band the pump would stop at 34 °C instead of 30 °C. In the band the
function now repeats its last command; "disabled" also records 0. Nothing else changes (the
fireplace-related globals set by the same function stay as they are).

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

FN_ID = '1572e937.c23a67'
root = pathlib.Path(__file__).resolve().parent
DISABLED_OLD = '''if (!pompapavimentoenabled) {
    msg.payload = 0;
    return msg;
}'''
DISABLED_NEW = '''if (!pompapavimentoenabled) {
    msg.payload = 0;
    context.set("lastPump", 0);  // FLOORPUMP-REPEAT.1
    return msg;
}'''
BAND_OLD = '''if (tempPufferSup > 35) msg.payload = 1;
else if (tempPufferSup < 30) msg.payload = 0;
else return null;

return msg;'''
BAND_NEW = '''// FLOORPUMP-REPEAT.1: in the 30-35 °C band repeat the last command, so the 15-minute safety
// stop of floorpump_v2 does not switch the pump off before 30 °C.
if (tempPufferSup > 35) msg.payload = 1;
else if (tempPufferSup < 30) msg.payload = 0;
else if (context.get("lastPump") !== undefined) msg.payload = context.get("lastPump");
else return null;

context.set("lastPump", msg.payload);
return msg;'''


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    fn = nodes[FN_ID]
    func = fn['func']
    assert 'FLOORPUMP-REPEAT' not in func, 'already patched'
    for old, new in ((DISABLED_OLD, DISABLED_NEW), (BAND_OLD, BAND_NEW)):
        assert func.count(old) == 1, old
        func = func.replace(old, new)
    patched = {**copy.deepcopy(fn), 'func': func}
    for name, obj in (('update.json', [patched]), ('rollback.json', [fn])):
        (root / name).write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')
    digest = lambda n: hashlib.sha256(json.dumps(n, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'FLOORPUMP-REPEAT.1', 'host': '192.168.1.15', 'modified_ids': [FN_ID], 'added_ids': [],
        'removed_ids': [], 'node_sha256_before': {FN_ID: digest(fn)}, 'node_sha256_after': {FN_ID: digest(patched)},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
