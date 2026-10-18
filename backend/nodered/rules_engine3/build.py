"""Generate RULES-ENGINE.3 from a Node-RED export (base: RULES-ENGINE.1 + .2 live).

Timed commands (docs/AI_MODE/COMANDI_A_TEMPO.md, phase T1): the engine function gets the new
source of ../rules_engine/rules.js (trigger 'at', start_active, missed and cleanup), the ntfy
formatter of RULES-ENGINE.2 the new ../rules_notify/ntfy.js ("Comando programmato").
Only the func of those two nodes changes; wires and every other node are untouched.

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

root = pathlib.Path(__file__).resolve().parent
LOGIC = hashlib.sha256(b'RULES-ENGINE.1/logic').hexdigest()[:16]
FORMAT = hashlib.sha256(b'RULES-ENGINE.2/format').hexdigest()[:16]


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    logic, fmt = nodes[LOGIC], nodes[FORMAT]
    assert logic['func'].startswith('// RULES-ENGINE.1') and 'RULES-ENGINE.3' not in logic['func'], 'unexpected engine base'
    assert 'RULES-ENGINE.2' in fmt['func'] and 'RULES-ENGINE.3' not in fmt['func'], 'unexpected formatter base'
    new_logic, new_fmt = copy.deepcopy(logic), copy.deepcopy(fmt)
    new_logic['func'] = (root.parent / 'rules_engine' / 'rules.js').read_text()
    new_fmt['func'] = (root.parent / 'rules_notify' / 'ntfy.js').read_text()
    (root / 'update.json').write_text(json.dumps([new_logic, new_fmt], ensure_ascii=False, indent=2) + '\n')
    (root / 'rollback.json').write_text(json.dumps([logic, fmt], ensure_ascii=False, indent=2) + '\n')
    digest = lambda x: hashlib.sha256(json.dumps(x, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'RULES-ENGINE.3', 'modified_ids': [LOGIC, FORMAT], 'removed_ids': [], 'added_ids': [],
        'node_sha256_before': {LOGIC: digest(logic), FORMAT: digest(fmt)},
        'node_sha256_after': {LOGIC: digest(new_logic), FORMAT: digest(new_fmt)},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
