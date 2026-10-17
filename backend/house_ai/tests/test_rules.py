import json
import re
import unittest

from assistant import ask
from energy_tools import catalog, validate_plan
from model_gateway import SYSTEM, plan_schema


class Planner:
    def __init__(self, plan):
        self.result = plan

    def plan(self, *args):
        return self.result


def create(trigger='threshold', metric='puffer_top', op='above', value='42', at='none', days='none',
           action='set_fireplace_power', target='none', setting='off', lifetime='once'):
    return {'id': 'r', 'tool': 'create_rule', 'trigger': trigger, 'metric': metric, 'op': op, 'value': value,
            'at': at, 'days': days, 'action': action, 'target': target, 'setting': setting, 'lifetime': lifetime}


def snapshot(*rules, mode='shadow'):
    return {'schema': 'house_ai.current_rules_input.v1', 'engine_mode': mode,
            'rules': [{'id': i, 'text': t, 'status': s, 'action': {'tool': a}} for i, t, s, a in rules]}


def run(*ops, rules=None, q='regola'):
    return ask(None, Planner({'operations': list(ops)}), q, rules_snapshot=rules)


PUFFER = ('r-puffer-42', 'quando il puffer alto supera 42 gradi spengo il caminetto, una volta', 'test', 'set_fireplace_power')
LIGHTS = ('r-luci', 'alle 22:00 ogni giorno spengo tutte le luci, ogni volta', 'active', 'set_light_scene')


