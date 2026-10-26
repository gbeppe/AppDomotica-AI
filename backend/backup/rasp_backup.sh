#!/bin/bash
# DomoPi hot backup of a Raspberry to the QNAP .4 (phase S4, docs/AI_MODE/BACKUP_RASPBERRY.md).
#   rasp_backup.sh snapshot [--dry-run]   nightly: database dumps + rsync snapshot with hard links
#   rasp_backup.sh image    [--dry-run]   weekly: bootable .img refreshed with rsync (A/B pair)
#   rasp_backup.sh init                   once: create the host folder and the target marker
#   rasp_backup.sh rotate-test <dates…>   print the snapshots rotation would delete (for tests)
# Runs as root from systemd. Reads /etc/domopi-backup/backup.conf. Never prints secrets: the
# snapshot contains them (tokens, password files), so it stays on the NFS share restricted to the
# Raspberry addresses. Writes only under $MOUNT/$HOST_DIR and /var/lib/domopi-backup.
set -euo pipefail

CONF=${DOMOPI_BACKUP_CONF:-/etc/domopi-backup/backup.conf}
STATE=${DOMOPI_BACKUP_STATE:-/var/lib/domopi-backup}
LOCK=/run/domopi-backup.lock
MODE=${1:-}
DRY=0
[ "${2:-}" = --dry-run ] && DRY=1

# Defaults, overridden by backup.conf
NFS_EXPORT=192.168.1.4:/rasp-backup
MOUNT=/mnt/rasp-backup
HOST_DIR=
MYSQL_DBS=
KEEP_DAILY=14
KEEP_WEEKLY=8
KEEP_MONTHLY=2
SNAPSHOT_SOURCES="/etc"
SNAPSHOT_EXCLUDES=()   # added to EXCLUDES for the nightly snapshot only
IMAGE_EXCLUDES=()      # added to EXCLUDES for the weekly image only: keep it bootable and complete
IMAGE_SKIP_DATA=       # mount points of data partitions saved elsewhere: the image keeps only their folders
IMAGE_STAGING=         # local folder where the image is built, then copied to the QNAP as one file (.20)
DEVICE=/dev/mmcblk0
RSYNC_MAX_VM_MB=600
LOOP_DIRECT_IO=off
IMAGE_SPLIT_FILES=10000   # largest folder copied by one rsync; bigger ones are split into their subfolders

