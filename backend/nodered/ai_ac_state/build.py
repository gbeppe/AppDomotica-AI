"""Generate AI-CLIMATE-AC-STATE.1: summary of the air conditioner for the app.

User's request after the T4 live test (30/10/2026): the air conditioner is driven by infrared only and
has no display, so nobody can see what it is doing; the app must show the current state, the state
requested and held back by a safeguard (compressor timers) and the alarms. Format and the four choices
agreed with the user the same day (docs/AI_MODE/COMANDI_A_TEMPO.md, "Stato del condizionatore").

The AI Climate function gets:
- two cycle variables (what the AI wanted but a compressor timer held back, whether the watchdog acted);
- one line after the watchdog and five "else" branches at the AI decisions that wait for a compressor
  timer (winter heat day/night on, winter off, summer cooling on, summer off/standby); night dry is not
  covered (its command is chosen with the Predictive Reserve);
- a summary block before the outputs and a 7th output wired to a new retained "mqtt out" node
  (zara/interface/climate/ac/state/stat, domopi.ac_state.v1).

The air conditioner behaves exactly as before: no decision, timer or IR command changes. Router untouched.

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

root = pathlib.Path(__file__).resolve().parent
AI = '61992c8879c11f66'           # "Machine Learning domotico locale ad alta precisione" (AI climate)
HOME_BROKER = '3ed6302a.5af928'

DECL = '''let causaTransizioneAc = null;
'''
DECL_NEW = DECL + '''// AI-CLIMATE-AC-STATE.1 (2026-10-30): what the AI wanted in this cycle but a compressor timer held
// back, and whether the watchdog acted; read by the summary for the app at the end.
let acRichiestaAi = null;
let acWatchdogCiclo = false;
'''
WATCHDOG = '''    // AI-CLIMATE-AC-OVERRIDE.1 (2026-10-23): manual priority'''
WATCHDOG_NEW = '''    acWatchdogCiclo = String(motivoAc).startsWith("WATCHDOG_");   // AI-CLIMATE-AC-STATE.1

''' + WATCHDOG
# (anchor, text appended right after it): the "else" of each AI decision that waits for a timer
WAITS = [
    ('''comandoIR = "eco19heat";
                    }''', ''' else acRichiestaAi = {mode: "heat", setpoint: 19, guard: "min_off"};'''),
    ('''comandoIR = "eco17heat";
                    }''', ''' else acRichiestaAi = {mode: "heat", setpoint: 17, guard: "min_off"};'''),
    ('''                if (richiediSpegnimentoInverno && minMancantiSpegnimento === 0) {
                    statoAttuale = "OFF"; flow.set("condizionatore_stato", "OFF", "file"); global.set("ultimo_spegnimento", adesso, "file"); comandoIR = "OFF";
                }''', ''' else if (richiediSpegnimentoInverno) acRichiestaAi = {mode: "off", setpoint: null, guard: "min_run"};'''),
    ('''                        motivoAc = "ESECUZIONE_ACCENSIONE_DIURNA";
                    }''', ''' else acRichiestaAi = {mode: "cool", setpoint: (temp >= 25 && temp < 26) ? 24 : 23, guard: "min_off"};'''),
    ('''                    motivoAc = "ESECUZIONE_STANDBY_INVERTER";
                }''', '''
                else if (richiediSpegnimento) acRichiestaAi = {mode: "off", setpoint: null, guard: "min_run"};
                else if (richiediStandby && statoAttuale !== "STANDBY_INVERTER") acRichiestaAi = {mode: "standby", setpoint: 28, guard: "min_run"};'''),
]
OUT_ANCHOR = '''let msgBroadlink = comandoIR ? { payload: comandoIR, topic: "broadlink/cmd" } : null;'''
RETURN = '''    msgPrPrestart
];'''
RETURN_NEW = '''    msgPrPrestart,
    msgAcState
];'''


def nid(name):
    return hashlib.sha256(('AI-CLIMATE-AC-STATE.1/' + name).encode()).hexdigest()[:16]


def patch_func(func):
    assert 'AI-CLIMATE-AC-STATE' not in func and 'AI-CLIMATE-AC-OVERRIDE.1' in func, 'unexpected base'
    for anchor in [DECL, WATCHDOG, OUT_ANCHOR, RETURN] + [a for a, _ in WAITS]:
        assert func.count(anchor) == 1, 'unexpected base: ' + anchor[:60]
    func = func.replace(DECL, DECL_NEW).replace(WATCHDOG, WATCHDOG_NEW)
    for anchor, extra in WAITS:
        func = func.replace(anchor, anchor + extra)
    func = func.replace(OUT_ANCHOR, (root / 'state.js').read_text() + '\n' + OUT_ANCHOR)
    return func.replace(RETURN, RETURN_NEW)


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    ai = nodes[AI]
    assert ai['outputs'] == 6 and len(ai['wires']) == 6, 'unexpected base'
    out_id = nid('out')
    assert out_id not in nodes, 'patch already applied'
    patched = copy.deepcopy(ai)
    patched['func'] = patch_func(ai['func'])
    patched['outputs'] = 7
    patched['wires'] = ai['wires'] + [[out_id]]
    added = [{'id': out_id, 'type': 'mqtt out', 'z': ai['z'], 'name': 'stato condizionatore (app)', 'topic': '',
              'qos': '1', 'retain': 'true', 'respTopic': '', 'contentType': '', 'userProps': '', 'correl': '',
              'expiry': '', 'broker': HOME_BROKER, 'x': ai['x'] + 420, 'y': ai['y'] + 240, 'wires': []}]
    (root / 'update.json').write_text(json.dumps([patched] + added, ensure_ascii=False, indent=2) + '\n')
    (root / 'rollback.json').write_text(json.dumps([ai], ensure_ascii=False, indent=2) + '\n')
    digest = lambda v: hashlib.sha256(json.dumps(v, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'AI-CLIMATE-AC-STATE.1', 'modified_ids': [AI], 'removed_ids': [], 'added_ids': [out_id],
        'node_sha256_before': {AI: digest(ai)}, 'node_sha256_after': {v['id']: digest(v) for v in [patched] + added},
    }, indent=2) + '\n')
    print(f'{len(added)} node added, 1 modified')


if __name__ == '__main__':
    main(sys.argv[1])
