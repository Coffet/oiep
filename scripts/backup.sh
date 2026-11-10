#!/usr/bin/env sh
# Create a consistent online backup of the live SQLite database.
# Run from anywhere:  sh scripts/backup.sh
# Output:             backups/oiep-YYYYMMDDTHHMMSSZ.db (mode 0600)
set -eu
cd "$(dirname "$0")/.."

# Backups contain every submitted note (including hidden ones), so keep them private.
umask 077

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup="backups/oiep-${stamp}.db"
# Unique per run so two concurrent backups cannot overwrite each other's temporary copy.
tmp_in_container="/data/oiep-backup-$$.db"

cleanup() {
    # Always remove the in-volume temporary copy, even when a later step fails.
    docker compose exec -T app rm -f "$tmp_in_container" >/dev/null 2>&1 || true
}
trap cleanup EXIT HUP INT TERM

mkdir -p backups

# `sqlite3 .backup` is safe while the app is running. The destination path is passed as a
# positional argument and expanded as data inside the container shell, never interpolated
# into the command text by the host shell.
docker compose exec -T app sh -c 'sqlite3 /data/oiep.db ".backup $1"' sh "$tmp_in_container"
docker compose cp "app:$tmp_in_container" "$backup"
chmod 600 "$backup"

if [ ! -s "$backup" ]; then
    echo "Backup file is empty: $backup" >&2
    exit 1
fi

printf '\nBackup written to: %s (mode 0600)\n' "$backup"
printf 'Copy it somewhere else (scp/S3) - a backup on the same disk is not a backup.\n'
