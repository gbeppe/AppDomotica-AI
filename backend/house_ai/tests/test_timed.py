import unittest
from datetime import datetime
from unittest import mock

import rules_tools
from assistant import ask
from test_fireplace_commands import Planner, heating, power, settings

NOW = datetime(2026, 10, 18, 17, 45, tzinfo=rules_tools.ROME)


def sched(action, setting, at, target='none', id='t'):
    return {'id': id, 'tool': 'schedule_command', 'action': action, 'target': target, 'setting': setting, 'at': at}


def run(q, *ops, mode='Manuale', fire=False):
    with mock.patch.object(rules_tools, 'clock', return_value=NOW):
        return ask(None, Planner({'operations': list(ops)}), q, heating_snapshot=heating(mode=mode, power=fire))


def rules(r):
    return [x['result']['rule'] for x in r['results'] if x['result'].get('rule')]


class TimedCommandTests(unittest.TestCase):
    def test_fireplace_until_one_confirmation(self):
        r = run('accendi il caminetto fino alle 19:00', power('on'), sched('set_fireplace_power', 'off', '19:00'))
        self.assertEqual(r['answer'], 'Accendo il caminetto e alle 19:00 (oggi) spengo il caminetto. Confermi?')
        [rule] = rules(r)
        self.assertEqual(rule['trigger'], {'kind': 'at', 'at': '2026-10-18T19:00:00+02:00'})
        self.assertEqual((rule['action'], rule['start_active'], rule['lifetime']),
                         ({'tool': 'set_fireplace_power', 'state': 'off'}, True, {'kind': 'once'}))
        self.assertRegex(rule['id'], r'^rt-[0-9a-f]+-0$')

    def test_light_until_and_range(self):
        r = run('accendi la luce del soggiorno fino alle 22', {'id': 'l', 'tool': 'set_light_state', 'light': 'lights_living',
                'state': 'on'}, sched('set_light_state', 'off', '22:00', 'lights_living'))
        self.assertEqual([x['text'] for x in (y['result'] for y in r['results'])],
                         ['adesso accendo la luce del soggiorno', 'alle 22:00 (oggi) spengo la luce del soggiorno'])
        self.assertEqual(r['answer'], 'Adesso accendo la luce del soggiorno e alle 22:00 (oggi) spengo la luce del soggiorno. Confermi?')
        self.assertEqual(len({x['id'] for x in rules(r)}), 2)
        # 'dalle 18 alle 20' said at 17:45; 'dalle 17 alle 20' -> 17:00 is past, the first part is now
        r = run('accendi la luce del portico dalle 18 alle 20', sched('set_light_state', 'on', '18:00', 'lights_porch_entrance', 'a'),
                sched('set_light_state', 'off', '20:00', 'lights_porch_entrance', 'b'))
        self.assertEqual([x['trigger']['at'] for x in rules(r)], ['2026-10-18T18:00:00+02:00', '2026-10-18T20:00:00+02:00'])
        r = run('accendi la luce del portico dalle 17 alle 20', sched('set_light_state', 'on', '17:00', 'lights_porch_entrance', 'a'),
                sched('set_light_state', 'off', '20:00', 'lights_porch_entrance', 'b'))
        self.assertTrue(r['results'][0]['result']['text'].startswith('adesso accendo'))

    def test_tomorrow_and_refusals(self):
        r = run('spegni tutte le luci fino alle 7', {'id': 's', 'tool': 'set_light_scene', 'scene': 'all_off'},
                sched('set_light_scene', 'all_on', '07:00'))
        self.assertEqual(rules(r)[1]['trigger']['at'], '2026-10-19T07:00:00+02:00')
        self.assertIn('alle 07:00 (domani) accendo tutte le luci', r['answer'])
        r = run('accendi il caminetto dalle 18 alle 20', sched('set_fireplace_power', 'on', '18:00', id='a'),
                sched('set_fireplace_power', 'off', '20:00', id='b'), mode='Riscaldamento')
        self.assertTrue(r['answer'].startswith('Il caminetto non è in Manuale'))
        self.assertEqual(r['results'], [])
        r = run('metti in manuale e accendi il caminetto fino alle 19', settings(mode='Manuale'), power('on'),
                sched('set_fireplace_power', 'off', '19:00'), mode='Riscaldamento')
        self.assertEqual(r['answer'], 'Metto il caminetto in modalità Manuale, lo accendo e alle 19:00 (oggi) spengo il caminetto. Confermi?')
        r = run('accendi la piscina fino alle 20', sched('set_light_state', 'on', '20:00', 'pool_water'))
        self.assertIn('luce non ammessa', r['answer'])


if __name__ == '__main__':
    unittest.main()
