"""Read-only AI Smart tool for the health of the two Raspberry Pis (phase S3 of docs/AI_MODE/SALUTE_IMPIANTO.md).

The app sends what it receives on zara/system/health/<host>/stat (backend/health, every 2 minutes)
as current_system_health; nothing is repaired or commanded from here.
"""
import time

HOSTS = (".20", ".15")
ABOUT = ("all", "20", "15")
STALE_MS = 6 * 60_000          # three missed rounds of the 2 minute timer, as the app's grey light
MAX_FAULTS = 3                 # spoken per Pi; the rest is in the details

CATALOG = [{
    "name": "system_health",
    "description": "Read the health of the home servers (Raspberry .20 and .15): faults found by the health check "
                   "every 2 minutes (services, broker, power, network, disk, energy data) with the advice, and whether "
                   "a Pi stopped reporting. 'Come sta l'impianto?', 'funziona tutto?', 'perché l'app non si collega?'. "
                   "Read only",
    "parameters": {"host": list(ABOUT)}
}]
TOOL_NAMES = ("system_health",)
FIELDS = {"system_health": {"id", "tool", "host"}}


def validate_operation(op):
    if op.get("host") not in ABOUT:
        raise ValueError("Invalid system health request")


def _text(value, limit=300):
    return isinstance(value, str) and len(value) <= limit


def validate_snapshot(snapshot):
    if snapshot is None:
        return None
    if (not isinstance(snapshot, dict) or set(snapshot) != {"schema", "hosts"}
            or snapshot["schema"] != "house_ai.current_system_health_input.v1"
            or not isinstance(snapshot["hosts"], dict) or set(snapshot["hosts"]) - set(HOSTS)):
        raise ValueError("Invalid system health snapshot")
    for host in snapshot["hosts"].values():
        if (not isinstance(host, dict) or set(host) != {"checked_at_ms", "ok", "checks"}
                or type(host["checked_at_ms"]) is not int or not 0 <= host["checked_at_ms"] <= 253402214400000
                or type(host["ok"]) is not bool or not isinstance(host["checks"], list) or len(host["checks"]) > 100):
            raise ValueError("Invalid host health")
        for c in host["checks"]:
            if (not isinstance(c, dict) or set(c) != {"id", "title", "ok", "detail", "advice"}
                    or not all(_text(c[k], 120 if k in ("id", "title") else 300) for k in ("id", "title", "detail", "advice"))
                    or type(c["ok"]) is not bool):
                raise ValueError("Invalid health check")
    return snapshot


def read(about, snapshot, now_ms=None):
    """house_ai.system_health.v1: per Pi light (green/red/grey), age and faults."""
    result = {"schema": "house_ai.system_health.v1", "about": about,
              "limitations": ["Riepiloghi dei controlli di salute sul .20 e sul .15 (ogni 2 minuti), ricevuti dall'app.",
                              "Solo lettura: le riparazioni restano manuali (fase S1)."]}
    if not snapshot or not snapshot["hosts"]:
        return {**result, "status": "missing", "hosts": {}}
    now_ms = int(time.time() * 1000) if now_ms is None else now_ms
    wanted = HOSTS if about == "all" else ("." + about,)
    hosts = {}
    for name in wanted:
        h = snapshot["hosts"].get(name)
        if h is None:
            hosts[name] = {"light": "grey", "age_min": None, "faults": [], "checks": 0}
            continue
        age = max(0, now_ms - h["checked_at_ms"])
        faults = [c for c in h["checks"] if not c["ok"]]
        light = "grey" if age > STALE_MS else "red" if faults or not h["ok"] else "green"
        hosts[name] = {"light": light, "age_min": age // 60_000, "checks": len(h["checks"]),
                       "faults": [{"title": c["title"], "detail": c["detail"], "advice": c["advice"]} for c in faults]}
    return {**result, "status": "available", "hosts": hosts}


def _ago(minutes):
    return "adesso" if minutes < 1 else "1 minuto fa" if minutes == 1 else f"{minutes} minuti fa"


def text(r):
    if r["status"] != "available":
        return "Lo stato dell'impianto non è disponibile: l'app non ha ricevuto i controlli di salute."
    hosts = r["hosts"]
    if all(h["light"] == "green" for h in hosts.values()):
        names = " e ".join(hosts)
        age = max(h["age_min"] for h in hosts.values())
        return (f"L'impianto è a posto: {names} controllat{'i' if len(hosts) > 1 else 'o'} {_ago(age)}, "
                "nessun guasto.")
    parts = []
    for name, h in hosts.items():
        if h["light"] == "grey":
            parts.append(f"Il {name} non manda aggiornamenti" + (f" da {h['age_min']} minuti" if h["age_min"] is not None else "")
                         + ": il Raspberry o il suo controllo non funziona.")
        elif h["light"] == "red":
            faults = h["faults"]
            spoken = "; ".join(f"{f['title']}, {f['detail']}" + (f". Cosa fare: {f['advice']}" if f["advice"] else "")
                               for f in faults[:MAX_FAULTS])
            more = f" Altri {len(faults) - MAX_FAULTS} nei dettagli." if len(faults) > MAX_FAULTS else ""
            parts.append(f"Sul {name} {'un guasto' if len(faults) == 1 else f'{len(faults)} guasti'}: {spoken}.{more}")
        else:
            parts.append(f"Il {name} è a posto.")
    return " ".join(parts)
