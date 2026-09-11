"""Daily historical reports; timestamps at the API boundary are milliseconds."""
import math
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from evidence import climate_day, day_bounds

FEEDS = {"ac": (353, "Potenza climatizzatore", "W"),
         "soc": (304, "Carica Powerwall", "%"),
         "battery": (306, "Potenza batteria (positiva: scarica)", "W")}


def normalize(raw, start, end, soc=False):
    points = {}
    for row in raw:
        if not isinstance(row, list) or len(row) < 2:
            continue
        t, value = row[:2]
        if type(t) not in (int, float) or not math.isfinite(t):
            continue
        t = int(t if t > 100_000_000_000 else t * 1000)
        if not start <= t < end:
            continue
        value = value if type(value) in (int, float) and math.isfinite(value) else None
        points[t] = value
    result = [[t, points[t]] for t in sorted(points)]
    excluded = []
    if soc:
        original = [p[1] for p in result]
        for i, (t, value) in enumerate(result):
            if value is None:
                continue
            invalid = not 0 <= value <= 100
            if 0 < i < len(result) - 1:
                a, c = original[i-1], original[i+1]
                near = result[i+1][0] - result[i-1][0] <= 60000
                invalid |= (near and a is not None and c is not None
                            and abs(value-a) > 3 and abs(value-c) > 3 and abs(a-c) < 1)
            if invalid:
                result[i][1] = None
                excluded.append({"timestamp_ms": t, "reason": "isolated_or_invalid_soc"})
    return result, excluded


def integrate(points, max_gap_ms=30000):
    watt_ms, covered = 0.0, 0
    for (ta, a), (tb, b) in zip(points, points[1:]):
        delta = tb-ta
        if a is not None and b is not None and a >= 0 and b >= 0 and 0 < delta <= max_gap_ms:
            watt_ms += (a+b)/2 * delta
            covered += delta
    return watt_ms / 3_600_000_000, covered


def report(client, log_path: Path | None, day: str):
    start, end = day_bounds(day)
    def fetch(item):
        key, (feed, label, unit) = item
        raw = client.history(feed, start//1000, end//1000, 30)
        points, excluded = normalize(raw, start, end, key == "soc")
        return key, {"feed_id": feed, "label": label, "unit": unit,
                     "points": points, "excluded": excluded}
    with ThreadPoolExecutor(max_workers=3) as pool:
        series = dict(pool.map(fetch, FEEDS.items()))
    energy, covered = integrate(series["ac"]["points"])
    evidence = climate_day(log_path, day) if log_path else {
        "events": [], "excluded": [], "limitations": ["Log climatico non configurato."]}
    valid_ac = sum(value is not None for _, value in series["ac"]["points"])
    for event in evidence["events"]:
        timestamp = event["timestamp_ms"]
        windows = {}
        for label, lo, hi in [("before", timestamp-120000, timestamp),
                              ("after", timestamp, timestamp+120000)]:
            values = [v for t,v in series["ac"]["points"] if lo <= t < hi and v is not None]
            windows[label] = {"samples": len(values),
                              "mean_w": sum(values)/len(values) if values else None}
        event["ac_observation_2m"] = windows
    return {"schema": "house_ai.daily_report.v1", "day": day,
            "timezone": "Europe/Rome", "period_ms": [start, end],
            "summary": (f"Il climatizzatore ha consumato circa {energy:.2f} kWh negli intervalli misurabili. "
                        f"Copertura temporale: {covered/(end-start):.1%}."
                        if covered else "Dati insufficienti per stimare il consumo."),
            "metrics": {"ac_energy_kwh": energy if covered else None,
                        "coverage_ratio": covered/(end-start),
                        "ac_valid_samples": valid_ac,
                        "expected_samples": (end-start)//30000},
            "series": series, "evidence": evidence,
            "limitations": ["Consumo elettrico AC: include tutte le modalità, non solo raffrescamento.",
                "Energia stimata per integrazione trapezoidale, senza attraversare campioni mancanti.",
                "Gli eventi documentano decisioni; la conferma fisica non è automatica.",
                "Risposta analitica senza modello generativo."] + evidence.get("limitations", [])}
