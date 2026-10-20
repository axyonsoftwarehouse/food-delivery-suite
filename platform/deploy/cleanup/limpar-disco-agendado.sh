#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RELEASES="${FOODIE_RELEASES:-/home/deploy/releases}"
MIN_PCT="${CLEANUP_MIN_PERCENT:-60}"
KEEP_RELEASES="${CLEANUP_KEEP_RELEASES:-5}"
TAR_DAYS="${CLEANUP_TAR_DAYS:-14}"

USO_PCT="$(df / | awk 'NR==2{gsub(/[^0-9]/,"",$5); print $5}')"
if [ -z "${USO_PCT:-}" ] || [ "$USO_PCT" -lt "$MIN_PCT" ]; then
  echo "disco em ${USO_PCT:-?}% (limite ${MIN_PCT}%): nada a fazer"
  exit 0
fi

echo "disco em ${USO_PCT}% (limite ${MIN_PCT}%): limpando"
FOODIE_MANTER_ROLLBACK="${CLEANUP_KEEP_ROLLBACK:-2}" bash "$DEPLOY_DIR/limpar-disco.sh"

if [ -d "$RELEASES" ]; then
  echo "removendo releases antigos (mantendo os $KEEP_RELEASES mais recentes)"
  find "$RELEASES" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' \
    | sort -nr \
    | tail -n +$((KEEP_RELEASES + 1)) \
    | cut -d' ' -f2- \
    | while IFS= read -r dir; do
        rm -rf -- "$dir"
      done
fi

echo "removendo tarballs de deploy com mais de $TAR_DAYS dias"
find /home/deploy -maxdepth 1 -type f -name 'platform-release-*.tar' -mtime +"$TAR_DAYS" -delete

echo "disco depois: $(df -h / | awk 'NR==2{print $5" usado, "$4" livres"}')"
