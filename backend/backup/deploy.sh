#!/bin/sh
# Install or remove the DomoPi backup (S4) on this Raspberry. Run with sudo from the folder holding
# rasp_backup.sh, backup-<host>.conf and the four systemd units:
#   sudo sh deploy.sh 20 install [--no-timers] | sudo sh deploy.sh 15 install | sudo sh deploy.sh <host> rollback
# --no-timers installs everything but leaves the timers off, for the first manual runs.
# No credentials: the NFS share is authorised by address on the QNAP and MariaDB by the root socket.
set -eu
HOST=$1
ACTION=$2
HERE=$(cd "$(dirname "$0")" && pwd)
ETC=/etc/domopi-backup
UNITS="domopi-backup-snapshot.service domopi-backup-snapshot.timer domopi-backup-image.service domopi-backup-image.timer"
case "$ACTION" in
install)
    TS=$(date -u +%Y%m%dT%H%M%SZ)
    BACKUP=/home/pi/AI_backups/backup/$TS-before-backup-s4
    mkdir -p "$BACKUP"
    for f in /opt/domopi-backup "$ETC"; do
        [ -e "$f" ] && cp -a "$f" "$BACKUP/" || echo "absent: $f" >> "$BACKUP/absent.txt"
    done
    install -d -m 0755 /opt/domopi-backup
    install -m 0755 "$HERE/rasp_backup.sh" /opt/domopi-backup/rasp_backup.sh
    install -d -m 0755 "$ETC"
    install -m 0644 "$HERE/backup-$HOST.conf" "$ETC/backup.conf"
    for u in $UNITS; do install -m 0644 "$HERE/$u" /etc/systemd/system/; done
    systemctl daemon-reload
    if [ "${3:-}" != --no-timers ]; then
        systemctl enable --now domopi-backup-snapshot.timer domopi-backup-image.timer
    fi
    echo "installed; backup $BACKUP"
    ;;
rollback)
    systemctl disable --now domopi-backup-snapshot.timer domopi-backup-image.timer 2>/dev/null || true
    for u in $UNITS; do rm -f "/etc/systemd/system/$u"; done
    systemctl daemon-reload
    rm -rf /opt/domopi-backup "$ETC"
    echo "removed (status in /var/lib/domopi-backup and the copies on the QNAP are kept)"
    ;;
*)
    echo "use: deploy.sh 20|15 install [--no-timers] | rollback" >&2
    exit 2
    ;;
esac
