"""Generate TASMOTA-TIMERS-OFF.1 from the .15 Node-RED export (tab "TASMOTAs").

The old thermostat timers of .15 must be off before HEATING-PROFILE.1 on .20 goes active
(docs/AI_MODE/PROFILO_RISCALDAMENTO.md): the living one is already disabled; the bathroom one
(timerswitch 04:00-06:00) still sends 11 °C straight to the Tasmota F294D7 (cmnd/.../TEMPTARGETSET)
at 04:00 and 06:00, bypassing the router. This patch only marks that node disabled, as the living
one; nothing is removed, rollback enables it again.

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

NODE_ID = '7e69383.2658ac8'      # timerswitch bathroom_schedule, tab TASMOTAs
root = pathlib.Path(__file__).resolve().parent


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    node = nodes[NODE_ID]
    assert node['type'] == 'timerswitch' and node.get('ontopic') == 'bathroom_schedule' and not node.get('d'), 'unexpected base'
    patched = {**copy.deepcopy(node), 'd': True}
    for name, obj in (('update.json', [patched]), ('rollback.json', [node])):
        (root / name).write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')
    digest = lambda n: hashlib.sha256(json.dumps(n, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'TASMOTA-TIMERS-OFF.1', 'host': '192.168.1.15', 'modified_ids': [NODE_ID], 'added_ids': [],
        'removed_ids': [], 'node_sha256_before': {NODE_ID: digest(node)}, 'node_sha256_after': {NODE_ID: digest(patched)},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
