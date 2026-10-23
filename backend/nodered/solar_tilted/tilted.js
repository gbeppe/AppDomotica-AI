// SOLAR-TILTED.1 (2026-10-23): forecast of the sunlight on the two PV roof faces (phase F3 of
// backend/solar/README.md), next to the clear-sky values of solarEnergy.php.
// msg.topic 'fetch' (hourly): one Open-Meteo request per face (global_tilted_irradiance, W/m²,
// mean of the hour that ENDS at the label, UTC). A response (msg.face) is checked and kept in the
// file context. msg.topic 'tick' (every 300 s): W of light on each face for the current hour to
// EmonCMS (emon/planetary/forecast_smodule, forecast_smodule2, retained like smodule) and a retained
// summary with today's and tomorrow's kWh of light (Europe/Rome days) on the home broker.
// Light on the surface, not electricity: EmonCMS applies the panel efficiency, as for smodule.
// Outputs: [requests for the http node, EmonCMS values, summary].
const FACES = {
    eclipse:   {tilt: 40, area: 19.66, topic: 'emon/planetary/forecast_smodule'},
    viessmann: {tilt: 15, area: 19.52, topic: 'emon/planetary/forecast_smodule2'},
};
const AZIMUTH = -37.5;              // Open-Meteo: 0 = south, negative = east (compass 142.5°)
const URL = 'https://api.open-meteo.com/v1/forecast?latitude=45.95&longitude=12.66'
    + '&hourly=global_tilted_irradiance&timezone=UTC&forecast_days=2';
const MAX_AGE_MS = 6 * 3600000;     // older data are not published
const SUMMARY = 'zara/interface/energy/solar_forecast/stat';
const now = Date.now();
const romeDay = ms => new Date(ms).toLocaleDateString('sv-SE', {timeZone: 'Europe/Rome'});
const round = (v, d) => Math.round(v * 10 ** d) / 10 ** d;

if (msg.topic === 'fetch') {
    return [Object.entries(FACES).map(([face, f]) => ({face, requestTimeout: 20000,
        url: URL + '&tilt=' + f.tilt + '&azimuth=' + AZIMUTH})), null, null];
}

if (msg.face) {
    const h = msg.payload && msg.payload.hourly;
    const ok = msg.statusCode === 200 && FACES[msg.face] && h && Array.isArray(h.time)
        && Array.isArray(h.global_tilted_irradiance) && h.time.length === h.global_tilted_irradiance.length
        && h.time.length >= 24;
    if (!ok) {
        node.warn('Open-Meteo ' + msg.face + ': risposta non valida (HTTP ' + msg.statusCode + '), tengo i dati precedenti');
        return null;
    }
    const hours = {};
    h.time.forEach((t, i) => {
        const v = h.global_tilted_irradiance[i];
        if (typeof v === 'number' && v >= 0 && v < 1500) hours[Date.parse(t + ':00Z')] = v;
    });
    const data = flow.get('solar_tilted', 'file') || {};
    data[msg.face] = {fetched_ms: now, hours};
    flow.set('solar_tilted', data, 'file');
    return null;
}

if (msg.topic !== 'tick') return null;
const data = flow.get('solar_tilted', 'file') || {};
const end = Math.ceil(now / 3600000) * 3600000;       // label of the current hour
const today = romeDay(now), tomorrow = romeDay(now + 86400000);
const values = [], summary = {schema: 'domopi.solar_forecast_tilted.v1', source: 'open-meteo',
    unit: 'luce sul piano dei pannelli (prima dell\'efficienza)', faces: {}};
for (const [face, f] of Object.entries(FACES)) {
    const d = data[face];
    if (!d || now - d.fetched_ms > MAX_AGE_MS || d.hours[end] === undefined) continue;
    const w = round(d.hours[end] * f.area, 1);
    values.push({topic: f.topic, payload: String(w)});
    const kwh = {[today]: 0, [tomorrow]: 0};
    for (const [label, v] of Object.entries(d.hours)) {
        const day = romeDay(Number(label) - 1800000);   // the middle of the hour decides the day
        if (day in kwh) kwh[day] += v * f.area / 1000;
    }
    summary.faces[face] = {tilt: f.tilt, area_m2: f.area, w_now: w, today_kwh: round(kwh[today], 1),
        tomorrow_kwh: round(kwh[tomorrow], 1), fetched_at: new Date(d.fetched_ms).toISOString()};
}
const faces = Object.values(summary.faces);
node.status(faces.length === 2 ? {fill: 'green', shape: 'dot', text: faces.map(x => x.w_now + ' W').join(' / ')}
    : {fill: 'yellow', shape: 'ring', text: 'dati mancanti o vecchi: ' + (2 - faces.length) + ' falde'});
if (!faces.length) return null;
summary.today_kwh = round(faces.reduce((s, x) => s + x.today_kwh, 0), 1);
summary.tomorrow_kwh = round(faces.reduce((s, x) => s + x.tomorrow_kwh, 0), 1);
summary.at = new Date(now).toISOString();
return [null, values, {topic: SUMMARY, payload: JSON.stringify(summary)}];
