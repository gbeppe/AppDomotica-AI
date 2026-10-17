"""Validated energy-domain tools called by a planner, never arbitrary code."""
import json
import math
import re
import time
from pathlib import Path

from energy_history import daily_extreme_report, energy_report, period_bounds
from log_tools import SOURCES as LOG_SOURCES, tool_catalog, read_day
from evidence import day_bounds
from light_semantics import validate_commands
from energy_flows import allocate
import rules_tools


CATALOG_PATH = Path(__file__).with_name("energy_sources.json")
MAX_OPERATIONS = 6
CURRENT_METRICS = {
    "solar_power_w", "home_consumption_w", "grid_power_w",
    "battery_power_w", "battery_soc_percent"
}
# Same freshness rule as the app energy diagram: older readings are not used.
ENERGY_STALE_AFTER_MS = 180_000
ENERGY_DETAILS = ("summary", "flows")
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
# Room sensors published by the Digital Twin Router (live broker 2026-10-11); a missing
# metric (e.g. outdoor humidex) simply stays missing.
ENV_ROOMS = ("living", "bedroom", "kids_room", "service_bathroom", "technical_room", "outdoor")
ENV_METRICS = ("temperature", "humidity", "humidex")
ENV_TOPICS = {f"{room}.{metric}": f"zara/interface/env/{room}/{metric}/stat"
              for room in ENV_ROOMS for metric in ENV_METRICS}
