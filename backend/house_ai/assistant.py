"""Dynamic interpretation followed by validated tools and deterministic answers."""
from datetime import datetime
from zoneinfo import ZoneInfo
from energy_tools import (catalog, execute, validate_climate_snapshot, validate_current_snapshot,
                          validate_lights_snapshot, validate_predictive_reserve_snapshot,
                          validate_thermostats_snapshot, validate_environment_snapshot)
from energy_presenter import compose, notes


def ask(client, planner, question, today=None, current_snapshot=None, climate_snapshot=None, lights_snapshot=None,
        conversation_context=None, log_root=None, pr_snapshot=None, thermostats_snapshot=None,
        environment_snapshot=None):
    if not isinstance(question, str) or not question.strip() or len(question) > 1000:
        raise ValueError('Invalid question')
    validate_current_snapshot(current_snapshot)
    validate_climate_snapshot(climate_snapshot)
    validate_lights_snapshot(lights_snapshot)
    validate_predictive_reserve_snapshot(pr_snapshot)
    validate_thermostats_snapshot(thermostats_snapshot)
    validate_environment_snapshot(environment_snapshot)
    if conversation_context is not None and conversation_context != {
            'domain': 'climate', 'focus': 'air_conditioner'}:
        raise ValueError('Invalid conversation context')
    now = datetime.now(ZoneInfo('Europe/Rome'))
    base = {'schema': 'house_ai.assistant_answer.v1', 'question': question,
            'generated_at': now.isoformat(), 'results': [], 'limitations': []}
    if planner is None:
        return {**base, 'status': 'not_configured',
                'answer': 'Il pianificatore dinamico non è configurato sul backend.'}
    current = today or now.date()
    try:
        planned_question = question.strip()
        if planned_question.lower().rstrip(' ?!') in ('perché', 'perche'):
            if conversation_context == {"domain": "climate", "focus": "air_conditioner"}:
                planned_question = "Perché il condizionatore è nello stato attuale?"
        proposed = planner.plan(planned_question, catalog(), current)
    except (ValueError, RuntimeError):
        raise RuntimeError('Planner unavailable or invalid response') from None
    try:
        execution = execute(client, proposed, current_snapshot=current_snapshot,
                            climate_snapshot=climate_snapshot, lights_snapshot=lights_snapshot,
                            log_root=log_root, pr_snapshot=pr_snapshot,
                            thermostats_snapshot=thermostats_snapshot,
                            environment_snapshot=environment_snapshot)
    except (ValueError, TypeError, OverflowError):
        raise RuntimeError('Planner returned an invalid plan') from None
    if execution['clarification']:
        return {**base, 'status': 'clarification_required', 'answer': execution['clarification']}
    # Spoken answers stay short; technical caveats travel with each result for the diagnostics view.
    for item in execution['results']:
        result = item['result']
        result['limitations'] = list(dict.fromkeys(list(result.get('limitations', [])) + notes(item)))
    statuses = [item['result']['status'] for item in execution['results']]
    overall = ('insufficient_data' if all(s in ('insufficient_data', 'missing', 'unavailable', 'source_rejected',
                                                'source_not_updated') for s in statuses)
               else 'partial' if any(s not in ('complete', 'command_ready') for s in statuses) else 'complete')
    context = ({'domain': 'climate', 'focus': 'air_conditioner'}
               if any(op['tool'] == 'current_air_conditioner' for op in execution['operations']) else None)
    return {**base, 'status': overall,
            'answer': compose(execution['results']),
            'plan': execution['operations'], 'results': execution['results'],
            **({'conversation_context': context} if context else {}),
            'limitations': ['Il pianificatore interpreta; i valori sono calcolati dagli strumenti validati.',
                            'Le risposte descrivono le fonti al momento della richiesta e non si aggiornano automaticamente.']}
