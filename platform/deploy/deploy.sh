#!/usr/bin/env bash
#
# deploy.sh — aplica um release do Foodie na VPS, com backup, verificação e rollback.
#
# RODA NA VPS. No seu PC, use `release.ps1`, que gera, envia e chama este script.
#
#   bash /home/deploy/deploy.sh <pacote.tar> <sha> [sha256]
#
# O pacote é o resultado de `git archive <sha>:platform` — o conteúdo da pasta
# platform/ na raiz do arquivo.
#
# Em ordem, o script:
#   1. valida o pacote (integridade, conteúdo mínimo e nome do projeto Compose)
#   2. marca as imagens atuais como pre-<sha>          (rollback de imagem)
#   3. faz backup do banco                             (deploy/backup.sh)
#   4. guarda um snapshot do código atual              (releases/<sha>/backup-source.tar)
#   5. aplica o código novo                            (rsync --delete)
#   6. reconstrói e sobe                               (docker compose up -d --build)
#   7. espera /ready e confere o schemaVersion contra as migrations do pacote
#   8. grava <tree>/.deployed
#   9. se 6, 7 ou 8 falharem: restaura o snapshot e sobe de novo
#
# Variáveis opcionais:
#   FOODIE_TREE          pasta da plataforma  (padrão /home/deploy/foodie-platform)
#   FOODIE_RELEASES      pasta de releases    (padrão /home/deploy/releases)
#   FOODIE_HEALTH_TRIES  tentativas de /ready (padrão 40)
#   FOODIE_HEALTH_SLEEP  segundos entre elas  (padrão 5)
#
set -euo pipefail

TREE="${FOODIE_TREE:-/home/deploy/foodie-platform}"
RELEASES="${FOODIE_RELEASES:-/home/deploy/releases}"
HEALTH_TRIES="${FOODIE_HEALTH_TRIES:-40}"
HEALTH_SLEEP="${FOODIE_HEALTH_SLEEP:-5}"
DEPLOY_DIR="$TREE/deploy"

PACKAGE="${1:-}"
SHA="${2:-}"
EXPECTED_SHA256="${3:-}"

say()  { printf '\n==> %s\n' "$*"; }
info() { printf '    %s\n' "$*"; }
warn() { printf '    [aviso] %s\n' "$*" >&2; }
die()  { printf '\n[ERRO] %s\n' "$*" >&2; exit 1; }

if [ "$PACKAGE" = "--help" ] || [ "$PACKAGE" = "-h" ]; then
  sed -n '2,30p' "$0" | sed 's/^#\{1,\} \{0,1\}//'
  exit 0
fi

[ -n "$PACKAGE" ] || die "uso: bash $0 <pacote.tar> <sha> [sha256]"
[ -n "$SHA" ]     || die "informe o sha do release"
[ -f "$PACKAGE" ] || die "pacote não encontrado: $PACKAGE"
[ -d "$TREE" ]    || die "pasta da plataforma não encontrada: $TREE"
[ -f "$DEPLOY_DIR/.env" ] || die "falta $DEPLOY_DIR/.env — sem ele o compose não sobe"

LOG="/home/deploy/deploy-${SHA}-$(date -u +%Y%m%d-%H%M%S).log"
exec > >(tee -a "$LOG") 2>&1

# ---------------------------------------------------------------- travas
exec 9>/home/deploy/.deploy.lock
flock -n 9 || die "já existe um deploy em andamento (lock em /home/deploy/.deploy.lock)"

ACTUAL_SHA256="$(sha256sum "$PACKAGE" | awk '{print $1}')"
if [ -n "$EXPECTED_SHA256" ] && [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
  die "sha256 do pacote não confere (esperado $EXPECTED_SHA256, obtido $ACTUAL_SHA256)"
fi

say "Deploy do release $SHA"
info "pacote : $PACKAGE ($(du -h "$PACKAGE" | cut -f1))"
info "sha256 : $ACTUAL_SHA256"
info "destino: $TREE"
info "log    : $LOG"

# ------------------------------------------------- validação do pacote
say "Validando o pacote"
tar -tf "$PACKAGE" >/dev/null 2>&1 || die "o pacote não é um tar válido"
LISTING="$(tar -tf "$PACKAGE")"
for required in deploy/docker-compose.yml apps/api-java/pom.xml apps/web/package.json; do
  case "$LISTING" in
    *"$required"*) info "ok: $required" ;;
    *) die "pacote inválido: falta $required" ;;
  esac
