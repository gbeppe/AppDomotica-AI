"""Generate AI-CLIMATE-AC-OVERRIDE.2: tools 1 and 2 of the winter field test (AI_CLIMATE_INVERNO.md section 8).

User's decisions of 31/10/2026:
1. the manual priority of the air conditioner works also with AI Climate disabled; the switch stops
   only the automatic logic, a priority goes on until its end (buttons Spegni/Auto end it earlier). The
   ventilation stays at the minimum while AI Climate is disabled.
2. optional "solar_only": true in the command: after 15 minutes in a row with more than 100 W from the
   battery or the grid the priority is closed, the compressor timers stand and the phone is warned.

The state summary of AI-CLIMATE-AC-STATE.1 gets the alarm SOLAR_ONLY_CLOSED. Router untouched.

Usage: python3 build.py <flows-export.json>
"""
import copy, hashlib, json, pathlib, sys

root = pathlib.Path(__file__).resolve().parent
AI = '61992c8879c11f66'
OVERRIDE = 'badc2655c944f8b1'
PAIRS = [
    ('''if (!aiAbilitata) {
    if (!systemWasDisabled) {''', '''// AI-CLIMATE-AC-OVERRIDE.2 (2026-10-31): a manual priority works also with AI Climate disabled (user's
// decision): the second branch runs for its watchdog and manual blocks, the AI logic is skipped there.
const acManualeInizio = flow.get('ac_override', 'file');
const acManualeVale = !!(acManualeInizio && Date.now() < Date.parse(acManualeInizio.until))
    || String(statoAttuale).startsWith("MANUALE_");

if (!aiAbilitata && !acManualeVale) {
    if (!systemWasDisabled) {'''),
    ('''    if (systemWasDisabled) {
        flow.set("system_was_disabled", false);''', '''    if (systemWasDisabled && aiAbilitata) {
        flow.set("system_was_disabled", false);'''),
    ('''    if (acOverrideAttivo && !bypassRoutine) {
        if (bloccoSpegnimentoEmergenza && acOverride.mode !== "off") {''',
     '''    // AI-CLIMATE-AC-OVERRIDE.2: a "solar only" priority ends after 15 minutes in a row with more than
    // 100 W from the battery or the grid; block A then switches off with the compressor timers.
    let acSoloSoleChiude = false;
    if (acOverrideAttivo && !bypassRoutine && acOverride.solar_only && acOverride.mode !== "off") {
        if (batteryDischargeW > 100 || gridImportW > 100) {
            const acPrelievoDa = flow.get("ac_solo_sole_da") || 0;
            if (!acPrelievoDa) flow.set("ac_solo_sole_da", adesso);
            else if (adesso - acPrelievoDa >= 15 * 60 * 1000) acSoloSoleChiude = true;
        } else {
            flow.set("ac_solo_sole_da", 0);
        }
    }
    if (acSoloSoleChiude) {
        flow.set("ac_solo_sole_da", 0);
        flow.set("ac_override", null, "file");
        flow.set("ac_override_closed", {reason: "SOLO_SOLE_PRELIEVO_15_MIN", mode: acOverride.mode, at: adesso, notified: false}, "file");
        statoAttuale = acStatoInizio;
        comandoIR = null;
        msgPrPrestart = null;
        global.set("ultimo_avvio", acUltimoAvvioInizio, "file");
        global.set("ultimo_spegnimento", acUltimoSpegnimentoInizio, "file");
        flow.set("condizionatore_stato", statoAttuale, "file");
        motivoAc = "SOLO_SOLE_CHIUDE_MANUALE";
    } else if (acOverrideAttivo && !bypassRoutine) {
        if (bloccoSpegnimentoEmergenza && acOverride.mode !== "off") {'''),
    ('''    if (esNotteFascia && velocitaVmc > VMC_MAX_NOTTE) { velocitaVmc = VMC_MAX_NOTTE; motivoVmc = motivoVmc + "_LIMITATO_NOTTE"; }
}
''', '''    if (esNotteFascia && velocitaVmc > VMC_MAX_NOTTE) { velocitaVmc = VMC_MAX_NOTTE; motivoVmc = motivoVmc + "_LIMITATO_NOTTE"; }
    if (!aiAbilitata) { velocitaVmc = 1; motivoVmc = "SISTEMA_DISABILITATO"; }   // manual AC with AI Climate off
}
'''),
    ('''if (!aiAbilitata) {
    flow.set("ac_log_temp", 0, "file");''', '''if (!aiAbilitata && !acManualeVale) {
    flow.set("ac_log_temp", 0, "file");'''),
    ('''    if (chiusa && adesso - chiusa.at < 60 * 60 * 1000) alarms.push("BATTERY_CLOSED_MANUAL");''',
     '''    if (chiusa && adesso - chiusa.at < 60 * 60 * 1000) {
        alarms.push(String(chiusa.reason).startsWith("SOLO_SOLE") ? "SOLAR_ONLY_CLOSED" : "BATTERY_CLOSED_MANUAL");
    }'''),
]


def main(export_path):
    data = json.loads(pathlib.Path(export_path).read_text())
    nodes = {n['id']: n for n in (data['flows'] if isinstance(data, dict) else data)}
    ai, ov = nodes[AI], nodes[OVERRIDE]
    assert 'AI-CLIMATE-AC-STATE.1' in ai['func'] and 'OVERRIDE.2' not in ai['func'], 'unexpected base'
    assert ov['func'] == (root.parent / 'ai_ac_override' / 'override.js').read_text(), 'unexpected base'
    func = ai['func']
    for a, b in PAIRS:
        assert func.count(a) == 1, 'unexpected base: ' + a[:60]
        func = func.replace(a, b)
    new_ai, new_ov = copy.deepcopy(ai), copy.deepcopy(ov)
    new_ai['func'] = func
    new_ov['func'] = (root / 'override.js').read_text()
    (root / 'update.json').write_text(json.dumps([new_ai, new_ov], ensure_ascii=False, indent=2) + '\n')
    (root / 'rollback.json').write_text(json.dumps([ai, ov], ensure_ascii=False, indent=2) + '\n')
    digest = lambda v: hashlib.sha256(json.dumps(v, sort_keys=True).encode()).hexdigest()
    (root / 'manifest.json').write_text(json.dumps({
        'patch': 'AI-CLIMATE-AC-OVERRIDE.2', 'modified_ids': [AI, OVERRIDE], 'removed_ids': [], 'added_ids': [],
        'node_sha256_before': {v['id']: digest(v) for v in (ai, ov)},
        'node_sha256_after': {v['id']: digest(v) for v in (new_ai, new_ov)},
    }, indent=2) + '\n')
    print('2 nodes modified')


if __name__ == '__main__':
    main(sys.argv[1])