class RuleTests(unittest.TestCase):
    def test_catalog_and_prompt(self):
        names = [t['name'] for t in catalog()['tools']]
        self.assertTrue({'create_rule', 'list_rules', 'delete_rule'} <= set(names))
        self.assertIn('create_rule', json.dumps(plan_schema()))
        self.assertIn('si creano SOLO con create_rule', SYSTEM)
        with self.assertRaises(ValueError):
            validate_plan({'operations': [create(action='opensprinkler')]})

    def test_the_users_rule_asks_for_confirmation(self):
        q = 'quando la temperatura del puffer alto raggiunge i 42 gradi spegni il caminetto'
        result = run(create(), q=q)
        r = result['results'][0]['result']
        self.assertEqual(r['status'], 'confirmation_required')
        self.assertEqual(r['rule'], {'text': 'quando il puffer alto supera 42 gradi spengo il caminetto, una volta',
                                     'trigger': {'kind': 'threshold', 'metric': 'puffer_top', 'op': 'above', 'value': 42.0},
                                     'conditions': [], 'action': {'tool': 'set_fireplace_power', 'state': 'off'},
                                     'lifetime': {'kind': 'once'}})
        self.assertEqual(result['answer'], 'Creo questa regola: quando il puffer alto supera 42 gradi spengo il caminetto, '
                                           'una volta. Confermi?')
        self.assertEqual(result['confirmation']['done'], 'Ok, creo questa regola: quando il puffer alto supera 42 gradi '
                                                         'spengo il caminetto, una volta.')
        self.assertFalse(set(re.findall(r'[a-zà-ù]+', result['answer'].lower())) & {'confermo', 'conferma'})

    def test_time_rule_and_thermostat_rule(self):
        r = run(create(trigger='time', metric='none', op='none', value='none', at='22:00', days='weekdays',
                       action='set_light_scene', setting='all_off', lifetime='always'))['results'][0]['result']
        self.assertEqual(r['rule']['trigger'], {'kind': 'time', 'at': '22:00', 'days': [1, 2, 3, 4, 5]})
        self.assertEqual(r['text'], 'alle 22:00 nei giorni feriali spengo tutte le luci, ogni volta')
        t = run(create(metric='outdoor', op='below', value='5', action='set_thermostat_target',
                       target='thermostat_bath', setting='21'))['results'][0]['result']
        self.assertEqual(t['rule']['action'], {'tool': 'set_thermostat_target', 'thermostat': 'thermostat_bath', 'target_c': 21.0})
        self.assertEqual(t['text'], 'quando la temperatura esterna scende sotto 5 gradi imposto il termostato del bagno a '
                                    '21 gradi, una volta')

    def test_refused_rules(self):
        cases = [(create(action='set_thermostat_target', target='thermostat_bath', setting='25'), 'setpoint ammesso'),
                 (create(action='set_light_state', target='pool_water', setting='on'), 'luce non ammessa'),
                 (create(metric='none'), 'condizione non ammessa'),
                 (create(value='200'), 'condizione non ammessa'),
                 (create(trigger='time', at='25:00', days='all'), 'orario non valido')]
        for op, reason in cases:
            result = run(op)
            self.assertEqual(result['results'][0]['result']['status'], 'rejected', reason)
            self.assertIn(reason, result['answer'])
            self.assertNotIn('confirmation', result)

    def test_max_ten_rules(self):
        full = snapshot(*[(f'r-{i:02d}', f'regola {i}', 'active', 'set_light_scene') for i in range(10)])
        self.assertEqual(run(create(), rules=full)['answer'], 'Ci sono già 10 regole attive: cancellane una.')

    def test_list(self):
        self.assertEqual(run({'id': 'l', 'tool': 'list_rules'}, rules=snapshot(PUFFER, LIGHTS))['answer'],
                         'Ci sono 2 regole: 1) quando il puffer alto supera 42 gradi spengo il caminetto, una volta '
                         '(in prova); 2) alle 22:00 ogni giorno spengo tutte le luci, ogni volta (attiva). '
                         'Il motore delle regole è in prova: per ora annuncia soltanto.')
        self.assertEqual(run({'id': 'l', 'tool': 'list_rules'}, rules=snapshot())['answer'], 'Non ci sono regole attive.')

    def test_delete(self):
        result = run({'id': 'd', 'tool': 'delete_rule', 'about': 'fireplace'}, rules=snapshot(PUFFER, LIGHTS),
                     q='cancella la regola del caminetto')
        self.assertEqual(result['results'][0]['result']['ids'], ['r-puffer-42'])
        self.assertEqual(result['answer'], 'Cancello la regola «quando il puffer alto supera 42 gradi spengo il caminetto, '
                                           'una volta». Confermi?')
        many = run({'id': 'd', 'tool': 'delete_rule', 'about': 'any'}, rules=snapshot(PUFFER, LIGHTS))
        self.assertTrue(many['answer'].startswith('Dimmi quale regola cancellare:'))
        everything = run({'id': 'd', 'tool': 'delete_rule', 'about': 'all'}, rules=snapshot(PUFFER, LIGHTS))
        self.assertEqual(everything['answer'], 'Cancello 2 regole. Confermi?')
        self.assertEqual(run({'id': 'd', 'tool': 'delete_rule', 'about': 'all'}, rules=snapshot())['answer'],
                         'Non ci sono regole da cancellare.')

    def test_snapshot_validation(self):
        with self.assertRaises(ValueError):
            run({'id': 'l', 'tool': 'list_rules'}, rules={**snapshot(), 'engine_mode': 'on'})
        bad = snapshot(PUFFER); bad['rules'][0]['id'] = 'Bad Id!'
        with self.assertRaises(ValueError):
            run({'id': 'l', 'tool': 'list_rules'}, rules=bad)

    def test_rule_wording_still_blocks_immediate_commands(self):
        result = run({'id': 'p', 'tool': 'set_light_scene', 'scene': 'all_off'}, q='alle 22 spegni tutte le luci')
        self.assertIn('non la eseguo come comando immediato', result['answer'])
        fireplace = {'id': 's', 'tool': 'set_fireplace_settings', 'mode': 'Acqua Sanitaria', 'start_time': '12:00',
                     'stop_time': '13:00', 'auto_power': 'unchanged'}
        times = run(fireplace, q='metti il caminetto in acqua sanitaria dalle 12 alle 13')
        self.assertEqual(times['results'][0]['result']['status'], 'confirmation_required')


if __name__ == '__main__':
    unittest.main()
