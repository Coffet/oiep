#!/usr/bin/env sh
# Create a consistent online backup of the live SQLite database.
# Run from anywhere:  sh scripts/backup.sh
# Output:             backups/oiep-YYYYMMDDTHHMMSSZ.db
set -eu
cd "$(dirname "$0")/.."

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p backups

docker compose exec -T app sh -c 'sqlite3 /data/oiep.db ".backup /data/oiep-backup-tmp.db"'
docker compose cp app:/data/oiep-backup-tmp.db "backups/oiep-${stamp}.db"
docker compose exec -T app rm -f /data/oiep-backup-tmp.db

printf '\nBackup written to: backups/oiep-%s.db\n' "$stamp"
printf 'Copy it somewhere else (scp/S3) — a backup on the same disk is not a backup.\n'
