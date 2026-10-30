#!/bin/bash
# Keeps the second Pi-hole (.15) aligned with the main one (.20), user's decision 30/10/2026.
#
#   pihole_sync.sh export   on .20: Teleporter archive into the rasp-backup share of the QNAP
#   pihole_sync.sh import   on .15: imports that archive and restarts pihole-FTL
#
# No SSH between the two hosts: the share is already used by both for the backups, and the archive
# (same content as /etc/pihole in the nightly snapshot) stays there. An archive older than MAX_AGE_H,
# or a broken one, is not imported: the .15 keeps answering with what it has.
set -euo pipefail
EXPORT=192.168.1.4:/rasp-backup
MNT=/mnt/pihole-sync
DIR=$MNT/pihole-sync
FILE=$DIR/teleporter.zip
MAX_AGE_H=36

log() { echo "$(date '+%F %T') $*"; }
cleanup() { umount "$MNT" 2>/dev/null || true; [ -z "${WORK:-}" ] || rm -rf "$WORK"; }
trap cleanup EXIT
mkdir -p "$MNT"
mount -t nfs4 -o noatime "$EXPORT" "$MNT"
WORK=$(mktemp -d)
cd "$WORK"

case "${1:-}" in
export)
    mkdir -p "$DIR"
    pihole-FTL --teleporter >/dev/null
    zip=$(ls ./*.zip)
    python3 -m zipfile -t "$zip" >/dev/null
    cp "$zip" "$FILE.new" && mv "$FILE.new" "$FILE"
    log "exported $(stat -c %s "$FILE") bytes"
    ;;
import)
    [ -f "$FILE" ] || { log "no archive: nothing done"; exit 1; }
    age_h=$(( ($(date +%s) - $(stat -c %Y "$FILE")) / 3600 ))
    [ "$age_h" -le "$MAX_AGE_H" ] || { log "archive is $age_h h old: nothing done"; exit 1; }
    cp "$FILE" ./teleporter.zip
    python3 -m zipfile -t teleporter.zip >/dev/null
    pihole-FTL --teleporter teleporter.zip >/dev/null
    systemctl restart pihole-FTL
    log "imported"
    ;;
*)
    echo "use: pihole_sync.sh export|import" >&2; exit 2
    ;;
esac
