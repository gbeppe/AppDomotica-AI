import json
import tempfile
import threading
import unittest
from datetime import datetime
from http.server import HTTPServer
from pathlib import Path
from unittest.mock import patch
from urllib.request import Request, urlopen
from zoneinfo import ZoneInfo

import energy_history
from day_summary import summary
from server import handler

NOW = datetime(2026, 10, 11, 14, 0, tzinfo=ZoneInfo('Europe/Rome'))
# Cumulative kWh counters (energy_sources.json since 23/10/2026) growing at these powers in W:
# PV 314, house 37, grid import 26 (none), grid export 29.
POWER = {314: 2000, 37: 1000, 26: 0, 29: 500}


class Client:
    def __init__(self, gap_after_s=None):
        self.gap_after_s = gap_after_s

    def history(self, feed, start, end, interval):
        stop = end if self.gap_after_s is None else min(end, start + self.gap_after_s)
        return [[t * 1000, POWER[feed] * t / 3_600_000] for t in range(start, stop + 1, interval)]


class Down:
    def history(self, *_args):
        raise RuntimeError('EmonCMS request failed; verify access and endpoint')


def forecast_dir(*records):
    root = tempfile.TemporaryDirectory()
    Path(root.name, 'zara_previsione_kwh_openmeteo.log').write_text(
        ''.join(json.dumps(r) + '\n' for r in records))
    return root


def run(client, root=None):
    with patch.object(energy_history.clock, 'time', return_value=NOW.timestamp()):
        return summary(client, root, now=NOW)


class DaySummaryTests(unittest.TestCase):
    def test_totals_until_now_and_tomorrow_forecast(self):
        with forecast_dir({'messaggio': 'Previsione elaborata', 'data_previsione': '2026-10-12',
                           'kwh_stimati_impianto_reali': 10}) as root:
            result = run(Client(), root)
        self.assertEqual(result['answer'], 'Oggi fino alle 14:00: prodotti 28 kilowattora, consumati 14, '
                                           'prelevati 0, immessi 7. Previsione per domani: 10 kilowattora.')
        self.assertEqual(result['status'], 'complete')
        self.assertEqual(result['schema'], 'house_ai.assistant_answer.v1')
        notes = [n for item in result['results'] for n in item['result']['limitations']]
        self.assertIn('Copertura del tempo trascorso oggi: 100 per cento.', notes)
        self.assertFalse(any(n.startswith('Copertura 58') for n in notes))  # whole-day share hidden

    def test_forecast_computation_time_goes_to_diagnostics(self):
        with forecast_dir({'data_previsione': '2026-10-12', 'kwh_stimati_impianto_reali': 10,
                           'elaborata_alle': '2026-10-11T12:15:12.165Z'}) as root:
            result = run(Client(), root)
        forecast = next(i for i in result['results'] if i['id'] == 'forecast')['result']
        self.assertIn('È una previsione per il giorno indicato, non produzione misurata; '
                      'elaborata il 11/10 alle 14:15.', forecast['limitations'])

    def test_missing_forecast_is_said_not_invented(self):
        with forecast_dir({'data_previsione': '2026-10-10', 'kwh_stimati_impianto_reali': 11}) as root:
            result = run(Client(), root)
        self.assertTrue(result['answer'].endswith('Previsione per domani non ancora disponibile.'))
        self.assertEqual(result['status'], 'partial')

    def test_gaps_are_flagged_as_partial(self):
        result = run(Client(gap_after_s=4 * 3600))
        self.assertIn('Dati parziali.', result['answer'])
        self.assertEqual(result['status'], 'partial')

    def test_emoncms_down(self):
        result = run(Down())
        self.assertEqual(result['answer'], 'Totali di oggi non disponibili. '
                                           'Previsione per domani non ancora disponibile.')
        self.assertEqual(result['status'], 'insufficient_data')

    def test_endpoint_requires_token_and_needs_no_planner(self):
        server = HTTPServer(('127.0.0.1', 0), handler(Client(), 't' * 24, None, None))
        threading.Thread(target=server.serve_forever, daemon=True).start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        url = f'http://127.0.0.1:{server.server_port}/v1/summary/day'
        with self.assertRaises(Exception) as denied:
            urlopen(url)
        self.assertEqual(denied.exception.code, 401)
        denied.exception.close()
        with urlopen(Request(url, headers={'Authorization': 'Bearer ' + 't' * 24})) as response:
            body = json.load(response)
        self.assertTrue(body['answer'].startswith('Oggi fino alle '))


if __name__ == '__main__':
    unittest.main()
