import json
import unittest

from assistant import ask
from energy_tools import HEATING_TOPICS, catalog, validate_heating_snapshot, validate_plan
from model_gateway import SYSTEM, plan_schema


# Live values of 14/10/2026 at 16:31, fireplace switched on by its time window.
LIVE = {'fireplace.power': True, 'fireplace.level': 3, 'fireplace.mode': 'Riscaldamento',
        'fireplace.start_time': '16:30', 'fireplace.stop_time': '17:30', 'fireplace.auto_power': True,
        'puffer.top_temperature': 42.9, 'puffer.bottom_temperature': 44.3, 'puffer.acs_temperature': 47.0,
        'floor_pump.enabled': False, 'floor_pump.running': False, 'gas_boiler.flame': False,
        'solar_thermal.collector_temperature': 21.7, 'solar_thermal.pump_speed': 0}


def snapshot(connected=True, **changes):
    values = {**LIVE, **changes}
    return {'schema': 'house_ai.current_heating_input.v1', 'connected': connected,
            'observations': {key: {'value': value, 'received_at_ms': 1_791_995_460_000, 'retained': True,
                                   'source_topic': HEATING_TOPICS[key]}
                             for key, value in values.items() if value is not None}}


class Planner:
    def __init__(self, plan):
        self.result = plan

    def plan(self, *args):
        return self.result


PLAN = {'operations': [{'id': 'h', 'tool': 'current_heating'}]}


def answer(**kwargs):
    return ask(None, Planner(PLAN), "Com'è il riscaldamento in casa?", heating_snapshot=snapshot(**kwargs))


class HeatingToolTests(unittest.TestCase):
    def test_tool_is_read_only_in_catalog_schema_and_prompt(self):
        tools = {t['name']: t for t in catalog()['tools']}
        self.assertEqual(tools['current_heating']['parameters'], {})
        self.assertIn('heating', catalog()['domains'])
        self.assertIn('current_heating', json.dumps(plan_schema()))
        self.assertIn('current_heating', SYSTEM)
        # Fireplace commands are separate tools (set_fireplace_*); puffer, pumps and boiler have none.
        self.assertFalse(any(t['name'].startswith(('set_puffer', 'set_floor', 'set_boiler', 'set_solar'))
                             for t in catalog()['tools']))
        self.assertEqual(validate_plan(PLAN)['operations'][0]['tool'], 'current_heating')
        with self.assertRaises(ValueError):
            validate_plan({'operations': [{'id': 'h', 'tool': 'current_heating', 'level': '6'}]})

    def test_topics_are_the_router_stat_topics(self):
        self.assertEqual(HEATING_TOPICS['fireplace.power'], 'zara/interface/fireplace/main/power/stat')
        self.assertEqual(HEATING_TOPICS['puffer.acs_temperature'], 'zara/interface/energy/puffer_acs/stat')
        self.assertTrue(all(t.endswith('/stat') for t in HEATING_TOPICS.values()))

    def test_snapshot_allowlist(self):
        self.assertIsNone(validate_heating_snapshot(None))
        self.assertEqual(len(validate_heating_snapshot(snapshot())['observations']), len(LIVE))
        bad = [snapshot(**{'fireplace.level': 7}), snapshot(**{'fireplace.mode': 'inverno'}),
               snapshot(**{'fireplace.start_time': '1630'}), snapshot(**{'fireplace.power': 'true'}),
               snapshot(**{'gas_boiler.modulation': 140}), snapshot(**{'puffer.top_temperature': float('nan')})]
        wrong_topic = snapshot()
        wrong_topic['observations']['fireplace.power']['source_topic'] = 'zara/interface/fireplace/main/power/cmd'
        extra = snapshot(); extra['observations']['fireplace.reset'] = dict(extra['observations']['fireplace.power'])
        for item in bad + [wrong_topic, extra, {**snapshot(), 'schema': 'other'}]:
            with self.assertRaises(ValueError):
                validate_heating_snapshot(item)

    def test_live_answer_reports_fireplace_and_plant(self):
        result = answer()
        self.assertNotEqual(result['status'], 'insufficient_data')
        self.assertEqual(result['answer'],
            'Caminetto acceso a livello 3. È in modalità Riscaldamento, programmato dalle 16:30 alle 17:30, '
            'con potenza automatica attiva. '
            'Puffer: alto 42,9 gradi, basso 44,3 gradi, acqua sanitaria 47 gradi. '
            'Pompa pavimento disabilitata e ferma. Caldaia a gas: fiamma spenta. '
            'Solare termico: pompa ferma, collettore 21,7 gradi.')
        limits = result['results'][0]['result']['limitations']
        self.assertTrue(any('non una misura della fiamma' in note for note in limits))
        self.assertTrue(any('retained' in note for note in limits))

    def test_off_hides_level_and_manual_has_no_window(self):
        text = answer(**{'fireplace.power': False})['answer']
        self.assertTrue(text.startswith('Caminetto spento. È in modalità Riscaldamento, programmato dalle 16:30 alle 17:30'))
        self.assertNotIn('livello', text)
        manual = answer(**{'fireplace.mode': 'Manuale'})['answer']
        self.assertIn('È in modalità Manuale, con potenza automatica attiva.', manual)
        no_mode = answer(**{'fireplace.mode': None, 'fireplace.auto_power': False})['answer']
        self.assertTrue(no_mode.startswith('Caminetto acceso a livello 3. Potenza automatica disattivata. Puffer'))

    def test_running_pump_boiler_and_solar(self):
        text = answer(**{'floor_pump.enabled': True, 'floor_pump.running': True, 'gas_boiler.flame': True,
                         'gas_boiler.modulation': 35, 'solar_thermal.pump_speed': 40})['answer']
        self.assertIn('Pompa pavimento abilitata e in funzione.', text)
        self.assertIn('Caldaia a gas: fiamma accesa, modulazione 35 per cento.', text)
        self.assertIn('Solare termico: pompa al 40 per cento', text)

    def test_partial_missing_and_disconnected(self):
        only_puffer = {key: None for key in LIVE if not key.startswith('puffer.')}
        self.assertEqual(answer(**only_puffer)['answer'],
                         'Puffer: alto 42,9 gradi, basso 44,3 gradi, acqua sanitaria 47 gradi.')
        missing = ask(None, Planner(PLAN), 'Il caminetto è acceso?')
        self.assertEqual(missing['status'], 'insufficient_data')
        self.assertIn('non è disponibile', missing['answer'])
        self.assertIn('La casa non è collegata', answer(connected=False)['answer'])


if __name__ == '__main__':
    unittest.main()
