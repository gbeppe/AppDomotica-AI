import unittest
from datetime import datetime
from unittest import mock

import rules_tools
from assistant import ask
from test_fireplace_commands import Planner

NOW = datetime(2026, 10, 23, 16, 0, tzinfo=rules_tools.ROME)


def ac(mode, setpoint='none', start='now', end='none', id='a'):
    return {'id': id, 'tool': 'ac_command', 'mode': mode, 'setpoint': setpoint, 'from': start, 'to': end}


def climate(reason):
    return {'schema': 'house_ai.current_climate_input.v1', 'connected': True, 'observations': {
        'recorded_reason': {'value': reason, 'received_at_ms': 1_792_000_000_000, 'retained': True,
                            'source_topic': 'zara/interface/stato_condizionatore/motivo_logica/stat'}}}


def run(q, *ops, reason=None):
    with mock.patch.object(rules_tools, 'clock', return_value=NOW):
        return ask(None, Planner({'operations': list(ops)}), q,
                   climate_snapshot=climate(reason) if reason else None)


def rule(r):
    return [x['result']['rule'] for x in r['results'] if x['result'].get('rule')][0]


class AirConditionerTests(unittest.TestCase):
    """Phase T4, user's decisions of 23/10/2026: off and on modes, at most 6 hours, always confirmed."""

    def test_heat_until_and_off(self):
        r = run('accendi il condizionatore in caldo a 20 fino alle 22', ac('heat', '20', end='22:00'))
        self.assertEqual(r['answer'], "Accendo il condizionatore in caldo a 20 gradi fino alle 22:00, poi torna "
                                      "l'automatico. Confermi?")
        self.assertEqual(rule(r)['action'], {'tool': 'set_ac_override', 'mode': 'heat', 'setpoint': 20,
                                             'until': '2026-10-23T22:00:00+02:00'})
        self.assertEqual(rule(r)['trigger']['at'], '2026-10-23T16:00:00+02:00')
        self.assertTrue(rule(r)['start_active'])
        r = run('spegni il condizionatore fino alle 18', ac('off', end='18:00'))
        self.assertEqual(rule(r)['action'], {'tool': 'set_ac_override', 'mode': 'off', 'until': '2026-10-23T18:00:00+02:00'})
        self.assertIn('Spengo il condizionatore fino alle 18:00', r['answer'])

    def test_defaults_range_and_from(self):
        r = run('metti il condizionatore in deumidifica', ac('dry'))
        self.assertEqual(rule(r)['action'], {'tool': 'set_ac_override', 'mode': 'dry', 'setpoint': 24,
                                             'until': '2026-10-23T18:00:00+02:00'})      # 24 °C and 2 hours
        r = run('raffresca dalle 18 alle 20', ac('cool', start='18:00', end='20:00'))
        self.assertEqual((rule(r)['trigger']['at'], rule(r)['action']['until'], rule(r)['action']['setpoint']),
                         ('2026-10-23T18:00:00+02:00', '2026-10-23T20:00:00+02:00', 24))
        self.assertIn('Alle 18:00 (oggi) accendo il condizionatore in raffrescamento a 24 gradi', r['answer'])
        r = run('caldo a 25', ac('heat', '25'))
        self.assertNotIn('confirmation', r)
        self.assertIn('temperatura non ammessa in caldo: da 16 a 22 gradi', ' '.join(
            x['result'].get('reason', '') for x in r['results']))

    def test_six_hour_limit_auto_and_warning(self):
        r = run('spegni il condizionatore fino alle 23', ac('off', end='23:00'))
        self.assertIn('il condizionatore si comanda al massimo per 6 ore',
                      ' '.join(x['result'].get('reason', '') for x in r['results']))
        r = run('rimetti il condizionatore in automatico', ac('auto'))
        self.assertEqual(r['answer'], 'Rimetto il condizionatore in automatico con AI Climate. Confermi?')
        self.assertEqual(rule(r)['action'], {'tool': 'set_ac_auto'})
        r = run('caldo fino alle 18', ac('heat', end='18:00'), reason='SISTEMA_DISABILITATO_DA_UTENTE')
        self.assertIn('attenzione: AI Climate è disattivato', r['answer'])

    def test_rules_snapshot_with_an_ac_rule_is_valid_and_deletable(self):
        snapshot = {'schema': 'house_ai.current_rules_input.v1', 'engine_mode': 'active', 'rules': [
            {'id': 'rt-1', 'text': 'accendo il condizionatore', 'status': 'active',
             'action': {'tool': 'set_ac_override', 'mode': 'heat', 'setpoint': 20, 'until': '2026-10-23T18:00:00+02:00'}}]}
        clean = rules_tools.validate_rules_snapshot(snapshot)
        self.assertEqual(clean['rules'][0]['action'], {'tool': 'set_ac_override'})
        deleted = rules_tools.delete_rules('air_conditioner', clean)
        self.assertEqual(deleted['ids'], ['rt-1'])


if __name__ == '__main__':
    unittest.main()
