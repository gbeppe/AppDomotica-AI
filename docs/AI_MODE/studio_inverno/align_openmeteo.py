"""Allinea l'archivio orario di Open-Meteo (openmeteo.json) alle ore di hourly.npz -> tout_openmeteo.npy.
Uso: python3 align_openmeteo.py <cartella con hourly.npz e openmeteo.json>"""
import json, os, sys, time
import numpy as np

os.environ['TZ'] = 'Europe/Rome'
time.tzset()
folder = sys.argv[1]
t = np.load(os.path.join(folder, 'hourly.npz'))['t']
om = json.load(open(os.path.join(folder, 'openmeteo.json')))['hourly']
idx = {int(time.mktime(time.strptime(x, '%Y-%m-%dT%H:%M'))): v for x, v in zip(om['time'], om['temperature_2m'])}
out = np.array([idx.get(int(x)) if idx.get(int(x)) is not None else np.nan for x in t], dtype=float)
np.save(os.path.join(folder, 'tout_openmeteo.npy'), out)
print('ore allineate', np.isfinite(out).sum(), 'su', out.size)
