#!/usr/bin/env bash
set -euo pipefail

PROJECT="${1:-}"
BASE="${BACKUP_R2_BASE:-/home/deploy/backup-r2}"
CONFIG="$BASE/${PROJECT}.env"
HC_URL=""

die() { echo "[ERRO] $*" >&2; exit 1; }
ping_hc() {
  [ -n "$HC_URL" ] || return 0
  curl -fsS -m 10 --retry 3 "${HC_URL%/}$1" >/dev/null 2>&1 || true
}
on_error() {
  local code=$?
  echo "[ERRO] backup $PROJECT falhou (codigo $code)" >&2
  ping_hc /fail
  exit "$code"
}
trap on_error ERR

[ -n "$PROJECT" ] || die "uso: $0 <foodie|storyverse>"
[ -f "$CONFIG" ] || die "falta $CONFIG"
set -a
. "$CONFIG"
set +a
HC_URL="${HEALTHCHECK_URL:-}"

: "${RCLONE_CONFIG:?defina RCLONE_CONFIG}"
: "${RCLONE_REMOTE:?defina RCLONE_REMOTE}"
: "${R2_BUCKET:?defina R2_BUCKET}"
: "${AGE_PUBLIC_KEY_FILE:?defina AGE_PUBLIC_KEY_FILE}"
[ -f "$AGE_PUBLIC_KEY_FILE" ] || die "falta a chave publica age: $AGE_PUBLIC_KEY_FILE"
[ -f "$RCLONE_CONFIG" ] || die "falta o rclone config: $RCLONE_CONFIG"
RECIPIENT="$(grep -E '^age1[0-9a-z]+$' "$AGE_PUBLIC_KEY_FILE" | head -1)"
[ -n "$RECIPIENT" ] || die "chave publica age invalida em $AGE_PUBLIC_KEY_FILE"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
TMP="$(mktemp -d "/tmp/backup-r2-${PROJECT}-XXXXXX")"
OUT="$TMP/out"
mkdir -p "$OUT"
if [ "${KEEP_TMP:-0}" = "1" ]; then
  echo "TMP mantido em $TMP"
  trap - EXIT
else
  trap 'rm -rf "$TMP"' EXIT
fi
LOCK="/run/lock/backup-r2-${PROJECT}.lock"
exec 9>"$LOCK"
flock -n 9 || { echo "backup $PROJECT ja em andamento"; exit 0; }

ping_hc /start

case "$PROJECT" in
  foodie)
    PROJECT_DIR="${PROJECT_DIR:-/home/deploy/foodie-platform}"
    COMPOSE_DIR="$PROJECT_DIR/deploy"
    ( cd "$COMPOSE_DIR" && bash backup.sh )
    DUMP="$(find "$COMPOSE_DIR/backups" -maxdepth 1 -type f -name 'foodie_platform-*.sql' -printf '%T@ %p\n' | sort -nr | head -1 | cut -d' ' -f2-)"
    [ -n "$DUMP" ] && [ -s "$DUMP" ] || die "dump do Foodie nao encontrado"
    cp "$DUMP" "$TMP/database.sql"
    docker run --rm -v "${FILES_VOLUME:?defina FILES_VOLUME}:/data:ro" alpine:3.22 tar -C /data -czf - . > "$TMP/uploads.tar.gz"
    tar -czf "$TMP/config.tar.gz" -C "$COMPOSE_DIR" ${CONFIG_FILES:?defina CONFIG_FILES}
    find "$COMPOSE_DIR/backups" -maxdepth 1 -type f -name 'foodie_platform-*.sql' -mtime +"${LOCAL_RETENTION_DAYS:-7}" -delete
    ;;
  storyverse)
    PROJECT_DIR="${PROJECT_DIR:-/home/deploy/storyverse}"
    ( cd "$PROJECT_DIR" && bash deploy/backup.sh )
    LATEST="$(find "$PROJECT_DIR/backups" -maxdepth 1 -mindepth 1 -type d -printf '%T@ %p\n' | sort -nr | head -1 | cut -d' ' -f2-)"
    [ -n "$LATEST" ] || die "backup local do Storyverse nao encontrado"
    cp "$LATEST/database.dump" "$TMP/database.dump"
    cp "$LATEST/media.tar.gz" "$TMP/media.tar.gz"
    tar -czf "$TMP/config.tar.gz" -C "$PROJECT_DIR" ${CONFIG_FILES:?defina CONFIG_FILES}
    ;;
  *)
    die "projeto desconhecido: $PROJECT"
    ;;
esac

for f in "$TMP"/*.sql "$TMP"/*.dump "$TMP"/*.tar.gz; do
  [ -f "$f" ] || continue
  base="$(basename "$f")"
  age -r "$RECIPIENT" -o "$OUT/${PROJECT}-${STAMP}-${base}.age" "$f"
done
[ -n "$(ls -A "$OUT")" ] || die "nenhum arquivo para enviar"
( cd "$OUT" && sha256sum ./*.age > SHA256SUMS )

PREFIX="${R2_PREFIX:-$PROJECT}/$(date -u +%Y/%m/%d)/${STAMP}"
if [ "${DRY_RUN:-0}" = "1" ]; then
  echo "DRY_RUN: arquivos criptografados em $OUT"
  ls -lh "$OUT"
  ping_hc ""
  exit 0
fi

rclone --config "$RCLONE_CONFIG" copy "$OUT" "${RCLONE_REMOTE}:${R2_BUCKET}/${PREFIX}/" --include "*.age" --include "SHA256SUMS"
COUNT="$(rclone --config "$RCLONE_CONFIG" lsf "${RCLONE_REMOTE}:${R2_BUCKET}/${PREFIX}/" | wc -l)"
[ "$COUNT" -ge 2 ] || die "R2 nao confirmou os arquivos"
ping_hc ""
echo "Backup $PROJECT enviado para ${R2_BUCKET}/${PREFIX} ($COUNT arquivos)"
