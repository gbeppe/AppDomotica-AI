"""Dynamic interpretation followed by validated tools and deterministic answers."""
from datetime import datetime
from zoneinfo import ZoneInfo
from energy_tools import catalog, execute, validate_current_snapshot
from energy_presenter import describe


def ask(client, planner, question, today=None, current_snapshot=None, log_root=None):
    if not isinstance(question, str) or not question.strip() or len(question) > 1000:
        raise ValueError('Invalid question')
    validate_current_snapshot(current_snapshot)
    now = datetime.now(ZoneInfo('Europe/Rome'))
    base = {'schema': 'house_ai.assistant_answer.v1', 'question': question,
            'generated_at': now.isoformat(), 'results': [], 'limitations': []}
    if planner is None:
        return {**base, 'status': 'not_configured',
                'answer': 'Il pianificatore dinamico non è configurato sul backend.'}
    current = today or now.date()
    try:
        proposed = planner.plan(question.strip(), catalog(), current)
    except (ValueError, RuntimeError):
        raise RuntimeError('Planner unavailable or invalid response') from None
    try:
        execution = execute(client, proposed, current_snapshot=current_snapshot, log_root=log_root)
    except (ValueError, TypeError, OverflowError):
        raise RuntimeError('Planner returned an invalid plan') from None
    if execution['clarification']:
        return {**base, 'status': 'clarification_required', 'answer': execution['clarification']}
    statuses = [item['result']['status'] for item in execution['results']]
    overall = ('insufficient_data' if all(s in ('insufficient_data', 'missing', 'unavailable', 'source_rejected') for s in statuses)
               else 'partial' if any(s != 'complete' for s in statuses) else 'complete')
    return {**base, 'status': overall,
            'answer': '\n\n'.join(describe(item) for item in execution['results']),
            'plan': execution['operations'], 'results': execution['results'],
            'limitations': ['Il pianificatore interpreta; i valori sono calcolati dagli strumenti validati.',
                            'Le risposte descrivono le fonti al momento della richiesta e non si aggiornano automaticamente.']}
