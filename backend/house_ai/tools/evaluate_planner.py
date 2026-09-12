"""Evaluate a configured gateway on synthetic Italian requests, without reading household data."""
import json
import os
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from planner import HttpJsonPlanner
from energy_tools import catalog, validate_plan


def evaluate(planner, suite):
    results = []
    for case in suite['cases']:
        try:
            plan = validate_plan(planner.plan(case['question'], catalog(), date.fromisoformat(suite['today'])))
            if case.get('clarification'):
                passed = bool(plan['clarification'])
            else:
                actual = [{k: v for k, v in op.items() if k != 'id'} for op in plan['operations']]
                key = lambda op: json.dumps(op, sort_keys=True)
                passed = not plan['clarification'] and sorted(actual, key=key) == sorted(case['operations'], key=key)
            results.append({'id': case['id'], 'passed': passed, 'plan': plan})
        except (ValueError, RuntimeError, TypeError):
            results.append({'id': case['id'], 'passed': False, 'error': 'Planner unavailable or invalid plan'})
    return {'schema': 'house_ai.planner_evaluation.v1', 'count': len(results),
            'passed': sum(row['passed'] for row in results), 'results': results,
            'limitations': ['Regression suite written with the implementation; not an independent linguistic evaluation.',
                            'No household source or actuator is accessed.']}


if __name__ == '__main__':
    url, token = os.environ.get('HOUSE_AI_PLANNER_URL'), os.environ.get('HOUSE_AI_PLANNER_TOKEN')
    if not url or not token:
        raise SystemExit('Configure HOUSE_AI_PLANNER_URL and HOUSE_AI_PLANNER_TOKEN in the environment')
    suite = json.loads(Path(__file__).resolve().parents[1].joinpath('evaluation/energy_it.json').read_text())
    result = evaluate(HttpJsonPlanner(url, token), suite)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    raise SystemExit(0 if result['passed'] == result['count'] else 1)
