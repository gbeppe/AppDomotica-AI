"""Read-only climate evidence extraction. No actuator or model dependencies."""
import json
import math
from collections import Counter
from datetime import date, datetime, time, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo


def day_bounds(day: str) -> tuple[int, int]:
    local_day = date.fromisoformat(day)
    zone = ZoneInfo("Europe/Rome")
    start = datetime.combine(local_day, time(), zone)
    end = datetime.combine(local_day + timedelta(days=1), time(), zone)
    return int(start.timestamp() * 1000), int(end.timestamp() * 1000)


def climate_day(path: Path, day: str) -> dict:
    start, end = day_bounds(day)
    events, excluded = [], []
    diagnostics = Counter()
    seen = set()
    with path.open(encoding="utf-8") as stream:
        for line_number, line in enumerate(stream, 1):
            if not line.strip():
                continue
            try:
                record = json.loads(line)
            except ValueError:
                diagnostics["non_json_lines"] += 1
                continue
            if not isinstance(record, dict):
                diagnostics["invalid_records"] += 1
                continue
            timestamp = record.get("timestamp")
            if (type(timestamp) not in (int, float)
                    or not math.isfinite(timestamp)):
                diagnostics["invalid_timestamps"] += 1
                continue
            if not start <= timestamp < end:
                continue
            source = {"file": path.name, "line": line_number}
            sections = [record.get(key, {}) for key in
                        ("logica_controllo", "condizioni_ambientali",
                         "dettaglio_comandi", "stato_energia")]
            if not all(isinstance(section, dict) for section in sections):
                excluded.append({"source": source, "reason": "invalid_structure"})
                continue
            logic, environment, commands, energy = sections
            # Conservative joint signature observed in development logs. A single
            # missing forecast alone does not justify removing a valid event.
            if (logic.get("previsione_solare_data") == "N/A"
                    and environment.get("temp_cameraMatrimoniale") == 0):
                excluded.append({"source": source, "timestamp_ms": timestamp,
                                 "reason": "development_fallback_signature"})
                continue
            fingerprint = json.dumps(record, sort_keys=True)
            if fingerprint in seen:
                excluded.append({"source": source, "timestamp_ms": timestamp,
                                 "reason": "exact_duplicate"})
                continue
            seen.add(fingerprint)
            reason = logic.get("motivo_ac")
            generic = reason in (None, "", "ESECUZIONE_SPEGNIMENTO",
                                 "LOGICA_INVERNALE_ATTIVA")
            events.append({
                "timestamp_ms": timestamp,
                "local_time": datetime.fromtimestamp(timestamp / 1000,
                    ZoneInfo("Europe/Rome")).isoformat(),
                "source": source,
                "evidence_type": "recorded_event",
                "causal_detail": "unspecified" if generic else "recorded_reason",
                "strategy_version": None,
                "physical_confirmation": "not_checked",
                "reason_ac": reason,
                "reason_vmc": commands.get("motivo_logica"),
                "command_recorded": commands.get("comando_ir_inviato"),
                "previous_state": commands.get("stato_ac_precedente"),
                "state": commands.get("stato_ac_attuale"),
                "environment": environment, "energy": energy, "logic": logic,
            })
    events.sort(key=lambda event: event["timestamp_ms"])
    return {"schema": "house_ai.climate_evidence.v1", "day": day,
            "timezone": "Europe/Rome", "period_ms": [start, end],
            "events": events, "excluded": excluded,
            "file_diagnostics": dict(diagnostics),
            "limitations": ["Event log does not establish continuous coverage.",
                "Recorded commands are not physical acknowledgements.",
                "Fallback exclusion does not prove a restart.",
                "Historical strategy version is unknown."]}
