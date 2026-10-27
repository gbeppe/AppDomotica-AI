"""Riassunti per la pagina «La casa d'inverno» (docs/AI_MODE/studio_inverno/index.html).

Ingressi (non nel repository: dati grezzi e personali restano fuori):
  hourly.npz           medie orarie dei feed EmonCMS dal 01/05/2023 (fetch_emon.sh + hourly.py, sessione del 27/10/2026)
  tout_openmeteo.npy   temperatura esterna oraria da Open-Meteo, allineata a hourly.npz
Uscita: data.js (const DATA = {...}) accanto a index.html, solo riassunti (giorno, mese, anno, profilo orario).

Uso: python3 build_data.py <cartella con hourly.npz e tout_openmeteo.npy>
"""
import json, os, sys, time
import numpy as np

os.environ['TZ'] = 'Europe/Rome'
time.tzset()

# --- tarature e prezzi (sezione 7 di AI_CLIMATE_INVERNO.md) ---
BOILER_CAL = 0.66                 # stima da fiamma/modulazione (5-24 kW) contro le bollette del gas
SMC_KWH = 10.7 * 0.95             # kWh di calore per Smc (rendimento caldaia ~0,95)
GAS_EUR_SMC = 1.0
PELLET_KG_H = 825 / 331           # 55 sacchi da 15 kg nell'inverno 2025-26, 331 ore di caminetto
PELLET_EUR_KG = 0.40
PELLET_KWH_KG = 4.8 * 0.88        # kWh di calore per kg (rendimento ~0,88)
ELEC_BUY = 0.24                   # €/kWh variabile dell'energia comprata (bollette Edison)
ELEC_SELL = 0.075                 # €/kWh dello scambio sul posto (~180 € l'anno)
GAS_BILLS = {                     # Smc reali dalle bollette (mesi interi)
    '2024-03': 25, '2024-04': 0, '2024-05': 0, '2024-06': 0, '2024-07': 0, '2024-08': 0, '2024-09': 0,
    '2024-10': 0, '2024-11': 0, '2024-12': 55, '2025-01': 97, '2025-02': 58, '2025-03': 11, '2025-04': 5,
}
GAS_BILLS_WINTER = {'2024-25': 221, '2025-26': 264}