EXCLUDES=(
    /proc/* /sys/* /dev/* /run/* /tmp/* /var/tmp/* /mnt/* /media/* /lost+found
    /var/swap /swapfile /var/cache/apt/archives/*.deb
    /home/*/.cache/* /root/.cache/*
)

log() { echo "$(date '+%F %T') $*"; }

# partition number of /dev/mmcblk0p2, /dev/sda2 or /path/file.img2 -> 2
partnum() { echo "$1" | sed 's/.*[^0-9]//'; }

# "TARGET FSROOT" lines of one partition (findmnt -rn): where its root is mounted, and the bind mounts
# of its subfolders as "<folder in the partition> <mount point>". On emonSD the root partition is also
# at /var/log.bak (log2ram keeps the real /var/log there), so a partition can have several mounts.
root_of() { awk '$2 == "/" {print $1; exit}'; }
binds_of() { awk '$2 != "/" {print $2, $1}'; }
mounted_at() { findmnt -rn -o TARGET,FSROOT "$1" | root_of; }
skipped_data() { case " $IMAGE_SKIP_DATA " in *" $1 "*) return 0 ;; esac; return 1; }
# disk identifier of a device or image file; sfdisk --disk-id is missing in util-linux 2.33 (.15)
disk_id() { sfdisk -d "$1" | sed -n 's/^label-id: *0x//p'; }
bind_mounts() { findmnt -rn -o TARGET,FSROOT "$1" | binds_of; }

# Exclude patterns written for / (like /home/*/.cache/*) rewritten for a copy of the folder /$1 alone
# ($1 relative, "" for the root, "home/pi" deeper): /home/*/.cache/* -> /*/.cache/* for "home" and
# /.cache/* for "home/pi"; patterns of other folders are dropped and unanchored ones kept. A pattern
# naming the folder itself or one of its parents is left to the caller (path_excluded).
excludes_for_path() {   # relative folder, patterns...
    local rel=$1 p i ok comps=() pc=()
    shift
    [ -z "$rel" ] || IFS=/ read -ra comps <<< "$rel"
    for p in "$@"; do
        if [ "${p#/}" = "$p" ]; then printf -- '--exclude=%s\n' "$p"; continue; fi
        IFS=/ read -ra pc <<< "${p#/}"
        [ "${#pc[@]}" -gt "${#comps[@]}" ] || continue
        ok=1
        for ((i = 0; i < ${#comps[@]}; i++)); do
            # shellcheck disable=SC2053
            [[ ${comps[i]} == ${pc[i]} ]] || { ok=0; break; }
        done
        [ "$ok" = 1 ] || continue
        printf -- '--exclude=/%s\n' "$(IFS=/; echo "${pc[*]:${#comps[@]}}")"
    done
}

# true when an anchored pattern names exactly the folder /$1 (so the whole folder is left out)
path_excluded() {   # relative folder, patterns...
    local rel=$1 p i ok comps=() pc=()
    shift
    IFS=/ read -ra comps <<< "$rel"
    for p in "$@"; do
        [ "${p#/}" != "$p" ] || continue
        IFS=/ read -ra pc <<< "${p#/}"
        [ "${#pc[@]}" -eq "${#comps[@]}" ] || continue
        ok=1
        for ((i = 0; i < ${#comps[@]}; i++)); do
            # shellcheck disable=SC2053
            [[ ${comps[i]} == ${pc[i]} ]] || { ok=0; break; }
        done
        [ "$ok" = 1 ] && return 0
    done
    return 1
}

# ---------------------------------------------------------------------------------------------
# Rotation: keep the newest KEEP_DAILY snapshots, then the newest snapshot of each ISO week for
# KEEP_WEEKLY more weeks. Input: YYYY-MM-DD names; output: names to delete.
rotation_deletions() {
    local kept=0 weeks=0 last_week= d week
    for d in $(printf '%s\n' "$@" | sort -r); do
        if [ "$kept" -lt "$KEEP_DAILY" ]; then
            kept=$((kept + 1))
            last_week=$(date -d "$d" +%G-%V)
            continue
        fi
        week=$(date -d "$d" +%G-%V)
        if [ "$week" != "$last_week" ] && [ "$weeks" -lt "$KEEP_WEEKLY" ]; then
            weeks=$((weeks + 1))
            last_week=$week
            continue
        fi
        echo "$d"
    done
}

if [ "$MODE" = rotate-test ]; then
    shift
    rotation_deletions "$@"
    exit 0
fi
if [ "$MODE" = exclude-test ]; then   # relative folder, patterns…
    shift
    excludes_for_path "$@"
    exit 0
fi
if [ "$MODE" = excluded-test ]; then  # relative folder, patterns…
    shift
    if path_excluded "$@"; then echo excluded; else echo copied; fi
    exit 0
fi
if [ "$MODE" = skip-test ]; then       # mount point, IMAGE_SKIP_DATA value
    IMAGE_SKIP_DATA=${3:-}
    if skipped_data "$2"; then echo skip; else echo copy; fi
    exit 0
fi
if [ "$MODE" = diskid-test ]; then     # image file (no root needed)
    disk_id "$2"
    exit 0
fi
if [ "$MODE" = mount-test ]; then     # findmnt -rn -o TARGET,FSROOT lines on stdin
    lines=$(cat)
    echo "root=$(echo "$lines" | root_of)"
    echo "$lines" | binds_of | sed 's/^/bind=/'
    exit 0
fi

# ---------------------------------------------------------------------------------------------
[ "$(id -u)" = 0 ] || { echo "run as root" >&2; exit 2; }
# shellcheck source=/dev/null
. "$CONF"
[ -n "$HOST_DIR" ] || { echo "HOST_DIR missing in $CONF" >&2; exit 2; }
TARGET=$MOUNT/$HOST_DIR
install -d -m 0700 "$STATE"
exec 9>"$LOCK"
flock -w 10800 9 || { log "another backup is still running"; exit 1; }

MOUNTED_BY_US=0
LOOPS=()
STARTED=$(date +%s)
RESULT_ERROR=

write_status() {   # mode, ok(true/false), details
    local file=$STATE/status-$1.json now
    now=$(date -Is)
    local prev_ok=null
    [ -f "$file" ] && prev_ok=$(sed -n 's/.*"last_ok": *\("[^"]*"\|null\).*/\1/p' "$file")
    [ -n "$prev_ok" ] || prev_ok=null
    local last_ok=$prev_ok
    # a dry run never counts as a backup for the health check
    [ "$2" = true ] && [ "$DRY" = 0 ] && last_ok="\"$now\""
    printf '{"schema": "domopi.backup_status.v1", "mode": "%s", "host": "%s", "last_run": "%s", "ok": %s, "last_ok": %s, "duration_s": %d, "details": "%s", "dry_run": %s}\n' \
        "$1" "$HOST_DIR" "$now" "$2" "$last_ok" $(($(date +%s) - STARTED)) "$3" \
        "$([ "$DRY" = 1 ] && echo true || echo false)" > "$file.tmp"
    mv "$file.tmp" "$file"
    if mountpoint -q "$MOUNT" && [ -d "$TARGET" ] && [ "$DRY" = 0 ]; then
        cp "$file" "$TARGET/status-$1.json" || true
    fi
}

