import argparse
import json
import os
import sys
from pathlib import Path

from emoncms import Emoncms
from evidence import climate_day
from energy_history import energy_report


def main():
    parser = argparse.ArgumentParser(description="Read-only home evidence tools")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("catalog")
    events = commands.add_parser("events")
    events.add_argument("--log", type=Path, required=True)
    events.add_argument("--day", required=True, help="YYYY-MM-DD, Europe/Rome")
    energy = commands.add_parser("energy")
    energy.add_argument("--start", required=True, help="YYYY-MM-DD, Europe/Rome")
    energy.add_argument("--end", required=True, help="Exclusive YYYY-MM-DD")
    energy.add_argument("--metric", required=True, choices=["grid_import_kwh", "soc_mean_percent"])
    energy.add_argument("--feed-id", required=True, type=int)
    energy.add_argument("--unit", required=True, choices=["W", "%"])
    energy.add_argument("--import-sign", type=int, choices=[-1, 1])
    args = parser.parse_args()
    try:
        if args.command == "catalog":
            result = Emoncms(os.environ.get("EMONCMS_URL", ""),
                             os.environ.get("EMONCMS_API_KEY", "")).catalog()
        elif args.command == "energy":
            client = Emoncms(os.environ.get("EMONCMS_URL", ""), os.environ.get("EMONCMS_API_KEY", ""))
            result = energy_report(client, args.start, args.end, args.metric,
                                   args.feed_id, args.unit, args.import_sign)
        else:
            result = climate_day(args.log, args.day)
        print(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False))
    except (ValueError, OSError, RuntimeError):
        print("Unable to complete request: check configuration, input and access.",
              file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
