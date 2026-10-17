"""Natural-language rules, phase H2 (docs/AI_MODE/REGOLE_NATURALI.md).

The planner turns a "quando... allora..." request into create_rule with closed parameters; this
module validates it against the same allowlists as the rules engine on .20 (RULES-ENGINE.1) and
returns the rule to publish after the user's confirmation. list_rules and delete_rule read the
rules the app received on zara/interface/rules/list/stat. Nothing is published from here.
"""
import re

NONE = "none"
# measure -> (spoken subject, unit, min, max)
MEASURES = {
    "puffer_top": ("il puffer alto", "gradi", 10, 95),
    "puffer_bottom": ("il puffer basso", "gradi", 10, 95),
    "outdoor": ("la temperatura esterna", "gradi", -20, 45),
    "living": ("il soggiorno", "gradi", 5, 35),
    "bedroom": ("la camera", "gradi", 5, 35),
    "kids_room": ("la cameretta", "gradi", 5, 35),
    "service_bathroom": ("il bagno di servizio", "gradi", 5, 35),
    "battery_soc": ("la batteria", "per cento", 0, 100),
    "solar_power": ("il fotovoltaico", "watt", 0, 15000),
    "home_power": ("il consumo della casa", "watt", 0, 15000),
}
LIGHTS = {"lights_living": "la luce del soggiorno", "lights_libreria": "la luce della libreria",
          "lights_tv": "la lampada TV", "lights_reading": "la luce del tavolino lettura",
          "lights_laundry": "la luce della lavanderia", "lights_porch_entrance": "la luce del portico",
          "lights_service_entrance": "la luce dell'ingresso di servizio", "lights_prolunga": "la prolunga"}
SCENES = {"tv": "attivo la scena TV", "sleep": "attivo la scena notte", "all_on": "accendo tutte le luci",
          "all_off": "spengo tutte le luci"}
THERMOSTATS = {"thermostat_living": "il termostato del soggiorno", "thermostat_bath": "il termostato del bagno"}
FIREPLACE_MODES = ("Disattivato", "Riscaldamento", "Integrazione Caldaia", "Acqua Sanitaria", "Manuale")
PROFILE_MODES = {"auto": "Automatico", "spento": "Spento", "ridotto": "Ridotto", "pieno": "Pieno"}
ACTIONS = ("set_light_state", "set_light_scene", "set_thermostat_target", "set_fireplace_power",
           "set_fireplace_level", "set_fireplace_mode", "set_heating_profile")
DAYS = {"all": [0, 1, 2, 3, 4, 5, 6], "weekdays": [1, 2, 3, 4, 5], "weekend": [0, 6]}
DAYS_TEXT = {"all": "ogni giorno", "weekdays": "nei giorni feriali", "weekend": "sabato e domenica"}
ABOUT = ("fireplace", "lights", "thermostats", "heating_profile", "all", "any")
TIME = re.compile(r"^([01]\d|2[0-3]):[0-5]\d$")
MAX_RULES = 10

CATALOG = [{
    "name": "create_rule",
    "description": "Create a rule 'when <measure> goes above/below <value>, or at <time>, do <action>'. Exactly one trigger and one action; lifetime once unless the user says always/every time. Created only after the user confirms in the app",
    "parameters": {"trigger": ["threshold", "time"], "metric": [NONE, *MEASURES], "op": [NONE, "above", "below"],
                   "value": "number or none", "at": "HH:MM or none", "days": [NONE, *DAYS],
                   "action": list(ACTIONS), "target": "light id, thermostat id, or none",
                   "setting": "on/off, scene, target °C, level 1-6, fireplace mode or heating profile",
                   "lifetime": ["once", "always"]}
}, {
    "name": "list_rules",
    "description": "List the rules of the house with their state (in test, active, expired)",
    "parameters": {}
}, {
    "name": "delete_rule",
    "description": "Delete the rule about a device ('cancella la regola del caminetto'), all rules, or the only one; confirmed in the app",
    "parameters": {"about": list(ABOUT)}
}]
TOOL_NAMES = tuple(t["name"] for t in CATALOG)
FIELDS = {"create_rule": {"id", "tool", "trigger", "metric", "op", "value", "at", "days", "action", "target",
                          "setting", "lifetime"},
          "list_rules": {"id", "tool"}, "delete_rule": {"id", "tool", "about"}}


def _number(text):
    try:
        value = float(str(text).replace(",", "."))
    except ValueError:
        return None
    return value if value == value and abs(value) != float("inf") else None