cleanup() {
    local rc=$?
    if [ "$rc" != 0 ] && [ "$MODE" != init ]; then
        write_status "$MODE" false "${RESULT_ERROR:-failed with code $rc}"
    fi
    for m in /run/domopi-backup-image/*; do mountpoint -q "$m" 2>/dev/null && safe_umount "$m"; done
    for m in "${LOOPS[@]}"; do losetup -d "$m" 2>/dev/null; done
    [ "$MOUNTED_BY_US" = 1 ] && safe_umount "$MOUNT"
    exit "$rc"
}
trap cleanup EXIT
# stopped by systemctl stop or Ctrl-C: still clean up and record the failure
trap '[ -z "$RSYNC_PID" ] || kill "$RSYNC_PID" 2>/dev/null; wait 2>/dev/null; RESULT_ERROR="stopped by a signal"; exit 143' TERM INT HUP

# With the QNAP unreachable a normal umount can fail or hang: fall back to a lazy one, which
# detaches at once, so no stale mount stays behind until the next reboot.
safe_umount() {
    timeout 60 umount "$1" 2>/dev/null || umount -l "$1" 2>/dev/null || log "could not unmount $1"
}

mount_share() {
    install -d -m 0755 "$MOUNT"
    if ! mountpoint -q "$MOUNT"; then
        mount -t nfs -o soft,timeo=50,retrans=2,noatime "$NFS_EXPORT" "$MOUNT" \
            || { RESULT_ERROR="NFS share $NFS_EXPORT not reachable"; exit 1; }
        MOUNTED_BY_US=1
    fi
    if [ "$MODE" != init ] && [ ! -f "$TARGET/.rasp-backup-target" ]; then
        RESULT_ERROR="marker $TARGET/.rasp-backup-target missing (run init)"
        exit 1
    fi
}

dump_databases() {
    local dir=/var/backups/domopi/mysql db
    install -d -m 0700 "$dir"
    for db in $MYSQL_DBS; do
        log "dump $db"
        if [ "$DRY" = 1 ]; then continue; fi
        mysqldump --single-transaction --quick --routines --events --triggers "$db" \
            | gzip -6 > "$dir/$db.sql.gz.tmp" || { RESULT_ERROR="mysqldump $db failed"; exit 1; }
        mv "$dir/$db.sql.gz.tmp" "$dir/$db.sql.gz"
    done
}

exclude_args() {   # snapshot|image
    local e extra=()
    if [ "$1" = image ]; then extra=("${IMAGE_EXCLUDES[@]}"); else extra=("${SNAPSHOT_EXCLUDES[@]}"); fi
    for e in "${EXCLUDES[@]}" "${extra[@]}"; do printf -- '--exclude=%s\n' "$e"; done
}

# rsync with an address-space limit: if it runs away it stops with an error instead of starving the
# Raspberry (the first image of the .20 grew to ~900 MB and was killed by the kernel on 25/10/2026;
# the memory cgroup is disabled in these kernels, so MemoryMax= in the unit would do nothing)
# Run in the background and waited for, so that a stop signal reaches the trap at once (bash runs traps
# only after a foreground child ends) and the cleanup unmounts and frees the loop devices.
RSYNC_PID=
rsync_lim() {
    local rc=0
    prlimit --as=$((RSYNC_MAX_VM_MB * 1048576)) rsync "$@" &
    RSYNC_PID=$!
    wait "$RSYNC_PID" || rc=$?
    RSYNC_PID=
    return "$rc"
}

# One loop device per image partition (offset and size from the image's table), never `losetup -P`: the
# image partitions carry the SD's PARTUUIDs on purpose (restore), udev then points
# /dev/disk/by-partuuid/<id>-01 at the loop device, and when that goes away systemd unmounts /boot,
# which the .20 mounts by PARTUUID (26/10/2026, 13:44-13:51). Without a partition table on the loop
# device there are no PARTUUIDs to collide.
# Sets PART_DEV (not printed: a $(...) subshell would lose the LOOPS record and leak the device).
part_loop() {   # image, partition number [-r]
    local line start size
    line=$(sfdisk -d "$1" | grep -E "^$1p?$2 :")
    start=$(echo "$line" | sed -n 's/.*start= *\([0-9]*\).*/\1/p')
    size=$(echo "$line" | sed -n 's/.*size= *\([0-9]*\).*/\1/p')
    # LOOP_DIRECT_IO=on: writes go straight to the image file instead of being cached twice (loop device
    # and backing file); meant for a local staging file. Off by default: on the .15, with the image on
    # NFS, the filesystems written that way could not be mounted (26/10/2026).
    PART_DEV=$(losetup -f --show ${3:-} --direct-io="$LOOP_DIRECT_IO" -o $((start * 512)) --sizelimit $((size * 512)) "$1")
    LOOPS+=("$PART_DEV")
}