def main(folder):
    d = np.load(os.path.join(folder, 'hourly.npz'))
    t = d['t']
    g = lambda f: d[f'f{f}']
    tout = np.load(os.path.join(folder, 'tout_openmeteo.npy'))
    lt = [time.localtime(x) for x in t]
    hour = np.array([x.tm_hour for x in lt])
    day = np.array([time.strftime('%Y-%m-%d', x) for x in lt])

    flame, mod = g(179), g(181)
    boiler = BOILER_CAL * np.where(np.isfinite(flame), np.clip(flame, 0, 1) * 5.0 + np.nan_to_num(mod) / 100 * 19.0, np.nan)
    fire = np.where(np.isnan(g(147)), np.nan, (g(147) < -115).astype(float))
    fire_kwh = fire * PELLET_KG_H * PELLET_KWH_KG
    ac = np.where(np.isfinite(g(362)), g(362), g(263) / 1000)
    pump = np.where(np.isnan(g(137)), np.nan, (g(137) < -20).astype(float))
    fire_el = np.abs(np.nan_to_num(g(214))) / 1000
    pv, load, grid, soc = g(307) / 1000, g(303) / 1000, g(305) / 1000, g(304)
    liv, bed = g(242), g(189)

    def nsum(a, m):
        v = a[m]
        return None if np.isnan(v).all() else round(float(np.nansum(v)), 2)

    def nmean(a, m):
        v = a[m]
        return None if np.isnan(v).all() else round(float(np.nanmean(v)), 2)

    # --- giorni ---
    days = []
    for dd in sorted(set(day)):
        m = day == dd
        if m.sum() < 20:
            continue
        exp = np.clip(-grid, 0, None)
        imp = np.clip(grid, 0, None)
        row = {
            'd': dd, 'tout': nmean(tout, m), 'liv': nmean(liv, m), 'bed': nmean(bed, m),
            'boiler': nsum(boiler, m), 'fire_h': nsum(fire, m), 'fire': nsum(fire_kwh, m), 'ac': nsum(ac, m),
            'pump_h': nsum(pump, m), 'pv': nsum(pv, m), 'load': nsum(load, m), 'exp': nsum(exp, m),
            'imp': nsum(imp, m), 'soc': nmean(soc, m), 'fire_el': nsum(fire_el, m),
        }
        row['hdd'] = None if row['tout'] is None else round(max(0.0, 18 - row['tout']), 2)
        days.append(row)

    # --- mesi ---
    months = {}
    for r in days:
        k = r['d'][:7]
        mo = months.setdefault(k, {'m': k, 'n': 0})
        mo['n'] += 1
        for f in ('boiler', 'fire', 'fire_h', 'ac', 'pump_h', 'pv', 'load', 'exp', 'imp', 'hdd', 'fire_el'):
            if r.get(f) is not None:
                mo[f] = round(mo.get(f, 0) + r[f], 1)
        for f in ('tout', 'liv'):
            if r.get(f) is not None:
                mo.setdefault('_' + f, []).append(r[f])
    for mo in months.values():
        for f in ('tout', 'liv'):
            vals = mo.pop('_' + f, [])
            mo[f] = round(sum(vals) / len(vals), 1) if vals else None
        mo['gas_smc_bill'] = GAS_BILLS.get(mo['m'])
        mo['gas_smc'] = mo['gas_smc_bill'] if mo['gas_smc_bill'] is not None else round(mo.get('boiler', 0) / SMC_KWH, 1)
        mo['eur_gas'] = round(mo['gas_smc'] * GAS_EUR_SMC, 1)
        mo['eur_pellet'] = round(mo.get('fire_h', 0) * PELLET_KG_H * PELLET_EUR_KG, 1)
        mo['eur_elec_buy'] = round(mo.get('imp', 0) * ELEC_BUY, 1)
        mo['eur_elec_sell'] = round(mo.get('exp', 0) * ELEC_SELL, 1)
        mo['eur_fire_el'] = round(mo.get('fire_el', 0) * ELEC_BUY, 1)

    # --- inverni (nov-mar) e anni (mag-apr) ---
    def period(key_months, label):
        sel = [months[k] for k in key_months if k in months]
        out = {'label': label, 'months': len(sel)}
        for f in ('boiler', 'fire', 'fire_h', 'ac', 'pump_h', 'pv', 'load', 'exp', 'imp', 'hdd', 'gas_smc',
                  'eur_gas', 'eur_pellet', 'eur_elec_buy', 'eur_elec_sell', 'eur_fire_el'):
            out[f] = round(sum(s.get(f, 0) or 0 for s in sel), 1)
        return out

    winters, years = [], []
    for y in (2023, 2024, 2025):
        wk = [f'{y}-11', f'{y}-12'] + [f'{y + 1}-{m:02d}' for m in (1, 2, 3)]
        w = period(wk, f'{y}-{(y + 1) % 100:02d}')
        w['gas_smc_bill'] = GAS_BILLS_WINTER.get(w['label'])
        if w['gas_smc_bill']:
            w['gas_smc'] = w['gas_smc_bill']
            w['eur_gas'] = float(w['gas_smc_bill'])
        w['eur_heat'] = round(w['eur_gas'] + w['eur_pellet'] + w['eur_fire_el'], 1)
        winters.append(w)
        yk = [f'{y}-{m:02d}' for m in range(5, 13)] + [f'{y + 1}-{m:02d}' for m in range(1, 5)]
        years.append(period(yk, f'mag {y} – apr {y + 1}'))

    # --- profilo orario medio per inverno ---
    profiles = {}
    for y in (2023, 2024, 2025):
        wsel = np.array([(x.tm_year == y and x.tm_mon >= 11) or (x.tm_year == y + 1 and x.tm_mon <= 3) for x in lt])
        prof = {}
        for name, arr in (('boiler', boiler), ('fire', fire), ('pump', pump), ('pv', pv), ('exp', np.clip(-grid, 0, None)),
                          ('imp', np.clip(grid, 0, None)), ('ac', ac), ('liv', liv), ('tout', tout), ('soc', soc), ('load', load)):
            prof[name] = [nmean(arr, wsel & (hour == h)) for h in range(24)]
        profiles[f'{y}-{(y + 1) % 100:02d}'] = prof

    data = {
        'generated': time.strftime('%d/%m/%Y'),
        'params': {'boiler_cal': BOILER_CAL, 'smc_kwh': SMC_KWH, 'gas_eur_smc': GAS_EUR_SMC, 'pellet_kg_h': round(PELLET_KG_H, 2),
                   'pellet_eur_kg': PELLET_EUR_KG, 'pellet_kwh_kg': PELLET_KWH_KG, 'elec_buy': ELEC_BUY, 'elec_sell': ELEC_SELL},
        'days': days, 'months': [months[k] for k in sorted(months)], 'winters': winters, 'years': years, 'profiles': profiles,
        'ac_effect': {'hours': 176, 'kwh_el_h': 1.06, 'tout': 13.5, 'boiler_with': 0.10, 'boiler_without': 0.35,
                      'ratio_hour': 0.23, 'days': 26, 'kwh_el_day': 5.0, 'boiler_day_delta': -1.8, 'ratio_day': 0.36,
                      'liv_rise_with': 0.20, 'liv_rise_without': 0.05},
    }
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data.js')
    with open(out, 'w') as f:
        f.write('// Generato da build_data.py: riassunti dei dati EmonCMS, Open-Meteo e bollette. Non modificare a mano.\n')
        f.write('const DATA = ' + json.dumps(data, ensure_ascii=False, separators=(',', ':')) + ';\n')
    print(out, os.path.getsize(out), 'byte;', len(days), 'giorni,', len(months), 'mesi')


if __name__ == '__main__':
    main(sys.argv[1])
