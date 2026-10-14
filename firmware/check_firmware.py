"""Controlla che nessun firmware versionato contenga credenziali.

Uso: python3 firmware/check_firmware.py (dalla radice del repository). Esce con 1 se trova problemi.
"""
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
SECRET_DEFINE = re.compile(
    r'#define\s+\w*(PASS|PASSWORD|SSID|USER|TOKEN|KEY)\w*\s+"[^"]+"', re.IGNORECASE)


def tracked_files():
    out = subprocess.run(['git', 'ls-files', '--', '.'], capture_output=True, text=True,
                         check=True, cwd=ROOT).stdout
    return [ROOT / line for line in out.splitlines()]


def main():
    problems = []
    for path in tracked_files():
        if path.name == 'secrets.h':
            problems.append(f'{path}: secrets.h non deve essere versionato')
        if path.suffix in ('.ino', '.h', '.cpp') and path.name != 'secrets.h.example':
            for n, line in enumerate(path.read_text(errors='replace').splitlines(), 1):
                if SECRET_DEFINE.search(line):
                    problems.append(f'{path}:{n}: credenziale scritta nel codice')
    for sketch in ROOT.glob('*/*/*.ino'):
        if sketch.stem != sketch.parent.name:
            problems.append(f'{sketch}: il nome dello sketch deve coincidere con la cartella')
        if '#include "secrets.h"' in sketch.read_text() and not (sketch.parent / 'secrets.h.example').exists():
            problems.append(f'{sketch.parent}: manca secrets.h.example')
    for p in problems:
        print(p)
    print('OK' if not problems else f'{len(problems)} problemi')
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
