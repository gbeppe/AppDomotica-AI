import json
import unittest

from assistant import ask
from energy_tools import HEATING_TOPICS, catalog, validate_plan
from model_gateway import SYSTEM, plan_schema


def heating(mode='Riscaldamento', power=False, start='16:30', stop='16:55'):
    values = {'fireplace.mode': mode, 'fireplace.power': power,
              'fireplace.start_time': start, 'fireplace.stop_time': stop}
    return {'schema': 'house_ai.current_heating_input.v1', 'connected': True,
            'observations': {k: {'value': v, 'received_at_ms': 1_792_000_000_000, 'retained': True,
                                 'source_topic': HEATING_TOPICS[k]} for k, v in values.items() if v is not None}}


class Planner:
    def __init__(self, plan):
        self.result = plan

    def plan(self, *args):
        return self.result


def settings(mode='unchanged', start='unchanged', stop='unchanged', auto='unchanged', id='s'):
    return {'id': id, 'tool': 'set_fireplace_settings', 'mode': mode, 'start_time': start,
            'stop_time': stop, 'auto_power': auto}


def power(state, id='p'):
    return {'id': id, 'tool': 'set_fireplace_power', 'state': state}


def run(*operations, **snapshot):
    return ask(None, Planner({'operations': list(operations)}), 'caminetto', heating_snapshot=heating(**snapshot))


class FireplaceCommandTests(unittest.TestCase):
    def test_catalog_schema_and_prompt(self):
        tools = {t['name']: t for t in catalog()['tools']}
        self.assertEqual(tools['set_fireplace_power']['parameters'], {'state': ['on', 'off']})
        self.assertIn('Acqua Sanitaria', tools['set_fireplace_settings']['parameters']['mode'])
        self.assertEqual(set(tools['set_fireplace_settings']['parameters']), {'mode', 'start_time', 'stop_time', 'auto_power'})
        self.assertFalse(any('level' in name for name in tools))
        self.assertIn('set_fireplace_settings', json.dumps(plan_schema()))
        self.assertIn('Il livello di\npotenza del caminetto non si imposta', SYSTEM)

    def test_plan_validation(self):
        validate_plan({'operations': [settings(mode='Acqua Sanitaria', start='12:00', stop='13:00')]})
        for bad in (settings(), settings(mode='inverno'), settings(start='12'), settings(start='24:00'),
                    settings(auto='true'), power('toggle')):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                validate_plan({'operations': [bad]})

    def test_mode_and_window_run_at_once(self):
        result = run(settings(mode='Acqua Sanitaria', start='12:00', stop='13:00'))
        self.assertEqual(result['answer'], 'Ok, metto il caminetto in modalità Acqua Sanitaria, dalle 12:00 alle 13:00.')
        command = result['results'][0]['result']
        self.assertEqual((command['status'], command['mode'], command['start_time'], command['stop_time'],
                          command['auto_power']), ('command_ready', 'Acqua Sanitaria', '12:00', '13:00', None))
        self.assertEqual(result['status'], 'complete')

    def test_single_settings(self):
        self.assertEqual(run(settings(mode='Riscaldamento'))['answer'], 'Ok, metto il caminetto in modalità Riscaldamento.')
        self.assertEqual(run(settings(stop='17:30'))['answer'], 'Ok, imposto gli orari del caminetto con spegnimento alle 17:30.')
        self.assertEqual(run(settings(auto='off'))['answer'], 'Ok, disattivo la potenza automatica del caminetto.')

    def test_time_order_uses_the_current_window(self):
        result = run(settings(start='17:00'))
        self.assertEqual(result['results'][0]['result']['status'], 'rejected')
        self.assertEqual(result['answer'], "Non imposto gli orari del caminetto: l'avvio alle 17:00 deve essere prima "
                                           "dello spegnimento alle 16:55.")
        self.assertEqual(run(settings(start='13:00', stop='12:00'))['results'][0]['result']['status'], 'rejected')

    def test_power_outside_manual_is_refused_option_a(self):
        result = run(power('on'))
        self.assertEqual(result['results'][0]['result']['status'], 'rejected')
        self.assertEqual(result['answer'], 'Il caminetto è in modalità Riscaldamento: per accenderlo a mano passa prima a Manuale.')
        self.assertEqual(run(power('off'), mode=None)['answer'],
                         'Non conosco la modalità del caminetto: non posso accenderlo o spegnerlo a mano.')

    def test_power_in_manual_asks_for_confirmation(self):
        result = run(power('on'), mode='Manuale')
        self.assertEqual(result['results'][0]['result']['status'], 'confirmation_required')
        self.assertEqual(result['answer'], "Confermi l'accensione del caminetto? Tocca Conferma.")
        self.assertEqual(run(power('off'), mode='Manuale', power=True)['answer'],
                         'Confermi lo spegnimento del caminetto? Tocca Conferma.')
        self.assertEqual(run(power('on'), mode='Manuale', power=True)['answer'], 'Il caminetto risulta già acceso.')

    def test_manual_then_on_in_one_request(self):
        result = run(settings(mode='Manuale'), power('on'))
        self.assertEqual(result['answer'], "Ok, metto il caminetto in modalità Manuale.\n\n"
                                           "Confermi l'accensione del caminetto? Tocca Conferma.")


if __name__ == '__main__':
    unittest.main()