ROOM_THERMOSTAT = {"living": "thermostat_living", "service_bathroom": "thermostat_bath"}
# Heating plant read-only state (Digital Twin Router, live broker 2026-10-14): fireplace
# settings and declared state from .15, puffer, floor pump, gas boiler, solar thermal.
HEATING_METRICS = {
    "fireplace.power": ("fireplace/main/power", "bool"),
    "fireplace.level": ("fireplace/main/level", "level"),
    "fireplace.mode": ("fireplace/main/mode", "mode"),
    "fireplace.start_time": ("fireplace/main/start_time", "time"),
    "fireplace.stop_time": ("fireplace/main/stop_time", "time"),
    "fireplace.auto_power": ("fireplace/main/auto_power", "bool"),
    "puffer.top_temperature": ("heating/puffer/top_temperature", "temperature"),
    "puffer.bottom_temperature": ("heating/puffer/bottom_temperature", "temperature"),
    "puffer.acs_temperature": ("energy/puffer_acs", "temperature"),
    "floor_pump.enabled": ("heating/floor_pump/enabled", "bool"),
    "floor_pump.running": ("heating/floor_pump/running", "bool"),
    "gas_boiler.flame": ("heating/gas_boiler/flame", "bool"),
    "gas_boiler.modulation": ("heating/gas_boiler/modulation", "percent"),
    "solar_thermal.collector_temperature": ("heating/solar_thermal/collector_temperature", "temperature"),
    "solar_thermal.pump_speed": ("heating/solar_thermal/pump_speed", "percent"),
}
HEATING_TOPICS = {key: f"zara/interface/{path}/stat" for key, (path, _) in HEATING_METRICS.items()}
# App labels published by ROUTER-FIREPLACE-MODE.1/.2.
FIREPLACE_MODES = ("Disattivato", "Riscaldamento", "Integrazione Caldaia", "Acqua Sanitaria", "Manuale")
TIME_PATTERN = re.compile(r"(?:[01][0-9]|2[0-3]):[0-5][0-9]")
# Fireplace commands (15/10/2026): settings run at once; manual on/off only in "Manuale" and
# only after the user confirms in the app. The power level is never set from AI Smart.
FIREPLACE_MANUAL = "Manuale"
FIREPLACE_LEVELS = ("1", "2", "3", "4", "5", "6")
UNCHANGED = "unchanged"
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
    # Broadlink RF socket (Node-RED "luce allarme"/"luci natale"); fase 3.3.
    "lights_prolunga": "zara/interface/lights/prolunga/power/stat",
}
# Node-RED "Scene Dispatcher" accepts exactly these payloads on zara/interface/lights/scene/cmd.
LIGHT_SCENES = ("tv", "sleep", "all_on", "all_off")
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
    result = {
        "domains": ["energy", "climate", "lights", "heating"],
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
            "name": "set_light_scene",
            "description": "Run one Node-RED light scene: tv (TV lamp and living on, bookshelf and TV chandelier off), sleep (all lights off after 2 minutes), all_on (main indoor and pool lights on; nothing in holiday mode, fewer with ECO lights), all_off (all indoor, outdoor and pool lights off)",
            "parameters": {"scene": list(LIGHT_SCENES)}
        }, {
            "name": "current_environment",
            "description": "Read room temperature, humidity and humidex for one room or the whole house (all includes outdoor and technical room); living and service_bathroom also report thermostat setpoint and heating on/off",
            "parameters": {"room": ["all", *ENV_ROOMS], "metric": ["all", *ENV_METRICS]}
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
            "name": "current_heating",
            "description": "Read the heating plant: fireplace on/off, power level, mode, daily time window and automatic power control; puffer top, bottom and domestic hot water temperatures; floor pump enabled and running; gas boiler flame and modulation; solar thermal pump and collector. Read only",
            "parameters": {}
        }, {
            "name": "set_fireplace_settings",
            "description": "Set the fireplace mode, daily start/stop time (HH:MM, start before stop) and/or automatic power control; unchanged for what the user did not ask. Never sets the power level",
            "parameters": {"mode": [UNCHANGED, *FIREPLACE_MODES], "start_time": "HH:MM or unchanged",
                           "stop_time": "HH:MM or unchanged", "auto_power": [UNCHANGED, "on", "off"]}
        }, {
            "name": "set_fireplace_power",
            "description": "Switch the fireplace on or off by hand; allowed only in mode Manuale and executed only after the user confirms in the app",
            "parameters": {"state": ["on", "off"]}
        }, {
            "name": "set_fireplace_level",
            "description": "Set the fireplace power level 1-6; allowed only in mode Manuale with the fireplace on, executed after the user confirms in the app",
            "parameters": {"level": list(FIREPLACE_LEVELS)}
        }, {
            "name": "current_predictive_reserve",
            "description": "Read whether the Predictive Reserve control is enabled, its operating mode (SHADOW or ACTIVE), model readiness and what that means now for new night dehumidification starts",
            "parameters": {}
        }, {
            "name": "current_energy_overview",
            "description": "Read the energy of the house now in one answer: solar production, battery level and charging/discharging, house consumption, grid import or export. detail=flows adds the estimated flows (solar to house/battery/grid, battery to house, grid to house)",
            "parameters": {"detail": list(ENERGY_DETAILS)}
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
            "Use current_energy_overview for the energy of the house now; current_energy_metric only for one explicit quantity.",
            "Use current_heating to read the fireplace, puffer, floor pump, gas boiler and solar thermal; only the fireplace settings, manual on/off and, in Manuale, the power level can be commanded.",
            "The thermostats heat; the air conditioner is driven only by the AI Climate controller and cannot be commanded.",
            "Rules ('quando... allora...', 'appena...', 'ogni volta che...'): create_rule with one trigger and one action; list_rules and delete_rule to read or remove them. Never turn a rule into an immediate command.",
            "No other control, cost, tariffs, unsupported metrics or causal speculation; clarify unsupported requests."
        ]
    }
    result["tools"].extend(rules_tools.CATALOG)
    return result


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
                    if tool in ("current_air_conditioner", "current_predictive_reserve", "current_heating") else {"id", "tool", "light"}
                    if tool == "current_lights" else {"id", "tool", "light", "state"}
                    if tool == "set_light_state" else {"id", "tool", "scene"}
                    if tool == "set_light_scene" else {"id", "tool", "room", "metric"}
                    if tool == "current_environment" else {"id", "tool", "thermostat"}
                    if tool == "current_thermostats" else {"id", "tool", "thermostat", "target_c"}
                    if tool == "set_thermostat_target" else {"id", "tool", "metric"}
                    if tool == "current_energy_metric" else {"id", "tool", "detail"}
                    if tool == "current_energy_overview" else {"id", "tool", "mode", "start_time", "stop_time", "auto_power"}
                    if tool == "set_fireplace_settings" else {"id", "tool", "state"}
                    if tool == "set_fireplace_power" else {"id", "tool", "level"}
                    if tool == "set_fireplace_level" else {"id", "tool", "source", "day"}
                    if tool == "backend_log_day" else {"id", "tool", "left_id", "right_id"}
                    if tool == "energy_comparison" else rules_tools.FIELDS.get(tool) if isinstance(tool, str) else None)
        if expected is None or set(operation) != expected:
            raise ValueError("Invalid operation schema")
        if isinstance(tool, str) and tool in rules_tools.TOOL_NAMES:
            rules_tools.validate_operation(operation)
        identifier = operation["id"]
        if (not isinstance(identifier, str) or not identifier or len(identifier) > 40
                or identifier in ids):
            raise ValueError("Invalid operation id")
        if tool in ("energy_metric", "energy_daily_extreme", "current_energy_metric") and not isinstance(operation["metric"], str):
            raise ValueError("Invalid metric")
        if ((tool == "energy_metric" and operation["metric"] not in sources)
                or (tool == "current_energy_metric" and operation["metric"] not in CURRENT_METRICS)):
            raise ValueError("Unknown tool or metric")
        if tool == "set_fireplace_settings":
            times = [operation[k] for k in ("start_time", "stop_time")]
            if (operation["mode"] not in (UNCHANGED, *FIREPLACE_MODES)
                    or operation["auto_power"] not in (UNCHANGED, "on", "off")
                    or not all(isinstance(t, str) and (t == UNCHANGED or TIME_PATTERN.fullmatch(t)) for t in times)
                    or all(operation[k] == UNCHANGED for k in ("mode", "start_time", "stop_time", "auto_power"))):
                raise ValueError("Unknown fireplace settings")
        if tool == "set_fireplace_power" and operation["state"] not in ("on", "off"):
            raise ValueError("Unknown fireplace power command")
        if tool == "set_fireplace_level" and operation["level"] not in FIREPLACE_LEVELS:
            raise ValueError("Unknown fireplace level")
        if tool == "current_energy_overview" and operation["detail"] not in ENERGY_DETAILS:
            raise ValueError("Unknown energy detail")
        if tool == "current_lights" and operation["light"] not in (["all"] + sorted(LIGHT_TOPICS)):
            raise ValueError("Unknown light")
        if tool == "set_light_state" and (operation["light"] not in LIGHT_TOPICS
                                           or operation["state"] not in ("on", "off")):
            raise ValueError("Unknown light command")
        if tool == "current_environment" and (operation["room"] not in ("all", *ENV_ROOMS)
                                              or operation["metric"] not in ("all", *ENV_METRICS)):
            raise ValueError("Unknown room or environment metric")
        if tool == "set_light_scene" and operation["scene"] not in LIGHT_SCENES:
            raise ValueError("Unknown light scene")
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