done

# O compose fixa o nome do projeto; sem isso os volumes seriam outros e o
# deploy criaria um banco novo e vazio.
CHECK_DIR="$(mktemp -d)"
tar -xf "$PACKAGE" -C "$CHECK_DIR" deploy/docker-compose.yml
FIRST_LINE="$(sed -n '1p' "$CHECK_DIR/deploy/docker-compose.yml")"
rm -rf "$CHECK_DIR"
[ "$FIRST_LINE" = "name: foodie-staging" ] || \
  die "o docker-compose.yml do pacote não declara 'name: foodie-staging' (linha 1: '$FIRST_LINE')"
info "ok: projeto Compose fixado em foodie-staging"

# ------------------------------------------------- estado atual
PREVIOUS="desconhecido"
if [ -f "$TREE/.deployed" ]; then
  PREVIOUS="$(sed -n 's/^sha=//p' "$TREE/.deployed")"
  PREVIOUS="${PREVIOUS%%$'\n'*}"
fi
say "Estado atual"
info "release implantado antes: $PREVIOUS"

# Perfil público: se o Caddy já está no ar, mantenha-o no ciclo.
PROFILE_ARGS=()
CADDY_RUNNING="$(docker ps \
  --filter label=com.docker.compose.project=foodie-staging \
  --filter label=com.docker.compose.service=caddy \
  --format '{{.Names}}')"
if [ -n "$CADDY_RUNNING" ]; then
  PROFILE_ARGS=(--profile public)
  info "perfil 'public' (Caddy) está ativo — será mantido"
fi
dc() { ( cd "$DEPLOY_DIR" && docker compose "${PROFILE_ARGS[@]}" "$@" ); }

normalize() { local v; v="$(printf '%s' "$1" | sed 's/^0*//')"; if [ -z "$v" ]; then v=0; fi; printf '%s' "$v"; }

# ------------------------------------------------- 1. imagens de rollback
say "Marcando imagens atuais como pre-$SHA"
for svc in api web migrate seed; do
  img="foodie-staging-$svc:latest"
  if docker image inspect "$img" >/dev/null 2>&1; then
    if docker tag "$img" "foodie-staging-$svc:pre-$SHA"; then
      info "$img -> :pre-$SHA"
    else
      warn "não consegui marcar $img"
    fi
  fi
done

# ------------------------------------------------- 2. backup do banco
say "Backup do banco (deploy/backup.sh)"
# Os .sh do repositório estão em modo 644 (sem bit de execução), então
# './backup.sh' daria "Permission denied". Chame sempre com 'bash'.
if ! ( cd "$DEPLOY_DIR" && bash backup.sh ); then
  die "o backup falhou — deploy abortado antes de alterar qualquer coisa"
fi

# ------------------------------------------------- 3. snapshot do código
mkdir -p "$RELEASES/$SHA"
SNAP="$RELEASES/$SHA/backup-source.tar"
say "Guardando o código atual em $SNAP"
tar -cf "$SNAP" -C "$TREE" \
  --exclude=./deploy/backups \
  --exclude=./deploy/.env \
  --exclude=./node_modules \
  --exclude=./.deployed \
  . 2>/dev/null
info "snapshot: $(du -h "$SNAP" | cut -f1)"

rollback() {
  say "ROLLBACK — restaurando o código de antes ($PREVIOUS)"
  tar -xf "$SNAP" -C "$TREE"
  dc up -d --build || true
  dc ps || true
  warn "O banco NÃO volta sozinho: migrations já aplicadas permanecem aplicadas."
  warn "Se o release novo migrou o schema, restaure o dump com:"
  warn "  (cd $DEPLOY_DIR && bash restore.sh <arquivo.sql>)"
  die "deploy abortado e código revertido; log em $LOG"
}

