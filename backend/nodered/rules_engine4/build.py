"""Generate RULES-ENGINE.4 from a Node-RED export (base: RULES-ENGINE.3 live).

VMC actions for timed commands (docs/AI_MODE/COMANDI_A_TEMPO.md, phase T2): set_vmc_speed,
set_vmc_override, set_vmc_auto. Only the func of the engine node changes (source:
../rules_engine/rules.js); the ntfy formatter is unchanged.

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

root = pathlib.Path(__file__).resolve().parent
LOGIC = hashlib.sha256(b'RULES-ENGINE.1/logic').hexdigest()[:16]


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    logic = nodes[LOGIC]
    assert 'RULES-ENGINE.3' in logic['func'] and 'RULES-ENGINE.4' not in logic['func'], 'unexpected engine base'
    new = copy.deepcopy(logic)
    new['func'] = (root.parent / 'rules_engine' / 'rules.js').read_text()
    (root / 'update.json').write_text(json.dumps([new], ensure_ascii=False, indent=2) + '\n')
    (root / 'rollback.json').write_text(json.dumps([logic], ensure_ascii=False, indent=2) + '\n')
    digest = lambda x: hashlib.sha256(json.dumps(x, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'RULES-ENGINE.4', 'modified_ids': [LOGIC], 'removed_ids': [], 'added_ids': [],
        'node_sha256_before': {LOGIC: digest(logic)}, 'node_sha256_after': {LOGIC: digest(new)},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
