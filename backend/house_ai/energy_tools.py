"""Validated energy-domain tools called by a planner, never arbitrary code."""
import json
import math
import time
from pathlib import Path

from energy_history import daily_extreme_report, energy_report, period_bounds
from log_tools import SOURCES as LOG_SOURCES, tool_catalog, read_day
from evidence import day_bounds
from light_semantics import validate_commands


CATALOG_PATH = Path(__file__).with_name("energy_sources.json")
MAX_OPERATIONS = 6
CURRENT_METRICS = {
    "solar_power_w", "home_consumption_w", "grid_power_w",
    "battery_power_w", "battery_soc_percent"
}
CLIMATE_METRICS = {"current_state", "air_mode", "temperature_set_c", "recorded_reason"}
CLIMATE_TOPICS = {
    "current_state": "zara/interface/stato_condizionatore/stato_attuale/stat",
    "air_mode": "zara/interface/stato_condizionatore/modalita_aria/stat",
    "temperature_set_c": "zara/interface/stato_condizionatore/temperatura_impostata_c/stat",
    "recorded_reason": "zara/interface/stato_condizionatore/motivo_logica/stat",
}
# Predictive Reserve read-only state published by Node-RED PR-MODE.1 (fase 3.1).
PR_TOPICS = {
    "control_enabled": "zara/interface/predictive_reserve/control_enabled/stat",
    "operating_mode": "zara/interface/predictive_reserve/operating_mode/stat",
    "ready": "zara/interface/predictive_reserve/ready/stat",
}
PR_VALUES = {
    "control_enabled": {"true", "false"},
    "operating_mode": {"SHADOW", "ACTIVE"},
    "ready": {"true", "false"},
}
# Heating thermostats (fase 3.2): Tasmota TEMPTARGETSET routed by the Digital Twin Router.
THERMOSTATS = ("thermostat_living", "thermostat_bath")
THERMOSTAT_METRICS = {
    "current_temperature": "number", "target_temperature": "number", "power": "bool",
    "min_temperature": "number", "max_temperature": "number",
}
THERMOSTAT_TOPICS = {f"{t}.{m}": f"zara/interface/climate/{t}/{m}/stat"
                     for t in THERMOSTATS for m in THERMOSTAT_METRICS}
# Same bounds and 0.5 °C step as the app slider; strings so the planner schema is a closed enum.
THERMOSTAT_TARGETS = [f"{x / 2:.1f}" for x in range(32, 45)]
LIGHT_TOPICS = {
    "lights_living": "zara/interface/lights/living/power/stat",
    "lights_libreria": "zara/interface/lights/libreria/power/stat",
    "lights_tv": "zara/interface/lights/tv/power/stat",
    "lights_reading": "zara/interface/lights/reading/power/stat",
    "lights_laundry": "zara/interface/lights/laundry/power/stat",
    "lights_porch_entrance": "zara/interface/lights/porch_entrance/power/stat",
    "lights_service_entrance": "zara/interface/lights/service_entrance/power/stat",
    "pool_water": "zara/interface/pool/water/power/stat",
    "pool_deck": "zara/interface/pool/deck/power/stat",
}
# Retired state keys accepted only for old-client transport compatibility, never tool targets.
LEGACY_LIGHT_TOPICS = {
    "lights_bedroom": "zara/interface/lights/bedroom/power/stat",
    "lights_hifi": "zara/interface/lights/hifi/power/stat",
}
DAILY_EXTREME_METRICS = {
    "grid_import_kwh", "grid_export_kwh", "home_consumption_kwh",
    "solar_production_kwh", "battery_charge_kwh", "battery_discharge_kwh"
}


