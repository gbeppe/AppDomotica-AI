"""Small deterministic Italian query interpreter for validated energy metrics."""
import json
import re
from datetime import date
from pathlib import Path

from energy_history import energy_report, relative_period


SOURCES_PATH = Path(__file__).with_name("energy_sources.json")


def load_sources(path=SOURCES_PATH):
    data = json.loads(path.read_text(encoding="utf-8"))
    return data["sources"]


def interpret(question, today=None):
    normalized = " ".join(question.casefold().split())
    if not normalized:
        raise ValueError("Empty question")
    grid = any(word in normalized for word in ("prelev", "rete", "import"))
    soc = ("soc" in normalized or "percentual" in normalized
           or "carica" in normalized or "ricarica" in normalized)
    if grid and soc:
        return {"status": "clarification_required",
                "message": "Chiedimi separatamente il prelievo dalla rete e il livello medio della batteria."}
    metric = "grid_import_kwh" if grid else "soc_mean_percent" if soc else None
    if metric is None:
        return {"status": "unsupported",
                "message": "Per ora posso calcolare prelievo dalla rete e livello medio della batteria Tesla."}
    if "mese scorso" in normalized or "scorso mese" in normalized:
        period = "previous_month"
    elif "settimana scorsa" in normalized or "scorsa settimana" in normalized:
        period = "previous_week"
    elif re.search(r"\bultim[oi] 30 giorni\b", normalized):
        period = "last_30_days"
    elif re.search(r"\bultim[ai] 7 giorni\b", normalized):
        period = "last_7_days"
    elif "ultimo mese" in normalized or "ultima settimana" in normalized:
        return {"status": "clarification_required",
                "message": "Intendi il periodo di calendario precedente oppure gli ultimi giorni completi?"}
    else:
        return {"status": "clarification_required",
                "message": "Indica mese scorso, settimana scorsa, ultimi 30 giorni oppure ultimi 7 giorni."}
    start, end = relative_period(period, today or date.today())
    return {"status": "ready", "metric": metric, "period_name": period,
            "start": start, "end": end}


def answer(client, question, today=None, sources=None):
    request = interpret(question, today)
    if request["status"] != "ready":
        return {"schema": "house_ai.energy_answer.v1", **request}
    source = (sources or load_sources())[request["metric"]]
    result = energy_report(client, request["start"], request["end"],
                           request["metric"], source["feed_id"], source["unit"],
                           source.get("import_sign"), source["interval_seconds"])
    value = result["value"]
    if value is None:
        text = "Dati insufficienti per calcolare il risultato nel periodo richiesto."
    else:
        label = ("Prelievo dalla rete" if request["metric"] == "grid_import_kwh"
                 else "Livello medio della batteria Tesla")
        text = (f"{label}: {value:.2f} {result['unit']} dal {request['start']} "
                f"al {request['end']} escluso. Copertura {result['coverage_ratio']:.1%}.")
        if result["status"] != "complete":
            text += " Il risultato riguarda solo gli intervalli coperti."
    return {"schema": "house_ai.energy_answer.v1", "status": result["status"],
            "question": question, "answer": text, "request": request,
            "result": result}
