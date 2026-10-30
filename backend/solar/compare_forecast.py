#!/usr/bin/env python3
"""Forecast vs real PV production for one day, per face (SOLAR-TILTED.1 feeds).

Runs on the .15 and reads the EmonCMS PHPFina files directly (read only, no API key):
  forecast light   449 Eclipse, 451 Viessmann (W on the panel plane, Open-Meteo)
  forecast power   450 Eclipse (x0.15), 452 Viessmann (x0.205)
  clear-sky power  146 Eclipse, 302 Viessmann (solarEnergy.php)
  real power       426 Huawei inverter = Eclipse face; 307 Tesla solar = total, so Tesla - Huawei = Viessmann
Prints hourly means (W), the day's kWh, the forecast error and the effective efficiency
(real power / forecast light) to compare with the EmonCMS coefficients.
Python 3.7 compatible (the .15 runs Raspbian 10), standard library only.

Usage: ssh pi@192.168.1.15 'python3 - [YYYY-MM-DD]' < compare_forecast.py   (default: today)
"""
import math
import struct
import sys
import time

PHPFINA = '/var/opt/emoncms/phpfina/'
FEEDS = (449, 450, 451, 452, 146, 302, 307, 426)
COEFF = {'Eclipse': 0.12, 'Viessmann': 0.195}   # forecast inputs 1527/1528 since 30/10/2026


def load(fid):
    with open(PHPFINA + '%d.meta' % fid, 'rb') as f:
        _, _, interval, start = struct.unpack('<4I', f.read(16))
    with open(PHPFINA + '%d.dat' % fid, 'rb') as f:
        raw = f.read()
    n = len(raw) // 4
    return interval, start, struct.unpack('<%df' % n, raw[:n * 4])


def mean(feed, a, b):
    interval, start, values = feed
    xs = []
    for t in range(a - a % interval, b, interval):
        i = (t - start) // interval
        if 0 <= i < len(values) and not math.isnan(values[i]):
            xs.append(values[i])
    return sum(xs) / len(xs) if xs else None


def pct(real, forecast):
    return '%+.0f %%' % (100.0 * (real - forecast) / forecast) if forecast else '-'


def main():
    day = sys.argv[1] if len(sys.argv) > 1 else time.strftime('%Y-%m-%d')
    t0 = int(time.mktime(time.strptime(day, '%Y-%m-%d')))
    now = int(time.time())
    feeds = {fid: load(fid) for fid in FEEDS}
    cols = ('Ecl prev', 'Ecl real', 'Vie prev', 'Vie real', 'Tot prev', 'Tot real', 'Sereno')
    kwh = dict.fromkeys(cols + ('Ecl luce', 'Vie luce'), 0.0)
    print('Previsione / reale del %s (W medi; Ecl real = Huawei 426, Vie real = Tesla 307 - Huawei)' % day)
    print('ora  ' + ''.join('%10s' % c for c in cols))
    for h in range(5, 21):
        a = t0 + h * 3600
        b = min(a + 3600, now)
        if a >= b:
            break
        m = {fid: mean(feeds[fid], a, b) for fid in FEEDS}
        if m[307] is None or m[426] is None or m[450] is None or m[452] is None:
            print('%02d   dati mancanti' % h)
            continue
        row = {
            'Ecl prev': m[450], 'Ecl real': m[426],
            'Vie prev': m[452], 'Vie real': max(m[307] - m[426], 0.0),
            'Tot prev': m[450] + m[452], 'Tot real': m[307],
            'Sereno': (m[146] or 0.0) + (m[302] or 0.0),
            'Ecl luce': m[449] or 0.0, 'Vie luce': m[451] or 0.0,
        }
        for k in kwh:
            kwh[k] += row[k] * (b - a) / 3600.0 / 1000.0
        print('%02d   ' % h + ''.join('%10.0f' % row[c] for c in cols))
    print('kWh  ' + ''.join('%10.2f' % kwh[c] for c in cols))
    print()
    for face, short in (('Eclipse', 'Ecl'), ('Viessmann', 'Vie')):
        light = kwh[short + ' luce']
        eff = kwh[short + ' real'] / light if light else 0.0
        print('%-9s reale %.2f kWh, previsto %.2f (%s); efficienza effettiva %.3f (coefficiente %.3f)'
              % (face, kwh[short + ' real'], kwh[short + ' prev'],
                 pct(kwh[short + ' real'], kwh[short + ' prev']), eff, COEFF[face]))
    print('Totale    reale %.2f kWh, previsto %.2f (%s); a cielo sereno %.2f'
          % (kwh['Tot real'], kwh['Tot prev'], pct(kwh['Tot real'], kwh['Tot prev']), kwh['Sereno']))


if __name__ == '__main__':
    main()
