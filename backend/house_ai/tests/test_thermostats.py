import json
import unittest

from assistant import ask
from energy_tools import (THERMOSTAT_TARGETS, THERMOSTAT_TOPICS, catalog, validate_plan,
                          validate_thermostats_snapshot)
from model_gateway import SYSTEM, plan_schema


VALUES = {'current_temperature': 22.4, 'target_temperature': 18.0, 'power': False,
          'min_temperature': 17.5, 'max_temperature': 18.5}


def snapshot(connected=True, only=None):
    return {'schema': 'house_ai.current_thermostats_input.v1', 'connected': connected,
            'observations': {f'{t}.{m}': {'value': v, 'received_at_ms': 1_791_666_000_000, 'retained': True,
                                          'source_topic': THERMOSTAT_TOPICS[f'{t}.{m}']}
                             for t in ('thermostat_living', 'thermostat_bath') if only in (None, t)
                             for m, v in VALUES.items()}}


class Planner:
    def __init__(self, plan):
        self.result = plan

    def plan(self, *args):
        return self.result


def read(thermostat='all'):
    return {'operations': [{'id': 't', 'tool': 'current_thermostats', 'thermostat': thermostat}]}


def command(thermostat='thermostat_living', target='20.5'):
    return {'operations': [{'id': 'c', 'tool': 'set_thermostat_target', 'thermostat': thermostat,
                            'target_c': target}]}


class ThermostatToolTests(unittest.TestCase):
    def test_catalog_schema_and_prompt(self):
        names = [t['name'] for t in catalog()['tools']]
        self.assertIn('current_thermostats', names)
        self.assertIn('set_thermostat_target', names)
        self.assertIn('set_thermostat_target', json.dumps(plan_schema()))
        self.assertIn('set_thermostat_target', SYSTEM)
        self.assertEqual(THERMOSTAT_TARGETS[0], '16.0')
        self.assertEqual(THERMOSTAT_TARGETS[-1], '22.0')
        self.assertEqual(len(THERMOSTAT_TARGETS), 13)

    def test_plan_validation_is_closed(self):
        self.assertEqual(validate_plan(command())['operations'][0]['target_c'], '20.5')
        self.assertEqual(validate_plan(read('thermostat_bath'))['operations'][0]['thermostat'], 'thermostat_bath')
        for bad in (command(target='22.5'), command(target='15.5'), command(target='20.3'),
                    command(target='20'), command(thermostat='all'), command(thermostat='bedroom'),
                    read('kitchen'),
                    {'operations': [{'id': 'c', 'tool': 'set_thermostat_target', 'thermostat': 'thermostat_bath',
                                     'target_c': '20.0', 'topic': 'zara/interface/x/cmd'}]}):
            with self.assertRaises(ValueError):
                validate_plan(bad)

    def test_snapshot_allowlist(self):
        self.assertIsNone(validate_thermostats_snapshot(None))
        self.assertEqual(len(validate_thermostats_snapshot(snapshot())['observations']), 10)
        wrong_topic = snapshot()
        wrong_topic['observations']['thermostat_bath.power']['source_topic'] = \
            'zara/interface/climate/thermostat_bath/power/cmd'
        string_value = snapshot(); string_value['observations']['thermostat_living.target_temperature']['value'] = '18'
        bool_temp = snapshot(); bool_temp['observations']['thermostat_living.current_temperature']['value'] = True
        number_power = snapshot(); number_power['observations']['thermostat_living.power']['value'] = 1
        absurd = snapshot(); absurd['observations']['thermostat_bath.current_temperature']['value'] = 400
        extra = snapshot(); extra['observations']['thermostat_kitchen.power'] = \
            dict(extra['observations']['thermostat_bath.power'])
        for item in (wrong_topic, string_value, bool_temp, number_power, absurd, extra,
                     {**snapshot(), 'schema': 'other'}):
            with self.assertRaises(ValueError):
                validate_thermostats_snapshot(item)

    def test_read_answer(self):
        result = ask(None, Planner(read()), 'Che temperatura c’è in casa?', thermostats_snapshot=snapshot())
        self.assertEqual(result['status'], 'partial')  # current-state tools are never 'complete'
        text = result['answer']
        self.assertIn('Termostato soggiorno: temperatura ambiente 22,4 °C, setpoint 18,0 °C, riscaldamento spento', text)
        self.assertIn('Termostato bagno', text)
        self.assertIn('timer delle fasce orarie 18,5 °C in fascia e 17,5 °C fuori fascia', text)
        self.assertIn('non una lettura del termostato', text)

    def test_read_single_and_missing(self):
        bath = ask(None, Planner(read('thermostat_bath')), 'Il bagno è riscaldato?',
                   thermostats_snapshot=snapshot(only='thermostat_living'))
        self.assertEqual(bath['status'], 'insufficient_data')
        self.assertIn('Termostato bagno: dati non disponibili', bath['answer'])
        self.assertNotIn('soggiorno', bath['answer'])
        disconnected = ask(None, Planner(read()), 'Temperature?', thermostats_snapshot=snapshot(connected=False))
        self.assertIn('Digital Twin disconnesso', disconnected['answer'])

    def test_command_result_has_no_topic(self):
        result = ask(None, Planner(command('thermostat_bath', '19.5')), 'Metti il bagno a 19 e mezzo')
        self.assertEqual(result['status'], 'complete')
        item = result['results'][0]['result']
        self.assertEqual(item['schema'], 'house_ai.thermostat_command.v1')
        self.assertEqual((item['thermostat'], item['target_c']), ('thermostat_bath', '19.5'))
        self.assertNotIn('zara/', json.dumps(result))
        self.assertIn('setpoint di termostato bagno a 19,5 °C', result['answer'])
        self.assertIn('ripristino ogni 8 ore', result['answer'])


if __name__ == '__main__':
    unittest.main()
