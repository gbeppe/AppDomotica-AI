import json
import unittest

from assistant import ask
from energy_tools import LIGHT_SCENES, LIGHT_TOPICS, catalog, validate_plan
from model_gateway import SYSTEM, plan_schema


class Planner:
    def __init__(self, plan):
        self.result = plan

    def plan(self, *args):
        return self.result


def scene(name):
    return {'operations': [{'id': 's', 'tool': 'set_light_scene', 'scene': name}]}


class LightSceneTests(unittest.TestCase):
    def test_catalog_schema_and_prompt(self):
        self.assertEqual(LIGHT_SCENES, ('tv', 'sleep', 'all_on', 'all_off'))
        self.assertIn('set_light_scene', [t['name'] for t in catalog()['tools']])
        self.assertIn('set_light_scene', json.dumps(plan_schema()))
        self.assertIn('set_light_scene', SYSTEM)

    def test_only_dispatcher_scenes_are_valid(self):
        for name in LIGHT_SCENES:
            self.assertEqual(validate_plan(scene(name))['operations'][0]['scene'], name)
        for bad in ('cinema', 'ALL_OFF', 'off', ''):
            with self.assertRaises(ValueError):
                validate_plan(scene(bad))
        with self.assertRaises(ValueError):
            validate_plan({'operations': [{'id': 's', 'tool': 'set_light_scene', 'scene': 'tv',
                                           'topic': 'zara/interface/lights/scene/cmd'}]})

    def test_answers_explain_real_effect_without_topics(self):
        spoken = {'tv': 'Ok, attivo la modalità TV.', 'sleep': 'Ok, tra 2 minuti spengo tutte le luci.',
                  'all_on': 'Ok, accendo tutte le luci.', 'all_off': 'Ok, spengo tutte le luci.'}
        details = {'tv': 'spegne libreria e lampadario TV', 'sleep': 'dopo 2 minuti',
                   'all_on': 'in modalità vacanza non ha effetto', 'all_off': 'interne, esterne e piscina'}
        for name, text in spoken.items():
            result = ask(None, Planner(scene(name)), 'scena')
            self.assertEqual(result['status'], 'complete')
            self.assertEqual(result['results'][0]['result']['schema'], 'house_ai.light_scene_command.v1')
            self.assertEqual(text, result['answer'])
            self.assertIn(details[name], ' '.join(result['results'][0]['result']['limitations']))
            self.assertNotIn('zara/', json.dumps(result))


class ProlungaTests(unittest.TestCase):
    def test_prolunga_is_a_mapped_light(self):
        self.assertEqual(LIGHT_TOPICS['lights_prolunga'], 'zara/interface/lights/prolunga/power/stat')
        plan = {'operations': [{'id': 'p', 'tool': 'set_light_state', 'light': 'lights_prolunga', 'state': 'off'}]}
        result = ask(None, Planner(plan), 'Spegni la prolunga')
        self.assertEqual('Ok, spengo la prolunga.', result['answer'])

    def test_prolunga_counts_in_summary(self):
        snapshot = {'schema': 'house_ai.current_lights_input.v1', 'connected': True, 'observations': {
            'lights_prolunga': {'value': True, 'received_at_ms': 1_791_666_000_000, 'retained': True,
                                'source_topic': LIGHT_TOPICS['lights_prolunga']}}}
        plan = {'operations': [{'id': 'l', 'tool': 'current_lights', 'light': 'all'}]}
        answer = ask(None, Planner(plan), 'Cosa è acceso?', lights_snapshot=snapshot)['answer']
        self.assertTrue(answer.startswith('Accese: prolunga.'))
        self.assertIn('Senza dato: soggiorno', answer)


if __name__ == '__main__':
    unittest.main()
