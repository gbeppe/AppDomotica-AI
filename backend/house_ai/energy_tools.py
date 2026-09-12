"""Validated energy-domain tools called by a planner, never arbitrary code."""
import json
import math
import time
from pathlib import Path

from energy_history import energy_report, period_bounds
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
        }, tool_catalog(), {
            "name": "energy_comparison",
            "description": "Calculate left minus right and percent change for the same historical metric; requires complete coverage",
            "parameters": {"left_id": "ID of preceding energy_metric", "right_id": "ID of preceding energy_metric"}
        }],
        "rules": [
            "Resolve relative periods into absolute dates.",
            "Ask for clarification when period or metric meaning is ambiguous.",
            "Use multiple operations for comparisons or compound questions. Every operation requires a unique id and tool name; max 6 operations.",
            "For comparisons select both periods and then energy_comparison using their IDs; same metric only.",
            "SOC is battery level, not energy charged. Ask which meaning is intended for ambiguous percentages.",
            "Today and the current month/week may be incomplete. End date is exclusive.",
            "Use backend_log_day for forecasts, recorded reasons, learning or SHADOW evidence. Never infer actions from SHADOW.",
            "No control, cost, tariffs, unsupported metrics or causal speculation; clarify unsupported requests."
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
    historical = {}
    sample_budget = 0
    for operation in operations:
        if not isinstance(operation, dict):
            raise ValueError("Invalid operation schema")
        tool = operation.get("tool")
        expected = ({"id", "tool", "metric", "start", "end"}
                    if tool == "energy_metric" else {"id", "tool", "metric"}
                    if tool == "current_energy_metric" else {"id", "tool", "source", "day"}
                    if tool == "backend_log_day" else {"id", "tool", "left_id", "right_id"}
                    if tool == "energy_comparison" else None)
        if expected is None or set(operation) != expected:
            raise ValueError("Invalid operation schema")
        identifier = operation["id"]
        if (not isinstance(identifier, str) or not identifier or len(identifier) > 40
                or identifier in ids):
            raise ValueError("Invalid operation id")
        if tool in ("energy_metric", "current_energy_metric") and not isinstance(operation["metric"], str):
            raise ValueError("Invalid metric")
        if ((tool == "energy_metric" and operation["metric"] not in sources)
                or (tool == "current_energy_metric" and operation["metric"] not in CURRENT_METRICS)):
            raise ValueError("Unknown tool or metric")
        if tool == "backend_log_day":
            if not isinstance(operation["source"], str) or operation["source"] not in LOG_SOURCES:
                raise ValueError("Unknown log source")
            if not isinstance(operation["day"], str):
                raise ValueError("Invalid day")
            day_bounds(operation["day"])
        if tool == "energy_metric":
            if not all(isinstance(operation[k], str) for k in ("start", "end")):
                raise ValueError("Invalid dates")
            lo, hi = period_bounds(operation["start"], operation["end"])
            sample_budget += (hi-lo)//(sources[operation["metric"]]["interval_seconds"]*1000)
            if sample_budget > 2_200_000:
                raise ValueError("Plan exceeds sample budget")
            historical[identifier] = operation
        if tool == "energy_comparison":
            left, right = operation["left_id"], operation["right_id"]
            if (not isinstance(left, str) or not isinstance(right, str)
                    or left == right or left not in historical or right not in historical
                    or historical[left]["metric"] != historical[right]["metric"]):
                raise ValueError("Invalid comparison references")
        ids.add(identifier)
        normalized.append(dict(operation))
    return {"operations": normalized, "clarification": None}


def finite_number(value):
    try:
        return type(value) in (int, float) and math.isfinite(value)
    except OverflowError:
        return False


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
                or not finite_number(observation["value"])
                or type(observation["received_at_ms"]) is not int
                or not 0 <= observation["received_at_ms"] <= 253402214400000
                or type(observation["retained"]) is not bool
                or not isinstance(observation["source_topic"], str)
                or not 1 <= len(observation["source_topic"]) <= 256
                or observation["source_topic"].endswith("/cmd")):
            raise ValueError("Invalid current energy observation")
        value = float(observation["value"])
        if metric == "battery_soc_percent" and not 0 <= value <= 100:
            raise ValueError("Invalid current SOC")
        if metric in ("solar_power_w", "home_consumption_w") and value < 0:
            raise ValueError("Negative generation or consumption")
        clean["observations"][metric] = {**observation, "value": value}
    return clean


def execute(client, plan, sources=None, current_snapshot=None, log_root=None):
    sources = sources or json.loads(CATALOG_PATH.read_text(encoding="utf-8"))["sources"]
    checked = validate_plan(plan, sources)
    current = validate_current_snapshot(current_snapshot)
    if checked["clarification"]:
        return checked
    results = []
    # Cache identical feed windows only within one request (import/export share a feed).
    class CachedClient:
        def __init__(self):
            self.rows = {}
            self.deadline = time.monotonic() + 40
        def history(self, feed, start, end, interval):
            key = (feed, start, end, interval)
            if key not in self.rows:
                if time.monotonic() > self.deadline:
                    raise RuntimeError("Request data budget exhausted")
                self.rows[key] = client.history(feed, start, end, interval)
            return self.rows[key]
    cached = CachedClient()
    for operation in checked["operations"]:
        if operation["tool"] == "energy_comparison":
            by_id = {item["id"]: item["result"] for item in results}
            a, b = by_id[operation["left_id"]], by_id[operation["right_id"]]
            source_ops = {op["id"]: op for op in checked["operations"]}
            left_op, right_op = source_ops[operation["left_id"]], source_ops[operation["right_id"]]
            valid = a["status"] == b["status"] == "complete"
            value = a["value"] - b["value"] if valid else None
            results.append({"id": operation["id"], "result": {
                "schema": "house_ai.energy_comparison.v1", "status": "complete" if valid else "insufficient_data",
                "left_id": operation["left_id"], "right_id": operation["right_id"],
                "metric": left_op["metric"],
                "left_period": {"start": left_op["start"], "end_exclusive": left_op["end"]},
                "right_period": {"start": right_op["start"], "end_exclusive": right_op["end"]},
                "value": value, "unit": "punti percentuali" if a.get("unit") == "%" else a.get("unit", "kWh"),
                "change_percent": value / b["value"] * 100 if valid and b["value"] != 0 else None,
                "limitations": ["Differenza fra i periodi richiesti; non normalizzata per durata.",
                                "La variazione relativa non è definita con base zero."]}})
            continue
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
        try:
            result = energy_report(cached, operation["start"], operation["end"],
                                   operation["metric"], source["feed_id"], source["unit"],
                                   sign, source["interval_seconds"])
        except (OSError, RuntimeError):
            result = {"schema": "house_ai.source_error.v1", "status": "unavailable",
                      "source": {"feed_id": source["feed_id"]},
                      "limitations": ["Sorgente EmonCMS non disponibile per questa operazione."]}
        if "excluded" in result:
            result["excluded_count"] = len(result["excluded"])
            result["excluded"] = result["excluded"][:100]
            result["excluded_truncated"] = result["excluded_count"] > 100
        results.append({"id": operation["id"], "metric": operation["metric"],
                        "result": result})
    return {"operations": checked["operations"], "results": results,
            "clarification": None}
