import time
import unittest

import system_health_tools as sh
from assistant import ask
from test_fireplace_commands import Planner

NOW = 1_792_744_000_000


def host(ok=True, age_min=1, faults=0):
    checks = [{'id': 'peer', 'title': 'Raspberry .15', 'ok': True, 'detail': '.15 raggiungibile', 'advice': ''}]
    checks += [{'id': f'service:s{i}', 'title': f'Servizio s{i}', 'ok': False, 'detail': 'stato inactive',
                'advice': f'riavvia s{i}'} for i in range(faults)]
    return {'checked_at_ms': NOW - age_min * 60_000, 'ok': ok and not faults, 'checks': checks}


def snap(**hosts):
    return {'schema': 'house_ai.current_system_health_input.v1', 'hosts': hosts}


class SystemHealthTests(unittest.TestCase):
    def test_all_green(self):
        r = sh.read('all', snap(**{'.20': host(), '.15': host(age_min=2)}), NOW)
        self.assertEqual(sh.text(r), "L'impianto è a posto: .20 e .15 controllati 2 minuti fa, nessun guasto.")

    def test_faults_with_advice_and_stale_pi(self):
        r = sh.read('all', snap(**{'.20': host(age_min=12), '.15': host(faults=1)}), NOW)
        self.assertEqual(sh.text(r), "Il .20 non manda aggiornamenti da 12 minuti: il Raspberry o il suo controllo non "
                                     "funziona. Sul .15 un guasto: Servizio s0, stato inactive. Cosa fare: riavvia s0.")
        r = sh.read('15', snap(**{'.20': host(), '.15': host(faults=5)}), NOW)
        self.assertEqual(list(r['hosts']), ['.15'])
        self.assertIn('Sul .15 5 guasti', sh.text(r))
        self.assertIn('Altri 2 nei dettagli.', sh.text(r))

    def test_missing_and_invalid(self):
        self.assertIn('non è disponibile', sh.text(sh.read('all', None, NOW)))
        r = sh.read('all', snap(**{'.20': host()}), NOW)
        self.assertEqual(r['hosts']['.15']['light'], 'grey')
        for bad in [{'schema': 'x', 'hosts': {}}, snap(**{'.99': host()}),
                    snap(**{'.20': {**host(), 'extra': 1}}),
                    snap(**{'.20': {**host(), 'checks': [{'id': 'a', 'title': 'b', 'ok': 'si', 'detail': '', 'advice': ''}]}})]:
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                sh.validate_snapshot(bad)

    def test_through_the_assistant(self):
        fresh = {'checked_at_ms': int(time.time() * 1000) - 60_000, 'ok': True, 'checks': host()['checks']}
        r = ask(None, Planner({'operations': [{'id': 'h', 'tool': 'system_health', 'host': 'all'}]}),
                'come sta l impianto?', health_snapshot=snap(**{'.20': fresh, '.15': fresh}))
        self.assertTrue(r['answer'].startswith("L'impianto è a posto"), r['answer'])
        self.assertTrue(any('controlli' in n for x in r['results'] for n in x['result']['limitations']))


if __name__ == '__main__':
    unittest.main()
