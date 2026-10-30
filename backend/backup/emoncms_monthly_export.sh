#!/bin/bash
# Monthly emoncms archive of the .15 (30/10/2026, replaces the old regular.backup.sh).
#
# emoncms-export.sh (official backup module) builds one emoncms-backup-<host>-<date>.tar.gz with feeds,
# database, emonhub.conf and settings.ini; it is copied to the QNAP .4 next to the nightly mirror and the
# last KEEP archives are kept. The nightly drive-backup.sh mirror has no history for the feeds: these
# archives are the only fixed monthly pictures of the energy data.
#
#   emoncms_monthly_export.sh            export, copy, prune
#   emoncms_monthly_export.sh --dry-run  check the QNAP and the password, list the archives, change nothing
#
# The rsync password is read from a file, never written here (the repository is public).
set -euo pipefail
PASSFILE=/home/pi/.config/domopi/rsync.pass
HOST=192.168.1.4
DEST=rsync://rsync@$HOST/ZaraHome/192-168-1-15/emoncms/
LOCAL=/var/opt/emoncms/backup
KEEP=12
DRY=${1:-}

log() { echo "$(date '+%F %T') $*"; }
timeout 5 bash -c "</dev/tcp/$HOST/873" 2>/dev/null || { log "QNAP $HOST not reachable on rsync: nothing done"; exit 1; }
RSYNC_PASSWORD=$(cat "$PASSFILE")
export RSYNC_PASSWORD

archives() { rsync --list-only "$DEST" | awk '{print $NF}' | grep -E '^emoncms-backup-.*\.tar\.gz$' | sort; }
old_archives() { archives | head -n -"$KEEP"; }

if [ "$DRY" = --dry-run ]; then
    log "archives on the QNAP:"; archives | sed 's/^/  /'
    log "would delete (more than $KEEP):"; old_archives | sed 's/^/  /'
    exit 0
fi

rm -f "$LOCAL"/emoncms-backup*.tar.gz
log "export"
/opt/emoncms/modules/backup/emoncms-export.sh
log "copy to the QNAP"
rsync "$LOCAL"/emoncms-backup*.tar.gz "$DEST"
rm -f "$LOCAL"/emoncms-backup*.tar.gz
# prune: an empty source with --delete removes only the names included here; everything else (the
# nightly mirror folder emoncms-backup/ above all) is excluded and therefore never touched
mapfile -t old < <(old_archives)
if [ "${#old[@]}" -gt 0 ]; then
    empty=$(mktemp -d)
    args=()
    for name in "${old[@]}"; do args+=(--include="$name"); done
    rsync -r --delete "${args[@]}" --exclude='*' "$empty"/ "$DEST"
    rmdir "$empty"
    log "deleted: ${old[*]}"
fi
log "done"