part_numbers() { sfdisk -d "$1" | grep -E "^$1p?[0-9]+ :" | cut -d' ' -f1 | sed 's/.*[^0-9]//'; }

free_loops() { local m; for m in "${LOOPS[@]}"; do losetup -d "$m" 2>/dev/null || true; done; LOOPS=(); }

# 24 = files vanished during the copy: normal on a live system
rsync_ok() { [ "$1" = 0 ] || [ "$1" = 24 ]; }

# ---------------------------------------------------------------------------------------------
do_init() {
    mount_share
    # 0750 root:0 -> readable by the QNAP administrators group (gid 0), nobody else
    install -d -m 0750 "$TARGET" "$TARGET/snapshots" "$TARGET/images"
    chmod 0750 "$TARGET" "$TARGET/snapshots" "$TARGET/images"
    if [ ! -f "$TARGET/.rasp-backup-target" ]; then
        printf '# DomoPi backup destination marker: the backup refuses to write without it,\n# so that an unmounted share is never filled on the SD card.\ncreated=%s\nhost=%s\n' \
            "$(date -Is)" "$HOST_DIR" > "$TARGET/.rasp-backup-target"
    fi
    log "ready: $TARGET"
}

do_snapshot() {
    mount_share
    dump_databases
    local today snaps new latest
    today=$(date +%F)
    snaps=$TARGET/snapshots
    new=$snaps/.incomplete-$today
    latest=$snaps/latest
    # unfinished snapshots of earlier days (a run that failed, e.g. the QNAP went away) are useless
    local stale
    for stale in "$snaps"/.incomplete-*; do
        [ -e "$stale" ] && [ "$stale" != "$new" ] || continue
        log "remove unfinished $(basename "$stale")"
        [ "$DRY" = 1 ] || rm -rf "$stale"
    done
    [ -d "$snaps/$today" ] && mv "$snaps/$today" "$new"
    local args=(-aHxR --numeric-ids --delete --delete-excluded --stats)
    [ -e "$latest" ] && args+=(--link-dest="$(readlink -f "$latest")")
    [ "$DRY" = 1 ] && args+=(--dry-run)
    mapfile -t ex < <(exclude_args snapshot)
    local sources=() s missing=0
    # unquoted on purpose: one path per word, globs expanded here
    for s in $SNAPSHOT_SOURCES; do
        if [ -e "$s" ]; then sources+=("$s"); else log "missing, skipped: $s"; missing=$((missing + 1)); fi
    done
    [ "${#sources[@]}" -gt 0 ] || { RESULT_ERROR="no snapshot source exists"; exit 1; }
    log "snapshot $today: ${#sources[@]} paths"
    local out rc=0
    out=$(rsync_lim "${args[@]}" "${ex[@]}" "${sources[@]}" "$new/" 2>&1) || rc=$?
    if ! rsync_ok "$rc"; then
        echo "$out" | tail -20
        RESULT_ERROR="rsync exit $rc"
        exit 1
    fi
    local sent
    sent=$(echo "$out" | sed -n 's/^Total transferred file size: \([0-9,.]*\).*/\1/p' | tr -d ',.')
    if [ "$DRY" = 1 ]; then
        log "dry run: would transfer ${sent:-?} bytes"
        rm -rf "$new"
        write_status snapshot true "dry run, ${sent:-?} bytes"
        return
    fi
    mv "$new" "$snaps/$today"
    ln -sfn "$today" "$latest"
    local old
    for old in $(rotation_deletions $(ls "$snaps" | grep -E '^[0-9]{4}-[0-9]{2}-[0-9]{2}$')); do
        log "rotation: delete $old"
        rm -rf "${snaps:?}/$old"
    done
    write_status snapshot true "$today, ${sent:-0} bytes changed, $missing paths missing"
    log "snapshot done"
}

