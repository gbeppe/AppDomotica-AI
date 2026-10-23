import json
import tempfile
import unittest
from pathlib import Path
from assistant import ask
from energy_tools import validate_plan
from evidence import day_bounds
from log_tools import read_day, SOURCES


class LogTests(unittest.TestCase):
    def test_verified_time_fields_and_provenance(self):
        with tempfile.TemporaryDirectory() as folder:
            for source, (filename, key, _) in SOURCES.items():
                stamp = '2026-09-12' if key == 'data_previsione' else day_bounds('2026-09-12')[0]
                Path(folder, filename).write_text('broken\n' + json.dumps({key: stamp}) + '\n')
                result = read_day(folder, source, '2026-09-12')
                self.assertEqual(result['records'][0]['source']['line'], 2)
                self.assertEqual(result['invalid_lines'], 1)
                self.assertEqual(result['status'], 'partial')
                self.assertEqual(read_day(folder, source, '2026-09-13')['status'], 'insufficient_data')

    def test_unavailable_traversal_and_bounded_results(self):
        self.assertEqual(read_day(None, 'climate', '2026-09-12')['status'], 'unavailable')
        with self.assertRaises(ValueError):
            read_day(None, '../secret', '2026-09-12')
        with tempfile.TemporaryDirectory() as folder:
            Path(folder, 'clima_controllo.log').write_text((json.dumps({'timestamp': day_bounds('2026-09-12')[0]}) + '\n') * 60)
            result = read_day(folder, 'climate', '2026-09-12')
            self.assertTrue(result['truncated'])
            self.assertEqual(result['matched_records'], 60)
            self.assertEqual(len(result['records']), 50)

    def test_climate_day_without_events_reports_the_previous_one(self):
        """2026-10-23: the climate log is written only on changes; 22/10 had none."""
        import os
        from energy_presenter import log_text
        event = lambda day, offset, reason: json.dumps({
            'timestamp': day_bounds(day)[0] + offset, 'dettaglio_comandi': {'stato_ac_attuale': 'OFF'},
            'logica_controllo': {'motivo_ac': reason}})
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder, 'clima_controllo.log')
            path.write_text('\n'.join([event('2026-10-19', 0, 'BLOCCO_PR_GATE_INVALID'),
                                       event('2026-10-21', 28_348_000, 'LOGICA_INVERNALE_ATTIVA'),
                                       event('2026-10-23', 28_642_000, 'LOGICA_INVERNALE_ATTIVA')]) + '\n')
            result = read_day(folder, 'climate', '2026-10-22')
            self.assertEqual(result['status'], 'insufficient_data')
            self.assertEqual(result['previous_record']['source']['line'], 2)
            text = log_text(result)
            self.assertIn('Il 22/10 il controller del clima non ha registrato cambi di stato', text)
            self.assertIn("L'ultimo evento precedente è del 21/10 alle 07:52: condizionatore spento, "
                          "motivo LOGICA_INVERNALE_ATTIVA.", text)
            self.assertNotIn('non prova assenza di eventi', text)
            self.assertIsNone(read_day(folder, 'climate', '2026-10-19')['previous_record'])
            first = log_text(read_day(folder, 'climate', '2026-10-18'))
            self.assertIn('Nel log non ci sono eventi precedenti', first)
            self.assertIsNone(read_day(folder, 'learning', '2026-10-22')['previous_record'])

    def test_plan_and_assistant(self):
        plan = {'operations': [{'id': 'a', 'tool': 'backend_log_day', 'source': 'reserve_shadow', 'day': '2026-09-06'}]}
        class Planner:
            def plan(self, *args):
                return plan
        result = ask(None, Planner(), 'Esamina il log shadow')
        self.assertEqual(result['status'], 'insufficient_data')
        self.assertIn('sorgente non disponibile', result['answer'])
        plan['operations'][0]['path'] = '/etc/passwd'
        with self.assertRaises(ValueError):
            validate_plan(plan)


