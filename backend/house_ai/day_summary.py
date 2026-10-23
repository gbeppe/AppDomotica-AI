"""Fase 3.5 C: "Com'è andata oggi" — fixed tools, deterministic text, no model call.

Today's energy totals (midnight to now, Europe/Rome) from EmonCMS and tomorrow's PV
forecast from the Node-RED log. Same answer schema as the assistant, so the app shows
it with the same card and diagnostics.
"""
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from energy_presenter import elapsed_coverage, notes, spoken
from energy_tools import execute

ROME = ZoneInfo('Europe/Rome')
QUESTION = "Com'è andata oggi?"
# Order of the spoken sentence: produced, consumed, imported, exported.
DAY_METRICS = (("solar_production_kwh", "prodotti"), ("home_consumption_kwh", "consumati"),
               ("grid_import_kwh", "prelevati"), ("grid_export_kwh", "immessi"))
PARTIAL_BELOW = 0.9  # share of the elapsed day covered by samples


def forecast_kwh(result):
    """kWh of the latest real forecast record for the day, or None."""
    for item in reversed(result.get('records', [])):
        value = item['record'].get('kwh_stimati_impianto_reali')
        if type(value) in (int, float) and value >= 0:
            return value
    return None


def summary(client, log_root=None, now=None):
    now = now or datetime.now(ROME)
    today, tomorrow = now.date(), now.date() + timedelta(days=1)
    plan = {'clarification': None, 'operations': [
        {'id': metric, 'tool': 'energy_metric', 'metric': metric,
         'start': today.isoformat(), 'end': tomorrow.isoformat()} for metric, _ in DAY_METRICS] + [
        {'id': 'forecast', 'tool': 'backend_log_day', 'source': 'solar_forecast', 'day': tomorrow.isoformat()}]}
    execution = execute(client, plan, log_root=log_root)
    by_id = {item['id']: item for item in execution['results']}

    totals, partial = [], False
    for metric, verb in DAY_METRICS:
        result = by_id[metric]['result']
        if result.get('value') is None:
            continue
        coverage = elapsed_coverage(result)
        partial |= coverage < PARTIAL_BELOW
        result['limitations'] = list(result.get('limitations', [])) + [
            f"Copertura del tempo trascorso oggi: {spoken(coverage * 100)} per cento."]
        totals.append(f"{verb} {spoken(result['value'])}")
    if totals:
        totals[0] += " kilowattora"
        text = f"Oggi fino alle {now.strftime('%H:%M')}: " + ", ".join(totals) + "."
        if partial:
            text += " Dati parziali."
    else:
        text = "Totali di oggi non disponibili."
    kwh = forecast_kwh(by_id['forecast']['result'])
    text += (f" Previsione per domani: {spoken(kwh)} kilowattora." if kwh is not None
             else " Previsione per domani non ancora disponibile.")

    for item in execution['results']:
        result = item['result']
        # The generic note gives whole-day coverage (low until midnight); the elapsed one is added above.
        extra = [n for n in notes(item) if not (item['id'] != 'forecast' and n.startswith('Copertura '))]
        result['limitations'] = list(dict.fromkeys(list(result.get('limitations', [])) + extra))
    status = ('insufficient_data' if not totals
              else 'partial' if partial or len(totals) < len(DAY_METRICS) or kwh is None else 'complete')
    return {'schema': 'house_ai.assistant_answer.v1', 'question': QUESTION,
            'generated_at': now.isoformat(), 'status': status, 'answer': text,
            'plan': execution['operations'], 'results': execution['results'],
            'limitations': ['Riepilogo calcolato con strumenti fissi, senza modello linguistico.',
                            'I totali di oggi crescono durante la giornata; la previsione è una stima per domani.']}
