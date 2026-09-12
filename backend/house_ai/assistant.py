"""Dynamic planner -> validated energy tools -> evidence-based response."""
from datetime import date

from energy_tools import catalog, execute


METRIC_LABELS = {
    "grid_import_kwh": "Prelievo dalla rete",
    "grid_export_kwh": "Energia immessa in rete",
    "home_consumption_kwh": "Consumo della casa",
    "solar_production_kwh": "Produzione fotovoltaica",
    "battery_charge_kwh": "Energia caricata nella batteria",
    "battery_discharge_kwh": "Energia scaricata dalla batteria",
    "soc_mean_percent": "Livello medio della batteria Tesla"
}


def ask(client, planner, question, today=None):
    if not isinstance(question, str) or not question.strip() or len(question) > 1000:
        raise ValueError("Invalid question")
    if planner is None:
        return {"schema": "house_ai.assistant_answer.v1", "status": "not_configured",
                "answer": "Il pianificatore dinamico non è configurato sul backend."}
    current = today or date.today()
    proposed = planner.plan(question.strip(), catalog(), current)
    execution = execute(client, proposed)
    if execution["clarification"]:
        return {"schema": "house_ai.assistant_answer.v1",
                "status": "clarification_required",
                "answer": execution["clarification"]}
    lines, statuses = [], []
    for item in execution["results"]:
        result = item["result"]
        statuses.append(result["status"])
        value = result["value"]
        label = METRIC_LABELS[item["metric"]]
        if value is None:
            lines.append(f"{label}: dati insufficienti.")
        else:
            lines.append(f"{label}: {value:.2f} {result['unit']} dal "
                         f"{result['period']['start']} al "
                         f"{result['period']['end_exclusive']} escluso; "
                         f"copertura {result['coverage_ratio']:.1%}.")
    overall = ("insufficient_data" if all(s == "insufficient_data" for s in statuses)
               else "partial" if any(s != "complete" for s in statuses) else "complete")
    return {"schema": "house_ai.assistant_answer.v1", "status": overall,
            "question": question, "answer": " ".join(lines),
            "plan": execution["operations"], "results": execution["results"],
            "limitations": ["Il pianificatore interpreta; i valori sono calcolati dagli strumenti validati.",
                            "Una copertura parziale non rappresenta un totale completo."]}
