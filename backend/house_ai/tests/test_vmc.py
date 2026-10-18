import unittest
from datetime import datetime
from unittest import mock

import rules_tools
from assistant import ask
from test_fireplace_commands import Planner

NOW = datetime(2026, 10, 18, 17, 45, tzinfo=rules_tools.ROME)


def vmc(speed, start='now', end='none', id='v'):
    return {'id': id, 'tool': 'vmc_command', 'speed': speed, 'from': start, 'to': end}


def run(q, *ops, humidity=None):
    env = None
    if humidity is not None:
        env = {'schema': 'house_ai.current_environment_input.v1', 'connected': True, 'observations': {
            'living.humidity': {'value': humidity, 'received_at_ms': 1_792_000_000_000, 'retained': True,
                                'source_topic': 'zara/interface/env/living/humidity/stat'}}}
    with mock.patch.object(rules_tools, 'clock', return_value=NOW):
        return ask(None, Planner({'operations': list(ops)}), q, environment_snapshot=env)


def rule(r, i=0):
    return [x['result']['rule'] for x in r['results'] if x['result'].get('rule')][i]


class VmcTests(unittest.TestCase):
    def test_plain_speed_and_until(self):
        r = run('metti la VMC a 3', vmc('3'))
        self.assertEqual(r['answer'], 'Metto la VMC a velocità 3 (AI Climate potrà cambiarla). Confermi?')
        self.assertEqual(rule(r)['action'], {'tool': 'set_vmc_speed', 'speed': 3})
        r = run('metti la VMC a velocità 3 fino alle 20', vmc('3', end='20:00'))
        self.assertEqual(r['answer'], "Metto la VMC a velocità 3 fino alle 20:00, poi torna l'automatico. Confermi?")
        self.assertEqual(rule(r)['action'], {'tool': 'set_vmc_override', 'speed': 3, 'until': '2026-10-18T20:00:00+02:00'})
        self.assertEqual(rule(r)['trigger']['at'], '2026-10-18T17:45:00+02:00')

    def test_from_and_range(self):
        r = run('VMC a 2 dalle 18 alle 20', vmc('2', '18:00', '20:00'))
        self.assertEqual(r['answer'], "Alle 18:00 (oggi) metto la VMC a velocità 2 fino alle 20:00, poi torna l'automatico. Confermi?")
        self.assertEqual(rule(r)['trigger']['at'], '2026-10-18T18:00:00+02:00')
        r = run('VMC a 2 dalle 18', vmc('2', '18:00'))
        self.assertEqual(rule(r)['action']['until'], '2026-10-18T20:00:00+02:00')          # 2 hours by default
        r = run('VMC a 2 dalle 17 alle 19', vmc('2', '17:00', '19:00'))                      # 17:00 already past
        self.assertEqual((rule(r)['trigger']['at'], rule(r)['action']['until']),
                         ('2026-10-18T17:45:00+02:00', '2026-10-18T19:00:00+02:00'))
        r = run('VMC a 1 dalle 23 alle 7', vmc('1', '23:00', '07:00'))
        self.assertEqual(rule(r)['action']['until'], '2026-10-19T07:00:00+02:00')
        self.assertIn('fino alle 07:00 (domani)', r['answer'])

    def test_auto_and_humidity_warning(self):
        r = run('rimetti la VMC in automatico', vmc('auto'))
        self.assertEqual(r['answer'], 'Rimetto la VMC in automatico con AI Climate. Confermi?')
        self.assertEqual(rule(r)['action'], {'tool': 'set_vmc_auto'})
        r = run('VMC a 1 fino alle 20', vmc('1', end='20:00'), humidity=67.4)
        self.assertIn('(attenzione: umidità al 67 per cento, AI Climate la terrebbe a 3)', r['answer'])
        self.assertEqual(rule(r)['action']['speed'], 1)                                     # the user's choice wins


if __name__ == '__main__':
    unittest.main()
