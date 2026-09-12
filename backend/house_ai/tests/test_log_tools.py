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

    def test_plan_and_assistant(self):
        plan = {'operations': [{'id': 'a', 'tool': 'backend_log_day', 'source': 'reserve_shadow', 'day': '2026-09-06'}]}
        class Planner:
            def plan(self, *args):
                return plan
        result = ask(None, Planner(), 'Esamina il log shadow')
        self.assertEqual(result['status'], 'insufficient_data')
        self.assertIn('simulazione', result['answer'])
        plan['operations'][0]['path'] = '/etc/passwd'
        with self.assertRaises(ValueError):
            validate_plan(plan)
