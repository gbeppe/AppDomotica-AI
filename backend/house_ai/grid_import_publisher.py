"""Publish today's and yesterday's grid import (kWh) for the app's "Importato ieri / oggi".

Run by house-ai-grid-import.timer every 15 minutes (oneshot, user house-ai). Values come
from EmonCMS with the same calculation as the assistant (energy_report, feed 305) and
are published retained on the local broker with mosquitto_pub. Insufficient data is
never published as zero: the retained value is cleared instead.
"""
import json
import os
import subprocess
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from emoncms import Emoncms
from energy_history import energy_report
from energy_tools import CATALOG_PATH

ROME = ZoneInfo('Europe/Rome')
PREFIX = 'zara/interface/energy/grid'
TOPICS = {'today': f'{PREFIX}/import_kwh_oggi/stat', 'yesterday': f'{PREFIX}/import_kwh_ieri/stat'}
MIN_COVERAGE = {'today': 0.8, 'yesterday': 0.95}  # share of the (elapsed) day with samples


def coverage(result):
    lo = result['period_ms'][0]
    elapsed = result['observed_until_ms'] - lo
    return min(result['covered_ms'] / elapsed, 1.0) if elapsed > 0 else 0.0


def values(client, now):
    """{'today': kWh or None, 'yesterday': kWh or None}; None when coverage is too low."""
    source = json.loads(CATALOG_PATH.read_text(encoding='utf-8'))['sources']['grid_import_kwh']
    today = now.date()
    periods = {'today': (today, today + timedelta(days=1)), 'yesterday': (today - timedelta(days=1), today)}
    out = {}
    for name, (start, end) in periods.items():
        try:
            result = energy_report(client, start.isoformat(), end.isoformat(), 'grid_import_kwh',
                                   source['feed_id'], source['unit'], source['import_sign'],
                                   source['interval_seconds'], now_ms=int(now.timestamp() * 1000))
        except (OSError, RuntimeError, ValueError):
            out[name] = None
            continue
        ok = result.get('value') is not None and coverage(result) >= MIN_COVERAGE[name]
        out[name] = round(result['value'], 2) if ok else None
    return out


def mosquitto_pub(topic, payload):
    """Retained publish; payload None clears the retained message."""
    command = ['mosquitto_pub', '-h', os.environ.get('HOUSE_AI_MQTT_HOST', '127.0.0.1'),
               '-q', '1', '-r', '-t', topic]
    command += ['-n'] if payload is None else ['-m', payload]
    subprocess.run(command, check=True, timeout=20, capture_output=True)


def run(client, now=None, publish=mosquitto_pub):
    now = now or datetime.now(ROME)
    result = values(client, now)
    for name, value in result.items():
        publish(TOPICS[name], None if value is None else f'{value:.2f}')
    return result


def main():
    client = Emoncms(os.environ.get('EMONCMS_URL', ''), os.environ.get('EMONCMS_API_KEY', ''))
    result = run(client)
    print(json.dumps(result))  # journal: kWh only, no credentials


if __name__ == '__main__':
    main()