def heating_value_ok(kind, value):
    if kind == "bool":
        return type(value) is bool
    if kind == "mode":
        return value in FIREPLACE_MODES
    if kind == "time":
        return isinstance(value, str) and TIME_PATTERN.fullmatch(value) is not None
    if not finite_number(value):
        return False
    if kind == "level":
        return value in (1, 2, 3, 4, 5, 6)
    if kind == "percent":
        return 0 <= value <= 100
    return -40 <= value <= 200


def validate_heating_snapshot(snapshot):
    if snapshot is None:
        return None
    if (not isinstance(snapshot, dict) or set(snapshot) != {"schema", "connected", "observations"}
            or snapshot["schema"] != "house_ai.current_heating_input.v1"
            or type(snapshot["connected"]) is not bool or not isinstance(snapshot["observations"], dict)
            or set(snapshot["observations"]) - set(HEATING_TOPICS)):
        raise ValueError("Invalid current heating snapshot")
    clean = {"schema": snapshot["schema"], "connected": snapshot["connected"], "observations": {}}
    for key, observation in snapshot["observations"].items():
        if (not isinstance(observation, dict)
                or set(observation) != {"value", "received_at_ms", "retained", "source_topic"}
                or not heating_value_ok(HEATING_METRICS[key][1], observation["value"])
                or type(observation["received_at_ms"]) is not int
                or not 0 <= observation["received_at_ms"] <= 253402214400000
                or type(observation["retained"]) is not bool
                or observation["source_topic"] != HEATING_TOPICS[key]):
            raise ValueError("Invalid heating observation")
        clean["observations"][key] = dict(observation)
    return clean


