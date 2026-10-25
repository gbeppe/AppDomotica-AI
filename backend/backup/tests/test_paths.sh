#!/bin/bash
# Mount points and exclude patterns of rasp_backup.sh, without root: bash backend/backup/tests/test_paths.sh
set -euo pipefail
SCRIPT=$(cd "$(dirname "$0")/.." && pwd)/rasp_backup.sh
fail=0
check() {   # name, expected, actual
    if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: expected [$2] got [$3]"; fail=1; fi
}

# .15 (emonSD): root partition also bind-mounted at /var/log.bak by log2ram (first image failed on this, 25/10)
check "emonSD root and bind" "root=/
bind=/var/log /var/log.bak" "$(printf '/ /\n/var/log.bak /var/log\n' | bash "$SCRIPT" mount-test)"
check "bind listed first" "root=/
bind=/var/log /var/log.bak" "$(printf '/var/log.bak /var/log\n/ /\n' | bash "$SCRIPT" mount-test)"
check ".20 single mount" "root=/" "$(printf '/ /\n' | bash "$SCRIPT" mount-test)"
check "data partition" "root=/var/opt/emoncms" "$(printf '/var/opt/emoncms /\n' | bash "$SCRIPT" mount-test)"

# patterns written for / rewritten per top-level folder
pats=('/proc/*' '/home/*/.cache/*' '/var/swap' '/var/log.bak/*' '/lost+found' '/etc/pihole/pihole-FTL.db' '*.tmp')
check "home" "--exclude=/*/.cache/*
--exclude=*.tmp" "$(bash "$SCRIPT" exclude-test home "${pats[@]}")"
check "var" "--exclude=/swap
--exclude=/log.bak/*
--exclude=*.tmp" "$(bash "$SCRIPT" exclude-test var "${pats[@]}")"
check "etc" "--exclude=/pihole/pihole-FTL.db
--exclude=*.tmp" "$(bash "$SCRIPT" exclude-test etc "${pats[@]}")"
check "proc empty" "--exclude=/*
--exclude=*.tmp" "$(bash "$SCRIPT" exclude-test proc "${pats[@]}")"
check "usr untouched" "--exclude=*.tmp" "$(bash "$SCRIPT" exclude-test usr "${pats[@]}")"

# the image keeps the Node-RED modules, the snapshot drops them (both hosts)
for c in 20 15; do
    conf=$(dirname "$SCRIPT")/backup-$c.conf
    snap=$(bash -c ". '$conf'; printf '%s\n' \"\${SNAPSHOT_EXCLUDES[@]}\"")
    img=$(bash -c "IMAGE_EXCLUDES=(); . '$conf'; printf '%s\n' \"\${IMAGE_EXCLUDES[@]}\"")
    check ".$c snapshot without node_modules" "1" "$(echo "$snap" | grep -c node_modules)"
    check ".$c image with node_modules" "0" "$(echo "$img" | grep -c node_modules || true)"
done

exit $fail
