#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

[ -f .env ] || { echo "Crie monitoring/.env com TELEGRAM_BOT_TOKEN e TELEGRAM_CHAT_ID." >&2; exit 1; }
set -a
# shellcheck disable=SC1091
. ./.env
set +a

: "${TELEGRAM_BOT_TOKEN:?defina em monitoring/.env}"
: "${TELEGRAM_CHAT_ID:?defina em monitoring/.env}"

sed -e "s|\${TELEGRAM_BOT_TOKEN}|${TELEGRAM_BOT_TOKEN}|g" \
    -e "s|\${TELEGRAM_CHAT_ID}|${TELEGRAM_CHAT_ID}|g" \
    alertmanager.yml > alertmanager.rendered.yml
chmod 644 alertmanager.rendered.yml
echo "Gerado alertmanager.rendered.yml (não versionado)."
