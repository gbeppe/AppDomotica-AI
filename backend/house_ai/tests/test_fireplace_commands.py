import json
import re
import unittest

from assistant import ask
from energy_tools import HEATING_TOPICS, catalog, validate_plan
from model_gateway import SYSTEM, plan_schema


def heating(mode='Riscaldamento', power=False, start='16:30', stop='16:55', level=3, auto=True):
    values = {'fireplace.mode': mode, 'fireplace.power': power, 'fireplace.level': level, 'fireplace.auto_power': auto,
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


def level(value, id='l'):
    return {'id': id, 'tool': 'set_fireplace_level', 'level': value}


def run(*operations, **snapshot):
    return ask(None, Planner({'operations': list(operations)}), 'caminetto', heating_snapshot=heating(**snapshot))


class FireplaceCommandTests(unittest.TestCase):
    def test_catalog_schema_and_prompt(self):
        tools = {t['name']: t for t in catalog()['tools']}
        self.assertEqual(tools['set_fireplace_power']['parameters'], {'state': ['on', 'off']})
        self.assertIn('Acqua Sanitaria', tools['set_fireplace_settings']['parameters']['mode'])
        self.assertEqual(set(tools['set_fireplace_settings']['parameters']), {'mode', 'start_time', 'stop_time', 'auto_power'})
        self.assertEqual(tools['set_fireplace_level']['parameters'], {'level': ['1', '2', '3', '4', '5', '6']})
        self.assertIn('set_fireplace_level', json.dumps(plan_schema()))
        self.assertIn('usa\nset_fireplace_level', SYSTEM)

    def test_plan_validation(self):
        validate_plan({'operations': [settings(mode='Acqua Sanitaria', start='12:00', stop='13:00')]})
        for bad in (settings(), settings(mode='inverno'), settings(start='12'), settings(start='24:00'),
                    settings(auto='true'), power('toggle'), level('0'), level('7'), level(4)):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                validate_plan({'operations': [bad]})

    def test_mode_and_window_wait_for_confirmation(self):
        result = run(settings(mode='Acqua Sanitaria', start='12:00', stop='13:00'))
        self.assertEqual(result['answer'], "Metto il caminetto in modalità Acqua Sanitaria, dalle 12:00 alle 13:00. Confermi?")
        command = result['results'][0]['result']
        self.assertEqual((command['status'], command['mode'], command['start_time'], command['stop_time'],
                          command['auto_power']), ('confirmation_required', 'Acqua Sanitaria', '12:00', '13:00', None))
        self.assertEqual(result['status'], 'complete')

    def test_single_settings(self):
        self.assertEqual(run(settings(mode='Riscaldamento'))['answer'], "Metto il caminetto in modalità Riscaldamento. Confermi?")
        self.assertEqual(run(settings(stop='17:30'))['answer'], "Imposto gli orari del caminetto con spegnimento alle 17:30. Confermi?")
        self.assertEqual(run(settings(auto='off'))['answer'], "Disattivo la potenza automatica del caminetto. Confermi?")

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
        self.assertEqual(result['answer'], "Accendo il caminetto. Confermi?")
        self.assertEqual(result['confirmation'], {'prompt': 'Accendo il caminetto. Confermi?',
                                                  'done': 'Ok, accendo il caminetto.'})
        self.assertEqual(run(power('off'), mode='Manuale', power=True)['answer'], "Spengo il caminetto. Confermi?")
        self.assertEqual(run(power('on'), mode='Manuale', power=True)['answer'], 'Il caminetto risulta già acceso.')
        self.assertNotIn('confirmation', run(power('on'), mode='Manuale', power=True))

    def test_manual_then_on_in_one_request_one_confirmation(self):
        result = run(settings(mode='Manuale'), power('on'))
        self.assertEqual(result['answer'], "Metto il caminetto in modalità Manuale e lo accendo. Confermi?")
        self.assertEqual(result['confirmation']['done'], 'Ok, metto il caminetto in modalità Manuale e lo accendo.')

    def test_level_only_in_manual_with_the_fireplace_on(self):
        result = run(level('4'), mode='Manuale', power=True)
        self.assertEqual(result['answer'], "Imposto il caminetto al livello 4. Confermi?")
        command = result['results'][0]['result']
        self.assertEqual((command['status'], command['level']), ('confirmation_required', 4))
        self.assertEqual(run(level('3'), mode='Manuale', power=True)['answer'], 'Il caminetto è già al livello 3.')
        self.assertEqual(run(level('4'), mode='Manuale')['answer'],
                         'Il caminetto è spento: il livello si cambia quando è acceso.')

    def test_level_outside_manual_follows_the_automatic_power(self):
        self.assertEqual(run(level('4'), power=True)['answer'],
                         'Il caminetto è in modalità Riscaldamento: il livello si cambia solo in Manuale. '
                         'Con la potenza automatica attiva il livello lo decide la casa.')
        self.assertEqual(run(level('4'), auto=False)['answer'],
                         'Il caminetto è in modalità Riscaldamento: il livello si cambia solo in Manuale. '
                         'La potenza automatica è disattivata.')
        self.assertEqual(run(level('4'), mode=None)['results'][0]['result']['status'], 'rejected')

    def test_manual_then_level_in_one_request(self):
        result = run(settings(mode='Manuale'), level('5'), power=True)
        self.assertEqual(result['answer'], "Metto il caminetto in modalità Manuale e imposto il caminetto al livello 5. Confermi?")


    def test_spoken_question_never_contains_a_confirmation_word(self):
        # The microphone opens right after the reading: the phone must not confirm by itself
        # (field test 16/10: "bagno a 21" sent although the user had not confirmed).
        thermostat = {'id': 't', 'tool': 'set_thermostat_target', 'thermostat': 'thermostat_bath', 'target_c': '21.0'}
        for result in (run(settings(mode='Manuale'), power('on')), run(power('off'), mode='Manuale', power=True),
                       run(level('4'), mode='Manuale', power=True), run(settings(stop='17:30')), run(thermostat)):
            words = set(re.findall(r"[a-zà-ù]+", result['answer'].lower()))
            self.assertTrue(result['answer'].endswith('Confermi?'))
            self.assertFalse(words & {'confermo', 'conferma'}, result['answer'])


    def test_rule_requests_never_become_immediate_commands(self):
        # Field test 16/10/2026: the second sentence became an immediate "off".
        for q in ("quando la temperatura del puffer alto raggiunge i 42 gradi spegni il caminetto",
                  "voglio impostare una automazione per spegnere il caminetto quando il puffer alto raggiunge i 40 gradi",
                  "appena fuori scende sotto 5 gradi metti il bagno a 21",
                  "crea una regola: se la batteria è piena allora accendi la piscina"):
            result = ask(None, Planner({'operations': [power('off')]}), q, heating_snapshot=heating(mode='Manuale', power=True))
            self.assertEqual(result['status'], 'clarification_required', q)
            self.assertIn('non la eseguo come comando immediato', result['answer'])
            self.assertNotIn('confirmation', result)
            self.assertEqual(result['results'], [])

    def test_immediate_commands_and_questions_still_work(self):
        thermostat = {'id': 't', 'tool': 'set_thermostat_target', 'thermostat': 'thermostat_bath', 'target_c': '20.0'}
        result = ask(None, Planner({'operations': [thermostat]}), 'regola il bagno a 20 gradi')
        self.assertEqual(result['results'][0]['result']['status'], 'confirmation_required')
        self.assertEqual(run(power('off'), mode='Manuale', power=True)['results'][0]['result']['status'], 'confirmation_required')
        read = ask(None, Planner({'operations': [{'id': 'h', 'tool': 'current_heating'}]}), 'quando si accende il caminetto?',
                   heating_snapshot=heating())
        self.assertNotEqual(read['status'], 'clarification_required')


class UntilTests(unittest.TestCase):
    def ask(self, q, *ops, **snap):
        return ask(None, Planner({'operations': list(ops)}), q, heating_snapshot=heating(**snap))

    def test_manual_until_keeps_times_and_warns(self):
        r = self.ask('accendi il caminetto fino alle 19:00', settings(stop='19:00'), power('on'), mode='Manuale',
                     start='17:00', stop='19:00')
        self.assertEqual([op['tool'] for op in r['plan']], ['set_fireplace_power'])
        self.assertEqual(r['answer'], "Acceso a mano, il caminetto non si spegne da solo all'orario: dovrai spegnerlo "
                                      "tu, dall'app o dicendo «spegni il caminetto». Accendo il caminetto. Confermi?")
        self.assertNotIn('Acceso a mano', r['confirmation']['prompt'])

    def test_switch_to_manual_in_the_same_request(self):
        r = self.ask('metti in manuale e accendi il caminetto per 2 ore', settings(mode='Manuale', start='17:45', stop='19:45'),
                     power('on'))
        s = [op for op in r['plan'] if op['tool'] == 'set_fireplace_settings'][0]
        self.assertEqual((s['mode'], s['start_time'], s['stop_time']), ('Manuale', 'unchanged', 'unchanged'))
        self.assertTrue(r['answer'].startswith('Acceso a mano'))

    def test_without_until_or_without_switching_on_nothing_changes(self):
        r = self.ask('accendi il caminetto', power('on'), mode='Manuale')
        self.assertFalse(r['answer'].startswith('Acceso a mano'))
        r = self.ask('accendi il caminetto fino alle 19', power('on'), mode='Riscaldamento')
        self.assertEqual(r['answer'], 'Il caminetto è in modalità Riscaldamento: per accenderlo a mano passa prima a Manuale.')
        r = self.ask('metti il caminetto in integrazione caldaia dalle 12 alle 15', settings(mode='Integrazione Caldaia',
                     start='12:00', stop='15:00'))
        s = r['plan'][0]
        self.assertEqual((s['start_time'], s['stop_time']), ('12:00', '15:00'))


if __name__ == '__main__':
    unittest.main()
