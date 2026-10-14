"""Generate ROUTER-FIREPLACE-MODE.2 from a Node-RED export (base: ROUTER-FIREPLACE-MODE.1, v120).

1. Mode "Manuale" -> `manuale` on .15: the .15 MAIN logic runs no automatic rule for an
   unknown mode but still accepts manual on/off, which is the wanted "manual only" mode.
2. Power switch: the router wrote 1/0 to zara/domotics/palazzetti/acceso on the .20 broker,
   a state topic nobody reads as a command. The real manual command on .15 is
   alexa/caminetto = on/off (broker emon). The state keeps coming from .../acceso, which .15
   writes as "on"/"off": the state rule now accepts lowercase "on".
3. Init guard v120 -> v121 (routing tables are cached in node context).

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

ROUTER_ID = '5a7c1d360710ca21'
root = pathlib.Path(__file__).resolve().parent
POWER_OLD = ('{ id: "fireplace.main.power", int_cmd: "zara/interface/fireplace/main/power/cmd", '
             'int_stat: "zara/interface/fireplace/main/power/stat", leg_cmd: "zara/domotics/palazzetti/acceso", '
             'leg_stat: "zara/domotics/palazzetti/acceso", broker: "home", cmd_trans: { "true": "1", "false": "0" }, '
             'stat_trans_func: (p) => { return (p === "1" || p === 1 || p === "true" || p === "ON" || p === true); } },')
POWER_NEW = ('{ id: "fireplace.main.power", int_cmd: "zara/interface/fireplace/main/power/cmd", '
             'int_stat: "zara/interface/fireplace/main/power/stat", leg_cmd: "alexa/caminetto", '
             'leg_stat: "zara/domotics/palazzetti/acceso", broker: "emon", // ROUTER-FIREPLACE-MODE.2: manual on/off of .15\n'
             '      cmd_trans: { "true": "on", "false": "off" }, '
             'stat_trans_func: (p) => { return (p === "1" || p === 1 || p === "true" || p === "ON" || p === "on" || p === true); } },')
MODE_PAIRS = [
    ('"Acqua Sanitaria": "sanitaria", "Disattivato": "disattivato" },',
     '"Acqua Sanitaria": "sanitaria", "Disattivato": "disattivato", "Manuale": "manuale" },'),
    ('sanitaria: "Acqua Sanitaria", disattivato: "Disattivato" })',
     'sanitaria: "Acqua Sanitaria", disattivato: "Disattivato", manuale: "Manuale" })'),
]


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    router = nodes[ROUTER_ID]
    func = router['func']
    assert 'ROUTER-FIREPLACE-MODE.1' in func, 'ROUTER-FIREPLACE-MODE.1 must be the base'
    assert func.count(POWER_OLD) == 1, 'fireplace.main.power entry differs from the expected one'
    assert func.count('router_initialized_v120') == 2, 'unexpected init guard'
    func = func.replace(POWER_OLD, POWER_NEW)
    for old, new in MODE_PAIRS:
        assert func.count(old) == 1, old
        func = func.replace(old, new)
    patched = copy.deepcopy(router)
    patched['func'] = func.replace('router_initialized_v120', 'router_initialized_v121')
    for name, obj in (('update.json', [patched]), ('rollback.json', [router])):
        (root / name).write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')
    digest = lambda n: hashlib.sha256(json.dumps(n, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'ROUTER-FIREPLACE-MODE.2', 'modified_ids': [ROUTER_ID], 'added_ids': [], 'removed_ids': [],
        'node_sha256_before': {ROUTER_ID: digest(router)}, 'node_sha256_after': {ROUTER_ID: digest(patched)},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