class FreshnessTests(unittest.TestCase):
    """2026-10-10: predictive_reserve_shadow.log is no longer written by Node-RED."""

    def _files(self, folder, ages_s, now):
        import os
        for source, (filename, key, _) in SOURCES.items():
            if source in ages_s:
                path = Path(folder, filename)
                path.write_text(json.dumps({key: 1}) + '\n')
                os.utime(path, (now - ages_s[source], now - ages_s[source]))

    def test_source_status_by_cadence(self):
        from log_tools import source_status
        import time
        now = time.time()
        with tempfile.TemporaryDirectory() as folder:
            self._files(folder, {'learning': 4 * 3600, 'climate': 9 * 86400,
                                 'reserve_shadow': 34 * 86400, 'solar_forecast': 3600}, now)
            status = {s: source_status(folder, s, int(now * 1000))['status'] for s in SOURCES}
            self.assertEqual(status, {'learning': 'stale', 'climate': 'online',
                                      'reserve_shadow': 'archive', 'solar_forecast': 'online'})
            Path(folder, 'clima_controllo.log').unlink()
            self.assertEqual(source_status(folder, 'climate')['status'], 'missing')
        self.assertEqual(source_status(None, 'learning')['status'], 'missing')

    def test_day_after_last_write_is_not_updated_not_no_events(self):
        import os
        from energy_presenter import log_text
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder, 'predictive_reserve_shadow.log')
            path.write_text(json.dumps({'timestamp_ms': day_bounds('2026-09-06')[0] + 1000}) + '\n')
            frozen = day_bounds('2026-09-06')[0] / 1000 + 4800
            os.utime(path, (frozen, frozen))
            archived = read_day(folder, 'reserve_shadow', '2026-09-06')
            self.assertEqual(archived['status'], 'partial')
            later = read_day(folder, 'reserve_shadow', '2026-10-09')
            self.assertEqual(later['status'], 'source_not_updated')
            text = log_text(later)
            self.assertIn('non è più aggiornato dal 06/09', text)
            self.assertIn('archivio storico', text)
            self.assertNotIn('non prova assenza di eventi', text)

    def test_assistant_treats_not_updated_as_insufficient(self):
        import os
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder, 'predictive_reserve_shadow.log')
            path.write_text('')
            stamp = day_bounds('2026-09-06')[0] / 1000
            os.utime(path, (stamp, stamp))
            plan = {'operations': [{'id': 'a', 'tool': 'backend_log_day', 'source': 'reserve_shadow', 'day': '2026-10-09'}]}
            class Planner:
                def plan(self, *args):
                    return plan
            result = ask(None, Planner(), 'Cosa dice il PR ieri?', log_root=folder)
            self.assertEqual(result['status'], 'insufficient_data')
            self.assertIn('non è più aggiornato', result['answer'])

    def test_health_reports_stale_logs_as_degraded_and_archive_date(self):
        import os, time
        from server import detailed_health
        class Client:
            def catalog(self): return []
        now = time.time()
        with tempfile.TemporaryDirectory() as folder:
            self._files(folder, {'learning': 60, 'climate': 9 * 86400,
                                 'reserve_shadow': 34 * 86400, 'solar_forecast': 3600}, now)
            logs = [c for c in detailed_health(Client(), None, folder)['components'] if c['id'] == 'logs'][0]
            self.assertEqual(logs['status'], 'online')
            self.assertIn('archivio fermo al', logs['detail'])
            self.assertEqual(len(logs['files']), 4)
            os.utime(Path(folder, 'apprendimento_storico.log'), (now - 5 * 3600, now - 5 * 3600))
            logs = [c for c in detailed_health(Client(), None, folder)['components'] if c['id'] == 'logs'][0]
            self.assertEqual(logs['status'], 'degraded')
            self.assertIn('in ritardo: apprendimento_storico.log', logs['detail'])
