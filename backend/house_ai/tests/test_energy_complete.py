import json
import tempfile
import unittest
from pathlib import Path
from assistant import ask
from energy_tools import execute, validate_plan
from evidence import day_bounds


def history(identifier, start='2026-09-01', end='2026-09-02', metric='grid_import_kwh'):
    return dict(id=identifier, tool='energy_metric', metric=metric, start=start, end=end)


class Client:
    def history(self, feed, start, end, interval):
        return [[t*1000, 1000] for t in range(start, end+1, interval)]


class EnergyCompleteTests(unittest.TestCase):
    def test_oversized_numeric_values_are_invalid_not_server_errors(self):
        from energy_tools import validate_current_snapshot
        from energy_presenter import numeric
        self.assertIsNone(numeric({'value': 10**1000}, 'value'))
        with self.assertRaises(ValueError):
            validate_current_snapshot(dict(schema='house_ai.current_energy_input.v1', connected=True,
                observations={'solar_power_w': dict(value=10**1000, received_at_ms=1, retained=False, source_topic='x/stat')}))

    def test_entire_plan_validated_before_any_read(self):
        class NoRead:
            def history(self, *args):
                self.fail('must not read')
        with self.assertRaises(ValueError):
            execute(NoRead(), {'operations': [history('a'), history('b', end='nonsense')]})
        for tool in [None, {}, 'unknown']:
            with self.assertRaises(ValueError):
                validate_plan({'operations': [{'tool': tool}]})

    def test_comparison_full_partial_and_zero(self):
        operations = [history('a'), history('b', end='2026-09-03'),
                      dict(id='difference', tool='energy_comparison', left_id='a', right_id='b')]
        result = execute(Client(), {'operations': operations})['results'][-1]['result']
        self.assertEqual(result['value'], -24)
        self.assertEqual(result['change_percent'], -50)
        class Empty:
            def history(self, *args): return []
        self.assertIsNone(execute(Empty(), {'operations': operations})['results'][-1]['result']['value'])
        class Zero:
            def history(self, feed, start, end, interval):
                return [[t*1000, 0] for t in range(start, end+1, interval)]
        result = execute(Zero(), {'operations': operations})['results'][-1]['result']
        self.assertEqual(result['value'], 0)
        self.assertIsNone(result['change_percent'])

    def test_comparison_rejects_units_metrics_and_forward_reference(self):
        compare = dict(id='c', tool='energy_comparison', left_id='a', right_id='b')
        for operations in [[compare, history('a'), history('b')],
                           [history('a'), history('b', metric='soc_mean_percent'), compare]]:
            with self.assertRaises(ValueError): validate_plan({'operations': operations})

    def test_source_failure_keeps_other_evidence(self):
        class Broken:
            def history(self, *args): raise RuntimeError('no source')
        result = execute(Broken(), {'operations': [history('a'), dict(id='b', tool='backend_log_day', source='solar_forecast', day='2026-09-12')]})
        self.assertEqual(len(result['results']), 2)
        self.assertEqual(result['results'][0]['result']['status'], 'unavailable')

    def test_signed_current_answer_and_unknown_freshness_are_spoken(self):
        class Planner:
            def plan(self, *args):
                return {'operations': [dict(id='g', tool='current_energy_metric', metric='grid_power_w'),
                                       dict(id='b', tool='current_energy_metric', metric='battery_power_w')]}
        snapshot = dict(schema='house_ai.current_energy_input.v1', connected=True, observations={
            key: dict(value=-500, received_at_ms=1789200000000, retained=True, source_topic='zara/interface/energy/test/stat')
            for key in ('grid_power_w','battery_power_w')})
        result = ask(None, Planner(), 'Flussi?', current_snapshot=snapshot)
        self.assertIn('Immissione in rete: 500,0 W', result['answer'])
        self.assertIn('Carica della batteria: 500,0 W', result['answer'])
        self.assertIn('Età della misura sorgente ignota', result['answer'])
        self.assertEqual(result['status'], 'partial')

    def test_log_forecast_is_not_a_measurement_and_zero_preserved(self):
        class Planner:
            def plan(self, *args):
                return {'operations': [dict(id='forecast', tool='backend_log_day', source='solar_forecast', day='2026-09-12')]}
        with tempfile.TemporaryDirectory() as root:
            Path(root, 'zara_previsione_kwh_openmeteo.log').write_text(json.dumps({'data_previsione':'2026-09-12', 'kwh_stimati_impianto_reali':0}))
            result = ask(None, Planner(), 'Previsione?', log_root=root)
            self.assertIn('0,00 kWh', result['answer'])
            self.assertIn('non produzione misurata', result['answer'])
            self.assertEqual(result['results'][0]['result']['records'][0]['source']['line'], 1)

    def test_log_nonfinite_nested_value_is_excluded(self):
        from log_tools import read_day
        with tempfile.TemporaryDirectory() as root:
            Path(root, 'clima_controllo.log').write_text('{"timestamp":'+str(day_bounds('2026-09-12')[0])+',"energy":1e999}')
            result = read_day(root, 'climate', '2026-09-12')
            self.assertEqual(result['invalid_lines'], 1)
            self.assertEqual(result['records'], [])
