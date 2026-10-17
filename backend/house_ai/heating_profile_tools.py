"""Read-only AI Smart tool for the heating profile and thermostat timer (HEATING-PROFILE.2 on .20).

The app sends what it receives on zara/interface/heating/profile/stat and timer/plan/stat as
current_heating_profile; nothing is commanded from here.
"""
import math
import re

NONE = "none"
PROFILES = {"spento": "Spento", "ridotto": "Ridotto", "pieno": "Pieno"}
MODES = ("auto", "spento", "ridotto", "pieno")
THERMOSTATS = {"thermostat_living": "Soggiorno", "thermostat_bath": "Bagno"}
ABOUT = ("all", "profile", *THERMOSTATS)
# HEATING-PROFILE.1 thresholds on the 72 h outdoor mean (user's decision of 16/10/2026).
PIENO_BELOW, SPENTO_ABOVE, HOURS_NEEDED = 10, 16, 48
TIME = re.compile(r"^([01]\d|2[0-3]):[0-5]\d$")

CATALOG = [{
    "name": "current_heating_profile",
    "description": "Read the heating profile (season: Spento/Ridotto/Pieno, automatic or fixed), why it was chosen, "
                   "and the thermostat timer plan: current setpoint, time slot, next change ('quando cambia il bagno?' = "
                   "next change, not the past). Read only",
    "parameters": {"about": list(ABOUT)}
}]
TOOL_NAMES = ("current_heating_profile",)
FIELDS = {"current_heating_profile": {"id", "tool", "about"}}


def validate_operation(op):
    if op.get("about") not in ABOUT:
        raise ValueError("Invalid heating profile request")


def _temp(v, low=-40, high=60):
    return v is None or (type(v) in (int, float) and math.isfinite(v) and low <= v <= high)


def validate_snapshot(snapshot):
    if snapshot is None:
        return None
    keys = {"schema", "profile", "mode", "outdoor_mean_72h", "hours_of_data", "timer_mode", "thermostats"}
    if (not isinstance(snapshot, dict) or set(snapshot) != keys
            or snapshot["schema"] != "house_ai.current_heating_profile_input.v1"
            or snapshot["profile"] not in PROFILES or snapshot["mode"] not in MODES
            or not _temp(snapshot["outdoor_mean_72h"])
            or type(snapshot["hours_of_data"]) is not int or not 0 <= snapshot["hours_of_data"] <= 10000
            or snapshot["timer_mode"] not in ("shadow", "active")
            or not isinstance(snapshot["thermostats"], dict) or set(snapshot["thermostats"]) - set(THERMOSTATS)):
        raise ValueError("Invalid heating profile snapshot")
    for p in snapshot["thermostats"].values():
        if (not isinstance(p, dict) or set(p) != {"inside", "value", "slot", "min", "max", "next"}
                or type(p["inside"]) is not bool
                or not all(_temp(p[k], 5, 30) for k in ("value", "min", "max"))
                or not (p["slot"] is None or (isinstance(p["slot"], str) and len(p["slot"]) <= 40))):
            raise ValueError("Invalid thermostat plan")
        n = p["next"]
        if n is not None and (not isinstance(n, dict) or set(n) != {"day_offset", "at", "inside", "value"}
                              or type(n["day_offset"]) is not int or not 0 <= n["day_offset"] <= 7
                              or not isinstance(n["at"], str) or not TIME.match(n["at"])
                              or type(n["inside"]) is not bool or not _temp(n["value"], 5, 30)):
            raise ValueError("Invalid next change")
    return snapshot


def read(about, snapshot):
    if snapshot is None:
        return {"schema": "house_ai.heating_profile.v1", "status": "missing", "about": about,
                "limitations": ["Profilo di riscaldamento non ricevuto dall'app."]}
    wanted = THERMOSTATS if about in ("all",) else (about,) if about in THERMOSTATS else ()
    return {"schema": "house_ai.heating_profile.v1", "status": "available", "about": about,
            "profile": snapshot["profile"], "mode": snapshot["mode"],
            "outdoor_mean_72h": snapshot["outdoor_mean_72h"], "hours_of_data": snapshot["hours_of_data"],
            "timer_mode": snapshot["timer_mode"],
            "thermostats": {k: v for k, v in snapshot["thermostats"].items() if k in wanted},
            "limitations": ["Piano calcolato dal timer sul .20; in prova non comanda i termostati."]}


def _deg(v):
    return "—" if v is None else f"{v:.1f}".rstrip("0").rstrip(".").replace(".", ",") + " gradi"


def _day(offset):
    return {0: "oggi", 1: "domani"}.get(offset, f"fra {offset} giorni")


def _why(r):
    if r["mode"] != "auto":
        return f"scelto a mano ({PROFILES[r['profile']]} fisso)"
    mean = r["outdoor_mean_72h"]
    if r["hours_of_data"] < HOURS_NEEDED:
        return (f"automatico, ma con solo {r['hours_of_data']} ore di dati esterni su {HOURS_NEEDED}: "
                f"resta Ridotto finché non ha 48 ore")
    rule = {"pieno": f"sotto {PIENO_BELOW}", "spento": f"sopra {SPENTO_ABOVE}",
            "ridotto": f"fra {PIENO_BELOW} e {SPENTO_ABOVE}"}[r["profile"]]
    return f"automatico: la media esterna delle ultime 72 ore è {_deg(mean)}, {rule} gradi"


def _plan(name, p):
    now = (f"in fascia{(' ' + p['slot']) if p['slot'] else ''}, {_deg(p['value'])}" if p["inside"]
           else f"fuori fascia, {_deg(p['value'])}")
    n = p["next"]
    nxt = (f"; prossimo cambio {_day(n['day_offset'])} alle {n['at']} a {_deg(n['value'])}" if n
           else "; nessun cambio previsto")
    return f"{name}: {now}{nxt}"


def text(r):
    if r["status"] == "missing":
        return "Non ho ricevuto il profilo di riscaldamento: riprova fra poco."
    parts = [f"Profilo di riscaldamento: {PROFILES[r['profile']]}, {_why(r)}."]
    if r["about"] == "profile":
        parts.append(f"Soglie: Pieno sotto {PIENO_BELOW} gradi, Spento sopra {SPENTO_ABOVE} gradi di media esterna.")
    if r["about"] != "all" and r["about"] != "profile":
        parts = []
    plans = [_plan(THERMOSTATS[k], v) for k, v in r["thermostats"].items()]
    if plans:
        parts.append(". ".join(plans) + ".")
    elif r["about"] in THERMOSTATS:
        parts.append(f"Non ho il piano del {THERMOSTATS[r['about']].lower()}.")
    if r["timer_mode"] == "shadow":
        parts.append("Il timer è in prova: per ora non cambia i termostati.")
    return " ".join(parts)