def validate_environment_snapshot(snapshot):
    if snapshot is None:
        return None
    if (not isinstance(snapshot, dict) or set(snapshot) != {"schema", "connected", "observations"}
            or snapshot["schema"] != "house_ai.current_environment_input.v1"
            or type(snapshot["connected"]) is not bool or not isinstance(snapshot["observations"], dict)
            or set(snapshot["observations"]) - set(ENV_TOPICS)):
        raise ValueError("Invalid current environment snapshot")
    clean = {"schema": snapshot["schema"], "connected": snapshot["connected"], "observations": {}}
    for key, observation in snapshot["observations"].items():
        metric = key.split(".", 1)[1]
        low, high = (0, 100) if metric == "humidity" else (-40, 70)
        if (not isinstance(observation, dict)
                or set(observation) != {"value", "received_at_ms", "retained", "source_topic"}
                or not finite_number(observation["value"]) or not low <= observation["value"] <= high
                or type(observation["received_at_ms"]) is not int
                or not 0 <= observation["received_at_ms"] <= 253402214400000
                or type(observation["retained"]) is not bool
                or observation["source_topic"] != ENV_TOPICS[key]):
            raise ValueError("Invalid environment observation")
        clean["observations"][key] = dict(observation)
    return clean


def heating_value(heating, key):
    observation = (heating["observations"] if heating else {}).get(key)
    return observation["value"] if observation else None


def fireplace_settings(operation, heating):
    pick = lambda key: None if operation[key] == UNCHANGED else operation[key]
    mode, start, stop, auto = pick("mode"), pick("start_time"), pick("stop_time"), pick("auto_power")
    result = {"schema": "house_ai.fireplace_settings_command.v1", "status": "confirmation_required",
              "mode": mode, "start_time": start, "stop_time": stop,
              "auto_power": None if auto is None else auto == "on",
              "limitations": ["Il backend seleziona solo modalità, orari e potenza automatica; topic e payload sono risolti dall'app.",
                              "Il livello di potenza si imposta da AI Smart solo in Manuale; nelle altre modalità, con la potenza automatica attiva, lo decide .15.",
                              "Il successivo /stat è l'eco del comando, non una conferma di .15."]}
    # .15 compares HH:MM strings within one day: the start must come before the stop.
    first = start or heating_value(heating, "fireplace.start_time")
    last = stop or heating_value(heating, "fireplace.stop_time")
    if (start or stop) and first and last and first >= last:
        result.update(status="rejected", reason="time_order", start_time=first, stop_time=last)
    return result


