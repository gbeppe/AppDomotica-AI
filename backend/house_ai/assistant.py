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
import heating_profile_tools
import rules_tools

# Conditional or future requests ("quando…", "appena…", "se… allora…", automations) are rules,
# which are not available yet: a command planned from one of them is never executed
# (field test 16/10/2026: "...imposta un'automazione per spegnere il caminetto" became an
# immediate off, stopped only by the confirmation).
# "regola" alone is also the verb "adjust" ("regola il bagno a 20"): only the noun counts.
# A sentence starting with "alle 22" or "ogni sera" is a time rule; "dalle 12 alle 13" (fireplace
# times) is not.
RULE_REQUEST = re.compile(r"\b(automazion\w*|(?:una|la|le|nuova|nuove) regol[ae]|quando|appena|ogni volta|"
                          r"ogni (?:sera|mattina|giorno|notte)|(?:stasera|domani) alle|se\b.+\ballora)\b|^\s*alle\s+\d",
                          re.IGNORECASE)
# User's decision (16/10/2026): a rule lasts once unless the user says so ("ogni volta", "sempre",
# "ogni sera", "tutti i giorni"); the planner's "always" is kept only with such words.
ALWAYS_WORDS = re.compile(r"\b(ogni|sempre|tutti i giorni|tutte le (?:sere|mattine|notti|volte)|feriali|"
                          r"fine settimana|sabato e domenica)\b", re.IGNORECASE)
# A fireplace switched on by hand (app, AI Smart) is never switched off by the .15 timetable, and in
# Manuale the start/stop times are not used at all (field test 18/10/2026: "accendi il caminetto
# fino alle 19:00" was confirmed, the fireplace stayed on at 19:00). Until a scheduled switch-off
# exists, "fino alle…" does not change the times in Manuale and the answer says so.
UNTIL = re.compile(r"\b(?:fino a(?:lle|ll'|d)?|entro le)\s*(?:ore\s*)?\d|"
                   r"\bper (?:un'ora|mezz'ora|un paio d'ore|\d+ or[ae])\b", re.IGNORECASE)
MANUAL_UNTIL_NOTE = ("Acceso a mano, il caminetto non si spegne da solo all'orario: "
                     "dovrai spegnerlo tu, dall'app o dicendo «spegni il caminetto». ")
RULES_UNAVAILABLE = ("Questa è una regola («quando… allora…»): non la eseguo come comando immediato. "
                     "Ripetila e la creo come regola, con la tua conferma. Non ho fatto niente.")


FIREPLACE_NOT_MANUAL = ("Il caminetto non è in Manuale: i comandi a tempo valgono solo in Manuale. "
                        "Dimmi per esempio «metti il caminetto in manuale e accendilo fino alle 19». Non ho fatto niente.")


def fireplace_mode(ops, heating_snapshot):
    planned = [op.get('mode') for op in ops if op.get('tool') == 'set_fireplace_settings' and op.get('mode') != 'unchanged']
    observed = ((heating_snapshot or {}).get('observations') or {}).get('fireplace.mode') or {}
    return planned[-1] if planned else observed.get('value')


def timed_plan(proposed, heating_snapshot):
    """Timed commands (docs/AI_MODE/COMANDI_A_TEMPO.md): one confirmation for the whole request.

    Immediate lights and scenes become 'now' timed commands; in 'dalle T1 alle T2' with T1 already past
    and T2 still ahead the first part becomes 'now'. Returns a refusal text, or None."""
    if not isinstance(proposed, dict):
        return None
    ops = [op for op in proposed.get('operations') or [] if isinstance(op, dict)]
    if not any(op.get('tool') == 'schedule_command' for op in ops):
        return None
    for op in ops:
        if op.get('tool') in ('set_light_state', 'set_light_scene'):
            light = op.get('tool') == 'set_light_state'
            action = {'action': op['tool'], 'target': op.get('light') if light else 'none',
                      'setting': op.get('state') if light else op.get('scene'), 'at': rules_tools.NOW}
            identifier = op.get('id')
            op.clear(); op.update({'id': identifier, 'tool': 'schedule_command', **action})
    seen = {}
    for i, op in enumerate(ops):
        if op.get('tool') != 'schedule_command':
            continue
        seen.setdefault((op.get('action'), op.get('target')), []).append(op)
    now = rules_tools.clock()
    for same in seen.values():
        for first, second in zip(same, same[1:]):
            a, b = rules_tools.occurrence(first.get('at'), now), rules_tools.occurrence(second.get('at'), now)
            if a and b and a > b:
                first['at'] = rules_tools.NOW
    if (any(op.get('tool') == 'schedule_command' and op.get('action') == 'set_fireplace_power' for op in ops)
            and fireplace_mode(ops, heating_snapshot) != 'Manuale'):
        return FIREPLACE_NOT_MANUAL
    return None


