"""Dynamic interpretation followed by validated tools and deterministic answers."""
import re
from datetime import datetime
from zoneinfo import ZoneInfo
from energy_tools import (catalog, execute, validate_climate_snapshot, validate_current_snapshot,
                          validate_lights_snapshot, validate_predictive_reserve_snapshot,
                          validate_thermostats_snapshot, validate_environment_snapshot,
                          validate_heating_snapshot)
from energy_presenter import compose, confirmation, notes
from planner import PlannerUnavailable

# Conditional or future requests ("quando…", "appena…", "se… allora…", automations) are rules,
# which are not available yet: a command planned from one of them is never executed
# (field test 16/10/2026: "...imposta un'automazione per spegnere il caminetto" became an
# immediate off, stopped only by the confirmation).
# "regola" alone is also the verb "adjust" ("regola il bagno a 20"): only the noun counts.
RULE_REQUEST = re.compile(r"\b(automazion\w*|(?:una|la|le|nuova|nuove) regol[ae]|quando|appena|ogni volta|se\b.+\ballora)\b",
                          re.IGNORECASE)
RULES_UNAVAILABLE = ("Le regole automatiche (\"quando… allora…\") non sono ancora disponibili: posso eseguire solo "
                     "comandi immediati. Non ho fatto niente.")


def ask(client, planner, question, today=None, current_snapshot=None, climate_snapshot=None, lights_snapshot=None,
        conversation_context=None, log_root=None, pr_snapshot=None, thermostats_snapshot=None,
        environment_snapshot=None, heating_snapshot=None):
    if not isinstance(question, str) or not question.strip() or len(question) > 1000:
        raise ValueError('Invalid question')
    validate_current_snapshot(current_snapshot)
    validate_climate_snapshot(climate_snapshot)
    validate_lights_snapshot(lights_snapshot)
    validate_predictive_reserve_snapshot(pr_snapshot)
    validate_thermostats_snapshot(thermostats_snapshot)
    validate_environment_snapshot(environment_snapshot)
    validate_heating_snapshot(heating_snapshot)
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
    except PlannerUnavailable:
        raise  # keeps RateLimited and its wait time
    except (ValueError, RuntimeError):
        raise PlannerUnavailable('Planner unavailable or invalid response') from None
    try:
        execution = execute(client, proposed, current_snapshot=current_snapshot,
                            climate_snapshot=climate_snapshot, lights_snapshot=lights_snapshot,
                            log_root=log_root, pr_snapshot=pr_snapshot,
                            thermostats_snapshot=thermostats_snapshot,
                            environment_snapshot=environment_snapshot,
                            heating_snapshot=heating_snapshot)
    except (ValueError, TypeError, OverflowError):
        raise PlannerUnavailable('Planner returned an invalid plan') from None
    if execution['clarification']:
        return {**base, 'status': 'clarification_required', 'answer': execution['clarification']}
    if RULE_REQUEST.search(question) and any(op['tool'].startswith('set_') for op in execution['operations']):
        return {**base, 'status': 'clarification_required', 'answer': RULES_UNAVAILABLE}
    # Spoken answers stay short; technical caveats travel with each result for the diagnostics view.
    for item in execution['results']:
        result = item['result']
        result['limitations'] = list(dict.fromkeys(list(result.get('limitations', [])) + notes(item)))
    statuses = [item['result']['status'] for item in execution['results']]
    overall = ('insufficient_data' if all(s in ('insufficient_data', 'missing', 'unavailable', 'source_rejected',
                                                'source_not_updated') for s in statuses)
               else 'partial' if any(s not in ('complete', 'command_ready', 'confirmation_required', 'rejected', 'unchanged')
                                     for s in statuses) else 'complete')
    context = ({'domain': 'climate', 'focus': 'air_conditioner'}
               if any(op['tool'] == 'current_air_conditioner' for op in execution['operations']) else None)
    return {**base, 'status': overall,
            'answer': compose(execution['results']),
            **({'confirmation': confirmation(execution['results'])} if confirmation(execution['results']) else {}),
            'plan': execution['operations'], 'results': execution['results'],
            **({'conversation_context': context} if context else {}),
            'limitations': ['Il pianificatore interpreta; i valori sono calcolati dagli strumenti validati.',
                            'Le risposte descrivono le fonti al momento della richiesta e non si aggiornano automaticamente.']}