def fireplace_power(state, heating, planned_mode):
    mode = planned_mode or heating_value(heating, "fireplace.mode")
    power = heating_value(heating, "fireplace.power")
    result = {"schema": "house_ai.fireplace_power_command.v1", "state": state, "mode": mode,
              "limitations": ["Acceso e spento manuali valgono solo in modalità Manuale; l'app li invia solo dopo la conferma.",
                              "Lo stato acceso/spento è quello dichiarato da .15, non una misura della fiamma."]}
    if mode != FIREPLACE_MANUAL:
        result.update(status="rejected", reason="mode_unknown" if mode is None else "not_manual")
    elif power is (state == "on") and planned_mode is None:
        result.update(status="unchanged")
    else:
        result.update(status="confirmation_required")
    return result


def fireplace_level(level, heating, planned_mode):
    mode = planned_mode or heating_value(heating, "fireplace.mode")
    result = {"schema": "house_ai.fireplace_level_command.v1", "level": int(level), "mode": mode,
              "auto_power": heating_value(heating, "fireplace.auto_power"),
              "limitations": ["Il livello si imposta da AI Smart solo in modalità Manuale e a caminetto acceso; "
                              "nelle altre modalità vale la potenza automatica attiva o disattiva.",
                              "Il successivo /stat è l'eco del comando, non una misura della fiamma."]}
    if mode != FIREPLACE_MANUAL:
        result.update(status="rejected", reason="mode_unknown" if mode is None else "not_manual")
    elif heating_value(heating, "fireplace.power") is not True:
        # .15 starts a manual ignition at level 3: a level set while off would be overwritten.
        result.update(status="rejected", reason="off")
    elif heating_value(heating, "fireplace.level") == int(level):
        result.update(status="unchanged")
    else:
        result.update(status="confirmation_required")
    return result


def energy_overview(current, detail, now_ms):
    observations = current["observations"] if current else {}
    fresh = {metric: o for metric, o in observations.items()
             if 0 <= now_ms - o["received_at_ms"] <= ENERGY_STALE_AFTER_MS}
    result = {"schema": "house_ai.current_energy_overview.v1",
              "status": "available" if fresh else "missing",
              "connected": current["connected"] if current else False,
              "observations": fresh, "stale": sorted(set(observations) - set(fresh)),
              "limitations": ["Timestamp di ricezione Android; letture più vecchie di 3 minuti non usate.",
                              "Sotto 50 watt rete, batteria e flussi sono considerati fermi."]}
    if detail == "flows":
        powers = ("solar_power_w", "battery_power_w", "grid_power_w", "home_consumption_w")
        if all(metric in fresh for metric in powers):
            allocation = allocate(*(fresh[metric]["value"] for metric in powers))
            result["flows"] = allocation["flows"]
            result["imbalance_w"] = allocation["imbalance_w"]
            result["limitations"].append(
                "Flussi stimati con regole fisse (fotovoltaico prima alla casa, poi alla batteria, poi in rete); "
                "nessun contatore li misura direttamente.")
            if not allocation["consistent"]:
                result["limitations"].append(
                    f"Le misure non tornano: differenza fra sorgenti e consumi di {abs(allocation['imbalance_w'])} watt.")
        else:
            result["flows"] = None
    return result


