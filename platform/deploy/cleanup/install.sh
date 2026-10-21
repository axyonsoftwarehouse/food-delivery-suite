#!/usr/bin/env bash
set -euo pipefail

SRC="$(cd "$(dirname "$0")" && pwd)"

if [ "$(id -u)" -ne 0 ]; then
  echo "rode com sudo: sudo bash $0" >&2
  exit 1
fi

chmod +x "$SRC/limpar-disco-agendado.sh"
install -m 644 "$SRC/foodie-cleanup.service" /etc/systemd/system/foodie-cleanup.service
install -m 644 "$SRC/foodie-cleanup.timer" /etc/systemd/system/foodie-cleanup.timer

systemctl daemon-reload
systemctl enable --now foodie-cleanup.timer

echo "timer instalado:"
systemctl list-timers --all --no-pager | grep foodie-cleanup || true
