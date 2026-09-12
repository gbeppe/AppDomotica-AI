"""Validated energy-domain tools called by a planner, never arbitrary code."""
import json
import math
from pathlib import Path

from energy_history import energy_report
from log_tools import SOURCES as LOG_SOURCES, tool_catalog, read_day
from evidence import day_bounds


CATALOG_PATH = Path(__file__).with_name("energy_sources.json")
MAX_OPERATIONS = 6
CURRENT_METRICS = {
    "solar_power_w", "home_consumption_w", "grid_power_w",
    "battery_power_w", "battery_soc_percent"
}


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
        }, {
            "name": "current_energy_metric",
            "description": "Read one current Digital Twin energy metric",
            "parameters": {"metric": sorted(CURRENT_METRICS)}
        }, tool_catalog()],
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
        if not isinstance(operation, dict):
            raise ValueError("Invalid operation schema")
        tool = operation.get("tool")
        expected = ({"id", "tool", "metric", "start", "end"}
                    if tool == "energy_metric" else {"id", "tool", "metric"}
                    if tool == "current_energy_metric" else {"id", "tool", "source", "day"}
                    if tool == "backend_log_day" else set())
        if set(operation) != expected:
            raise ValueError("Invalid operation schema")
        identifier = operation["id"]
        if (not isinstance(identifier, str) or not identifier or len(identifier) > 40
                or identifier in ids):
            raise ValueError("Invalid operation id")
        if ((tool == "energy_metric" and operation["metric"] not in sources)
                or (tool == "current_energy_metric" and operation["metric"] not in CURRENT_METRICS)):
            raise ValueError("Unknown tool or metric")
        if tool == "backend_log_day":
            if not isinstance(operation["source"], str) or operation["source"] not in LOG_SOURCES:
                raise ValueError("Unknown log source")
            day_bounds(operation["day"])
        # energy_report validates calendar bounds before network access.
        ids.add(identifier)
        normalized.append(dict(operation))
    return {"operations": normalized, "clarification": None}


def validate_current_snapshot(snapshot):
    if snapshot is None:
        return None
    if (not isinstance(snapshot, dict)
            or set(snapshot) != {"schema", "connected", "observations"}
            or snapshot["schema"] != "house_ai.current_energy_input.v1"
            or type(snapshot["connected"]) is not bool
            or not isinstance(snapshot["observations"], dict)
            or set(snapshot["observations"]) - CURRENT_METRICS):
        raise ValueError("Invalid current energy snapshot")
    clean = {"schema": snapshot["schema"], "connected": snapshot["connected"], "observations": {}}
    for metric, observation in snapshot["observations"].items():
        if (not isinstance(observation, dict)
                or set(observation) != {"value", "received_at_ms", "retained", "source_topic"}
                or type(observation["value"]) not in (int, float)
                or not math.isfinite(observation["value"])
                or type(observation["received_at_ms"]) is not int
                or observation["received_at_ms"] < 0
                or type(observation["retained"]) is not bool
                or not isinstance(observation["source_topic"], str)
                or not 1 <= len(observation["source_topic"]) <= 256
                or observation["source_topic"].endswith("/cmd")):
            raise ValueError("Invalid current energy observation")
        value = float(observation["value"])
        if metric == "battery_soc_percent" and not 0 <= value <= 100:
            raise ValueError("Invalid current SOC")
        clean["observations"][metric] = {**observation, "value": value}
    return clean


def execute(client, plan, sources=None, current_snapshot=None, log_root=None):
    sources = sources or json.loads(CATALOG_PATH.read_text(encoding="utf-8"))["sources"]
    checked = validate_plan(plan, sources)
    current = validate_current_snapshot(current_snapshot)
    if checked["clarification"]:
        return checked
    results = []
    for operation in checked["operations"]:
        if operation["tool"] == "backend_log_day":
            results.append({"id": operation["id"], "result":
                            read_day(log_root, operation["source"], operation["day"])})
            continue
        if operation["tool"] == "current_energy_metric":
            observation = current["observations"].get(operation["metric"]) if current else None
            results.append({"id": operation["id"], "metric": operation["metric"],
                            "result": {"schema": "house_ai.current_energy_result.v1",
                                "status": "available" if observation else "missing",
                                "connected": current["connected"] if current else False,
                                "observation": observation,
                                "limitations": ["Timestamp di ricezione Android; età della misura sorgente ignota.",
                                                "Retained non costituisce conferma fisica o di freschezza."]}})
            continue
        source = sources[operation["metric"]]
        sign = source.get("import_sign", source.get("direction_sign"))
        result = energy_report(client, operation["start"], operation["end"],
                               operation["metric"], source["feed_id"], source["unit"],
                               sign, source["interval_seconds"])
        results.append({"id": operation["id"], "metric": operation["metric"],
                        "result": result})
    return {"operations": checked["operations"], "results": results,
            "clarification": None}
