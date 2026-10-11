"""Generate AI-CLIMATE-CLEANUP.1: remove the 6 unwired copies of "Machine Learning domotico".

Usage: python3 build.py <flows-export.json>
Only the copy fed by the sensor switch (61992c8879c11f66) is kept; the copies have no
input and no output wires, so removing them cannot change behaviour.
"""
import hashlib, json, pathlib, sys

TAB = '2cc001ef3d59afd2'                 # AI climate
KEEP = '61992c8879c11f66'
COPIES = ['112b83a5b0996529', '234c14caa02c7881', '34d195884b61e3d7', '655f20f9bfc74f4f',
          'd5d60e578d517b21', 'aef522b9530d2781']
root = pathlib.Path(__file__).resolve().parent


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    flows = data['flows'] if isinstance(data, dict) else data
    nodes = {n['id']: n for n in flows}
    wired_to = {w for n in flows for ws in n.get('wires', []) for w in ws}
    linked = {t for n in flows for t in n.get('links', [])}
    assert KEEP in wired_to and nodes[KEEP]['name'].startswith('Machine Learning domotico')
    for node_id in COPIES:
        n = nodes[node_id]
        assert n['z'] == TAB and n['type'] == 'function' and n['name'].startswith('Machine Learning domotico'), node_id
        assert node_id not in wired_to and node_id not in linked, node_id + ' has an input'
        assert not any(n.get('wires', [])[i] for i in range(len(n.get('wires', [])))), node_id + ' has outputs'
    removed = [nodes[i] for i in COPIES]
    (root / 'update.json').write_text('[]\n')
    (root / 'rollback.json').write_text(json.dumps(removed, ensure_ascii=False, indent=2) + '\n')
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'AI-CLIMATE-CLEANUP.1', 'modified_ids': [], 'added_ids': [], 'removed_ids': COPIES,
        'removed_reasons': {i: 'unwired copy of Machine Learning domotico' for i in COPIES},
        'node_sha256_before': {n['id']: hashlib.sha256(json.dumps(n, sort_keys=True).encode()).hexdigest() for n in removed},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