# ------------------------------------------------- 4. aplicar o código
INCOMING="$RELEASES/$SHA/incoming"
say "Aplicando o código novo"
rm -rf "$INCOMING"
mkdir -p "$INCOMING"
tar -xf "$PACKAGE" -C "$INCOMING"

if command -v rsync >/dev/null 2>&1; then
  rsync -a --delete \
    --exclude='deploy/.env' \
    --exclude='deploy/backups/' \
    --exclude='.deployed' \
    "$INCOMING"/ "$TREE"/
  info "sincronizado com rsync (o que saiu do repositório saiu da VPS)"
else
  warn "rsync ausente: extraindo com tar (arquivos apagados no repositório podem sobrar)"
  tar -xf "$PACKAGE" -C "$TREE"
fi

# Os scripts vêm do git em modo 644; deixa executáveis para uso manual.
chmod +x "$DEPLOY_DIR"/*.sh 2>/dev/null || true
chmod +x "$TREE"/monitoring/*.sh 2>/dev/null || true

# ------------------------------------------------- 5. subir
say "Reconstruindo e subindo"
if ! dc up -d --build; then
  dc ps || true
  rollback
fi
dc ps

# ------------------------------------------------- 6. verificar
say "Esperando a API ficar pronta"
GOT=""
i=1
while [ "$i" -le "$HEALTH_TRIES" ]; do
  if READY="$(dc exec -T api curl -fsS http://localhost:4001/ready 2>/dev/null)"; then
    GOT="$(printf '%s' "$READY" | sed -n 's/.*"schemaVersion":"\([^"]*\)".*/\1/p')"
    info "tentativa $i: $READY"
    break
  fi
  info "tentativa $i/$HEALTH_TRIES: ainda não responde"
  sleep "$HEALTH_SLEEP"
  i=$((i + 1))
done

if [ -z "$GOT" ]; then
  rollback
fi

EXPECTED_SCHEMA="$(normalize "$(ls "$TREE"/apps/api-java/src/main/resources/db/migration/V*__*.sql 2>/dev/null \
  | sed -E 's#.*/V([0-9]+)__.*#\1#' | sort -n | tail -1 || true)")"
GOT_NORM="$(normalize "$GOT")"
info "schemaVersion na API: $GOT | maior migration do pacote: $EXPECTED_SCHEMA"
if [ "$GOT_NORM" != "$EXPECTED_SCHEMA" ]; then
  rollback
fi

# Checagem pública: só informa, não derruba o deploy.
DOMAIN_RAW="$(sed -n 's/^FOODIE_DOMAIN=//p' "$DEPLOY_DIR/.env")"
DOMAIN="$(printf '%s' "$DOMAIN_RAW" | sed -n '1p')"
DOMAIN="${DOMAIN//\"/}"
DOMAIN="${DOMAIN//\'/}"
if [ -n "$DOMAIN" ]; then
  if curl -fsS --max-time 15 "https://api.$DOMAIN/ready" >/dev/null 2>&1; then
    info "público: https://api.$DOMAIN/ready respondeu 200"
  else
    warn "público: https://api.$DOMAIN/ready não respondeu (o interno está ok)"
  fi
fi

# ------------------------------------------------- 7. registrar
cat > "$TREE/.deployed" <<EOF
sha=$SHA
sha256=$ACTUAL_SHA256
schema=$GOT
previous=$PREVIOUS
deployed_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
package=$PACKAGE
log=$LOG
EOF

say "Deploy concluído"
info "release no ar : $SHA"
info "schemaVersion : $GOT"
info "registro      : $TREE/.deployed"
info "log           : $LOG"
info "rollback de imagem disponível: foodie-staging-{api,web}:pre-$SHA"
info "para voltar o código: tar -xf $SNAP -C $TREE && (cd $DEPLOY_DIR && docker compose up -d --build)"
