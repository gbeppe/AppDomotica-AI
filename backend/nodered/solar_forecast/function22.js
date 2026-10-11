// SOLAR-FORECAST.1 (2026-10-11): Open-Meteo forecast for the PV controller.
// Target day in Europe/Rome: before noon the current day (night retries), from noon
// the next day. The inject fires at 23:00 and every 30 min until 05:00; once the
// target day has a fresh forecast (computed in the last 7 h) further runs do nothing.
// Every failure is logged with node.warn; the forecast log only receives real forecasts.
const SUPERFICIE_MQ = 34;
const EFFICIENZA = (0.20 - 0.08);
const LOSS_FACTOR = 0.85;
const FRESH_MS = 7 * 3600 * 1000;

const now = new Date();
const oggi = now.toLocaleDateString("sv-SE", { timeZone: "Europe/Rome" });
const ora = Number(now.toLocaleString("en-GB", { timeZone: "Europe/Rome", hour: "2-digit", hour12: false }));
const domani = new Date(Date.parse(oggi + "T12:00:00Z") + 24 * 3600 * 1000).toISOString().slice(0, 10);
const target = ora < 12 ? oggi : domani;

if (flow.get("previsione_data_domani") === target &&
    now.getTime() - (flow.get("previsione_elaborata_ms") || 0) < FRESH_MS) {
    return null;  // already fresh for this target: nothing to do
}

let data;

// 1. GESTIONE SICURA DEGLI ERRORI API
try {
    data = typeof msg.payload === "string" ? JSON.parse(msg.payload) : msg.payload;
} catch (e) {
    node.error("Open-Meteo API Error: Formato risposta non valido (HTTP " + msg.statusCode +
        "). Applico fallback di sicurezza.", msg);
    flow.set("previsione_kwh_domani", 12); // Valore di sicurezza medio (comportamento invariato)
    return null;
}

// 2. ELABORAZIONE DATI METEO
if (!data || !data.hourly) {
    node.warn("Open-Meteo: risposta senza dati orari (HTTP " + msg.statusCode + ", motivo: " +
        String((data && data.reason) || "non indicato").slice(0, 200) + "). Previsione per " + target +
        " non aggiornata; nuovo tentativo al prossimo giro.");
    return null;
}

let ore = data.hourly.time;
let radiazione = data.hourly.shortwave_radiation || data.hourly.direct_radiation || data.hourly.direct_radiation_instant;

if (!radiazione || !Array.isArray(ore)) {
    node.error("Open-Meteo non ha restituito dati di radiazione validi.");
    return null;
}

let sommaWh_per_mq = 0;
let oreTrovate = 0;

for (let i = 0; i < ore.length; i++) {
    if (ore[i] && ore[i].startsWith(target)) {
        sommaWh_per_mq += radiazione[i] || 0;
        oreTrovate++;
    }
}

let kwh_per_mq = sommaWh_per_mq / 1000;
let kwhStimatiDomani = Math.round(SUPERFICIE_MQ * EFFICIENZA * kwh_per_mq * LOSS_FACTOR);

// Giornata incompleta o anomala: nessuna previsione inventata, si ritenta.
if (oreTrovate < 24 || isNaN(kwhStimatiDomani) || kwhStimatiDomani < 0) {
    node.warn("Open-Meteo: dati incompleti per " + target + " (" + oreTrovate +
        " ore). Previsione non aggiornata; nuovo tentativo al prossimo giro.");
    return null;
}

flow.set("previsione_kwh_domani", kwhStimatiDomani);
flow.set("previsione_data_domani", target);
flow.set("previsione_elaborata_ms", now.getTime());

msg.payload = {
    messaggio: "Previsione elaborata",
    data_previsione: target,
    ore_elaborate: oreTrovate,
    radiazione_totale_kwh_mq: parseFloat(kwh_per_mq.toFixed(2)),
    kwh_stimati_impianto_reali: kwhStimatiDomani,
    elaborata_alle: now.toISOString()
};

msg.topic = "casa/clima/previsione_meteo";
return msg;
