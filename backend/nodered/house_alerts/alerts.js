// HOUSE-ALERTS.1 (2026-10-17): house alerts on the phone through ntfy, channel domopi-casa.
// Inputs: MQTT messages (topic + payload) and a tick every 60 s (msg.topic === 'tick').
// Output: one message per notification, {payload: {key, title, message, tags, priority}}.
// Each alert is sent on the transition, once; a repeat of the same alert waits at least 2 hours;
// the "back to normal" message is sent only when the alert had been sent.
const MIN = 60000, REPEAT_MS = 120 * MIN, OFFLINE_MS = 5 * MIN;
const PUFFER = {high: 85, back: 80}, BATTERY = {low: 20, back: 25};
const STALE = {   // source topic on the emon broker -> [key, name, limit, consequence]
    'emon/emonth5/temperature_calibrated': ['stale_living', 'sensore di temperatura del soggiorno', 30 * MIN, ''],
    'emon/bagnoServizio/temperature': ['stale_bath', 'sensore di temperatura del bagno', 30 * MIN, ''],
    'emon/weather/extTemp': ['stale_outdoor', 'temperatura esterna', 120 * MIN, ' Il profilo automatico non si aggiorna.'],
};
const BOARDS = {'/esp8266/palazzetti/online': ['board_fireplace', 'del caminetto'],
                '/esp8266/floorPump/online': ['board_floorpump', 'della pompa pavimento']};
const PROFILES = {spento: 'Spento', ridotto: 'Ridotto', pieno: 'Pieno'};

const now = msg.now || Date.now();
const s = context.get('s', 'file') || {start: now, last: {}, active: {}, sent: {}, offlineSince: {}, profile: null};
const out = [];
const deg = v => String(Math.round(v * 10) / 10).replace('.', ',');
const raise = (key, title, message, tags, priority) => {
    if (s.active[key]) return;
    s.active[key] = true;
    if (s.sent[key] && now - s.sent[key] < REPEAT_MS) { s.active[key] = 'quiet'; return; }
    s.sent[key] = now;
    out.push({payload: {key, title, message, tags, priority}});
};
const clear = (key, title, message) => {
    const was = s.active[key];
    delete s.active[key];
    if (was === true) out.push({payload: {key: key + '_ok', title, message, tags: ['white_check_mark'], priority: 3}});
};

const t = msg.topic, p = String(msg.payload).trim();
if (t === 'tick') {
    for (const [topic, [key, name, limit, consequence]] of Object.entries(STALE)) {
        const last = s.last[topic] || s.start;
        if (now - last > limit) {
            const min = Math.round((now - last) / MIN);
            const ago = min >= 120 ? Math.round(min / 60) + ' ore' : min + ' minuti';
            raise(key, 'Dati mancanti', `Nessun valore (${name}) da ${ago}.${consequence}`, ['warning'], 3);
        }
    }
    for (const [topic, [key, name]] of Object.entries(BOARDS)) {
        const since = s.offlineSince[topic];
        if (since && now - since >= OFFLINE_MS)
            raise(key, 'Scheda scollegata', `La scheda ${name} è scollegata da ${Math.round((now - since) / MIN)} minuti.`, ['electric_plug'], 4);
    }
} else if (STALE[t]) {
    s.last[t] = now;
    const [key, name] = STALE[t];
    clear(key, 'Dati di nuovo presenti', `${name.charAt(0).toUpperCase() + name.slice(1)}: valori di nuovo presenti.`);
} else if (BOARDS[t]) {
    const [key, name] = BOARDS[t];
    if (p === 'offline') s.offlineSince[t] = s.offlineSince[t] || now;
    else if (p === 'online') { delete s.offlineSince[t]; clear(key, 'Scheda collegata', `La scheda ${name} è di nuovo collegata.`); }
} else if (t === 'zara/interface/heating/puffer/top_temperature/stat') {
    const v = parseFloat(p);
    if (Number.isFinite(v) && v >= 5 && v <= 110) {
        if (v > PUFFER.high) raise('puffer_hot', `Puffer alto a ${deg(v)} °C`, `Sopra ${PUFFER.high} °C: controlla caminetto e pompe.`, ['fire', 'warning'], 4);
        else if (v < PUFFER.back) clear('puffer_hot', 'Puffer rientrato', `Puffer alto tornato a ${deg(v)} °C.`);
    }
} else if (t === 'zara/interface/energy/battery/soc/stat') {
    const v = parseFloat(p);
    if (Number.isFinite(v) && v >= 0 && v <= 100) {
        if (v < BATTERY.low) raise('battery_low', `Batteria al ${Math.round(v)} %`, `Sotto il ${BATTERY.low} %.`, ['battery'], 3);
        else if (v > BATTERY.back) clear('battery_low', 'Batteria risalita', `Batteria al ${Math.round(v)} %.`);
    }
} else if (t === 'zara/interface/heating/profile/stat') {
    let o; try { o = JSON.parse(p); } catch (e) { o = null; }
    if (o && PROFILES[o.profile]) {
        if (s.profile && s.profile !== o.profile) {
            const how = o.mode === 'auto'
                ? `L'automatico è passato da ${PROFILES[s.profile]} a ${PROFILES[o.profile]}` +
                  (Number.isFinite(o.outdoor_mean_72h) ? ` (media esterna 72 ore ${deg(o.outdoor_mean_72h)} °C)` : '') + '.'
                : `Profilo cambiato da ${PROFILES[s.profile]} a ${PROFILES[o.profile]}.`;
            out.push({payload: {key: 'profile', title: `Profilo di riscaldamento: ${PROFILES[o.profile]}`,
                message: how + (o.timer_mode === 'active' ? '' : ' Il timer è in prova: i termostati non cambiano.'),
                tags: ['thermometer'], priority: 3}});
        }
        s.profile = o.profile;
    }
}
context.set('s', s, 'file');   // survives restarts: no repeated alerts, staleness from the real last value
return [out];
