"""Generate AI-CLIMATE-VMC-OVERRIDE.1: manual priority of the VMC speed with an end time.

Phase T2 of docs/AI_MODE/COMANDI_A_TEMPO.md. In the "AI climate" tab: a new function
"VMC priorità manuale" (override.js) fed by zara/interface/ventilation/vmc/override/cmd and a 60 s
tick, publishing the speed command (to the router, like the app buttons) and the retained state
zara/interface/ventilation/vmc/override/stat. The AI Climate function gets a short block before its
5-minute anti-bounce: while the priority lasts its VMC speed is the manual one. Nothing else changes
(AC, Predictive Reserve, outputs and wires untouched).

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

root = pathlib.Path(__file__).resolve().parent
AI = '61992c8879c11f66'          # "Machine Learning domotico locale ad alta precisione" (AI climate)
HOME_BROKER = '3ed6302a.5af928'
ANCHOR = "let currentTime = Date.now();\nlet lastVmcChangeTime = flow.get('vmc_ultimo_cambio_ms') || 0;"
BLOCK = """// AI-CLIMATE-VMC-OVERRIDE.1 (2026-10-18): manual priority with an end time (voice timed commands,
// app buttons; node "VMC priorità manuale"). While it lasts the manual speed wins, also over the
// night limit and the anti-condensation levels (user's decision); then AI Climate takes over again.
const vmcOverride = flow.get('vmc_override', 'file');
const vmcOverrideAttivo = !!(vmcOverride && Date.now() < Date.parse(vmcOverride.until));
if (vmcOverrideAttivo) { velocitaVmc = vmcOverride.speed; motivoVmc = "MANUALE_FINO_ALLE_" + vmcOverride.label; }

"""
COOLDOWN = "if (velocitaVmc !== lastVmcSpeed) {\n    if (currentTime - lastVmcChangeTime < cooldownMs) {"
COOLDOWN_NEW = "if (velocitaVmc !== lastVmcSpeed && !vmcOverrideAttivo) {\n    if (currentTime - lastVmcChangeTime < cooldownMs) {"


def nid(name):
    return hashlib.sha256(('AI-CLIMATE-VMC-OVERRIDE.1/' + name).encode()).hexdigest()[:16]


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    ai = nodes[AI]
    func = ai['func']
    assert func.count(ANCHOR) == 1 and func.count(COOLDOWN) == 1 and 'AI-CLIMATE-VMC-OVERRIDE' not in func, 'unexpected base'
    patched = copy.deepcopy(ai)
    patched['func'] = func.replace(ANCHOR, BLOCK + ANCHOR).replace(COOLDOWN, COOLDOWN_NEW)
    tab, x, y = ai['z'], 300, 900
    n = {k: nid(k) for k in ('in', 'tick', 'logic', 'out')}
    assert not set(n.values()) & set(nodes), 'patch already applied'
    added = [
        {'id': n['in'], 'type': 'mqtt in', 'z': tab, 'name': 'VMC priorità manuale (cmd)',
         'topic': 'zara/interface/ventilation/vmc/override/cmd', 'qos': '1', 'datatype': 'utf8', 'broker': HOME_BROKER,
         'nl': False, 'rap': True, 'rh': 0, 'inputs': 0, 'x': x, 'y': y, 'wires': [[n['logic']]]},
        {'id': n['tick'], 'type': 'inject', 'z': tab, 'name': 'ogni 60 s',
         'props': [{'p': 'topic', 'vt': 'str'}, {'p': 'refresh', 'v': 'true', 'vt': 'bool'}],
         'repeat': '60', 'crontab': '', 'once': True, 'onceDelay': '15', 'topic': 'tick', 'x': x - 50, 'y': y + 50,
         'wires': [[n['logic']]]},
        {'id': n['logic'], 'type': 'function', 'z': tab, 'name': 'VMC priorità manuale',
         'func': (root / 'override.js').read_text(), 'outputs': 2, 'timeout': 0, 'noerr': 0, 'initialize': '',
         'finalize': '', 'libs': [], 'x': x + 260, 'y': y, 'wires': [[n['out']], [n['out']]]},
        {'id': n['out'], 'type': 'mqtt out', 'z': tab, 'name': 'velocità e stato priorità VMC', 'topic': '', 'qos': '',
         'retain': '', 'respTopic': '', 'contentType': '', 'userProps': '', 'correl': '', 'expiry': '',
         'broker': HOME_BROKER, 'x': x + 520, 'y': y, 'wires': []},
    ]
    (root / 'update.json').write_text(json.dumps([patched] + added, ensure_ascii=False, indent=2) + '\n')
    (root / 'rollback.json').write_text(json.dumps([ai], ensure_ascii=False, indent=2) + '\n')
    digest = lambda v: hashlib.sha256(json.dumps(v, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'AI-CLIMATE-VMC-OVERRIDE.1', 'modified_ids': [AI], 'removed_ids': [], 'added_ids': [v['id'] for v in added],
        'node_sha256_before': {AI: digest(ai)}, 'node_sha256_after': {v['id']: digest(v) for v in [patched] + added},
    }, indent=2) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