# ---------------------------------------------------------------------------------------------
# Image: copy of the SD partition table (same disk identifier, so the PARTUUIDs in fstab and
# cmdline.txt still match) with the last partition shrunk to its used space + 20 % + 1 GiB, in a
# sparse file. Two images (A, B): each run refreshes the one without a valid <image>.ok (or the
# older), so the last good image always stays intact while the other is written. <image>.ok is
# removed before writing and recreated only at the end: restore from the image with the newest .ok.
image_paths() {
    local a=$TARGET/images/$HOST_DIR-A.img b=$TARGET/images/$HOST_DIR-B.img
    if [ ! -e "$a.ok" ]; then echo "$a"
    elif [ ! -e "$b.ok" ]; then echo "$b"
    elif [ "$a.ok" -ot "$b.ok" ]; then echo "$a"
    else echo "$b"; fi
}

part_used() {   # partition: bytes the image has to hold (only the folders of a skipped data partition,
                # and never the local working image itself)
    local mp used staged=0
    mp=$(mounted_at "$1")
    if skipped_data "$mp"; then echo 0; return; fi
    used=$(df -B1 --output=used "$mp" | tail -1)
    if [ -n "$IMAGE_STAGING" ] && [ -d "$IMAGE_STAGING" ] \
        && [ "$(stat -c %d "$IMAGE_STAGING")" = "$(stat -c %d "$mp")" ]; then
        staged=$(du -sB1 "$IMAGE_STAGING" | cut -f1)
    fi
    echo $((used - staged))
}

last_part_sectors() {   # used bytes -> size of the image's last partition: used + 20 % + 1 GiB, MiB-aligned
    local size_s=$(( ($1 * 12 / 10 + 1073741824) / 512 ))
    echo $(( (size_s + 2047) / 2048 * 2048 ))
}

