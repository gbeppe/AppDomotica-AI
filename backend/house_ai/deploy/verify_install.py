"""Read-only post-install checks. Does not print secrets or query household history."""
import json
import os
import stat
import sys
from pathlib import Path
from urllib.request import Request, urlopen


def main():
    token = os.environ.get("HOUSE_AI_TOKEN", "")
    if len(token) < 24:
        raise SystemExit("Load HOUSE_AI_TOKEN from the private backend environment")
    request = Request("http://127.0.0.1:8765/v1/health",
                      headers={"Authorization": "Bearer " + token})
    with urlopen(request, timeout=10) as response:
        health = json.load(response)
    expected = {"status": "ok", "mode": "read_only",
                "planner_configured": True, "log_root_configured": True}
    if health != expected:
        raise SystemExit("Unexpected health response: " + json.dumps(health))
    required = ["apprendimento_storico.log", "clima_controllo.log",
                "predictive_reserve_shadow.log", "zara_previsione_kwh_openmeteo.log"]
    log_root = Path(os.environ.get("HOUSE_AI_LOG_ROOT", "/home/pi/AI_climate"))
    missing = []
    for name in required:
        try:
            if not stat.S_ISREG((log_root / name).lstat().st_mode):
                missing.append(name)
        except OSError:
            missing.append(name)
    if missing:
        raise SystemExit("Missing configured logs: " + ", ".join(missing))
    print("PASS: backend health, read-only mode, planner and log root configured")
    print("PASS: four allowlisted log files are regular files")
    print("Python:", sys.version.split()[0])


if __name__ == "__main__":
    main()
