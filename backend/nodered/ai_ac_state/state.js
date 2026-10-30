// AI-CLIMATE-AC-STATE.1 (2026-10-30): summary of the air conditioner for the app (retained
// zara/interface/climate/ac/state/stat, schema domopi.ac_state.v1). It is driven by infrared only and
// has no display: the app shows what it is doing, the power reading that confirms it, the state still
// held back by a compressor timer and the alarms. Codes only, the texts are the app's. Sent when
// something changes; power and room values at most every 30 s. Nothing here changes the decisions.
let msgAcState = null;
{
    const p2 = n => String(Math.floor(Math.abs(n))).padStart(2, "0");
    const iso = ms => {
        if (!ms) return null;
        const d = new Date(ms), o = -d.getTimezoneOffset();
        return d.getFullYear() + "-" + p2(d.getMonth() + 1) + "-" + p2(d.getDate()) + "T" + p2(d.getHours()) + ":" +
            p2(d.getMinutes()) + ":" + p2(d.getSeconds()) + (o >= 0 ? "+" : "-") + p2(o / 60) + ":" + p2(o % 60);
    };
    const modoDi = s => s === "OFF" ? "off" : s === "STANDBY_INVERTER" ? "standby"
        : /CALDO|HEAT/.test(s) ? "heat" : /DEUMIDIFICA|DRY/.test(s) ? "dry" : "cool";
    const avvio = global.get("ultimo_avvio", "file") || 0;
    const fermo = global.get("ultimo_spegnimento", "file") || 0;
    const modo = modoDi(statoAttuale);
    const acceso = modo !== "off";

    // who sent the last IR command and since when the state is this one
    let ultimo = flow.get("ac_state_ultimo_comando", "file") || {code: null, at: 0, by: "ai"};
    if (comandoIR) {
        ultimo = {code: comandoIR, at: adesso, by: acWatchdogCiclo ? "watchdog"
            : (!aiAbilitata || causaTransizioneAc === "MANUALE") ? "user" : "ai"};
        flow.set("ac_state_ultimo_comando", ultimo, "file");
    }
    let da = flow.get("ac_state_da", "file");
    if (!da || da.stato !== statoAttuale) {
        da = {stato: statoAttuale, at: comandoIR ? adesso : (ultimo.at || adesso)};
        flow.set("ac_state_da", da, "file");
    }

    // power reading: standby under 30 W (14 W measured), fan only under 200 W, compressor above
    let conferma;
    if (!acceso) conferma = acPower < 30 ? "off" : "stopping";
    else if (acPower >= 200) conferma = "running";
    else if (acPower < 10 && adesso - avvio > 6 * 60 * 1000) conferma = "no_power";   // as the watchdog
    else conferma = "starting";

    // requested state held back by a compressor timer
    const ov = flow.get("ac_override", "file");
    const ovAttivo = !!(ov && adesso < Date.parse(ov.until));
    const attesa = tipo => tipo === "min_run"
        ? {type: "min_run", until: iso(avvio + MIN_RUN_TIME), minutes: MIN_RUN_MINUTI}
        : {type: "min_off", until: iso(fermo + MIN_OFF_TIME), minutes: MIN_OFF_MINUTI};
    let target = null, guard = null;
    if (ovAttivo && String(motivoAc).startsWith("MANUALE_IN_ATTESA_COMPRESSORE")) {
        target = {mode: ov.mode, setpoint: ov.setpoint == null ? null : ov.setpoint, by: "user", until: ov.until};
        guard = attesa(acceso ? "min_run" : "min_off");
    } else if (String(motivoAc).startsWith("FINE_MANUALE_ATTESA_COMPRESSORE")) {
        target = {mode: "off", setpoint: null, by: "ai", until: null};
        guard = attesa("min_run");
    } else if (acRichiestaAi && aiAbilitata && !ovAttivo) {
        target = {mode: acRichiestaAi.mode, setpoint: acRichiestaAi.setpoint, by: "ai", until: null};
        guard = attesa(acRichiestaAi.guard);
    }

    // alarms
    const alarms = [];
    if (conferma === "no_power") alarms.push("ON_NO_POWER");
    if (acWatchdogCiclo) flow.set("ac_state_watchdog_at", adesso, "file");
    if (adesso - (flow.get("ac_state_watchdog_at", "file") || 0) < 30 * 60 * 1000) alarms.push("WATCHDOG_RESTART");
    const chiusa = flow.get("ac_override_closed", "file");
    if (chiusa && adesso - chiusa.at < 60 * 60 * 1000) alarms.push("BATTERY_CLOSED_MANUAL");
    if (motivoAc === "WATCHDOG_SOSPENSIONE_SISTEMA_GUASTO") flow.set("ac_state_sospeso", true, "file");
    else if (aiAbilitata) flow.set("ac_state_sospeso", false, "file");
    if (flow.get("ac_state_sospeso", "file")) alarms.push("WATCHDOG_SUSPENDED");

    const r1 = n => Math.round(Number(n) * 10) / 10;
    const corpo = {
        schema: "domopi.ac_state.v1",
        ai_enabled: !!aiAbilitata,
        current: {mode: modo, setpoint: acceso && tempImpostata ? tempImpostata : null, code: ultimo.code,
                  since: iso(da.at), by: ultimo.by},
        confirm: {status: conferma, power_w: Math.round(acPower)},
        target, guard, alarms,
        reason: String(motivoAc),
        context: {surplus_w: Math.round(surplus), soc: Math.round(soc), temp: r1(temp), humidex: r1(humidex),
                  vmc: velocitaVmc},
    };
    const chiave = JSON.stringify([corpo.ai_enabled, corpo.current, conferma, target, guard, alarms, corpo.reason]);
    const prima = flow.get("ac_state_pubblicato") || {chiave: null, at: 0, power: 0, ctx: null};
    const ctx = corpo.context;
    const variato = Math.abs(acPower - prima.power) > 50 || !prima.ctx || Math.abs(ctx.surplus_w - prima.ctx.surplus_w) > 100 ||
        ctx.soc !== prima.ctx.soc || ctx.temp !== prima.ctx.temp || ctx.humidex !== prima.ctx.humidex || ctx.vmc !== prima.ctx.vmc;
    if (chiave !== prima.chiave || (variato && adesso - prima.at >= 30 * 1000)) {
        corpo.ts = iso(adesso);
        msgAcState = {topic: "zara/interface/climate/ac/state/stat", payload: JSON.stringify(corpo), retain: true, qos: 1};
        flow.set("ac_state_pubblicato", {chiave, at: adesso, power: acPower, ctx});
    }
}
