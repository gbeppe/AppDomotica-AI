#!/bin/bash
# Mount points and exclude patterns of rasp_backup.sh, without root: bash backend/backup/tests/test_paths.sh
set -euo pipefail
PATH=$PATH:/sbin:/usr/sbin   # sfdisk for a normal user on Raspbian
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

# deeper folders (recursive split of big folders, 26/10)
check "root keeps all" "--exclude=/proc/*
--exclude=/home/*/.cache/*
--exclude=/var/swap
--exclude=/var/log.bak/*
--exclude=/lost+found
--exclude=/etc/pihole/pihole-FTL.db
--exclude=*.tmp" "$(bash "$SCRIPT" exclude-test "" "${pats[@]}")"
check "home/pi" "--exclude=/.cache/*
--exclude=*.tmp" "$(bash "$SCRIPT" exclude-test home/pi "${pats[@]}")"
check "home/pi/.node-red" "--exclude=*.tmp" "$(bash "$SCRIPT" exclude-test home/pi/.node-red "${pats[@]}")"
check "var/lib staging" "--exclude=/domopi-backup/staging/*" "$(bash "$SCRIPT" exclude-test var/lib '/var/lib/domopi-backup/staging/*')"
check "lost+found left out" "excluded" "$(bash "$SCRIPT" excluded-test lost+found "${pats[@]}")"
check "var/swap left out" "excluded" "$(bash "$SCRIPT" excluded-test var/swap "${pats[@]}")"
check "home/pi copied" "copied" "$(bash "$SCRIPT" excluded-test home/pi "${pats[@]}")"
check "glob folder left out" "excluded" "$(bash "$SCRIPT" excluded-test home/pi/node_modules '/home/*/node_modules')"

# the image keeps the Node-RED modules, the snapshot drops them (both hosts)
for c in 20 15; do
    conf=$(dirname "$SCRIPT")/backup-$c.conf
    snap=$(bash -c ". '$conf'; printf '%s\n' \"\${SNAPSHOT_EXCLUDES[@]}\"")
    img=$(bash -c "IMAGE_EXCLUDES=(); . '$conf'; printf '%s\n' \"\${IMAGE_EXCLUDES[@]}\"")
    check ".$c snapshot without node_modules" "1" "$(echo "$snap" | grep -c node_modules)"
    check ".$c image with node_modules" "0" "$(echo "$img" | grep -c node_modules || true)"
done

# skipped data partitions (emoncms on the .15): folders only
check "skip emoncms" "skip" "$(bash "$SCRIPT" skip-test /var/opt/emoncms /var/opt/emoncms)"
check "root never skipped" "copy" "$(bash "$SCRIPT" skip-test / /var/opt/emoncms)"
check "prefix is not a match" "copy" "$(bash "$SCRIPT" skip-test /var/opt /var/opt/emoncms)"
check "nothing skipped by default" "copy" "$(bash "$SCRIPT" skip-test /var/opt/emoncms)"
check ".15 skips emoncms data" "/var/opt/emoncms" "$(bash -c ". '$(dirname "$SCRIPT")/backup-15.conf'; echo \"\$IMAGE_SKIP_DATA\"")"
check ".20 skips nothing" "" "$(bash -c "IMAGE_SKIP_DATA=; . '$(dirname "$SCRIPT")/backup-20.conf'; echo \"\$IMAGE_SKIP_DATA\"")"

# the .20 builds its image on the SD and copies it (writes through NFS too slow there, 25-26/10); the .15 does not
check ".20 staging" "/var/lib/domopi-backup/staging" "$(bash -c "IMAGE_STAGING=; . '$(dirname "$SCRIPT")/backup-20.conf'; echo \"\$IMAGE_STAGING\"")"
check ".15 no staging" "" "$(bash -c "IMAGE_STAGING=; . '$(dirname "$SCRIPT")/backup-15.conf'; echo \"\$IMAGE_STAGING\"")"

# disk identifier read from sfdisk -d (sfdisk --disk-id is missing on the .15)
img=$(mktemp)
truncate -s 8M "$img"
printf 'label: dos\nlabel-id: 0x43ed7bb4\n2048,,c\n' | sfdisk --quiet "$img" >/dev/null 2>&1
check "disk id" "43ed7bb4" "$(bash "$SCRIPT" diskid-test "$img")"
rm -f "$img"

exit $fail