def catalog(path=CATALOG_PATH):
    sources = json.loads(path.read_text(encoding="utf-8"))["sources"]
    return {
        "domains": ["energy", "climate", "lights"],
        "tools": [{
            "name": "energy_metric",
            "description": "Calculate one validated historical energy metric",
            "parameters": {
                "metric": sorted(sources),
                "start": "YYYY-MM-DD Europe/Rome, inclusive",
                "end": "YYYY-MM-DD Europe/Rome, exclusive"
            }
        }, {
            "name": "current_lights",
            "description": "Read declared states and latest available Node-RED command evidence (who requested on/off, origin and delivery stage) for one or all mapped lights. This is not full on/off history or physical confirmation",
            "parameters": {"light": ["all"] + sorted(LIGHT_TOPICS)}
        }, {
            "name": "set_light_state",
            "description": "Request ON or OFF for one mapped light",
            "parameters": {"light": sorted(LIGHT_TOPICS), "state": ["on", "off"]}
        }, {
            "name": "current_thermostats",
            "description": "Read room temperature, heating setpoint, heating on/off and the time-slot timer setpoints (max inside the slot, min outside) for the living-room or bathroom thermostat",
            "parameters": {"thermostat": ["all", *THERMOSTATS]}
        }, {
            "name": "set_thermostat_target",
            "description": "Set the heating setpoint of one thermostat, 16.0-22.0 °C in 0.5 steps",
            "parameters": {"thermostat": list(THERMOSTATS), "target_c": THERMOSTAT_TARGETS}
        }, {
            "name": "current_air_conditioner",
            "description": "Read current declared air-conditioner state, mode, setpoint and recorded controller reason",
            "parameters": {}
        }, {
            "name": "current_predictive_reserve",
            "description": "Read whether the Predictive Reserve control is enabled, its operating mode (SHADOW or ACTIVE), model readiness and what that means now for new night dehumidification starts",
            "parameters": {}
        }, {
            "name": "current_energy_metric",
            "description": "Read one current Digital Twin energy metric",
            "parameters": {"metric": sorted(CURRENT_METRICS)}
        }, {
            "name": "energy_daily_extreme",
            "description": "Find the maximum or minimum complete daily total in a validated historical period",
            "parameters": {
                "metric": sorted(DAILY_EXTREME_METRICS),
                "start": "YYYY-MM-DD Europe/Rome, inclusive",
                "end": "YYYY-MM-DD Europe/Rome, exclusive",
                "extremum": ["maximum", "minimum"]
            }
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
            "Use current_predictive_reserve for the current Predictive Reserve state; it cannot explain past nights.",
            "The thermostats heat; the air conditioner is driven only by the AI Climate controller and cannot be commanded.",
            "No other control, cost, tariffs, unsupported metrics or causal speculation; clarify unsupported requests."
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
                    if tool == "energy_metric" else {"id", "tool", "metric", "start", "end", "extremum"}
                    if tool == "energy_daily_extreme" else {"id", "tool"}
                    if tool in ("current_air_conditioner", "current_predictive_reserve") else {"id", "tool", "light"}
                    if tool == "current_lights" else {"id", "tool", "light", "state"}
                    if tool == "set_light_state" else {"id", "tool", "thermostat"}
                    if tool == "current_thermostats" else {"id", "tool", "thermostat", "target_c"}
                    if tool == "set_thermostat_target" else {"id", "tool", "metric"}
                    if tool == "current_energy_metric" else {"id", "tool", "source", "day"}
                    if tool == "backend_log_day" else {"id", "tool", "left_id", "right_id"}
                    if tool == "energy_comparison" else None)
        if expected is None or set(operation) != expected:
            raise ValueError("Invalid operation schema")
        identifier = operation["id"]
        if (not isinstance(identifier, str) or not identifier or len(identifier) > 40
                or identifier in ids):
            raise ValueError("Invalid operation id")
        if tool in ("energy_metric", "energy_daily_extreme", "current_energy_metric") and not isinstance(operation["metric"], str):
            raise ValueError("Invalid metric")
        if ((tool == "energy_metric" and operation["metric"] not in sources)
                or (tool == "current_energy_metric" and operation["metric"] not in CURRENT_METRICS)):
            raise ValueError("Unknown tool or metric")
        if tool == "current_lights" and operation["light"] not in (["all"] + sorted(LIGHT_TOPICS)):
            raise ValueError("Unknown light")
        if tool == "set_light_state" and (operation["light"] not in LIGHT_TOPICS
                                           or operation["state"] not in ("on", "off")):
            raise ValueError("Unknown light command")
        if tool == "current_thermostats" and operation["thermostat"] not in ("all", *THERMOSTATS):
            raise ValueError("Unknown thermostat")
        if tool == "set_thermostat_target" and (operation["thermostat"] not in THERMOSTATS
                                                 or operation["target_c"] not in THERMOSTAT_TARGETS):
            raise ValueError("Unknown thermostat command")
        if tool == "energy_daily_extreme" and (operation["metric"] not in DAILY_EXTREME_METRICS
                                                or operation["extremum"] not in ("maximum", "minimum")):
            raise ValueError("Unknown daily extreme metric or mode")
        if tool == "backend_log_day":
            if not isinstance(operation["source"], str) or operation["source"] not in LOG_SOURCES:
                raise ValueError("Unknown log source")
            if not isinstance(operation["day"], str):
                raise ValueError("Invalid day")
            day_bounds(operation["day"])
        if tool in ("energy_metric", "energy_daily_extreme"):
            if not all(isinstance(operation[k], str) for k in ("start", "end")):
                raise ValueError("Invalid dates")
            lo, hi = period_bounds(operation["start"], operation["end"])
            sample_budget += (hi-lo)//(sources[operation["metric"]]["interval_seconds"]*1000)
            if sample_budget > 2_200_000:
                raise ValueError("Plan exceeds sample budget")
            if tool == "energy_metric":
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
        keys = set(observation) if isinstance(observation, dict) else set()
        if (not isinstance(observation, dict)
                or keys not in ({"value", "received_at_ms", "retained", "source_topic"},
                                {"value", "received_at_ms", "retained", "source_topic", "last_actor", "action_at_ms"})
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


def validate_climate_snapshot(snapshot):
    if snapshot is None:
        return None
    if (not isinstance(snapshot, dict) or set(snapshot) != {"schema", "connected", "observations"}
            or snapshot["schema"] != "house_ai.current_climate_input.v1"
            or type(snapshot["connected"]) is not bool or not isinstance(snapshot["observations"], dict)
            or set(snapshot["observations"]) - CLIMATE_METRICS):
        raise ValueError("Invalid current climate snapshot")
    clean = {"schema": snapshot["schema"], "connected": snapshot["connected"], "observations": {}}
    for metric, observation in snapshot["observations"].items():
        if (not isinstance(observation, dict)
                or set(observation) != {"value", "received_at_ms", "retained", "source_topic"}
                or not isinstance(observation["value"], str) or not 1 <= len(observation["value"]) <= 500
                or type(observation["received_at_ms"]) is not int
                or not 0 <= observation["received_at_ms"] <= 253402214400000
                or type(observation["retained"]) is not bool
                or observation["source_topic"] != CLIMATE_TOPICS[metric]):
            raise ValueError("Invalid climate observation")
        clean["observations"][metric] = dict(observation)
    return clean


def validate_lights_snapshot(snapshot):
    if snapshot is None:
        return None
    if (not isinstance(snapshot, dict) or set(snapshot) not in ({"schema", "connected", "observations"},
                {"schema", "connected", "observations", "commands"})
            or snapshot["schema"] != "house_ai.current_lights_input.v1"
            or type(snapshot["connected"]) is not bool or not isinstance(snapshot["observations"], dict)
            or set(snapshot["observations"]) - (set(LIGHT_TOPICS) | set(LEGACY_LIGHT_TOPICS))):
        raise ValueError("Invalid current lights snapshot")
    clean = {"schema": snapshot["schema"], "connected": snapshot["connected"], "observations": {}}
    for light, observation in snapshot["observations"].items():
        keys = set(observation) if isinstance(observation, dict) else set()
        if (not isinstance(observation, dict)
                or keys not in ({"value", "received_at_ms", "retained", "source_topic"},
                                {"value", "received_at_ms", "retained", "source_topic", "last_actor", "action_at_ms"})
                or type(observation["value"]) is not bool
                or type(observation["received_at_ms"]) is not int
                or not 0 <= observation["received_at_ms"] <= 253402214400000
                or type(observation["retained"]) is not bool
                or observation["source_topic"] != {**LEGACY_LIGHT_TOPICS, **LIGHT_TOPICS}[light]):
            raise ValueError("Invalid light observation")
        if ("last_actor" in observation and (observation["last_actor"] not in ("utente", "automazione")
                or type(observation["action_at_ms"]) is not int
                or not 0 <= observation["action_at_ms"] <= observation["received_at_ms"])):
            raise ValueError("Invalid light actor evidence")
        if light in LEGACY_LIGHT_TOPICS:
            continue
        # Legacy Android actors were inferred from state changes, not Node-RED evidence.
        clean["observations"][light] = {key: value for key, value in observation.items()
                                        if key not in ("last_actor", "action_at_ms")}
    clean["commands"] = validate_commands(snapshot.get("commands", {}), LIGHT_TOPICS)
    return clean


def validate_predictive_reserve_snapshot(snapshot):
    if snapshot is None:
        return None
    if (not isinstance(snapshot, dict) or set(snapshot) != {"schema", "connected", "observations"}
            or snapshot["schema"] != "house_ai.current_predictive_reserve_input.v1"
            or type(snapshot["connected"]) is not bool or not isinstance(snapshot["observations"], dict)
            or set(snapshot["observations"]) - set(PR_TOPICS)):
        raise ValueError("Invalid current predictive reserve snapshot")
    clean = {"schema": snapshot["schema"], "connected": snapshot["connected"], "observations": {}}
    for metric, observation in snapshot["observations"].items():
        if (not isinstance(observation, dict)
                or set(observation) != {"value", "received_at_ms", "retained", "source_topic"}
                or observation["value"] not in PR_VALUES[metric]
                or type(observation["received_at_ms"]) is not int
                or not 0 <= observation["received_at_ms"] <= 253402214400000
                or type(observation["retained"]) is not bool
                or observation["source_topic"] != PR_TOPICS[metric]):
            raise ValueError("Invalid predictive reserve observation")
        clean["observations"][metric] = dict(observation)
    return clean


def validate_thermostats_snapshot(snapshot):
    if snapshot is None:
        return None
    if (not isinstance(snapshot, dict) or set(snapshot) != {"schema", "connected", "observations"}
            or snapshot["schema"] != "house_ai.current_thermostats_input.v1"
            or type(snapshot["connected"]) is not bool or not isinstance(snapshot["observations"], dict)
            or set(snapshot["observations"]) - set(THERMOSTAT_TOPICS)):
        raise ValueError("Invalid current thermostats snapshot")
    clean = {"schema": snapshot["schema"], "connected": snapshot["connected"], "observations": {}}
    for key, observation in snapshot["observations"].items():
        kind = THERMOSTAT_METRICS[key.split(".", 1)[1]]
        if (not isinstance(observation, dict)
                or set(observation) != {"value", "received_at_ms", "retained", "source_topic"}
                or (kind == "bool" and type(observation["value"]) is not bool)
                or (kind == "number" and (not finite_number(observation["value"])
                                          or not -30 <= observation["value"] <= 60))
                or type(observation["received_at_ms"]) is not int
                or not 0 <= observation["received_at_ms"] <= 253402214400000
                or type(observation["retained"]) is not bool
                or observation["source_topic"] != THERMOSTAT_TOPICS[key]):
            raise ValueError("Invalid thermostat observation")
        clean["observations"][key] = dict(observation)
    return clean


def execute(client, plan, sources=None, current_snapshot=None, climate_snapshot=None,
            lights_snapshot=None, log_root=None, pr_snapshot=None, thermostats_snapshot=None):
    sources = sources or json.loads(CATALOG_PATH.read_text(encoding="utf-8"))["sources"]
    checked = validate_plan(plan, sources)
    current = validate_current_snapshot(current_snapshot)
    climate = validate_climate_snapshot(climate_snapshot)
    lights = validate_lights_snapshot(lights_snapshot)
    reserve = validate_predictive_reserve_snapshot(pr_snapshot)
    thermostats = validate_thermostats_snapshot(thermostats_snapshot)
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
        if operation["tool"] == "current_predictive_reserve":
            observations = reserve["observations"] if reserve else {}
            results.append({"id": operation["id"], "result": {
                "schema": "house_ai.current_predictive_reserve.v1",
                "status": "available" if observations else "missing",
                "connected": reserve["connected"] if reserve else False,
                "observations": observations,
                "limitations": ["Stato dichiarato dal Digital Twin; la modalità si cambia solo dall'editor Node-RED.",
                    "Lo stato attuale non spiega decisioni di notti passate: il PR non registra le sue decisioni.",
                    "Timestamp di ricezione Android; età delle misure sorgente ignota."]}})
            continue
        if operation["tool"] == "current_thermostats":
            selected = THERMOSTATS if operation["thermostat"] == "all" else (operation["thermostat"],)
            observations = {key: value for key, value in (thermostats["observations"] if thermostats else {}).items()
                            if key.split(".", 1)[0] in selected}
            results.append({"id": operation["id"], "thermostat": operation["thermostat"], "result": {
                "schema": "house_ai.current_thermostats.v1",
                "status": "available" if observations else "missing",
                "connected": thermostats["connected"] if thermostats else False,
                "observations": observations,
                "limitations": ["Stati dichiarati dal Digital Twin; il setpoint è l'ultimo comando inviato, non una lettura del termostato.",
                    "Timestamp di ricezione Android; età delle misure sorgente ignota."]}})
            continue
        if operation["tool"] == "set_thermostat_target":
            results.append({"id": operation["id"], "thermostat": operation["thermostat"], "result": {
                "schema": "house_ai.thermostat_command.v1", "status": "command_ready",
                "thermostat": operation["thermostat"], "target_c": operation["target_c"],
                "limitations": ["Il backend seleziona solo termostato e setpoint; topic e payload sono risolti dall'app.",
                                "Il timer delle fasce orarie (Node-RED .15) e la modalità vacanza possono reimpostare il setpoint.",
                                "Il successivo /stat è l'eco del comando, non una conferma del termostato."]}})
            continue
        if operation["tool"] == "current_air_conditioner":
            observations = climate["observations"] if climate else {}
            results.append({"id": operation["id"], "result": {
                "schema": "house_ai.current_air_conditioner.v1",
                "status": "available" if observations else "missing",
                "connected": climate["connected"] if climate else False,
                "observations": observations,
                "limitations": ["Stati dichiarati dal Digital Twin; non conferma fisica.",
                    "Il motivo è testo registrato dal controller, non una deduzione del modello.",
                    "Timestamp di ricezione Android; età delle misure sorgente ignota."]}})
            continue
        if operation["tool"] == "current_lights":
            selected = lights["observations"] if lights else {}
            commands = lights["commands"] if lights else {}
            if operation["light"] != "all":
                selected = ({operation["light"]: selected[operation["light"]]}
                            if operation["light"] in selected else {})
            if operation["light"] != "all":
                commands = {key: value for key, value in commands.items() if key == operation["light"]}
            results.append({"id": operation["id"], "light": operation["light"], "result": {
                "schema": "house_ai.current_lights.v1",
                "status": "available" if selected or commands else "missing",
                "connected": lights["connected"] if lights else False,
                "observations": selected,
                "commands": commands,
                "mapped_count": len(LIGHT_TOPICS),
                "limitations": ["Stati dichiarati dal Digital Twin; non conferma fisica.",
                    "Timestamp di ricezione Android, non istante del cambio stato.",
                    "Retained non dimostra freschezza, durata o autore dell'azione."]}})
            continue
        if operation["tool"] == "set_light_state":
            results.append({"id": operation["id"], "light": operation["light"], "result": {
                "schema": "house_ai.light_command.v1", "status": "command_ready",
                "light": operation["light"], "state": operation["state"],
                "limitations": ["Il backend seleziona solo ID e stato; topic e payload sono risolti dall'app.",
                                "Il successivo /stat è uno stato dichiarato, non una conferma fisica."]}})
            continue
        source = sources[operation["metric"]]
        sign = source.get("import_sign", source.get("direction_sign"))
        try:
            if operation["tool"] == "energy_daily_extreme":
                result = daily_extreme_report(cached, operation["start"], operation["end"],
                                              operation["metric"], source["feed_id"], source["unit"],
                                              sign, source["interval_seconds"], operation["extremum"])
            else:
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
