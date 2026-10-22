#!/usr/bin/env bash
set -euo pipefail
umask 077
cd "$(dirname "$0")"

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="${BACKUP_DIR:-./backups}"
mkdir -p "$OUT"
FILE="$OUT/foodie_platform-$STAMP.sql"

docker compose exec -T db sh -c 'exec mariadb-dump -u root -p"$MARIADB_ROOT_PASSWORD" --single-transaction --routines --triggers --databases foodie_platform' > "$FILE"
chmod 600 "$FILE"

echo "Backup gerado: $FILE"
echo "Guarde-o fora da VPS (ex.: cópia criptografada). Restaure com: bash restore.sh $FILE"
