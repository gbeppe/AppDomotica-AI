"""Generate ROUTER-FIREPLACE-RETAIN.1 from a Node-RED export (base: ROUTER-FIREPLACE-MODE.2, v121).

1. Fireplace settings (mode, start/stop time, auto power) are now published retained on the
   .15 broker. The router sent them without retain, so the broker kept the old values
   (17:00/19:00) and .15 went back to them when its Node-RED re-read the retained topics,
   while the app still showed the new ones. Registry entries carry `leg_retain: true`;
   only those routes set msg.retain. The manual on/off and the level (alexa/caminetto) stay
   one-shot commands, never retained.
2. "Legacy Cmd (Emon Broker)" had retain "false", which Node-RED applies over msg.retain;
   it becomes "" so the per-route flag is honoured (unset msg.retain still means false).
3. Init guard v121 -> v122 (routing tables are cached in node context).

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

ROUTER_ID = '5a7c1d360710ca21'
EMON_OUT_ID = '6d4fa4d4930d5586'
root = pathlib.Path(__file__).resolve().parent
RETAINED = ('zara/domotics/palazzetti/modalita', 'zara/domotics/palazzetti/oraavvio',
            'zara/domotics/palazzetti/oraspegnimento', 'zara/domotics/controlloAutomaticoPotenzaCaminetto')
ROUTE_OLD = '''                transform: entity.cmd_trans
            };'''
ROUTE_NEW = '''                transform: entity.cmd_trans,
                retain: entity.leg_retain === true // ROUTER-FIREPLACE-RETAIN.1
            };'''
MSG_OLD = '''        const routedMsg = {
            topic: route.topic,
            payload: outPayload,
            broker: route.broker
        };
'''
MSG_NEW = MSG_OLD + '''        // Settings that .15 re-reads from its broker stay retained there (ROUTER-FIREPLACE-RETAIN.1).
        if (route.retain) routedMsg.retain = true;
'''


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    router, emon_out = nodes[ROUTER_ID], nodes[EMON_OUT_ID]
    func = router['func']
    assert 'ROUTER-FIREPLACE-MODE.2' in func, 'ROUTER-FIREPLACE-MODE.2 must be the base'
    assert 'leg_retain' not in func, 'already patched'
    assert func.count('router_initialized_v121') == 2, 'unexpected init guard'
    assert emon_out['type'] == 'mqtt out' and emon_out['retain'] == 'false' and emon_out['topic'] == ''
    for topic in RETAINED:
        old = f'leg_cmd: "{topic}", leg_stat: "{topic}", broker: "emon",'
        assert func.count(old) == 1, topic
        func = func.replace(old, old + ' leg_retain: true,')
    for old, new in ((ROUTE_OLD, ROUTE_NEW), (MSG_OLD, MSG_NEW)):
        assert func.count(old) == 1, old
        func = func.replace(old, new)
    patched = copy.deepcopy(router)
    patched['func'] = func.replace('router_initialized_v121', 'router_initialized_v122')
    patched_out = {**copy.deepcopy(emon_out), 'retain': ''}
    for name, obj in (('update.json', [patched, patched_out]), ('rollback.json', [router, emon_out])):
        (root / name).write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')
    digest = lambda n: hashlib.sha256(json.dumps(n, sort_keys=True).encode()).hexdigest()
    ids = [ROUTER_ID, EMON_OUT_ID]
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'ROUTER-FIREPLACE-RETAIN.1', 'modified_ids': ids, 'added_ids': [], 'removed_ids': [],
        'node_sha256_before': {ROUTER_ID: digest(router), EMON_OUT_ID: digest(emon_out)},
        'node_sha256_after': {ROUTER_ID: digest(patched), EMON_OUT_ID: digest(patched_out)},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
