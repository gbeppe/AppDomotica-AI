import argparse
import json
import os
import sys
from pathlib import Path

from emoncms import Emoncms
from evidence import climate_day


def main():
    parser = argparse.ArgumentParser(description="Read-only home evidence tools")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("catalog")
    events = commands.add_parser("events")
    events.add_argument("--log", type=Path, required=True)
    events.add_argument("--day", required=True, help="YYYY-MM-DD, Europe/Rome")
    args = parser.parse_args()
    try:
        if args.command == "catalog":
            result = Emoncms(os.environ.get("EMONCMS_URL", ""),
                             os.environ.get("EMONCMS_API_KEY", "")).catalog()
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
