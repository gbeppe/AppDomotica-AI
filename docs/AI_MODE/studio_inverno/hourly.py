"""Medie orarie dei feed PHPFina copiati in emon/ (dal 01/05/2023). Uscita: hourly.npz con t (inizio ora, Unix)
e una colonna per feed (NaN = nessun dato nell'ora). Feed contatori (kWh crescenti) -> differenza oraria."""
import gzip, os, sys, numpy as np
D = sys.argv[1]
START = 1682892000                       # 01/05/2023 00:00 Europe/Rome
COUNTERS = {362}                         # kWh cumulativi -> kWh per ora
cols = {}
END = None
for name in sorted(os.listdir(D)):
    if not name.endswith('.meta'):
        continue
    fid, iv, s0 = map(int, open(os.path.join(D, name)).read().split())
    raw = np.frombuffer(gzip.open(os.path.join(D, f'{fid}.dat.gz')).read(), dtype='<f4').astype(float)
    t = s0 + iv * np.arange(raw.size)
    keep = t >= START
    t, v = t[keep], raw[keep]
    h = (t - START) // 3600
    n = int(h.max()) + 1 if h.size else 0
    END = max(END or 0, n)
    ok = ~np.isnan(v)
    if fid in COUNTERS:
        last = np.full(n, np.nan)
        order = np.where(ok)[0]
        last[h[order]] = v[order]          # last value of each hour
        col = np.diff(np.concatenate([[np.nan], last]))
        col[(col < 0) | (col > 5)] = np.nan
    else:
        s = np.bincount(h[ok], weights=v[ok], minlength=n)
        c = np.bincount(h[ok], minlength=n)
        with np.errstate(invalid='ignore', divide='ignore'):
            col = np.where(c > 0, s / np.maximum(c, 1), np.nan)
    cols[fid] = col
    print(f'{fid}: {ok.sum()} campioni, {np.isfinite(col).sum()} ore con dati')
out = {'t': START + 3600 * np.arange(END)}
for fid, col in cols.items():
    full = np.full(END, np.nan); full[:col.size] = col
    out[f'f{fid}'] = full
np.savez_compressed(os.path.join(D, '..', 'hourly.npz'), **out)
print('ore totali', END)
