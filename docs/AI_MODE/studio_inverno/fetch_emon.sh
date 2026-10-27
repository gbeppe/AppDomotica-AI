#!/bin/bash
# copia la parte dal 01/05/2023 dei feed PHPFina indicati, dal .15 al PC (bassa priorità sul .15)
D=$1; shift
START=$(date -d "2023-05-01 00:00" +%s)
for f in "$@"; do
  meta=$(ssh -i ~/.ssh/id_ed25519_codex_domopi -o IdentitiesOnly=yes pi@192.168.1.15 "od -An -tu4 -N16 /var/opt/emoncms/phpfina/$f.meta" 2>/dev/null | tr -s ' ')
  set -- $meta; iv=$3; st=$4
  [ -n "$iv" ] || { echo "$f: meta illeggibile"; continue; }
  skip=$(( START > st ? (START - st) / iv : 0 )); s0=$(( st + skip * iv ))
  ssh -i ~/.ssh/id_ed25519_codex_domopi -o IdentitiesOnly=yes pi@192.168.1.15 "nice -n 19 ionice -c3 dd if=/var/opt/emoncms/phpfina/$f.dat bs=4 skip=$skip status=none | nice -n 19 gzip -1" 2>/dev/null > $D/$f.dat.gz
  echo "$f $iv $s0" > $D/$f.meta
  echo "$f: iv=$iv start=$(date -d @$s0 +%F) $(du -h $D/$f.dat.gz | cut -f1)"
done