def execute(client, plan, sources=None, current_snapshot=None, climate_snapshot=None,
            lights_snapshot=None, log_root=None, pr_snapshot=None, thermostats_snapshot=None,
            environment_snapshot=None, heating_snapshot=None, rules_snapshot=None):
    sources = sources or json.loads(CATALOG_PATH.read_text(encoding="utf-8"))["sources"]
    checked = validate_plan(plan, sources)
    rules = rules_tools.validate_rules_snapshot(rules_snapshot)
    current = validate_current_snapshot(current_snapshot)
    climate = validate_climate_snapshot(climate_snapshot)
    lights = validate_lights_snapshot(lights_snapshot)
    reserve = validate_predictive_reserve_snapshot(pr_snapshot)
    thermostats = validate_thermostats_snapshot(thermostats_snapshot)
    environment = validate_environment_snapshot(environment_snapshot)
    heating = validate_heating_snapshot(heating_snapshot)
    if checked["clarification"]:
        return checked
    results = []
    planned_mode = None  # a mode set earlier in the same plan counts for set_fireplace_power
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
        if operation["tool"] == "set_fireplace_settings":
            result = fireplace_settings(operation, heating)
            if result["status"] == "confirmation_required" and result["mode"]:
                planned_mode = result["mode"]
            results.append({"id": operation["id"], "result": result})
            continue
        if operation["tool"] == "set_fireplace_power":
            results.append({"id": operation["id"], "result": fireplace_power(operation["state"], heating, planned_mode)})
            continue
        if operation["tool"] == "set_fireplace_level":
            results.append({"id": operation["id"], "result": fireplace_level(operation["level"], heating, planned_mode)})
            continue
        if operation["tool"] == "create_rule":
            results.append({"id": operation["id"], "result": rules_tools.build_rule(operation, rules)})
            continue
        if operation["tool"] == "list_rules":
            results.append({"id": operation["id"], "result": rules_tools.list_rules(rules)})
            continue
        if operation["tool"] == "delete_rule":
            results.append({"id": operation["id"], "result": rules_tools.delete_rules(operation["about"], rules)})
            continue
        if operation["tool"] == "current_energy_overview":
            results.append({"id": operation["id"], "detail": operation["detail"],
                            "result": energy_overview(current, operation["detail"], int(time.time() * 1000))})
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
        if operation["tool"] == "current_heating":
            observations = heating["observations"] if heating else {}
            results.append({"id": operation["id"], "result": {
                "schema": "house_ai.current_heating.v1",
                "status": "available" if observations else "missing",
                "connected": heating["connected"] if heating else False,
                "observations": observations,
                "limitations": ["Stati dichiarati dal Digital Twin: acceso/spento e livello del caminetto sono gli ultimi comandi confermati dalla scheda, non una misura della fiamma.",
                    "Caminetto, pompa pavimento e caldaia sono gestiti dalle logiche Node-RED di .15; AI Smart li legge soltanto.",
                    "Timestamp di ricezione Android; età delle misure sorgente ignota."]}})
            continue
        if operation["tool"] == "current_environment":
            rooms = ENV_ROOMS if operation["room"] == "all" else (operation["room"],)
            metrics = ENV_METRICS if operation["metric"] == "all" else (operation["metric"],)
            wanted_keys = {f"{r}.{m}" for r in rooms for m in metrics}
            observations = {key: value for key, value in (environment["observations"] if environment else {}).items()
                            if key in wanted_keys}
            # Thermostat state only for a single-room overview, where it belongs to the answer.
            wanted = ROOM_THERMOSTAT.get(operation["room"]) if operation["metric"] == "all" else None
            room_thermostat = {key: value for key, value in (thermostats["observations"] if thermostats else {}).items()
                               if wanted and key.split(".", 1)[0] == wanted}
            results.append({"id": operation["id"], "room": operation["room"], "metric": operation["metric"], "result": {
                "schema": "house_ai.current_environment.v1",
                "status": "available" if observations or room_thermostat else "missing",
                "connected": environment["connected"] if environment else False,
                "observations": observations, "thermostat": room_thermostat,
                "limitations": ["Valori dichiarati dal Digital Twin; timestamp di ricezione Android, età della misura ignota.",
                                "Il setpoint del termostato è l'ultimo valore comandato, non una lettura del termostato."]}})
            continue
        if operation["tool"] == "set_light_scene":
            results.append({"id": operation["id"], "result": {
                "schema": "house_ai.light_scene_command.v1", "status": "command_ready",
                "scene": operation["scene"],
                "limitations": ["Il backend seleziona solo il nome della scena; topic e payload sono risolti dall'app.",
                                "Node-RED esegue la scena in sequenza; gli stati /stat successivi sono dichiarati, non conferme fisiche."]}})
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
                "schema": "house_ai.thermostat_command.v1", "status": "confirmation_required",
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