def validate_operation(op):
    """Shape and closed values only; the rule content is checked in build_rule."""
    tool = op["tool"]
    if tool == "delete_rule" and op["about"] not in ABOUT:
        raise ValueError("Unknown rule target")
    if tool == "create_rule":
        if (op["trigger"] not in ("threshold", "time") or op["action"] not in ACTIONS
                or op["lifetime"] not in ("once", "always")
                or op["metric"] not in (NONE, *MEASURES) or op["op"] not in (NONE, "above", "below")
                or op["days"] not in (NONE, *DAYS)
                or not all(isinstance(op[k], str) and len(op[k]) <= 40 for k in ("value", "at", "target", "setting"))):
            raise ValueError("Invalid rule")


def _action(op):
    """(engine action, spoken text) or (None, reason)."""
    tool, target, setting = op["action"], op["target"], op["setting"].strip()
    if tool == "set_light_state":
        if target in LIGHTS and setting in ("on", "off"):
            return {"tool": tool, "light": target, "state": setting}, f"{'accendo' if setting == 'on' else 'spengo'} {LIGHTS[target]}"
        return None, "luce non ammessa"
    if tool == "set_light_scene":
        return ({"tool": tool, "scene": setting}, SCENES[setting]) if setting in SCENES else (None, "scena non ammessa")
    if tool == "set_thermostat_target":
        value = _number(setting)
        if target in THERMOSTATS and value is not None and 16 <= value <= 22 and value * 2 == round(value * 2):
            return ({"tool": tool, "thermostat": target, "target_c": value},
                    f"imposto {THERMOSTATS[target]} a {_spoken(value)} gradi")
        return None, "setpoint ammesso solo tra 16 e 22 gradi a passi di mezzo grado"
    if tool == "set_fireplace_power":
        if setting in ("on", "off"):
            return {"tool": tool, "state": setting}, f"{'accendo' if setting == 'on' else 'spengo'} il caminetto"
        return None, "comando del caminetto non valido"
    if tool == "set_fireplace_level":
        if setting in ("1", "2", "3", "4", "5", "6"):
            return {"tool": tool, "level": int(setting)}, f"imposto il caminetto al livello {setting}"
        return None, "livello ammesso da 1 a 6"
    if tool == "set_fireplace_mode":
        return ({"tool": tool, "mode": setting}, f"metto il caminetto in modalità {setting}") \
            if setting in FIREPLACE_MODES else (None, "modalità del caminetto non valida")
    if tool == "set_heating_profile":
        return ({"tool": tool, "mode": setting}, f"metto il profilo di riscaldamento su {PROFILE_MODES[setting]}") \
            if setting in PROFILE_MODES else (None, "profilo non valido")
    return None, "azione non ammessa"


def _spoken(value):
    return (str(int(value)) if value == int(value) else f"{value:.1f}".replace(".", ","))


def build_rule(op, rules_snapshot):
    """house_ai.rule_command.v1: the rule to publish after confirmation, or why it is refused."""
    result = {"schema": "house_ai.rule_command.v1", "verb": "create",
              "limitations": ["La regola resta in prova per un giorno: annuncia cosa farebbe senza inviare comandi.",
                              "All'intervento non chiede conferma: vale la conferma data alla creazione.",
                              "Il motore delle regole sul .20 rivalida la regola con le stesse liste."]}
    if rules_snapshot is not None and sum(1 for r in rules_snapshot["rules"] if r["status"] != "expired") >= MAX_RULES:
        return {**result, "status": "rejected", "reason": "Ci sono già 10 regole attive: cancellane una."}
    action, action_text = _action(op)
    if action is None:
        return {**result, "status": "rejected", "reason": action_text}
    if op["trigger"] == "threshold":
        measure = MEASURES.get(op["metric"])
        value = _number(op["value"])
        if measure is None or op["op"] == NONE or value is None or not measure[2] <= value <= measure[3]:
            return {**result, "status": "rejected", "reason": "condizione non ammessa"}
        trigger = {"kind": "threshold", "metric": op["metric"], "op": op["op"], "value": value}
        when = f"quando {measure[0]} {'supera' if op['op'] == 'above' else 'scende sotto'} {_spoken(value)} {measure[1]}"
    else:
        if not TIME.match(op["at"]) or op["days"] == NONE:
            return {**result, "status": "rejected", "reason": "orario non valido"}
        trigger = {"kind": "time", "at": op["at"], "days": DAYS[op["days"]]}
        when = f"alle {op['at']} {DAYS_TEXT[op['days']]}"
    lifetime = {"kind": op["lifetime"]}
    text = f"{when} {action_text}, {'una volta' if op['lifetime'] == 'once' else 'ogni volta'}"
    rule = {"text": text, "trigger": trigger, "conditions": [], "action": action, "lifetime": lifetime}
    return {**result, "status": "confirmation_required", "rule": rule, "text": text}


