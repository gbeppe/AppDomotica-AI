#!/usr/bin/env python3
"""Tool 4 of the winter field test: day by day report and verdict (docs/AI_MODE/AI_CLIMATE_INVERNO.md section 8).

Runs on the .15 and only reads the PHPFina files of EmonCMS, with the journal of WINTER-TEST.1 given as a
JSON file (payload of zara/interface/climate/winter_test/stat on the .20). From the PC:
  ssh domopi-new '. read the retained topic' > stat.json; scp stat.json report.py emonpi-new:/tmp/; python3 report.py stat.json

Boiler heat as in the winter study: flame on = 5 to 24 kW by modulation, x 0.66 (calibrated on the gas bills).
Success: at least 0.77 kWh of boiler heat saved per kWh of the air conditioner on test days.
"""
import json, math, os, struct, sys, time

PHPFINA = '/var/opt/emoncms/phpfina/'
FLAME, MOD, PUMP, LIVING, BEDROOM, BATH, FIRE, SOC, SITE, BATTERY = 179, 181, 137, 242, 189, 388, 214, 304, 305, 306
AC_TOTAL = '/var/opt/emoncms/phptimeseries/feed_354.MYD'   # Shelly emeter_total, Wh (feed 362 does not count: sign)
THRESHOLD = 0.77
BOILER_CAL = 0.66


def load(fid):
    # the feeds are years long: never read them whole (the .15 has 1.8 GB), only seek to each point
    with open(PHPFINA + '%d.meta' % fid, 'rb') as f:
        _, _, interval, start = struct.unpack('<4I', f.read(16))
    return interval, start, open(PHPFINA + '%d.dat' % fid, 'rb')


def value(feed, t):
    interval, start, f = feed
    i = (t - start) // interval
    if i < 0:
        return None
    f.seek(i * 4)
    raw = f.read(4)
    if len(raw) < 4:
        return None
    v = struct.unpack('<f', raw)[0]
    return None if math.isnan(v) else v


def ac_wh(t):
    """Air conditioner counter at the first record from t (PHPTimeSeries: 9 bytes, flag, time, value)."""
    with open(AC_TOTAL, 'rb') as f:
        def rec(i):
            f.seek(i * 9)
            return struct.unpack('<xIf', f.read(9))
        lo, hi = 0, os.path.getsize(AC_TOTAL) // 9 - 1
        while lo < hi:
            mid = (lo + hi) // 2
            if rec(mid)[0] < t:
                lo = mid + 1
            else:
                hi = mid
        return rec(lo)[1]


def series(feed, a, b, step=60):
    return [v for v in (value(feed, t) for t in range(a, b, step)) if v is not None]


def day_row(feeds, day):
    t0 = int(time.mktime(time.strptime(day, '%Y-%m-%d')))
    t1, step = t0 + 86400, 60
    heat = 0.0
    for t in range(t0, t1, step):
        flame, mod = value(feeds[FLAME], t), value(feeds[MOD], t)
        if flame and flame > 0 and mod is not None:
            heat += (5 + 19 * mod / 100.0) * step / 3600.0 * BOILER_CAL
    mean = lambda f, a=t0, b=t1: (lambda x: sum(x) / len(x) if x else None)(series(feeds[f], a, b))
    hours = lambda f, w: sum(1 for v in series(feeds[f], t0, t1) if abs(v) > w) / 60.0
    w0, w1 = t0 + 10 * 3600 + 1800, t0 + 15 * 3600
    drawn = sum(max(value(feeds[SITE], t) or 0, 0) + max(value(feeds[BATTERY], t) or 0, 0) for t in range(w0, w1, 60)) / 60000.0
    return {'day': day, 'boiler_kwh': round(heat, 2), 'pump_h': round(hours(PUMP, 5), 2), 'fire_h': round(hours(FIRE, 50), 2),
            'ac_kwh': round((ac_wh(t1) - ac_wh(t0)) / 1000.0, 2),
            'living': round(mean(LIVING) or 0, 1), 'living_window': round(mean(LIVING, w0, w1) or 0, 1),
            'bedroom': round(mean(BEDROOM) or 0, 1), 'bath': round(mean(BATH) or 0, 1),
            'drawn_window_kwh': round(drawn, 2), 'soc_end': value(feeds[SOC], t1 - 60)}


def main():
    stat = json.load(open(sys.argv[1]))
    feeds = {f: load(f) for f in (FLAME, MOD, PUMP, LIVING, BEDROOM, BATH, FIRE, SOC, SITE, BATTERY)}
    rows = {'prova': [], 'confronto': []}
    keys = ('day', 'boiler_kwh', 'pump_h', 'fire_h', 'ac_kwh', 'living', 'living_window', 'bedroom', 'bath', 'drawn_window_kwh', 'soc_end')
    print('tipo       ' + ' '.join(k[:10].rjust(10) for k in keys))
    for e in stat.get('journal', []):
        if e.get('type') not in ('prova', 'confronto', 'collaudo'):
            print('%-10s %s %s' % (e.get('type'), e.get('day'), e.get('reason', '')))
            continue
        r = day_row(feeds, e['day'])
        print('%-10s ' % e['type'] + ' '.join(str(r[k])[:10].rjust(10) for k in keys) + ('  ' + e['result'] if e.get('result') not in (None, 'fine') else ''))
        if e['type'] in rows and r['fire_h'] < 0.5 and e.get('result') in (None, 'fine'):
            rows[e['type']].append(r)
    print('validi: %d prova, %d confronto (giorni con caminetto o prova interrotta esclusi)' % (len(rows['prova']), len(rows['confronto'])))
    if not rows['prova'] or not rows['confronto']:
        print('Servono giorni validi di entrambi i tipi: nessun verdetto.')
        return
    avg = lambda t, k: sum(r[k] or 0 for r in rows[t]) / len(rows[t])
    saved, ac = avg('confronto', 'boiler_kwh') - avg('prova', 'boiler_kwh'), avg('prova', 'ac_kwh') - avg('confronto', 'ac_kwh')
    print('Caldaia: prova %.1f, confronto %.1f kWh di calore al giorno; condizionatore in più %.1f kWh' % (avg('prova', 'boiler_kwh'), avg('confronto', 'boiler_kwh'), ac))
    if ac <= 0:
        print('Condizionatore non più usato nei giorni di prova: nessun verdetto.')
        return
    ratio = saved / ac
    print('Risparmio: %.2f kWh di calore per kWh elettrico (soglia %.2f): %s' % (ratio, THRESHOLD, 'CONVIENE' if ratio >= THRESHOLD else 'NON CONVIENE'))
    if len(rows['prova']) < 5 or len(rows['confronto']) < 5:
        print('Attenzione: meno di 5 giorni per tipo, risultato provvisorio.')


if __name__ == '__main__':
    main()
