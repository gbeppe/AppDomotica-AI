import json
import unittest

from assistant import ask
from energy_tools import ENV_TOPICS, THERMOSTAT_TOPICS, catalog, validate_environment_snapshot, validate_plan
from model_gateway import SYSTEM, plan_schema


VALUES = {'living.temperature': 22.4, 'living.humidity': 67.0, 'living.humidex': 27.1,
          'bedroom.temperature': 22.1, 'bedroom.humidity': 60.5, 'bedroom.humidex': 25.6,
          'kids_room.temperature': 22.1, 'kids_room.humidity': 63.0,
          'service_bathroom.temperature': 22.4, 'service_bathroom.humidity': 64.0,
          'technical_room.temperature': 24.0, 'technical_room.humidity': 48.0,
          'outdoor.temperature': 17.7, 'outdoor.humidity': 75.2}


def env(connected=True, drop=()):
    return {'schema': 'house_ai.current_environment_input.v1', 'connected': connected,
            'observations': {k: {'value': v, 'received_at_ms': 1_791_700_000_000, 'retained': True,
                                 'source_topic': ENV_TOPICS[k]} for k, v in VALUES.items() if k not in drop}}


def thermostats():
    values = {'thermostat_living.target_temperature': 18.0, 'thermostat_living.power': False,
              'thermostat_living.current_temperature': 22.4}
    return {'schema': 'house_ai.current_thermostats_input.v1', 'connected': True,
            'observations': {k: {'value': v, 'received_at_ms': 1_791_700_000_000, 'retained': True,
                                 'source_topic': THERMOSTAT_TOPICS[k]} for k, v in values.items()}}


class Planner:
    def __init__(self, plan):
        self.result = plan

    def plan(self, *args):
        return self.result


def question(room, metric, **kw):
    plan = {'operations': [{'id': 'e', 'tool': 'current_environment', 'room': room, 'metric': metric}]}
    return ask(None, Planner(plan), 'ambiente', environment_snapshot=kw.get('snapshot', env()),
               thermostats_snapshot=thermostats())


class EnvironmentToolTests(unittest.TestCase):
    def test_catalog_schema_prompt_and_plan(self):
        self.assertIn('current_environment', [t['name'] for t in catalog()['tools']])
        self.assertIn('current_environment', json.dumps(plan_schema()))
        self.assertIn('cameretta', SYSTEM)
        self.assertEqual(validate_plan({'operations': [{'id': 'e', 'tool': 'current_environment',
                                                        'room': 'outdoor', 'metric': 'humidity'}]})['operations'][0]['room'], 'outdoor')
        for bad in ({'room': 'kitchen', 'metric': 'all'}, {'room': 'living', 'metric': 'pressure'}, {'room': 'living'}):
            with self.assertRaises(ValueError):
                validate_plan({'operations': [{'id': 'e', 'tool': 'current_environment', **bad}]})

    def test_snapshot_allowlist(self):
        self.assertEqual(len(validate_environment_snapshot(env())['observations']), 14)
        wrong = env(); wrong['observations']['living.temperature']['source_topic'] = 'zara/interface/env/living/temperature/cmd'
        humid = env(); humid['observations']['living.humidity']['value'] = 140
        text = env(); text['observations']['outdoor.temperature']['value'] = '17'
        extra = env(); extra['observations']['kitchen.temperature'] = dict(extra['observations']['living.temperature'])
        for item in (wrong, humid, text, extra, {**env(), 'schema': 'x'}):
            with self.assertRaises(ValueError):
                validate_environment_snapshot(item)

    def test_single_metric_is_one_short_sentence(self):
        self.assertEqual(question('living', 'temperature')['answer'], 'Nel living ci sono 22,4 gradi.')
        self.assertEqual(question('outdoor', 'humidity')['answer'], "All'esterno l'umidità è al 75,2 per cento.")
        self.assertEqual(question('outdoor', 'humidex')['answer'], "All'esterno l'humidex non è disponibile.")

    def test_room_overview_includes_thermostat(self):
        answer = question('living', 'all')['answer']
        self.assertEqual(answer, 'Living: 22,4 gradi, umidità 67 per cento, humidex 27,1. '
                                 'Termostato: setpoint 18 gradi, riscaldamento spento.')
        self.assertEqual(question('kids_room', 'all')['answer'], 'Cameretta: 22,1 gradi, umidità 63 per cento.')

    def test_house_lists_every_room_including_outdoor_and_technical(self):
        answer = question('all', 'all')['answer']
        for part in ('Living 22,4 gradi, 67 per cento.', 'Camera da letto 22,1 gradi, 60,5 per cento.',
                     'Locale tecnico 24 gradi, 48 per cento.', 'Esterno 17,7 gradi, 75,2 per cento.'):
            self.assertIn(part, answer)
        self.assertNotIn('Termostato', answer)
        self.assertEqual(question('all', 'temperature')['answer'],
                         'Temperature: living 22,4, camera da letto 22,1, cameretta 22,1, bagno servizio 22,4, '
                         'locale tecnico 24, esterno 17,7 gradi.')

    def test_missing_and_disconnected(self):
        result = question('bedroom', 'all', snapshot=env(drop=('bedroom.temperature', 'bedroom.humidity', 'bedroom.humidex')))
        self.assertEqual(result['answer'], 'Camera da letto: dati non disponibili.')
        self.assertIn('La casa non è collegata', question('living', 'temperature', snapshot=env(connected=False))['answer'])


class ComposeTests(unittest.TestCase):
    def test_commands_become_one_sentence_before_readings(self):
        plan = {'operations': [{'id': 'a', 'tool': 'set_light_state', 'light': 'lights_tv', 'state': 'on'},
                               {'id': 'b', 'tool': 'set_light_state', 'light': 'lights_libreria', 'state': 'off'},
                               {'id': 'c', 'tool': 'set_thermostat_target', 'thermostat': 'thermostat_bath', 'target_c': '20.0'},
                               {'id': 'd', 'tool': 'current_environment', 'room': 'living', 'metric': 'temperature'}]}
        result = ask(None, Planner(plan), 'tutto', environment_snapshot=env())
        self.assertEqual(result['answer'], 'Ok, accendo la lampada TV, spengo la luce della libreria e imposto '
                                           'il termostato del bagno a 20 gradi.\n\nNel living ci sono 22,4 gradi.')


if __name__ == '__main__':
    unittest.main()
