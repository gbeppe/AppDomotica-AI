#!/bin/bash
# Rotation of rasp_backup.sh (14 daily + 8 weekly), without root: bash backend/backup/tests/test_rotation.sh
set -euo pipefail
SCRIPT=$(cd "$(dirname "$0")/.." && pwd)/rasp_backup.sh
fail=0
check() {   # name, expected, actual
    if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: expected [$2] got [$3]"; fail=1; fi
}
days() {    # n days back from 2026-10-25 (a Sunday)
    local i
    for ((i = 0; i < $1; i++)); do date -d "2026-10-25 -$i day" +%F; done
}

# 14 days or fewer: nothing deleted
check "14 days kept" "" "$(bash "$SCRIPT" rotate-test $(days 14))"

# 15 days: the 15th (2026-10-11, Sunday of week 41) is the newest of an older week -> weekly, kept
check "15th day becomes weekly" "" "$(bash "$SCRIPT" rotate-test $(days 15))"

# 120 days: 14 daily (12/10..25/10) + one per week for 8 older weeks, everything else deleted
kept=$(comm -23 <(days 120 | sort) <(bash "$SCRIPT" rotate-test $(days 120) | sort) | wc -l)
check "120 days -> 22 kept" "22" "$kept"
weekly=$(comm -23 <(days 120 | sort) <(bash "$SCRIPT" rotate-test $(days 120) | sort) | head -8 | tr '\n' ' ')
check "weekly copies are the Sundays" "2026-08-23 2026-08-30 2026-09-06 2026-09-13 2026-09-20 2026-09-27 2026-10-04 2026-10-11 " "$weekly"

# a gap (no backups for a while) does not delete the old weekly copies early
check "gap keeps old weeks" "" "$(bash "$SCRIPT" rotate-test $(days 10) 2026-09-01 2026-08-20 2026-08-10)"

# order of the arguments does not matter
check "unsorted input" "$(bash "$SCRIPT" rotate-test $(days 40))" "$(bash "$SCRIPT" rotate-test $(days 40 | sort))"

exit $fail