def manual_until(proposed, question, heating_snapshot):
    """'Accendi il caminetto fino alle 19': True when the note is due; in Manuale the times are dropped.

    With a timed command in the plan the switch-off is scheduled, so no note is due."""
    if not isinstance(proposed, dict) or not UNTIL.search(question):
        return False
    ops = [op for op in proposed.get('operations') or [] if isinstance(op, dict)]
    if not any(op.get('tool') == 'set_fireplace_power' and op.get('state') == 'on' for op in ops):
        return False
    if fireplace_mode(ops, heating_snapshot) == 'Manuale':
        for op in ops:
            if op.get('tool') == 'set_fireplace_settings':
                op['start_time'] = op['stop_time'] = 'unchanged'
        proposed['operations'] = [op for op in proposed['operations'] if not (
            isinstance(op, dict) and op.get('tool') == 'set_fireplace_settings'
            and all(op.get(k) == 'unchanged' for k in ('mode', 'start_time', 'stop_time', 'auto_power')))]
    return not any(op.get('tool') == 'schedule_command' for op in ops)


def ask(client, planner, question, today=None, current_snapshot=None, climate_snapshot=None, lights_snapshot=None,
        conversation_context=None, log_root=None, pr_snapshot=None, thermostats_snapshot=None,
        environment_snapshot=None, heating_snapshot=None, rules_snapshot=None, profile_snapshot=None):
    if not isinstance(question, str) or not question.strip() or len(question) > 1000:
        raise ValueError('Invalid question')
    validate_current_snapshot(current_snapshot)
    validate_climate_snapshot(climate_snapshot)
    validate_lights_snapshot(lights_snapshot)
    validate_predictive_reserve_snapshot(pr_snapshot)
    validate_thermostats_snapshot(thermostats_snapshot)
    validate_environment_snapshot(environment_snapshot)
    validate_heating_snapshot(heating_snapshot)
    rules_tools.validate_rules_snapshot(rules_snapshot)
    heating_profile_tools.validate_snapshot(profile_snapshot)
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
    if isinstance(proposed, dict) and not ALWAYS_WORDS.search(question):
        for op in proposed.get('operations') or []:
            if isinstance(op, dict) and op.get('tool') == 'create_rule' and op.get('lifetime') == 'always':
                op['lifetime'] = 'once'
    refusal = timed_plan(proposed, heating_snapshot)
    if refusal:
        return {**base, 'status': 'clarification_required', 'answer': refusal}
    until_note = manual_until(proposed, question, heating_snapshot)
    try:
        execution = execute(client, proposed, current_snapshot=current_snapshot,
                            climate_snapshot=climate_snapshot, lights_snapshot=lights_snapshot,
                            log_root=log_root, pr_snapshot=pr_snapshot,
                            thermostats_snapshot=thermostats_snapshot,
                            environment_snapshot=environment_snapshot,
                            heating_snapshot=heating_snapshot, rules_snapshot=rules_snapshot,
                            profile_snapshot=profile_snapshot)
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
    until_note = until_note and any(
        item['result']['schema'] == 'house_ai.fireplace_power_command.v1' and item['result']['state'] == 'on'
        and item['result']['status'] == 'confirmation_required' for item in execution['results'])
    return {**base, 'status': overall,
            'answer': (MANUAL_UNTIL_NOTE if until_note else '') + compose(execution['results']),
            **({'confirmation': confirmation(execution['results'])} if confirmation(execution['results']) else {}),
            'plan': execution['operations'], 'results': execution['results'],
            **({'conversation_context': context} if context else {}),
            'limitations': ['Il pianificatore interpreta; i valori sono calcolati dagli strumenti validati.',
                            'Le risposte descrivono le fonti al momento della richiesta e non si aggiornano automaticamente.']}
