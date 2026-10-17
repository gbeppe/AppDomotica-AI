import unittest

from assistant import ask


class Planner:
    def __init__(self, plan):
        self.result = plan

    def plan(self, *args):
        return self.result


def snapshot(**over):
    base = {'schema': 'house_ai.current_heating_profile_input.v1', 'profile': 'ridotto', 'mode': 'auto',
            'outdoor_mean_72h': 18.1, 'hours_of_data': 34, 'timer_mode': 'shadow', 'thermostats': {
                'thermostat_living': {'inside': False, 'value': 17.5, 'slot': None, 'min': 17.5, 'max': 18,
                                      'next': {'day_offset': 1, 'at': '17:00', 'inside': True, 'value': 18}},
                'thermostat_bath': {'inside': True, 'value': 18, 'slot': 'sera', 'min': 17, 'max': 18,
                                    'next': {'day_offset': 0, 'at': '22:00', 'inside': False, 'value': 17}}}}
    base.update(over)
    return base


def run(about, profile=None, q='che profilo è attivo?'):
    plan = {'operations': [{'id': 'h', 'tool': 'current_heating_profile', 'about': about}]}
    return ask(None, Planner(plan), q, profile_snapshot=profile)


class HeatingProfileTests(unittest.TestCase):
    def test_profile_while_auto_waits_for_data(self):
        self.assertEqual(run('profile', snapshot())['answer'],
                         'Profilo di riscaldamento: Ridotto, automatico, ma con solo 34 ore di dati esterni su 48: '
                         'resta Ridotto finché non ha 48 ore. Soglie: Pieno sotto 10 gradi, Spento sopra 16 gradi di '
                         'media esterna. Il timer è in prova: per ora non cambia i termostati.')

    def test_profile_with_enough_data_and_fixed(self):
        auto = run('profile', snapshot(profile='spento', hours_of_data=60, outdoor_mean_72h=17.25))['answer']
        self.assertIn('Spento, automatico: la media esterna delle ultime 72 ore è 17,2 gradi, sopra 16 gradi.', auto)
        fixed = run('all', snapshot(mode='pieno', profile='pieno', timer_mode='active'))['answer']
        self.assertTrue(fixed.startswith('Profilo di riscaldamento: Pieno, scelto a mano (Pieno fisso).'))
        self.assertNotIn('in prova', fixed)

    def test_one_thermostat(self):
        self.assertEqual(run('thermostat_bath', snapshot(), q='quando cambia il bagno?')['answer'],
                         'Bagno: in fascia sera, 18 gradi; prossimo cambio oggi alle 22:00 a 17 gradi. '
                         'Il timer è in prova: per ora non cambia i termostati.')
        living = run('thermostat_living', snapshot(), q='perché il soggiorno è a 17,5?')['answer']
        self.assertTrue(living.startswith('Soggiorno: fuori fascia, 17,5 gradi; prossimo cambio domani alle 17:00'))

    def test_missing_and_invalid(self):
        self.assertEqual(run('all')['answer'], 'Non ho ricevuto il profilo di riscaldamento: riprova fra poco.')
        for bad in (snapshot(profile='turbo'), snapshot(hours_of_data=-1), {**snapshot(), 'extra': 1}):
            with self.assertRaises(ValueError):
                run('all', bad)
        with self.assertRaises(Exception):
            ask(None, Planner({'operations': [{'id': 'h', 'tool': 'current_heating_profile', 'about': 'kitchen'}]}),
                'x', profile_snapshot=snapshot())


if __name__ == '__main__':
    unittest.main()
