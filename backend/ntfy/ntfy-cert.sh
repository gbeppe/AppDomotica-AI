#!/bin/sh
# Copy for ntfy of the certificate of domopi.tailf30ba8.ts.net that house-ai-lan-cert keeps in
# /etc/house-ai/tls (NTFY.1). Readable only by root and the ntfy group; ntfy restarts on change.
set -eu
SRC=/etc/house-ai/tls
DST=/etc/ntfy/tls
[ -s "$SRC/cert.pem" ] && [ -s "$SRC/key.pem" ] || exit 0
install -d -o root -g ntfy -m 0750 "$DST"
if cmp -s "$SRC/cert.pem" "$DST/cert.pem" && cmp -s "$SRC/key.pem" "$DST/key.pem"; then
    exit 0
fi
install -o root -g ntfy -m 0640 "$SRC/cert.pem" "$DST/cert.pem.new"
install -o root -g ntfy -m 0640 "$SRC/key.pem" "$DST/key.pem.new"
mv -f "$DST/cert.pem.new" "$DST/cert.pem"
mv -f "$DST/key.pem.new" "$DST/key.pem"
systemctl try-restart ntfy.service
