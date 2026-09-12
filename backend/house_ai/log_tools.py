"""Bounded read-only access to explicitly configured Node-RED JSONL sources."""
import json
import math
from datetime import date
from pathlib import Path

from evidence import day_bounds

SOURCES = {
    "learning": ("apprendimento_storico.log", "timestamp", "hourly_learning"),
    "climate": ("clima_controllo.log", "timestamp", "recorded_control_event"),
    "reserve_shadow": ("predictive_reserve_shadow.log", "timestamp_ms", "shadow_simulation"),
    "solar_forecast": ("zara_previsione_kwh_openmeteo.log", "data_previsione", "forecast"),
}
MAX_BYTES = 32 * 1024 * 1024
MAX_LINE = 128 * 1024
MAX_RECORDS = 50


def tool_catalog():
    return {"name": "backend_log_day",
            "description": "Read recorded Node-RED evidence for a day; forecasts and SHADOW are not measurements or actions",
            "parameters": {"source": list(SOURCES), "day": "YYYY-MM-DD Europe/Rome; forecast uses target date"}}


def read_day(root, source, day):
    if not isinstance(source, str) or source not in SOURCES:
        raise ValueError("Unknown log source")
    start, end = day_bounds(day)
    filename, time_key, kind = SOURCES[source]
    result = {"schema": "house_ai.backend_log_evidence.v1", "source": source,
              "file": filename, "day": day, "timezone": "Europe/Rome",
              "evidence_type": kind, "status": "unavailable", "records": [],
              "matched_records": 0, "invalid_lines": 0, "truncated": False,
              "limitations": ["I log non garantiscono copertura continua né conferma fisica dei comandi.",
                              "SHADOW è simulazione; le previsioni non sono produzione misurata.",
                              "La data della previsione è il giorno previsto, non l'ora di elaborazione."]}
    if root is None:
        return result
    path = Path(root) / filename
    try:
        with path.open("rb") as stream:
            if path.is_symlink() or path.stat().st_size > MAX_BYTES:
                result["status"] = "source_rejected"
                return result
            consumed = 0
            retained_bytes = []
            for number, raw in enumerate(iter(lambda: stream.readline(MAX_LINE + 1), b""), 1):
                consumed += len(raw)
                if len(raw) > MAX_LINE or consumed > MAX_BYTES:
                    result["truncated"] = True
                    break
                try:
                    record = json.loads(raw, parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))
                    if not isinstance(record, dict):
                        raise ValueError()
                    stamp = record.get(time_key)
                    if time_key == "data_previsione":
                        date.fromisoformat(stamp)
                        matches = stamp == day
                    else:
                        if type(stamp) not in (int, float) or not math.isfinite(stamp):
                            raise ValueError()
                        matches = start <= stamp < end
                    if matches:
                        result["matched_records"] += 1
                        result["records"].append({"source": {"file": filename, "line": number}, "record": record})
                        retained_bytes.append(len(raw))
                        while len(result["records"]) > MAX_RECORDS or sum(retained_bytes) > 160_000:
                            result["records"].pop(0)
                            retained_bytes.pop(0)
                            result["truncated"] = True
                except (ValueError, TypeError, UnicodeDecodeError):
                    result["invalid_lines"] += 1
        result["status"] = "partial" if result["records"] else "insufficient_data"
    except OSError:
        pass
    return result
