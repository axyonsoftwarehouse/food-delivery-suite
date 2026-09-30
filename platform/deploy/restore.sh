#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

FILE="${1:?Uso: bash restore.sh <arquivo.sql>}"
[ -f "$FILE" ] || { echo "Arquivo não encontrado: $FILE" >&2; exit 1; }

docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD"' < "$FILE"

echo "Restauração concluída a partir de $FILE"
echo "Confira /ready na API antes de liberar tráfego."
