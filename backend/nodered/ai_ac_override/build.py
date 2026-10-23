"""Generate AI-CLIMATE-AC-OVERRIDE.1: manual priority of the air conditioner with an end time.

Phase T4 of docs/AI_MODE/COMANDI_A_TEMPO.md (user's decisions of 23/10/2026: off and on in heat,
cool or dry; a battery protection wins and closes the priority; compressor timers respected; at most
6 hours). In the "AI climate" tab: a new function "Condizionatore priorità manuale" (override.js) fed
by zara/interface/climate/ac/override/cmd and a 60 s tick, publishing the retained state
zara/interface/climate/ac/override/stat, and a link to the ntfy chain of HOUSE-ALERTS.1 (a new link-in
node in its tab, wired to the existing "notifica ntfy"). The AI Climate function gets two blocks:

A. before its AC decision: remember the state and the start/stop times of the cycle; after the end of
   a manual priority close the manual state (off, after the 15 minute minimum run) before the AI
   logic, which does not know the manual states;
B. after its AC decision: while a priority lasts, restore what the AI decided in this cycle and apply
   the manual state and IR code with the compressor timers; a battery protection (summer peak shaving
   or night reserve) closes the priority instead and the AI decision stands.

VMC, Predictive Reserve logic, outputs and wires of the AI Climate function are untouched; the PR
pre-start request is not sent while a priority lasts. Router untouched.

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

root = pathlib.Path(__file__).resolve().parent
AI = '61992c8879c11f66'           # "Machine Learning domotico locale ad alta precisione" (AI climate)
HOME_BROKER = '3ed6302a.5af928'
ALERTS_TAB = '835191316c016bc4'   # "Avvisi (DomoPi)", HOUSE-ALERTS.1
NOTIFY = 'a880a8a1ebba64c6'       # its "notifica ntfy" function
ANCHOR_A = '    if (!bypassRoutine && aiAbilitata) {\n        if (MODALITA_STAGIONE === "INVERNO") {'
ANCHOR_B = '    // ==================================================\n    // B.3 LOGICA VMC '
BLOCK_A = '''    // AI-CLIMATE-AC-OVERRIDE.1 (2026-10-23): manual priority of the air conditioner (node
    // "Condizionatore priorità manuale"). The state and start/stop times of this cycle are kept to be
    // restored below while a priority lasts. After its end a manual state is closed here, before the
    // AI logic that does not know it: off once the compressor has run 15 minutes, then AI decides.
    const acOverride = flow.get('ac_override', 'file');
    const acOverrideAttivo = !!(acOverride && Date.now() < Date.parse(acOverride.until));
    const acStatoInizio = statoAttuale;
    const acUltimoAvvioInizio = global.get("ultimo_avvio", "file");
    const acUltimoSpegnimentoInizio = global.get("ultimo_spegnimento", "file");
    if (!acOverrideAttivo && String(statoAttuale).startsWith("MANUALE_")) {
        bypassRoutine = true;
        if (minMancantiSpegnimento === 0) {
            statoAttuale = "OFF";
            flow.set("condizionatore_stato", "OFF", "file");
            global.set("ultimo_spegnimento", adesso, "file");
            comandoIR = "OFF";
            causaTransizioneAc = "FINE_MANUALE";
            motivoAc = "FINE_MANUALE_SPEGNIMENTO";
        } else {
            motivoAc = "FINE_MANUALE_ATTESA_COMPRESSORE_" + minMancantiSpegnimento + "_MIN";
        }
    }

'''
BLOCK_B = '''    // AI-CLIMATE-AC-OVERRIDE.1: while a manual priority lasts its state wins over this cycle's AI
    // decision (restored first), with the compressor timers (user's decision 3a). A battery protection
    // closes it instead (decision 2a): the AI decision stands and the phone is warned.
    if (acOverrideAttivo && !bypassRoutine) {
        if (bloccoSpegnimentoEmergenza && acOverride.mode !== "off") {
            flow.set("ac_override", null, "file");
            flow.set("ac_override_closed", {reason: motivoAc, mode: acOverride.mode, at: adesso, notified: false}, "file");
            motivoAc = "PROTEZIONE_BATTERIA_CHIUDE_MANUALE (" + motivoAc + ")";
        } else {
            statoAttuale = acStatoInizio;
            comandoIR = null;
            msgPrPrestart = null;
            global.set("ultimo_avvio", acUltimoAvvioInizio, "file");
            global.set("ultimo_spegnimento", acUltimoSpegnimentoInizio, "file");
            flow.set("condizionatore_stato", statoAttuale, "file");
            const acObiettivo = acOverride.mode === "off" ? "OFF"
                : "MANUALE_" + ({heat: "CALDO", cool: "RAFFRESCAMENTO", dry: "DEUMIDIFICA"})[acOverride.mode];
            const acAcceso = statoAttuale !== "OFF";
            const acFino = "_FINO_ALLE_" + acOverride.label;
            if (acObiettivo === "OFF") {
                if (!acAcceso) {
                    motivoAc = "MANUALE_SPENTO" + acFino;
                } else if (minMancantiSpegnimento > 0) {
                    motivoAc = "MANUALE_IN_ATTESA_COMPRESSORE_" + minMancantiSpegnimento + "_MIN";
                } else {
                    statoAttuale = "OFF";
                    flow.set("condizionatore_stato", "OFF", "file");
                    global.set("ultimo_spegnimento", adesso, "file");
                    comandoIR = "OFF";
                    causaTransizioneAc = "MANUALE";
                    motivoAc = "MANUALE_SPENTO" + acFino;
                }
            } else if (statoAttuale === acObiettivo && flow.get("ac_override_inviato", "file") === acOverride.code) {
                motivoAc = acObiettivo + acFino;
            } else if (!acAcceso && minMancantiAccensione > 0) {
                motivoAc = "MANUALE_IN_ATTESA_COMPRESSORE_" + minMancantiAccensione + "_MIN";
            } else {
                if (!acAcceso) global.set("ultimo_avvio", adesso, "file");
                statoAttuale = acObiettivo;
                flow.set("condizionatore_stato", acObiettivo, "file");
                comandoIR = acOverride.code;
                flow.set("ac_override_inviato", acOverride.code, "file");
                causaTransizioneAc = "MANUALE";
                motivoAc = acObiettivo + acFino;
            }
        }
    }

'''


def nid(name):
    return hashlib.sha256(('AI-CLIMATE-AC-OVERRIDE.1/' + name).encode()).hexdigest()[:16]


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    ai = nodes[AI]
    func = ai['func']
    assert func.count(ANCHOR_A) == 1 and func.count(ANCHOR_B) == 1, 'unexpected base'
    assert 'AI-CLIMATE-AC-OVERRIDE' not in func and 'AI-CLIMATE-VMC-OVERRIDE.1' in func, 'unexpected base'
    assert nodes[NOTIFY]['name'] == 'notifica ntfy' and nodes[NOTIFY]['z'] == ALERTS_TAB
    patched = copy.deepcopy(ai)
    patched['func'] = func.replace(ANCHOR_A, BLOCK_A + ANCHOR_A).replace(ANCHOR_B, BLOCK_B + ANCHOR_B)
    tab, x, y = ai['z'], 300, 1040
    n = {k: nid(k) for k in ('in', 'tick', 'logic', 'out', 'alert_out', 'alert_in')}
    assert not set(n.values()) & set(nodes), 'patch already applied'
    added = [
        {'id': n['in'], 'type': 'mqtt in', 'z': tab, 'name': 'Condizionatore priorità manuale (cmd)',
         'topic': 'zara/interface/climate/ac/override/cmd', 'qos': '1', 'datatype': 'utf8', 'broker': HOME_BROKER,
         'nl': False, 'rap': True, 'rh': 0, 'inputs': 0, 'x': x, 'y': y, 'wires': [[n['logic']]]},
        {'id': n['tick'], 'type': 'inject', 'z': tab, 'name': 'ogni 60 s',
         'props': [{'p': 'topic', 'vt': 'str'}, {'p': 'refresh', 'v': 'true', 'vt': 'bool'}],
         'repeat': '60', 'crontab': '', 'once': True, 'onceDelay': '20', 'topic': 'tick', 'x': x - 50, 'y': y + 50,
         'wires': [[n['logic']]]},
        {'id': n['logic'], 'type': 'function', 'z': tab, 'name': 'Condizionatore priorità manuale',
         'func': (root / 'override.js').read_text(), 'outputs': 2, 'timeout': 0, 'noerr': 0, 'initialize': '',
         'finalize': '', 'libs': [], 'x': x + 280, 'y': y, 'wires': [[n['out']], [n['alert_out']]]},
        {'id': n['out'], 'type': 'mqtt out', 'z': tab, 'name': 'stato priorità condizionatore', 'topic': '', 'qos': '1',
         'retain': '', 'respTopic': '', 'contentType': '', 'userProps': '', 'correl': '', 'expiry': '',
         'broker': HOME_BROKER, 'x': x + 560, 'y': y, 'wires': []},
        {'id': n['alert_out'], 'type': 'link out', 'z': tab, 'name': '→ avviso ntfy', 'mode': 'link',
         'links': [n['alert_in']], 'x': x + 520, 'y': y + 50},
        {'id': n['alert_in'], 'type': 'link in', 'z': ALERTS_TAB, 'name': 'avvisi da altri tab (AI Climate)',
         'links': [n['alert_out']], 'x': 140, 'y': 620, 'wires': [[NOTIFY]]},
    ]
    (root / 'update.json').write_text(json.dumps([patched] + added, ensure_ascii=False, indent=2) + '\n')
    (root / 'rollback.json').write_text(json.dumps([ai], ensure_ascii=False, indent=2) + '\n')
    digest = lambda v: hashlib.sha256(json.dumps(v, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'AI-CLIMATE-AC-OVERRIDE.1', 'modified_ids': [AI], 'removed_ids': [], 'added_ids': [v['id'] for v in added],
        'node_sha256_before': {AI: digest(ai)}, 'node_sha256_after': {v['id']: digest(v) for v in [patched] + added},
    }, indent=2) + '\n')
    print(f'{len(added)} nodes added, 1 modified')


if __name__ == '__main__':
    main(sys.argv[1])
