"""Generate ROUTER-FIREPLACE-MODE.1 from a Node-RED export.

The app sends the fireplace mode as its menu label ("Acqua Sanitaria", ...). The router
forwarded it untranslated to the .20 broker, while the fireplace logic on .15 listens to
its own broker and expects inverno / conCaldaia / sanitaria / disattivato: a mode chosen
in the app never reached the fireplace. This patch routes the mode to the .15 broker,
translates labels to the .15 values (and back for the state) and bumps the init guard so
the cached routing tables are rebuilt.

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

ROUTER_ID = '5a7c1d360710ca21'
root = pathlib.Path(__file__).resolve().parent
OLD = ('{ id: "fireplace.main.mode", int_cmd: "zara/interface/fireplace/main/mode/cmd", '
       'int_stat: "zara/interface/fireplace/main/mode/stat", leg_cmd: "zara/domotics/palazzetti/modalita", '
       'leg_stat: "zara/domotics/palazzetti/modalita", broker: "home", '
       'stat_trans_func: (p) => { return String(p); } },')
NEW = ('{ id: "fireplace.main.mode", int_cmd: "zara/interface/fireplace/main/mode/cmd", '
       'int_stat: "zara/interface/fireplace/main/mode/stat", leg_cmd: "zara/domotics/palazzetti/modalita", '
       'leg_stat: "zara/domotics/palazzetti/modalita", broker: "emon", '
       '// ROUTER-FIREPLACE-MODE.1: app labels <-> values of the fireplace logic on .15\n'
       '      cmd_trans: { "Riscaldamento": "inverno", "Integrazione Caldaia": "conCaldaia", '
       '"Acqua Sanitaria": "sanitaria", "Disattivato": "disattivato" },\n'
       '      stat_trans_func: (p) => ({ inverno: "Riscaldamento", conCaldaia: "Integrazione Caldaia", '
       'sanitaria: "Acqua Sanitaria", disattivato: "Disattivato" })[String(p)] || String(p) },')


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    router = nodes[ROUTER_ID]
    assert router['func'].count(OLD) == 1, 'fireplace.main.mode entry differs from the expected one'
    assert router['func'].count('router_initialized_v119') == 2, 'unexpected init guard'
    patched = copy.deepcopy(router)
    patched['func'] = router['func'].replace(OLD, NEW).replace('router_initialized_v119', 'router_initialized_v120')
    for name, obj in (('update.json', [patched]), ('rollback.json', [router])):
        (root / name).write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')
    digest = lambda n: hashlib.sha256(json.dumps(n, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'ROUTER-FIREPLACE-MODE.1', 'modified_ids': [ROUTER_ID], 'added_ids': [], 'removed_ids': [],
        'node_sha256_before': {ROUTER_ID: digest(router)}, 'node_sha256_after': {ROUTER_ID: digest(patched)},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