# true when the image must be recreated: a partition of the SD no longer fits, or the last partition
# is more than twice the size it would get now (a data partition newly skipped, a big cleanup)
image_needs_rebuild() {   # file
    local part n used img_s last
    last=$(sfdisk -d "$DEVICE" | grep "^$DEVICE" | tail -1 | cut -d' ' -f1)
    for part in $(sfdisk -d "$DEVICE" | grep "^$DEVICE" | cut -d' ' -f1); do
        n=$(partnum "$part")
        used=$(part_used "$part")
        img_s=$(sfdisk -d "$1" | grep -E "^$1p?$n :" | sed -n 's/.*size= *\([0-9]*\).*/\1/p')
        [ -n "$img_s" ] || return 0
        [ "$used" -gt $((img_s * 512 * 9 / 10)) ] && return 0
        if [ "$part" = "$last" ] && [ "$img_s" -gt $(( $(last_part_sectors "$used") * 2 )) ]; then return 0; fi
    done
    return 1
}

create_image() {   # file
    local file=$1 dump last start used size_s total
    dump=$(sfdisk -d "$DEVICE")
    last=$(echo "$dump" | grep "^$DEVICE" | tail -1)
    local lastdev=${last%% *}
    start=$(echo "$last" | sed -n 's/.*start= *\([0-9]*\).*/\1/p')
    used=$(part_used "$lastdev")
    size_s=$(last_part_sectors "$used")
    total=$(( (start + size_s + 2048) * 512 ))
    log "create $(basename "$file"): $((total / 1048576)) MiB (sparse)"
    rm -f "$file"
    truncate -s "$total" "$file"
    echo "$dump" | grep -vE '^(device|last-lba|first-lba):' \
        | sed "s#^\($lastdev : start= *$start, size= *\)[0-9]*#\1$size_s#" \
        | sfdisk --quiet "$file"
    local part n src type label dev
    for part in $(echo "$dump" | grep "^$DEVICE" | cut -d' ' -f1); do
        n=$(partnum "$part")
        src=$part
        type=$(blkid -o value -s TYPE "$src")
        label=$(blkid -o value -s LABEL "$src" || true)
        part_loop "$file" "$n"
        dev=$PART_DEV
        case "$type" in
            vfat) mkfs.vfat -F 32 ${label:+-n "$label"} "$dev" >/dev/null ;;
            ext4|ext3|ext2) "mkfs.$type" -q -F ${label:+-L "$label"} "$dev" ;;
            *) RESULT_ERROR="unsupported filesystem $type on $src"; exit 1 ;;
        esac
    done
    free_loops
}

# The image on the QNAP is written in place through a loop device over NFS (.15, fast link), or, with
# IMAGE_STAGING, built in a local file and then copied as one stream (.20: through NFS the writes were so
# slow that rsync piled up ~900 MB waiting for them and was killed by the kernel, 25-26/10/2026).
do_image() {
    mount_share
    dump_databases
    local file work
    file=$(image_paths)
    if [ "$DRY" = 1 ]; then
        log "dry run: would refresh $file${IMAGE_STAGING:+ through $IMAGE_STAGING}"
        sfdisk -d "$DEVICE"
        write_status image true "dry run, target $(basename "$file")"
        return
    fi
    rm -f "$file.ok"
    if [ -z "$IMAGE_STAGING" ]; then
        refresh_image "$file"
        work=$file
    else
        install -d -m 0700 "$IMAGE_STAGING"
        work=$IMAGE_STAGING/$HOST_DIR.img
        refresh_image "$work"
        copy_to_share "$work" "$file"
        verify_image "$file"
    fi
    date -Is > "$file.ok"
    keep_monthly "$file" "$work"
    write_status image true "$(basename "$file") refreshed and verified"
    log "image done: $file"
}

# Direct I/O (no dirty pages piling up for the NFS writeback) and holes kept as holes.
copy_to_share() {   # local image, image on the share
    log "copy $(basename "$1") -> $(basename "$2")"
    rm -f "$2.tmp"
    dd if="$1" of="$2.tmp" bs=4M conv=sparse oflag=direct status=none \
        || { RESULT_ERROR="copy to the QNAP failed"; exit 1; }
    truncate -s "$(stat -c %s "$1")" "$2.tmp"
    mv "$2.tmp" "$2"
}

