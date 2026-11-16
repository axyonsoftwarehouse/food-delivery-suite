#!/usr/bin/env bash
set -euo pipefail

BASE="${BACKUP_R2_BASE:-/home/deploy/backup-r2}"
SRC="$(cd "$(dirname "$0")" && pwd)"

if [ "$(id -u)" -ne 0 ]; then
  echo "rode com sudo: sudo bash $0" >&2
  exit 1
fi

if ! command -v age >/dev/null 2>&1; then
  apt-get update -y
  apt-get install -y age
fi

RCLONE_VERSION="v1.75.2"
if ! command -v rclone >/dev/null 2>&1 || ! rclone version | head -1 | grep -q "rclone ${RCLONE_VERSION}"; then
  TMP_RCLONE="$(mktemp -d)"
  curl -fsSL "https://downloads.rclone.org/${RCLONE_VERSION}/rclone-${RCLONE_VERSION}-linux-amd64.deb" -o "$TMP_RCLONE/rclone.deb"
  dpkg -i "$TMP_RCLONE/rclone.deb"
  rm -rf "$TMP_RCLONE"
fi

install -d -m 700 -o deploy -g deploy "$BASE"
install -m 750 -o deploy -g deploy "$SRC/run-backup.sh" "$BASE/run-backup.sh"

# foodie-production usa o rclone e a chave age do foodie (mesmo bucket, prefixo próprio).
PROJETOS="foodie foodie-production storyverse"

for p in $PROJETOS; do
  if [ ! -f "$BASE/$p.env" ]; then
    install -m 600 -o deploy -g deploy "$SRC/$p.env.example" "$BASE/$p.env"
    echo "criado $BASE/$p.env"
  fi
  [ -f "$SRC/$p-rclone.conf.example" ] || continue
  if [ ! -f "$BASE/$p-rclone.conf" ]; then
    install -m 600 -o deploy -g deploy "$SRC/$p-rclone.conf.example" "$BASE/$p-rclone.conf"
    echo "criado $BASE/$p-rclone.conf"
  fi
done

for p in $PROJETOS; do
  install -m 644 "$SRC/$p-backup-r2.service" "/etc/systemd/system/$p-backup-r2.service"
  install -m 644 "$SRC/$p-backup-r2.timer" "/etc/systemd/system/$p-backup-r2.timer"
done

systemctl daemon-reload

# Cada timer só é ligado quando o SEU projeto está pronto: arquivos do rclone e da chave age
# preenchidos e a árvore do projeto existente. Um projeto que ainda não existe (a produção, antes
# da virada) não desliga o backup dos outros.
pronto() {
  (
    set -a
    # shellcheck disable=SC1090
    . "$BASE/$1.env"
    set +a
    [ -f "${RCLONE_CONFIG:-}" ] && ! grep -q 'PREENCHER' "$RCLONE_CONFIG" || exit 1
    [ -f "${AGE_PUBLIC_KEY_FILE:-}" ] || exit 1
    [ -z "${PROJECT_DIR:-}" ] || [ -d "$PROJECT_DIR" ] || exit 1
  )
}

for p in $PROJETOS; do
  if pronto "$p"; then
    systemctl enable --now "$p-backup-r2.timer"
    echo "$p: timer habilitado e iniciado"
  else
    systemctl disable --now "$p-backup-r2.timer" >/dev/null 2>&1 || true
    echo "$p: timer instalado, mas não iniciado: preencha $BASE/$p.env (rclone, chave age, PROJECT_DIR) e rode de novo"
  fi
done

echo
echo "Arquivos:"
echo "  $BASE/run-backup.sh"
echo "  $BASE/foodie.env"
echo "  $BASE/foodie-production.env"
echo "  $BASE/storyverse.env"
echo "  $BASE/foodie-rclone.conf"
echo "  $BASE/storyverse-rclone.conf"
echo "  $BASE/foodie-age.pub"
echo "  $BASE/storyverse-age.pub"
echo
echo "Teste manual: sudo systemctl start foodie-backup-r2.service"
echo "Log: journalctl -u foodie-backup-r2.service -n 100 --no-pager"
