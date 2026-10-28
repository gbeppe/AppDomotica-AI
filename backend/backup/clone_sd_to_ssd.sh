#!/bin/bash
# Copy a Raspberry SD card to an SSD on the PC, with the Raspberry switched off (phase S4/B5,
# docs/AI_MODE/BACKUP_RASPBERRY.md §6). Same partition table and disk identifier (so the PARTUUIDs in
# cmdline.txt and fstab still match), FAT partitions byte for byte, ext4 partitions with e2image (used
# blocks only), then the last partition grown to the whole SSD. The SD is only read.
#
#   sudo bash clone_sd_to_ssd.sh <sd> <ssd>          shows what it would do, writes nothing
#   sudo bash clone_sd_to_ssd.sh <sd> <ssd> --yes    copies
# e.g. sudo bash clone_sd_to_ssd.sh /dev/mmcblk0 /dev/sdb --yes
# REPAIR_COPY=1: the SD has filesystem errors (e2fsck -fn fails): copy it as it is, leave the SD untouched, and
# repair the copy on the SSD with e2fsck -fy (the .20 SD on 28/10/2026: orphan list and directory entries of
# deleted files). Files the repair removes go to lost+found; restore them from the nightly snapshot.
# Lesson of 28/10/2026: on the .20 the repair detached whole directories the running kernel still read (1230
# libraries, /etc/mysql) and the copy did not boot. With a damaged SD prefer rsync -aHAXx from the running
# system onto a new filesystem (BACKUP_RASPBERRY.md §6, diary of the .20).
set -euo pipefail
SD=${1:?sd device}
SSD=${2:?ssd device}
GO=${3:-}

part() { [[ $1 =~ [0-9]$ ]] && echo "${1}p$2" || echo "${1}$2"; }
die() { echo "STOP: $*" >&2; exit 1; }

[ "$(id -u)" = 0 ] || die "run with sudo"
[ -b "$SD" ] && [ -b "$SSD" ] || die "$SD or $SSD is not a block device"
[ "$SD" != "$SSD" ] || die "same device twice"
[ "$(lsblk -dno TYPE "$SD")" = disk ] && [ "$(lsblk -dno TYPE "$SSD")" = disk ] || die "give whole disks, not partitions"
for d in "$SD" "$SSD"; do
    if lsblk -no MOUNTPOINT "$d" | grep -q /; then
        echo "unmounting the partitions of $d (the desktop mounts them automatically)"
        for p in $(lsblk -lno NAME,MOUNTPOINT "$d" | awk '$2 ~ /^\// {print "/dev/"$1}'); do umount "$p"; done
    fi
done
grep -qE "^($(lsblk -lno NAME "$SSD" | sed 's#^#/dev/#' | paste -sd'|'))\b" /proc/mounts && die "$SSD still mounted"

sd_bytes=$(blockdev --getsize64 "$SD"); ssd_bytes=$(blockdev --getsize64 "$SSD")
[ "$ssd_bytes" -ge "$sd_bytes" ] || die "the SSD is smaller than the SD"
[ -z "$(lsblk -no PTTYPE "$SSD" | tr -d ' \n')" ] || [ "${FORCE:-}" = 1 ] \
    || die "$SSD already has a partition table: check it is the right disk, then rerun with FORCE=1"
diskid=$(sfdisk --dump "$SD" | sed -n 's/^label-id: *0x//p')
[ -n "$diskid" ] || die "no disk identifier on $SD"
mapfile -t nums < <(sfdisk --dump "$SD" | grep -E "^$SD" | sed -E 's#^/dev/[a-z0-9]+[^0-9]([0-9]+) .*#\1#')
last=${nums[-1]}

echo "SD : $SD  $(lsblk -dno SIZE,MODEL "$SD")  disk id $diskid"
echo "SSD: $SSD  $(lsblk -dno SIZE,MODEL "$SSD")"
lsblk -o NAME,SIZE,FSTYPE,LABEL "$SD"
for n in "${nums[@]}"; do
    t=$(blkid -o value -s TYPE "$(part "$SD" "$n")" || true)
    echo "partition $n: $t -> $( [ "$t" = vfat ] && echo 'byte for byte' || echo 'e2image (used blocks)' )$( [ "$n" = "$last" ] && echo ', then grown to the whole SSD' )"
