#!/bin/sh
# Copies the Tailscale certificate of this host for house-ai-lan (run as root by
# house-ai-lan-cert.service). tailscale cert renews it when close to expiry; the
# LAN service restarts only when the files actually change.
set -eu
NAME=domopi.tailf30ba8.ts.net
DIR=/etc/house-ai/tls
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
tailscale cert --cert-file "$TMP/cert.pem" --key-file "$TMP/key.pem" "$NAME" >/dev/null
openssl x509 -in "$TMP/cert.pem" -noout -checkend 86400 >/dev/null
install -d -o root -g house-ai -m 0750 "$DIR"
if cmp -s "$TMP/cert.pem" "$DIR/cert.pem" && cmp -s "$TMP/key.pem" "$DIR/key.pem"; then
    exit 0
fi
install -o root -g house-ai -m 0640 "$TMP/cert.pem" "$DIR/cert.pem.new"
install -o root -g house-ai -m 0640 "$TMP/key.pem" "$DIR/key.pem.new"
mv -f "$DIR/cert.pem.new" "$DIR/cert.pem"
mv -f "$DIR/key.pem.new" "$DIR/key.pem"
systemctl try-restart house-ai-lan.service
