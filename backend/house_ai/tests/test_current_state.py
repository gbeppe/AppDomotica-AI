import unittest
from current_state import CurrentState


class CurrentStateTest(unittest.TestCase):
    def test_empty_state_is_unknown_not_off(self):
        state = CurrentState().snapshot()
        self.assertEqual(state['lights']['unknown'], 8)
        self.assertEqual(state['lights']['reported_off'], 0)
        self.assertTrue(all(e['value'] is None for e in state['entities']))

    def test_repeated_states_are_not_counted_twice_and_commands_are_ignored(self):
        state = CurrentState()
        topic = 'zara/interface/lights/living/power/stat'
        for now in [1, 2, 3]:
            state.ingest(topic, 'true', now, True)
        self.assertFalse(state.ingest(topic.replace('/stat', '/cmd'), 'false', 4))
        self.assertFalse(state.ingest('zara/interface/pool/pump/power/stat', 'true', 4))
        self.assertEqual(state.snapshot()['lights']['reported_on'], 1)
        self.assertIsNone(state.snapshot()['lights']['physical_on_count'])

    def test_invalid_replaces_old_value_and_older_delivery_is_ignored(self):
        state = CurrentState()
        topic = 'zara/interface/lights/living/power/stat'
        state.ingest(topic, 'true', 1)
        state.ingest(topic, 'unavailable', 3)
        self.assertFalse(state.ingest(topic, 'true', 2))
        self.assertEqual(state.snapshot()['lights']['unknown'], 8)

    def test_temperature_zero_valid_and_retained_does_not_prove_freshness(self):
        state = CurrentState()
        state.ingest('zara/interface/env/living/temperature/stat', '0', 1, True)
        state.ingest('zara/interface/energy/puffer_acs/stat', 'NaN', 2)
        entities = {e['id']: e for e in state.snapshot()['entities']}
        self.assertEqual(entities['temperature.living']['value'], 0)
        self.assertEqual(entities['temperature.living']['freshness'], 'unknown')
        self.assertIsNone(entities['temperature.living']['source_timestamp_ms'])
        self.assertIsNone(entities['temperature.acs']['value'])