GROUPS = {"fireplace": ("set_fireplace_",), "lights": ("set_light_",), "thermostats": ("set_thermostat_",),
          "heating_profile": ("set_heating_profile",)}


def delete_rules(about, rules_snapshot):
    result = {"schema": "house_ai.rule_command.v1", "verb": "delete", "limitations": []}
    if rules_snapshot is None:
        return {**result, "status": "missing", "reason": "elenco delle regole non disponibile"}
    rules = [r for r in rules_snapshot["rules"] if r["status"] != "expired"]
    if about == "all":
        chosen = rules
    elif about == "any":
        chosen = rules if len(rules) == 1 else []
    else:
        chosen = [r for r in rules if r["action"]["tool"].startswith(GROUPS[about])]
    if not rules:
        return {**result, "status": "rejected", "reason": "Non ci sono regole da cancellare."}
    if not chosen or (about not in ("all",) and len(chosen) > 1):
        names = "; ".join(r["text"] for r in (chosen or rules))
        return {**result, "status": "rejected", "reason": f"Dimmi quale regola cancellare: {names}."}
    return {**result, "status": "confirmation_required", "ids": [r["id"] for r in chosen],
            "texts": [r["text"] for r in chosen]}


def list_rules(rules_snapshot):
    if rules_snapshot is None:
        return {"schema": "house_ai.rules_list.v1", "status": "missing", "rules": [], "engine_mode": None,
                "limitations": ["Elenco delle regole non ricevuto dall'app."]}
    return {"schema": "house_ai.rules_list.v1", "status": "available", "rules": rules_snapshot["rules"],
            "engine_mode": rules_snapshot["engine_mode"],
            "limitations": ["Stato delle regole come pubblicato dal motore sul .20."]}


def validate_rules_snapshot(snapshot):
    if snapshot is None:
        return None
    if (not isinstance(snapshot, dict) or set(snapshot) != {"schema", "engine_mode", "rules"}
            or snapshot["schema"] != "house_ai.current_rules_input.v1"
            or snapshot["engine_mode"] not in ("shadow", "active") or not isinstance(snapshot["rules"], list)
            or len(snapshot["rules"]) > 20):
        raise ValueError("Invalid rules snapshot")
    clean = []
    for r in snapshot["rules"]:
        if (not isinstance(r, dict) or set(r) != {"id", "text", "status", "action"}
                or not isinstance(r["id"], str) or not re.fullmatch(r"[a-z0-9-]{3,40}", r["id"])
                or not isinstance(r["text"], str) or len(r["text"]) > 300
                or r["status"] not in ("test", "active", "paused", "expired")
                or not isinstance(r["action"], dict) or r["action"].get("tool") not in ACTIONS):
            raise ValueError("Invalid rule in snapshot")
        clean.append({"id": r["id"], "text": r["text"], "status": r["status"], "action": {"tool": r["action"]["tool"]}})
    return {"schema": snapshot["schema"], "engine_mode": snapshot["engine_mode"], "rules": clean}


STATUS_TEXT = {"test": "in prova", "active": "attiva", "paused": "sospesa", "expired": "esaurita"}


def list_text(r):
    if r["status"] == "missing":
        return "Non ho ricevuto l'elenco delle regole: riprova fra poco."
    rules = [x for x in r["rules"] if x["status"] != "expired"]
    if not rules:
        return "Non ci sono regole attive."
    lines = [f"{i}) {x['text']} ({STATUS_TEXT[x['status']]})" for i, x in enumerate(rules, 1)]
    head = "C'è una regola: " if len(rules) == 1 else f"Ci sono {len(rules)} regole: "
    tail = " Il motore delle regole è in prova: per ora annuncia soltanto." if r["engine_mode"] == "shadow" else ""
    return head + "; ".join(lines) + "." + tail


def rejected_text(r):
    reason = r.get("reason", "")
    if r["verb"] == "create":
        return reason if reason.endswith(".") else f"Non creo la regola: {reason}."
    return reason
