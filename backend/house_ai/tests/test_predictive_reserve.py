import json
import tempfile
import unittest
from pathlib import Path

from assistant import ask
from energy_tools import PR_TOPICS, catalog, validate_plan, validate_predictive_reserve_snapshot
from evidence import day_bounds
from log_tools import read_day
from energy_presenter import log_text
from model_gateway import plan_schema


def snapshot(control='false', mode='ACTIVE', ready='true', connected=True, drop=()):
    values = {'control_enabled': control, 'operating_mode': mode, 'ready': ready}
    return {'schema': 'house_ai.current_predictive_reserve_input.v1', 'connected': connected,
            'observations': {metric: {'value': value, 'received_at_ms': 1_791_590_000_000,
                                      'retained': True, 'source_topic': PR_TOPICS[metric]}
                             for metric, value in values.items() if metric not in drop}}


class Planner:
    def __init__(self, plan):
        self.result = plan

    def plan(self, *args):
        return self.result


PR_PLAN = {'operations': [{'id': 'pr', 'tool': 'current_predictive_reserve'}]}


def diagnostics(result):
    """Spoken answer plus the technical notes shown under provenance and limits."""
    return result['answer'] + '\n' + '\n'.join(
        note for item in result.get('results', []) for note in item['result'].get('limitations', []))


class PredictiveReserveToolTests(unittest.TestCase):
    def test_tool_is_in_catalog_plan_schema_and_validation(self):
        self.assertIn('current_predictive_reserve', [t['name'] for t in catalog()['tools']])
        self.assertIn('current_predictive_reserve', json.dumps(plan_schema()))
        self.assertEqual(validate_plan(PR_PLAN)['operations'][0]['tool'], 'current_predictive_reserve')
        with self.assertRaises(ValueError):
            validate_plan({'operations': [{'id': 'pr', 'tool': 'current_predictive_reserve', 'mode': 'ACTIVE'}]})

    def test_snapshot_allowlist(self):
        self.assertIsNone(validate_predictive_reserve_snapshot(None))
        self.assertEqual(len(validate_predictive_reserve_snapshot(snapshot())['observations']), 3)
        bad = [snapshot(mode='MAYBE'), snapshot(control='on'), snapshot(ready='1')]
        wrong_topic = snapshot(); wrong_topic['observations']['ready']['source_topic'] = 'zara/interface/x/stat'
        extra = snapshot(); extra['observations']['extra'] = dict(extra['observations']['ready'])
        for item in bad + [wrong_topic, extra, {**snapshot(), 'schema': 'other'}]:
            with self.assertRaises(ValueError):
                validate_predictive_reserve_snapshot(item)

    def answer(self, **kwargs):
        return ask(None, Planner(PR_PLAN), 'Il PR è attivo?', pr_snapshot=snapshot(**kwargs))

    def test_effect_table(self):
        self.assertIn('disattivato', self.answer(control='false')['answer'])
        shadow = self.answer(control='true', mode='SHADOW')['answer']
        self.assertIn('SHADOW', shadow); self.assertIn('blocca gli avvii notturni', shadow)
        self.assertIn('modello non è pronto', self.answer(control='true', ready='false')['answer'])
        active = self.answer(control='true')
        self.assertIn('eco26dry', active['answer'])
        self.assertIn('Modalità: ACTIVE · Modello: pronto.', diagnostics(active))
        self.assertIn('non spiega decisioni di notti passate', diagnostics(active))
        self.assertNotIn('notti passate', active['answer'])
        unknown = self.answer(control='true', drop=('operating_mode',))['answer']
        self.assertIn('non sono disponibili', unknown)

    def test_missing_snapshot_is_insufficient(self):
        result = ask(None, Planner(PR_PLAN), 'Il PR è attivo?')
        self.assertEqual(result['status'], 'insufficient_data')
        self.assertIn('non è disponibile', result['answer'])

    def test_disconnected_is_declared(self):
        self.assertIn('La casa non è collegata', self.answer(connected=False)['answer'])

    def test_pr_reason_in_climate_log_is_explained(self):
        with tempfile.TemporaryDirectory() as folder:
            record = {'timestamp': day_bounds('2026-09-11')[0] + 3600_000,
                      'logica_controllo': {'motivo_ac': 'PR_PRESTART_PENDING'}}
            Path(folder, 'clima_controllo.log').write_text(json.dumps(record) + '\n')
            text = log_text(read_day(folder, 'climate', '2026-09-11'))
            self.assertIn('Motivo AC registrato: PR_PRESTART_PENDING', text)
            self.assertIn("in attesa dell'autorizzazione del Predictive Reserve", text)
