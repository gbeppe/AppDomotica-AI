#!/bin/sh
# Install or remove the DomoPi health check (S1) on this Raspberry. Run with sudo from the folder
# holding health_check.py, health-<host>.json and the two systemd units:
#   sudo sh deploy.sh 20 install | sudo sh deploy.sh 15 install | sudo sh deploy.sh <host> rollback
# Credentials are copied from files, never printed:
#   .20: /home/pi/.config/domopi/ntfy_nodered.token and mqtt_domotica.pass
#   .15: /tmp/domopi-health-secrets/{ntfy.token,mqtt.pass}, written beforehand through ssh and
#        deleted here after the copy.
set -eu
HOST=$1
ACTION=$2
HERE=$(cd "$(dirname "$0")" && pwd)
ETC=/etc/domopi-health
case "$ACTION" in
install)
    TS=$(date -u +%Y%m%dT%H%M%SZ)
    BACKUP=/home/pi/AI_backups/health/$TS-before-health-s1
    mkdir -p "$BACKUP"
    for f in /opt/domopi-health "$ETC" /etc/systemd/system/domopi-health.service /etc/systemd/system/domopi-health.timer; do
        [ -e "$f" ] && cp -a "$f" "$BACKUP/" || echo "absent: $f" >> "$BACKUP/absent.txt"
    done
    install -d -m 0755 /opt/domopi-health
    install -m 0755 "$HERE/health_check.py" /opt/domopi-health/health_check.py
    install -d -m 0755 "$ETC"
    install -m 0644 "$HERE/health-$HOST.json" "$ETC/config.json"
    if [ "$HOST" = 20 ]; then
        install -m 0600 /home/pi/.config/domopi/ntfy_nodered.token "$ETC/ntfy.token"
        install -d -m 0700 "$ETC/mqtt"
        umask 077
        for tool in mosquitto_sub mosquitto_pub; do
            { echo "-u domotica"; printf -- '-P %s\n' "$(cat /home/pi/.config/domopi/mqtt_domotica.pass)"; } > "$ETC/mqtt/$tool"
        done
    else
        install -m 0600 /tmp/domopi-health-secrets/ntfy.token "$ETC/ntfy.token"
        install -m 0600 /tmp/domopi-health-secrets/mqtt.pass "$ETC/mqtt.pass"
        rm -rf /tmp/domopi-health-secrets
    fi
    install -d -m 0700 /var/lib/domopi-health
    install -m 0644 "$HERE/domopi-health.service" "$HERE/domopi-health.timer" /etc/systemd/system/
    systemctl daemon-reload
    systemctl enable --now domopi-health.timer
    echo "installed; backup $BACKUP"
    ;;
rollback)
    systemctl disable --now domopi-health.timer || true
    rm -f /etc/systemd/system/domopi-health.service /etc/systemd/system/domopi-health.timer
    systemctl daemon-reload
    rm -rf /opt/domopi-health "$ETC" /var/lib/domopi-health
    echo "removed"
    ;;
*)
    echo "use: deploy.sh 20|15 install|rollback" >&2
    exit 2
    ;;
esac
