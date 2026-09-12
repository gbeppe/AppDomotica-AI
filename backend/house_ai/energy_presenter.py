"""Deterministic Italian presentation of energy evidence; no generated numbers."""
from datetime import datetime
from zoneinfo import ZoneInfo
import math

LABELS = {
    "grid_import_kwh": "Prelievo dalla rete", "grid_export_kwh": "Energia immessa in rete",
    "home_consumption_kwh": "Consumo della casa", "solar_production_kwh": "Produzione fotovoltaica",
    "battery_charge_kwh": "Energia caricata nella batteria", "battery_discharge_kwh": "Energia scaricata dalla batteria",
    "soc_mean_percent": "Livello medio della batteria Tesla", "solar_power_w": "Produzione fotovoltaica",
    "home_consumption_w": "Consumo della casa", "grid_power_w": "Scambio con la rete",
    "battery_power_w": "Potenza della batteria", "battery_soc_percent": "Carica della batteria Tesla",
}


def number(value, digits=2):
    return f"{value:.{digits}f}".replace('.', ',')


def numeric(record, *path):
    for key in path:
        record = record.get(key) if isinstance(record, dict) else None
    try:
        return record if type(record) in (int, float) and math.isfinite(record) else None
    except OverflowError:
        return None


def log_text(result):
    prefix = f"Log {result['file']} del {result['day']}"
    if not result['records']:
        return prefix + ": " + ("sorgente non disponibile." if result['status'] in ('unavailable', 'source_rejected')
                                 else "nessun record utilizzabile; non prova assenza di eventi.")
    # Latest by evidence timestamp, not file order; forecast has only target date.
    key = 'timestamp_ms' if result['source'] == 'reserve_shadow' else 'timestamp'
    last = (result['records'][-1] if result['source'] == 'solar_forecast' else
            max(result['records'], key=lambda r: numeric(r['record'], key) or 0))
    record = last['record']
    parts = [f"{prefix}: {result['matched_records']} record trovati. Ultimo record restituito, riga {last['source']['line']}."]
    if result['source'] == 'solar_forecast':
        value = numeric(record, 'kwh_stimati_impianto_reali')
        parts.append(f"Produzione prevista: {number(value)} kWh." if value is not None else "Produzione prevista non disponibile.")
        if result["matched_records"] > 1:
            parts.append("Sono presenti più record per questo giorno; riporto l’ultimo restituito in ordine di file.")
        parts.append("È una previsione per il giorno indicato, non produzione misurata; ora di elaborazione non presente.")
    elif result['source'] == 'learning':
        for key, label in [('consumo_medio_w', 'Consumo medio orario registrato'), ('produzione_media_w', 'Produzione media oraria registrata'), ('carica_media_batteria_w', 'Carica media oraria registrata')]:
            value = numeric(record, 'metriche_medie_ora', key)
            if value is not None:
                parts.append(f"{label}: {number(value)} W.")
        value = numeric(record, 'calcolo_efficienza', 'nuova_efficienza_storica_ema')
        if value is not None:
            parts.append(f"Efficienza storica EMA registrata: {number(value, 3)}; non è una misura istantanea.")
    elif result['source'] == 'reserve_shadow':
        for key, label in [('reserve_kwh', 'Riserva simulata'), ('climate_budget_kwh', 'Budget climatico simulato'), ('support_pred_kwh', 'Supporto previsto')]:
            value = numeric(record, 'energy', key)
            if value is not None:
                parts.append(f"{label}: {number(value)} kWh.")
        parts.append("Questo log è telemetria SHADOW: non dimostra attivazioni o autorizzazioni ai dispositivi.")
    else:
        logic = record.get('logica_controllo', {})
        reason = logic.get('motivo_ac') if isinstance(logic, dict) else None
        if isinstance(reason, str):
            parts.append(f"Motivo AC registrato: {reason[:180]}.")
        parts.append("È un evento registrato, non una conferma fisica né una spiegazione causale aggiuntiva.")
    if result['truncated']:
        parts.append("Risultati limitati: il dettaglio non comprende tutti i record.")
    if result['invalid_lines']:
        parts.append(f"Righe non utilizzabili: {result['invalid_lines']}.")
    parts.append("I log non garantiscono copertura continua e non sostituiscono lo stato corrente.")
    return ' '.join(parts)


def describe(item):
    r = item['result']
    schema = r['schema']
    if schema == 'house_ai.backend_log_evidence.v1':
        return log_text(r)
    if schema == 'house_ai.energy_comparison.v1':
        if r['value'] is None:
            return 'Confronto non calcolabile: servono dati completi e comparabili.'
        text = (f"{LABELS[r['metric']]}: differenza del periodo {r['left_period']['start']} → "
                f"{r['left_period']['end_exclusive']} rispetto a {r['right_period']['start']} → "
                f"{r['right_period']['end_exclusive']} (fini escluse): {number(r['value'])} {r['unit']}.")
        if r['change_percent'] is not None:
            text += f" Variazione rispetto al secondo periodo: {number(r['change_percent'])}%."
        else:
            text += ' Variazione percentuale non definita: base zero.'
        return text + ' I periodi non sono normalizzati per durata.'
    label = LABELS.get(item.get('metric'), item.get('id', 'Dato'))
    if schema == 'house_ai.source_error.v1':
        return f"{label}: sorgente non disponibile; riprova più tardi."
    if schema == 'house_ai.current_energy_result.v1':
        obs = r['observation']
        if obs is None:
            return f"{label}: dato non disponibile."
        value = obs['value']
        metric = item['metric']
        if metric == 'grid_power_w':
            label = 'Prelievo dalla rete' if value > 0 else 'Immissione in rete' if value < 0 else 'Scambio netto con la rete'
            value = abs(value)
        if metric == 'battery_power_w':
            label = 'Scarica della batteria' if value > 0 else 'Carica della batteria' if value < 0 else 'Scambio netto della batteria'
            value = abs(value)
        unit = '%' if metric == 'battery_soc_percent' else 'W'
        received = datetime.fromtimestamp(obs['received_at_ms']/1000, ZoneInfo('Europe/Rome')).isoformat()
        qualifier = ' Digital Twin disconnesso.' if not r['connected'] else ''
        return (f"{label}: {number(value, 1)} {unit}, ultimo dato ricevuto il {received}." + qualifier +
                (' Messaggio retained.' if obs['retained'] else '') +
                ' Età della misura sorgente ignota; non è una conferma fisica in tempo reale.')
    if r['value'] is None:
        return f"{label}: dati insufficienti dal {r['period']['start']} al {r['period']['end_exclusive']} escluso."
    return (f"{label}: {number(r['value'])} {r['unit']} dal {r['period']['start']} "
            f"al {r['period']['end_exclusive']} escluso; copertura {number(r['coverage_ratio']*100, 1)}%. " +
            ('Stima limitata agli intervalli coperti, non totale del periodo.' if r['status'] != 'complete'
             else 'Stima da campioni EmonCMS, non misura fiscale.'))
