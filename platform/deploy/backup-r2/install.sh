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

for p in foodie storyverse; do
  if [ ! -f "$BASE/$p.env" ]; then
    install -m 600 -o deploy -g deploy "$SRC/$p.env.example" "$BASE/$p.env"
    echo "criado $BASE/$p.env"
  fi
  if [ ! -f "$BASE/$p-rclone.conf" ]; then
    install -m 600 -o deploy -g deploy "$SRC/$p-rclone.conf.example" "$BASE/$p-rclone.conf"
    echo "criado $BASE/$p-rclone.conf"
  fi
done

for p in foodie storyverse; do
  install -m 644 "$SRC/$p-backup-r2.service" "/etc/systemd/system/$p-backup-r2.service"
  install -m 644 "$SRC/$p-backup-r2.timer" "/etc/systemd/system/$p-backup-r2.timer"
done

systemctl daemon-reload

ready=1
for p in foodie storyverse; do
  grep -q 'PREENCHER' "$BASE/$p-rclone.conf" 2>/dev/null && ready=0
  [ -f "$BASE/$p-age.pub" ] || ready=0
done

if [ "$ready" = "1" ]; then
  systemctl enable --now foodie-backup-r2.timer storyverse-backup-r2.timer
  echo "timers habilitados e iniciados"
else
  systemctl disable foodie-backup-r2.timer storyverse-backup-r2.timer >/dev/null 2>&1 || true
  echo "timers instalados, mas nao iniciados: preencha os arquivos em $BASE e rode novamente"
fi

echo
echo "Arquivos:"
echo "  $BASE/run-backup.sh"
echo "  $BASE/foodie.env"
echo "  $BASE/storyverse.env"
echo "  $BASE/foodie-rclone.conf"
echo "  $BASE/storyverse-rclone.conf"
echo "  $BASE/foodie-age.pub"
echo "  $BASE/storyverse-age.pub"
echo
echo "Teste manual: sudo systemctl start foodie-backup-r2.service"
echo "Log: journalctl -u foodie-backup-r2.service -n 100 --no-pager"
