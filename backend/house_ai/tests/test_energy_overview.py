import json
import time
import unittest

from assistant import ask
from energy_flows import allocate
from energy_tools import catalog, validate_plan
from model_gateway import SYSTEM, plan_schema

TOPICS = {'solar_power_w': 'zara/interface/energy/solar/power/stat',
          'home_consumption_w': 'zara/interface/energy/home/consumption/stat',
          'grid_power_w': 'zara/interface/energy/grid/power_raw/stat',
          'battery_power_w': 'zara/interface/energy/battery/power_raw/stat',
          'battery_soc_percent': 'zara/interface/energy/battery/soc/stat'}
# Live values of 15/10/2026: solar charging the Powerwall, small export.
LIVE = {'solar_power_w': 2708.0, 'home_consumption_w': 561.0, 'grid_power_w': -37.0,
        'battery_power_w': -2140.0, 'battery_soc_percent': 45.8}


def snapshot(age_s=None, connected=True, **changes):
    now = int(time.time() * 1000)
    age_s = age_s or {}
    return {'schema': 'house_ai.current_energy_input.v1', 'connected': connected,
            'observations': {m: {'value': v, 'received_at_ms': now - age_s.get(m, 5) * 1000,
                                 'retained': False, 'source_topic': TOPICS[m]}
                             for m, v in {**LIVE, **changes}.items() if v is not None}}


class Planner:
    def __init__(self, plan):
        self.result = plan

    def plan(self, *args):
        return self.result


def answer(detail='summary', **kwargs):
    plan = {'operations': [{'id': 'e', 'tool': 'current_energy_overview', 'detail': detail}]}
    return ask(None, Planner(plan), 'energia?', current_snapshot=snapshot(**kwargs))


class FlowAllocationTests(unittest.TestCase):
    def test_live_case_matches_the_measured_sinks(self):
        # 37 W of export is below the 50 W deadband, as in the app energy diagram.
        result = allocate(2708, -2140, -37, 561)
        self.assertEqual(result['flows'], {'solar_to_home': 561, 'solar_to_battery': 2140})
        self.assertTrue(result['consistent'])
        export = allocate(3400, -2100, -760, 561)
        self.assertEqual(export['flows'], {'solar_to_home': 561, 'solar_to_battery': 2100, 'solar_to_grid': 760})
        self.assertEqual(export['imbalance_w'], -21)

    def test_evening_battery_and_grid_feed_the_house(self):
        self.assertEqual(allocate(0, 900, 300, 1200)['flows'], {'battery_to_home': 900, 'grid_to_home': 300})

    def test_partial_solar_with_import(self):
        self.assertEqual(allocate(400, 0, 800, 1200)['flows'], {'solar_to_home': 400, 'grid_to_home': 800})

    def test_rare_grid_charging_and_battery_export(self):
        self.assertEqual(allocate(0, -3000, 3500, 500)['flows'], {'grid_to_home': 500, 'grid_to_battery': 3000})
        self.assertEqual(allocate(0, 2000, -1500, 500)['flows'], {'battery_to_home': 500, 'battery_to_grid': 1500})

    def test_idle_noise_is_ignored_and_large_imbalance_is_not_hidden(self):
        self.assertEqual(allocate(30, 20, -10, 600)['flows'], {})
        result = allocate(3000, 0, 0, 500)
        self.assertFalse(result['consistent'])
        self.assertEqual(result['flows'], {'solar_to_home': 500})
        self.assertEqual(result['imbalance_w'], 2500)


class EnergyOverviewToolTests(unittest.TestCase):
    def test_catalog_schema_and_prompt(self):
        tools = {t['name']: t for t in catalog()['tools']}
        self.assertEqual(tools['current_energy_overview']['parameters'], {'detail': ['summary', 'flows']})
        self.assertIn('current_energy_overview', json.dumps(plan_schema()))
        self.assertIn('dettaglio energia', SYSTEM)
        with self.assertRaises(ValueError):
            validate_plan({'operations': [{'id': 'e', 'tool': 'current_energy_overview', 'detail': 'all'}]})

    def test_summary(self):
        self.assertEqual(answer()['answer'],
            'Il fotovoltaico produce 2,7 kilowatt, la batteria è al 46 per cento e si sta caricando, '
            'la casa consuma 561 watt, non stai importando dalla rete.')
        self.assertIn('stai immettendo 760 watt in rete.', answer(solar_power_w=3400.0, grid_power_w=-760.0)['answer'])

    def test_flows_list_only_active_ones_in_italian(self):
        result = answer('flows', solar_power_w=3400.0, grid_power_w=-760.0, battery_power_w=-2100.0)
        self.assertTrue(result['answer'].endswith(
            'Dal fotovoltaico: 561 watt alla casa, 2,1 kilowatt alla batteria e 760 watt in rete.'))
        limits = result['results'][0]['result']['limitations']
        self.assertTrue(any('nessun contatore li misura' in note for note in limits))
        evening = answer('flows', solar_power_w=0.0, battery_power_w=900.0, grid_power_w=300.0,
                         home_consumption_w=1200.0)['answer']
        self.assertTrue(evening.startswith('Il fotovoltaico è fermo, la batteria è al 46 per cento e si sta '
                                           'scaricando, la casa consuma 1,2 kilowatt, stai importando 300 watt dalla rete.'))
        self.assertIn('Dalla batteria: 900 watt alla casa. Dalla rete: 300 watt alla casa.', evening)
        self.assertNotIn('Dal fotovoltaico', evening)

    def test_no_import_idle_battery(self):
        text = answer(battery_power_w=10.0, grid_power_w=20.0, battery_soc_percent=100.0)['answer']
        self.assertIn('la batteria è al 100 per cento, la casa', text)
        self.assertIn('non stai importando dalla rete.', text)

    def test_stale_readings_are_not_used(self):
        result = answer('flows', age_s={'grid_power_w': 400})
        self.assertNotIn('rete.', result['answer'].split('.')[0])
        self.assertIn('Non aggiornati da più di 3 minuti: rete.', result['answer'])
        self.assertIn('Flussi non calcolabili', result['answer'])

    def test_missing_and_disconnected(self):
        empty = ask(None, Planner({'operations': [{'id': 'e', 'tool': 'current_energy_overview',
                                                   'detail': 'summary'}]}), 'energia?')
        self.assertEqual(empty['status'], 'insufficient_data')
        self.assertIn('non sono disponibili', empty['answer'])
        self.assertIn('La casa non è collegata', answer(connected=False)['answer'])


if __name__ == '__main__':
    unittest.main()
