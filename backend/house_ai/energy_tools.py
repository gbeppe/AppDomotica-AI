"""Validated energy-domain tools called by a planner, never arbitrary code."""
import json
from pathlib import Path

from energy_history import energy_report


CATALOG_PATH = Path(__file__).with_name("energy_sources.json")
MAX_OPERATIONS = 6


def catalog(path=CATALOG_PATH):
    sources = json.loads(path.read_text(encoding="utf-8"))["sources"]
    return {
        "domain": "energy",
        "tools": [{
            "name": "energy_metric",
            "description": "Calculate one validated historical energy metric",
            "parameters": {
                "metric": sorted(sources),
                "start": "YYYY-MM-DD Europe/Rome, inclusive",
                "end": "YYYY-MM-DD Europe/Rome, exclusive"
            }
        }],
        "rules": [
            "Resolve relative periods into absolute dates.",
            "Ask for clarification when period or metric meaning is ambiguous.",
            "Use multiple operations for comparisons or compound questions."
        ]
    }


def validate_plan(plan, sources=None):
    sources = sources or json.loads(CATALOG_PATH.read_text(encoding="utf-8"))["sources"]
    if not isinstance(plan, dict) or set(plan) - {"operations", "clarification"}:
        raise ValueError("Invalid plan envelope")
    clarification = plan.get("clarification")
    operations = plan.get("operations", [])
    if clarification is not None:
        if not isinstance(clarification, str) or not clarification.strip() or operations:
            raise ValueError("Invalid clarification plan")
        return {"operations": [], "clarification": clarification.strip()[:500]}
    if not isinstance(operations, list) or not 1 <= len(operations) <= MAX_OPERATIONS:
        raise ValueError("Plan must contain bounded operations")
    normalized, ids = [], set()
    for operation in operations:
        if not isinstance(operation, dict) or set(operation) != {"id", "tool", "metric", "start", "end"}:
            raise ValueError("Invalid operation schema")
        identifier = operation["id"]
        if (not isinstance(identifier, str) or not identifier or len(identifier) > 40
                or identifier in ids):
            raise ValueError("Invalid operation id")
        if operation["tool"] != "energy_metric" or operation["metric"] not in sources:
            raise ValueError("Unknown tool or metric")
        # energy_report validates calendar bounds before network access.
        ids.add(identifier)
        normalized.append(dict(operation))
    return {"operations": normalized, "clarification": None}


def execute(client, plan, sources=None):
    sources = sources or json.loads(CATALOG_PATH.read_text(encoding="utf-8"))["sources"]
    checked = validate_plan(plan, sources)
    if checked["clarification"]:
        return checked
    results = []
    for operation in checked["operations"]:
        source = sources[operation["metric"]]
        sign = source.get("import_sign", source.get("direction_sign"))
        result = energy_report(client, operation["start"], operation["end"],
                               operation["metric"], source["feed_id"], source["unit"],
                               sign, source["interval_seconds"])
        results.append({"id": operation["id"], "metric": operation["metric"],
                        "result": result})
    return {"operations": checked["operations"], "results": results,
            "clarification": None}
