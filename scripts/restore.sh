#!/usr/bin/env sh
# Replace the live database with a backup. THIS IS DESTRUCTIVE.
# Usage: sh scripts/restore.sh backups/oiep-YYYYMMDDTHHMMSSZ.db
set -eu
cd "$(dirname "$0")/.."

usage() {
    echo "Usage: sh scripts/restore.sh backups/oiep-YYYYMMDDTHHMMSSZ.db" >&2
    exit 1
}

[ "$#" -eq 1 ] || usage
backup="$1"

if [ ! -f "$backup" ]; then
    echo "No such backup file: $backup" >&2
    exit 1
fi

backup_dir="$(cd "$(dirname "$backup")" && pwd)"
backup_name="$(basename "$backup")"

# The restore helper runs inside a container that has the production data volume mounted.
# It receives only this plain file name, and the name is dereferenced as data ("$BACKUP_NAME")
# inside the helper - never pasted into shell syntax. Rejecting everything outside this small
# character set rules out quoting tricks, option injection, path traversal and newlines.
case "$backup_name" in
    ''|*[!A-Za-z0-9._-]*)
        echo "Unsupported backup file name: $backup_name" >&2
        echo "Allowed characters: letters, digits, '.', '_' and '-'." >&2
        exit 1
        ;;
esac
# Docker volume specifications cannot represent a directory containing ':' (a Windows drive
# letter passed through a shell that does not translate it) or a newline.
case "$backup_dir" in
    *:*|*'
'*)
        echo "Unsupported backup directory (contains ':' or a newline): $backup_dir" >&2
        echo "Run this script from Git Bash/WSL on Windows (see docs/DEPLOY.md)." >&2
        exit 1
        ;;
esac
# A SQLite database starts with the 16-byte header "SQLite format 3\0". Refuse to replace the
# live database with a file that is obviously not a database (wrong path, truncated copy...).
if ! dd if="$backup" bs=1 count=15 2>/dev/null | grep -q 'SQLite format 3'; then
    echo "Refusing to restore: $backup does not look like a SQLite database." >&2
    exit 1
fi

echo "This will REPLACE the live OIEP database with:"
echo "  $backup"
echo
printf 'Type exactly "yes" to continue: '
read -r answer
# Be forgiving of Windows CRLF when running through Git Bash/WSL.
answer="$(printf '%s' "$answer" | tr -d '\r')"
if [ "$answer" != "yes" ]; then
    echo "Cancelled."
    exit 1
fi

echo "Stopping the app..."
docker compose stop app

# Copy into the volume under a temporary name and only then replace the database with an
# atomic rename, so a failed copy/chown can never leave the deployment without a database.
# The app runs as UID/GID 10001, so the restored file is owned by that user and is mode 0600
# (hidden reports are not world-readable any more). Removing the old -wal/-shm entries before
# the rename is safe here (the app is stopped, and the old database is being replaced anyway)
# and prevents SQLite from applying a stale WAL to the restored file.
if ! docker run --rm --network none \
        -v oiep-data:/data \
        -v "${backup_dir}:/backup:ro" \
        -e BACKUP_NAME="$backup_name" \
        --entrypoint sh \
        alpine:3.20 -c 'set -eu
            tmp="/data/.oiep-restore-$$.db"
            trap "rm -f \"$tmp\"" EXIT
            cp "/backup/$BACKUP_NAME" "$tmp"
            chown 10001:10001 "$tmp"
            chmod 600 "$tmp"
            rm -f /data/oiep.db-wal /data/oiep.db-shm
            mv -f "$tmp" /data/oiep.db
            chown 10001:10001 /data
            chmod 700 /data
            trap - EXIT'; then
    echo "Restore FAILED; the previous database was not replaced and the app is stopped." >&2
    echo "Start it again with: docker compose start app" >&2
    exit 1
fi

docker compose start app
echo
echo "Restored as UID/GID 10001 with mode 0600."
echo "Watch the startup log with:"
echo "  docker compose logs -f --tail=80 app"
