#!/usr/bin/env bash
set -euo pipefail
umask 077
cd "$(dirname "$0")"

[ -f .env ] || { echo "Crie monitoring/.env com TELEGRAM_BOT_TOKEN e TELEGRAM_CHAT_ID." >&2; exit 1; }
set -a
# shellcheck disable=SC1091
. ./.env
set +a

: "${TELEGRAM_BOT_TOKEN:?defina em monitoring/.env}"
: "${TELEGRAM_CHAT_ID:?defina em monitoring/.env}"

# O arquivo é montado como secret e o Alertmanager roda como `nobody` (65534): o dono tem de ser esse
# uid, com 600, senão o contêiner não lê ("permission denied"). Gera num temporário e troca por
# renomeação, porque o arquivo anterior já pertence ao 65534 e o usuário deploy não o sobrescreve.
AM_UID=65534
TMP="$(mktemp alertmanager.rendered.XXXXXX)"
trap 'rm -f "$TMP"' EXIT

sed -e "s|\${TELEGRAM_BOT_TOKEN}|${TELEGRAM_BOT_TOKEN}|g" \
    -e "s|\${TELEGRAM_CHAT_ID}|${TELEGRAM_CHAT_ID}|g" \
    alertmanager.yml > "$TMP"
chmod 600 "$TMP"

if [ "$(id -u)" = 0 ]; then
  chown "$AM_UID:$AM_UID" "$TMP"
elif ! sudo -n chown "$AM_UID:$AM_UID" "$TMP" 2>/dev/null; then
  echo "Não consegui entregar o arquivo ao Alertmanager: rode com um usuário que tenha" >&2
  echo "sudo sem senha, ou como root (o script faz chown $AM_UID:$AM_UID). Nada foi alterado." >&2
  exit 1
fi

mv -f "$TMP" alertmanager.rendered.yml
echo "Gerado alertmanager.rendered.yml (não versionado; 600, dono $AM_UID, montado como secret)."
echo "Para o Alertmanager ler o novo: docker compose restart alertmanager"
