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
        expected = {'tv': 'spengo libreria e lampadario TV', 'sleep': 'tra 2 minuti',
                    'all_on': 'modalità vacanza la scena non ha effetto', 'all_off': 'interne, esterne e piscina'}
        for name, text in expected.items():
            result = ask(None, Planner(scene(name)), 'scena')
            self.assertEqual(result['status'], 'complete')
            self.assertEqual(result['results'][0]['result']['schema'], 'house_ai.light_scene_command.v1')
            self.assertIn(text, result['answer'])
            self.assertNotIn('zara/', json.dumps(result))


class ProlungaTests(unittest.TestCase):
    def test_prolunga_is_a_mapped_light(self):
        self.assertEqual(LIGHT_TOPICS['lights_prolunga'], 'zara/interface/lights/prolunga/power/stat')
        plan = {'operations': [{'id': 'p', 'tool': 'set_light_state', 'light': 'lights_prolunga', 'state': 'off'}]}
        result = ask(None, Planner(plan), 'Spegni la prolunga')
        self.assertIn('spegnimento di Prolunga', result['answer'])

    def test_prolunga_counts_in_summary(self):
        snapshot = {'schema': 'house_ai.current_lights_input.v1', 'connected': True, 'observations': {
            'lights_prolunga': {'value': True, 'received_at_ms': 1_791_666_000_000, 'retained': True,
                                'source_topic': LIGHT_TOPICS['lights_prolunga']}}}
        plan = {'operations': [{'id': 'l', 'tool': 'current_lights', 'light': 'all'}]}
        answer = ask(None, Planner(plan), 'Cosa è acceso?', lights_snapshot=snapshot)['answer']
        self.assertIn('Tra i 10 punti luce mappati, risultano accesi 1', answer)
        self.assertIn('Accesi dichiarati: Prolunga.', answer)


if __name__ == '__main__':
    unittest.main()