done
[ "$GO" = --yes ] || { echo; echo "Nothing written. Rerun with --yes to copy."; exit 0; }

for n in "${nums[@]}"; do
    t=$(blkid -o value -s TYPE "$(part "$SD" "$n")" || true)
    case "$t" in vfat|ext2|ext3|ext4) ;; *) die "partition $n: unsupported filesystem '$t'";; esac
    if [ "$t" != vfat ] && ! e2fsck -fn "$(part "$SD" "$n")" >/dev/null 2>&1; then
        [ "${REPAIR_COPY:-}" = 1 ] || die "the SD partition $n has errors: copy stopped (REPAIR_COPY=1 copies and repairs the copy)"
        echo "the SD partition $n has errors: it is copied as it is and the copy is repaired (SD untouched)"
    fi
done
echo "== partition table"
sfdisk --dump "$SD" | grep -vE '^(device|last-lba|first-lba):' | sfdisk --quiet --wipe always "$SSD"
# the label-id in the dump was not kept on 28/10/2026 (SSD came out e0d4036e): set it explicitly
sfdisk --disk-id "$SSD" "0x$diskid" >/dev/null
partprobe "$SSD"; udevadm settle
for n in "${nums[@]}"; do
    src=$(part "$SD" "$n"); dst=$(part "$SSD" "$n"); t=$(blkid -o value -s TYPE "$src")
    echo "== partition $n ($t)"
    if [ "$t" = vfat ]; then
        dd if="$src" of="$dst" bs=4M conv=fsync status=progress
    else
        e2image -ra -p "$src" "$dst"
        if [ "${REPAIR_COPY:-}" = 1 ]; then
            echo "== repair of the copy, partition $n"
            rc=0; e2fsck -fy "$dst" > "/root/clone-repair-p$n.log" 2>&1 || rc=$?
            # 0 clean, 1 errors corrected, 2 corrected and reboot advised; anything above is a failure
            [ "$rc" -le 2 ] || die "e2fsck could not repair partition $n (code $rc, log /root/clone-repair-p$n.log)"
            echo "repaired (e2fsck code $rc), log /root/clone-repair-p$n.log"
        fi
    fi
done
echo "== grow partition $last"
parted -s "$SSD" resizepart "$last" 100%
sfdisk --disk-id "$SSD" "0x$diskid" >/dev/null     # parted may rewrite the table
partprobe "$SSD"; udevadm settle
lastdst=$(part "$SSD" "$last")
if [ "$(blkid -o value -s TYPE "$lastdst")" != vfat ]; then e2fsck -fy "$lastdst" >/dev/null || true; resize2fs "$lastdst"; fi
echo "== checks"
[ "$(sfdisk --dump "$SSD" | sed -n 's/^label-id: *0x//p')" = "$diskid" ] || die "disk identifier differs"
for n in "${nums[@]}"; do
    dst=$(part "$SSD" "$n"); t=$(blkid -o value -s TYPE "$dst")
    case "$t" in vfat) fsck.vfat -n "$dst" >/dev/null ;; ext*) e2fsck -fn "$dst" >/dev/null ;; esac || die "partition $n has errors after the copy"
done
m=$(mktemp -d)
mount -o ro "$(part "$SSD" 1)" "$m"
grep -q "root=PARTUUID=$diskid-02" "$m/cmdline.txt" && echo "cmdline.txt: root=PARTUUID=$diskid-02 ok" || echo "WARNING: cmdline.txt does not point at PARTUUID $diskid-02"
umount "$m"
mount -o ro "$(part "$SSD" 2)" "$m"
grep -q "PARTUUID=$diskid" "$m/etc/fstab" && echo "fstab: uses PARTUUID $diskid ok" || echo "WARNING: fstab does not use PARTUUID $diskid (fix it before booting)"
umount "$m"; rmdir "$m"
sync
lsblk -o NAME,SIZE,FSTYPE,LABEL,PARTUUID "$SSD"
echo "DONE: the SSD is ready. Eject it, connect it to a blue USB port of the Raspberry, keep the SD out, power on."
