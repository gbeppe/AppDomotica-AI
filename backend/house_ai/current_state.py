"""Interpret only mapped public states. No MQTT client, commands or device I/O."""
import json
import math
from pathlib import Path


def load_catalog():
    return json.loads(Path(__file__).with_name('current_catalog.json').read_text())


class CurrentState:
    def __init__(self, catalog=None):
        self.catalog = catalog if catalog is not None else load_catalog()
        self.by_topic = {entity['topic']: entity for entity in self.catalog['entities']}
        self.observations = {}

    def ingest(self, topic, payload, received_at_ms, retained=False):
        entity = self.by_topic.get(topic)
        if entity is None:
            return False
        if type(received_at_ms) is not int or received_at_ms < 0:
            raise ValueError('Invalid receive timestamp')
        previous = self.observations.get(entity['id'])
        if previous and received_at_ms < previous['received_at_ms']:
            return False
        text = str(payload).strip().lower()
        if entity['kind'] == 'light':
            value = {'true': True, 'on': True, '1': True,
                     'false': False, 'off': False, '0': False}.get(text)
        else:
            try:
                value = float(text.replace(',', '.'))
                if not math.isfinite(value):
                    value = None
            except ValueError:
                value = None
        self.observations[entity['id']] = {
            'value': value, 'quality': 'reported' if value is not None else 'invalid',
            'received_at_ms': received_at_ms, 'source_timestamp_ms': None,
            'retained': retained, 'freshness': 'unknown',
        }
        return True

    def snapshot(self):
        entities = []
        for entity in self.catalog['entities']:
            observation = self.observations.get(entity['id'], {
                'value': None, 'quality': 'missing', 'received_at_ms': None,
                'source_timestamp_ms': None, 'retained': None, 'freshness': 'unknown',
            })
            entities.append({**entity, **observation})
        lights = [entity for entity in entities if entity['kind'] == 'light']
        return {
            'schema': 'house_ai.current_state.v1', 'entities': entities,
            'lights': {
                'mapped': len(lights),
                'reported_on': sum(e['value'] is True for e in lights),
                'reported_off': sum(e['value'] is False for e in lights),
                'unknown': sum(e['value'] is None for e in lights),
                'physical_on_count': None,
                'whole_house_coverage': 'not_verified',
            },
            'limitations': self.catalog['limitations'],
        }
