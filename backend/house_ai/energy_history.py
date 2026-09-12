"""Read-only historical metrics; explicit source semantics, no inferred grid sign."""
import math
import time as clock
from datetime import date, datetime, time, timedelta
from zoneinfo import ZoneInfo

ROME = ZoneInfo('Europe/Rome')


def period_bounds(start, end):
    """Local dates, end exclusive; bounded to 366 local calendar days."""
    a, b = date.fromisoformat(start), date.fromisoformat(end)
    if not 0 < (b-a).days <= 366:
        raise ValueError('Period must contain 1 to 366 days')
    return tuple(int(datetime.combine(d, time(), ROME).timestamp() * 1000) for d in (a, b))


def relative_period(name, today):
    """Explicit period names; natural-language ambiguity is left to the caller."""
    if name == 'previous_month':
        end = today.replace(day=1)
        start = (end-timedelta(days=1)).replace(day=1)
    elif name == 'previous_week':
        end = today-timedelta(days=today.weekday())
        start = end-timedelta(days=7)
    elif name in ('last_7_days', 'last_30_days'):
        end = today
        start = end-timedelta(days=7 if name == 'last_7_days' else 30)
    else:
        raise ValueError('Unknown period')
    return start.isoformat(), end.isoformat()


def positive_area(a, b, duration):
    """Integral of positive part of a linear segment, including zero crossing."""
    if a >= 0 and b >= 0:
        return (a+b)*duration/2
    if a <= 0 and b <= 0:
        return 0.0
    return max(a, b)**2 * duration / (2*abs(b-a))


def calculate(rows, start_ms, end_ms, metric, max_gap_ms=30000, import_sign=None):
    power_metrics = {'grid_import_kwh', 'grid_export_kwh',
                     'home_consumption_kwh', 'solar_production_kwh',
                     'battery_charge_kwh', 'battery_discharge_kwh'}
    if metric not in power_metrics | {'soc_mean_percent'}:
        raise ValueError('Unknown metric')
    if end_ms <= start_ms or max_gap_ms <= 0:
        raise ValueError('Invalid bounds')
    if metric in power_metrics and import_sign not in (-1, 1):
        raise ValueError('Verified direction sign required')
    points, excluded = {}, []
    for row in rows:
        if not isinstance(row, (list, tuple)) or len(row) != 2:
            raise ValueError('Invalid history row')
        t, value = row
        if type(t) not in (float, int) or not math.isfinite(t):
            raise ValueError('Invalid timestamp')
        t = int(t if t > 100_000_000_000 else t*1000)
        if not start_ms <= t <= end_ms:
            continue
        valid = type(value) in (float, int) and math.isfinite(value)
        if metric == 'soc_mean_percent' and valid:
            valid = 0 <= value <= 100
        if not valid:
            excluded.append({'timestamp_ms': t, 'reason': 'missing_or_invalid'})
            value = None
        if t in points and points[t] != value:
            value = None
            excluded.append({'timestamp_ms': t, 'reason': 'conflicting_duplicate'})
        points[t] = value
    total, covered = 0.0, 0
    ordered = sorted(points.items())
    for (ta, a), (tb, b) in zip(ordered, ordered[1:]):
        dt = tb-ta
        if a is None or b is None or not 0 < dt <= max_gap_ms:
            continue
        total += (positive_area(a*import_sign, b*import_sign, dt)
                  if metric in power_metrics else (a+b)*dt/2)
        covered += dt
    value = (total/3_600_000_000 if metric in power_metrics else total/covered) if covered else None
    return {'metric': metric, 'value': value,
            'unit': 'kWh' if metric in power_metrics else '%',
            'coverage_ratio': covered/(end_ms-start_ms), 'covered_ms': covered,
            'status': 'complete' if covered == end_ms-start_ms else 'partial' if covered else 'insufficient_data',
            'excluded': excluded}


def energy_report(client, start, end, metric, feed_id, unit, import_sign=None, interval=30, now_ms=None):
    """Caller supplies a verified feed/unit/sign; query without coarse monthly averaging."""
    if (type(feed_id) is not int or feed_id <= 0 or type(interval) is not int
            or not 1 <= interval <= 300):
        raise ValueError('Invalid source configuration')
    expected = {'grid_import_kwh': 'W', 'grid_export_kwh': 'W',
                'home_consumption_kwh': 'W', 'solar_production_kwh': 'W',
                'battery_charge_kwh': 'W', 'battery_discharge_kwh': 'W',
                'soc_mean_percent': '%'}
    if metric not in expected or unit != expected[metric]:
        raise ValueError('Verified source unit required')
    if unit == 'W' and import_sign not in (-1, 1):
        raise ValueError('Verified direction sign required')
    lo, hi = period_bounds(start, end)
    # Bound work independently from HTTP response limits.
    if (hi-lo)//(interval*1000) > 1_100_000:
        raise ValueError('Too many samples requested')
    observed_until = min(hi, now_ms if now_ms is not None else int(clock.time()*1000))
    rows, cursor, requests = [], lo//1000, 0
    while cursor <= observed_until//1000:
        stop = min(cursor+9999*interval, observed_until//1000+interval)
        for row in client.history(feed_id, cursor, stop, interval):
            if (not isinstance(row, (list, tuple)) or len(row) != 2
                    or type(row[0]) not in (int, float) or not math.isfinite(row[0])):
                raise RuntimeError('Invalid historical source row')
            timestamp = row[0] if row[0] > 100_000_000_000 else row[0]*1000
            if timestamp <= observed_until:
                rows.append(row)
        requests += 1
        cursor = stop
    result = calculate(rows, lo, hi, metric, interval*1000, import_sign)
    result.update({'schema': 'house_ai.energy_history.v1', 'timezone': 'Europe/Rome',
                   'period': {'start': start, 'end_exclusive': end}, 'period_ms': [lo, hi], 'observed_until_ms': observed_until,
                   'source': {'feed_id': feed_id, 'unit': unit, 'import_sign': import_sign,
                              'interval_seconds': interval, 'requests': requests},
                   'limitations': ['Stima sugli intervalli coperti, senza colmare dati mancanti.',
                       'Interpolazione lineare fra campioni; variazioni non campionate non ricostruibili.',
                       'Semantica della sorgente fornita dal chiamante; non verificata automaticamente.',
                       'Copertura completa non certifica accuratezza fisica o assenza di aliasing.']})
    return result
