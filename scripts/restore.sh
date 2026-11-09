#!/usr/bin/env sh
# Replace the live database with a backup. THIS IS DESTRUCTIVE.
# Usage: sh scripts/restore.sh backups/oiep-YYYYMMDDTHHMMSSZ.db
set -eu
cd "$(dirname "$0")/.."

if [ "$#" -ne 1 ]; then
    echo "Usage: sh scripts/restore.sh backups/oiep-YYYYMMDDTHHMMSSZ.db" >&2
    exit 1
fi

backup="$1"
if [ ! -f "$backup" ]; then
    echo "No such backup file: $backup" >&2
    exit 1
fi

backup_dir="$(cd "$(dirname "$backup")" && pwd)"
backup_name="$(basename "$backup")"

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

docker compose stop app

# The app runs as UID/GID 10001. The helper restores the file into the same
# named volume and gives it back to that user.
docker run --rm \
    -v oiep-data:/data \
    -v "${backup_dir}:/backup:ro" \
    alpine:3.20 \
    sh -c "rm -f /data/oiep.db /data/oiep.db-wal /data/oiep.db-shm && cp '/backup/${backup_name}' /data/oiep.db && chown 10001:10001 /data/oiep.db && chmod 644 /data/oiep.db"

docker compose start app
echo
echo "Restored. Watch the startup log with:"
echo "  docker compose logs -f --tail=80 app"