refresh_image() {   # file: create or update it from the running system and verify it
    local file=$1
    if [ ! -e "$file" ]; then
        create_image "$file"
    elif image_needs_rebuild "$file"; then
        log "partition sizes changed for $(basename "$file"): recreating"
        create_image "$file"
    fi
    install -d -m 0700 /run/domopi-backup-image
    local part n src dst rc b t pats bex binds
    for part in $(sfdisk -d "$DEVICE" | grep "^$DEVICE" | cut -d' ' -f1); do
        n=$(partnum "$part")
        src=$(mounted_at "$part")
        [ -n "$src" ] || { RESULT_ERROR="partition $part is not mounted"; exit 1; }
        mapfile -t binds < <(bind_mounts "$part")
        dst=/run/domopi-backup-image/p$n
        install -d "$dst"
        part_loop "$file" "$n"
        mount "$PART_DEV" "$dst"
        log "image: partition $n ($src)"
        rc=0
        if [ "$(blkid -o value -s TYPE "$part")" = vfat ]; then
            rsync_lim -rt --modify-window=1 --delete "$src/" "$dst/" || rc=$?
        elif [ "$src" = / ]; then
            # exclude patterns are written for /, so they apply to the root partition only;
            # bind mounts of the same partition are copied to their real place below
            pats=("${EXCLUDES[@]}" "${IMAGE_EXCLUDES[@]}")
            for b in "${binds[@]}"; do pats+=("${b#* }/*"); done
            [ -z "$IMAGE_STAGING" ] || pats+=("$IMAGE_STAGING/*")
            copy_tree "" "$dst" "${pats[@]}" || rc=$?
        elif skipped_data "$src"; then
            # saved by its own backup (emoncms on the .15): only the folders, with owners and modes
            log "image: $src data skipped (IMAGE_SKIP_DATA), folders only"
            rsync_lim -aHx --numeric-ids --delete -f '+ */' -f '- *' "$src/" "$dst/" || rc=$?
        else
            bex=()
            for b in "${binds[@]}"; do
                t=${b#* }
                case $t in "$src"/*) bex+=("--exclude=/${t#"$src"/}/*") ;; esac
            done
            rsync_lim -aHx --numeric-ids --delete "${bex[@]}" "$src/" "$dst/" || rc=$?
        fi
        rsync_ok "$rc" || { RESULT_ERROR="rsync partition $n exit $rc"; exit 1; }
        # a folder of this partition hidden by another mount but visible elsewhere (emonSD: the real
        # /var/log is under the log2ram tmpfs, bind-mounted at /var/log.bak) goes to its own place
        for b in "${binds[@]}"; do
            log "image: ${b#* } -> ${b%% *}"
            install -d "$dst${b%% *}"
            rc=0
            rsync_lim -aHx --numeric-ids --delete "${b#* }/" "$dst${b%% *}/" || rc=$?
            rsync_ok "$rc" || { RESULT_ERROR="rsync ${b#* } exit $rc"; exit 1; }
        done
        umount "$dst"
        free_loops
    done
    verify_image "$file"
}

# Root partition, split by size: rsync keeps the whole file list of a run in memory (~3-4 KB per file
# on the .20 with the long Node.js paths: /home alone, 120 000 files, took ~450 MB and the kernel stopped
# it, 25-26/10/2026). A folder with more than IMAGE_SPLIT_FILES entries is copied as its own level (files,
# links, empty folders and mount points, deleting what is gone) and then folder by folder, recursively;
# smaller ones in one run. The log shows how far it got.
copy_tree() {   # relative folder ("" = root), image root, exclude patterns written for /...
    local rel=$1 dst=$2 src n d dev rc=0 ex=()
    shift 2
    src=/$rel
    mapfile -t ex < <(excludes_for_path "$rel" "$@")
    n=$(find "$src" -xdev 2>/dev/null | head -n $((IMAGE_SPLIT_FILES + 1)) | wc -l)
    if [ "$n" -le "$IMAGE_SPLIT_FILES" ]; then
        log "image: $src"
        rsync_lim -aHx --numeric-ids --delete --delete-excluded "${ex[@]}" "$src/" "$dst/$rel/" || rc=$?
        return "$rc"
    fi
    rsync_lim -dlptgoDHx --numeric-ids --delete --delete-excluded "${ex[@]}" "$src/" "$dst/$rel/" || rc=$?
    rsync_ok "$rc" || return "$rc"
    dev=$(stat -c %d "$src/")
    while IFS= read -r -d '' d; do
        [ "$(stat -c %d "$src/$d")" = "$dev" ] || continue     # other filesystems (-x)
        path_excluded "${rel:+$rel/}$d" "$@" && continue
        rc=0
        copy_tree "${rel:+$rel/}$d" "$dst" "$@" || rc=$?
        rsync_ok "$rc" || return "$rc"
    done < <(find "$src/" -mindepth 1 -maxdepth 1 -type d -printf '%f\0' | sort -z)
    return 0
}

# Read-only checks before an image is marked good: every filesystem clean, the boot partition points
# at the image's own root partition, kernel, fstab and init present.
verify_image() {   # file
    local file=$1 diskid part type dst=/run/domopi-backup-image/verify
    diskid=$(disk_id "$file")
    [ -n "$diskid" ] || { RESULT_ERROR="no disk identifier in $(basename "$file")"; exit 1; }
    local n dev p1 p2
    for n in $(part_numbers "$file"); do
        part_loop "$file" "$n" -r
        dev=$PART_DEV
        [ "$n" = 1 ] && p1=$dev
        [ "$n" = 2 ] && p2=$dev
        type=$(blkid -p -o value -s TYPE "$dev")
        case "$type" in
            vfat) fsck.vfat -n "$dev" >/dev/null || { RESULT_ERROR="fsck of partition $n failed"; exit 1; } ;;
            ext*) e2fsck -fn "$dev" >/dev/null 2>&1 || { RESULT_ERROR="e2fsck of partition $n failed"; exit 1; } ;;
        esac
    done
    install -d "$dst"
    mount -o ro "$p1" "$dst"
    grep -q "root=PARTUUID=$diskid-02" "$dst/cmdline.txt" \
        || { umount "$dst"; RESULT_ERROR="cmdline.txt does not point at PARTUUID $diskid-02"; exit 1; }
    ls "$dst"/kernel*.img >/dev/null 2>&1 || { umount "$dst"; RESULT_ERROR="no kernel in the boot partition"; exit 1; }
    umount "$dst"
    mount -o ro "$p2" "$dst"
    [ -s "$dst/etc/fstab" ] && [ -e "$dst/sbin/init" ] \
        || { umount "$dst"; RESULT_ERROR="fstab or init missing in the root partition"; exit 1; }
    umount "$dst"
    free_loops
    log "image verified"
}

# Once a month the freshly verified image is also kept as <host>-M-YYYY-MM.img (last KEEP_MONTHLY):
# protects against a fault or a wrong setting found more than two weeks later.
keep_monthly() {   # verified image on the share, same image to copy from (local when staged)
    [ "$KEEP_MONTHLY" -gt 0 ] || return 0
    local m=$TARGET/images/$HOST_DIR-M-$(date +%Y-%m).img old
    [ -e "$m.ok" ] && return 0
    log "monthly copy $(basename "$m")"
    if [ "$2" = "$1" ]; then
        cp --sparse=always "$1" "$m.tmp"
        mv "$m.tmp" "$m"
    else
        copy_to_share "$2" "$m"
    fi
    cp "$1.ok" "$m.ok"
    for old in $(ls "$TARGET/images/" | grep -E "^$HOST_DIR-M-[0-9]{4}-[0-9]{2}\.img$" | sort -r | tail -n +$((KEEP_MONTHLY + 1))); do
        log "monthly rotation: delete $old"
        rm -f "$TARGET/images/$old" "$TARGET/images/$old.ok"
    done
}

case "$MODE" in
    init) do_init ;;
    snapshot) do_snapshot ;;
    image) do_image ;;
    *) echo "use: rasp_backup.sh snapshot|image|init [--dry-run]" >&2; exit 2 ;;
esac
