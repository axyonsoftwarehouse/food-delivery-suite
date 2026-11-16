#!/usr/bin/env bash
#
# deploy.sh — aplica um release do Foodie na VPS, com backup, verificação e rollback.
#
# RODA NA VPS. No seu PC, use `release.ps1`, que gera, envia e chama este script.
#
#   FOODIE_ENV=staging|production bash /home/deploy/deploy.sh <pacote.tar> <sha> [sha256]
#
# Sem FOODIE_ENV, publica no staging. Os dois ambientes usam o mesmo
# docker-compose.yml, cada um com árvore, .env, banco e volumes próprios
# (deploy/lib-ambiente.sh). Na produção, só entra o commit que já está
# publicado no staging.
#
# O pacote é o resultado de `git archive <sha>:platform` — o conteúdo da pasta
# platform/ na raiz do arquivo.
#
# Em ordem, o script:
#   0. confere, sem alterar nada: pacote, nome do projeto Compose, volume do
#      banco, .env do ambiente (deploy/conferir-env.sh) e, na produção, se o
#      commit é o mesmo do staging
#   1. valida o pacote (integridade e conteúdo mínimo)
#   2. marca as imagens atuais como pre-<sha>          (rollback de imagem)
#   3. faz backup do banco                             (deploy/backup.sh)
#   4. guarda um snapshot do código atual              (releases/<sha>/backup-source.tar)
#   5. aplica o código novo                            (rsync --delete)
#   6. reconstrói e sobe                               (docker compose up -d --build)
#   7. espera /ready e confere o schemaVersion contra as migrations do pacote
#   8. grava <tree>/.deployed
#   9. se 6, 7 ou 8 falharem: restaura o snapshot e sobe de novo
#  10. se o disco passou do limite, limpa (deploy/limpar-disco.sh)
#
# Variáveis opcionais:
#   FOODIE_ENV           staging (padrão) ou production
#   FOODIE_TREE          pasta da plataforma  (padrão: a do ambiente, ver lib-ambiente.sh)
#   FOODIE_RELEASES      pasta de releases    (padrão: a do ambiente)
#   FOODIE_HOME          pasta do usuário deploy: logs e trava (padrão /home/deploy)
#   FOODIE_STAGING_TREE  árvore do staging, de onde a produção lê o commit publicado
#                        e o .env comparado (padrão /home/deploy/foodie-platform)
#   FOODIE_PROMOCAO_FORCADA=1  publica na produção um commit que não está no staging
#                        (emergência; fica registrado no log)
#   FOODIE_HEALTH_TRIES  tentativas de /ready (padrão 40)
#   FOODIE_HEALTH_SLEEP  segundos entre elas  (padrão 5)
#   FOODIE_MIN_FREE_GB   espaço livre mínimo em / para publicar (padrão 8)
#   FOODIE_LIMPEZA_EM    % de uso de / que dispara a limpeza pós-deploy (padrão 70)
#
set -euo pipefail

HEALTH_TRIES="${FOODIE_HEALTH_TRIES:-40}"
HEALTH_SLEEP="${FOODIE_HEALTH_SLEEP:-5}"
CASA="${FOODIE_HOME:-/home/deploy}"

PACKAGE="${1:-}"
SHA="${2:-}"
EXPECTED_SHA256="${3:-}"

say()  { printf '\n==> %s\n' "$*"; }
info() { printf '    %s\n' "$*"; }
warn() { printf '    [aviso] %s\n' "$*" >&2; }
die()  { printf '\n[ERRO] %s\n' "$*" >&2; exit 1; }

if [ "$PACKAGE" = "--help" ] || [ "$PACKAGE" = "-h" ]; then
  sed -n '2,42p' "$0" | sed 's/^#\{1,\} \{0,1\}//'
  exit 0
fi

[ -n "$PACKAGE" ] || die "uso: FOODIE_ENV=staging|production bash $0 <pacote.tar> <sha> [sha256]"
[ -n "$SHA" ]     || die "informe o sha do release"
export SENTRY_RELEASE="$SHA"
[ -f "$PACKAGE" ] || die "pacote não encontrado: $PACKAGE"

# O sha256 é conferido antes de abrir o pacote: logo abaixo, código dele é carregado.
ACTUAL_SHA256="$(sha256sum "$PACKAGE" | awk '{print $1}')"
if [ -n "$EXPECTED_SHA256" ] && [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
  die "sha256 do pacote não confere (esperado $EXPECTED_SHA256, obtido $ACTUAL_SHA256)"
fi
tar -tf "$PACKAGE" >/dev/null 2>&1 || die "o pacote não é um tar válido"

# ------------------------------------------------------- ambiente
# As regras de ambiente vêm do PRÓPRIO pacote: a árvore pode estar numa versão
# anterior, sem elas.
LIB_DIR="$(mktemp -d)"
trap 'rm -rf "$LIB_DIR"' EXIT
tar -xf "$PACKAGE" -C "$LIB_DIR" deploy/lib-ambiente.sh deploy/conferir-env.sh deploy/docker-compose.yml 2>/dev/null || \
  die "o pacote não traz deploy/lib-ambiente.sh e deploy/conferir-env.sh; ele é anterior aos dois ambientes.
       Para publicar um commit antigo, use o deploy.sh do próprio pacote (é o que o deploy-ci.sh faz)."
# shellcheck source=lib-ambiente.sh
. "$LIB_DIR/deploy/lib-ambiente.sh"

AMBIENTE="${FOODIE_ENV:-staging}"
ambiente_validar "$AMBIENTE" || die "FOODIE_ENV inválido: '$AMBIENTE'"
PROJETO="$(ambiente_projeto "$AMBIENTE")"
TREE="$(ambiente_arvore "$AMBIENTE")"
RELEASES="$(ambiente_releases "$AMBIENTE")"
STAGING_TREE="${FOODIE_STAGING_TREE:-$(FOODIE_TREE='' ambiente_arvore staging)}"
DEPLOY_DIR="$TREE/deploy"

[ -d "$TREE" ]    || die "pasta do ambiente $AMBIENTE não encontrada: $TREE"
[ -f "$DEPLOY_DIR/.env" ] || die "falta $DEPLOY_DIR/.env — sem ele o compose não sobe"

LOG="$CASA/deploy-${AMBIENTE}-${SHA}-$(date -u +%Y%m%d-%H%M%S).log"
exec > >(tee -a "$LOG") 2>&1

say "Deploy do release $SHA no ambiente $AMBIENTE"
info "projeto: $PROJETO"
info "pacote : $PACKAGE ($(du -h "$PACKAGE" | cut -f1))"
info "sha256 : $ACTUAL_SHA256"
info "destino: $TREE"
info "log    : $LOG"

# ------------------------------------------------- conferências (nada é alterado)
say "Conferindo o ambiente $AMBIENTE"

LISTING="$(tar -tf "$PACKAGE")"
for required in deploy/docker-compose.yml apps/api-java/pom.xml apps/web/package.json; do
  case "$LISTING" in
    *"$required"*) info "ok: $required" ;;
    *) die "pacote inválido: falta $required" ;;
  esac
done

# Fim de linha dos scripts. No Windows, 'core.autocrlf=true' faz o 'git archive'
# gravar CRLF; em Linux 'bash backup.sh' morre com
# "set: pipefail: invalid option name". Melhor barrar aqui do que subir scripts
# quebrados. Verificado em 30/09/2026.
# Conta os bytes CR com `tr`: o `grep -c $'\r'` do Git para Windows não enxerga CR.
CR_COUNT="$(tar -xOf "$PACKAGE" deploy/backup.sh | tr -cd '\r' | wc -c | tr -d ' ')"
if [ "$CR_COUNT" != "0" ]; then
  die "o pacote tem CRLF nos scripts (deploy/backup.sh: $CR_COUNT caractere(s) CR).
     Gere o pacote com fim de linha LF:
       git -c core.autocrlf=false -c core.eol=lf archive --format=tar -o <arquivo>.tar <sha>:platform"
fi
info "ok: scripts com fim de linha LF"

# O nome do projeto decide os volumes: com o nome errado, o ambiente subiria com
# um banco novo e vazio.
ambiente_conferir_projeto "$AMBIENTE" "$DEPLOY_DIR/.env" || die "deploy abortado antes de alterar qualquer coisa"
# Uma COMPOSE_PROJECT_NAME herdada do terminal valeria mais que a do .env.
export COMPOSE_PROJECT_NAME="$PROJETO"
info "ok: projeto Compose $PROJETO"
# O docker-compose.yml do pacote não pode fixar outro nome: `name:` venceria o .env.
if grep -Eq '^name:' "$LIB_DIR/deploy/docker-compose.yml"; then
  die "o docker-compose.yml do pacote fixa 'name:' ($(grep -E '^name:' "$LIB_DIR/deploy/docker-compose.yml" | head -n 1 | tr -d '\r'));
       o nome do projeto tem de vir do COMPOSE_PROJECT_NAME do .env de cada ambiente."
fi

VOLUME_BANCO="${PROJETO}_foodie_platform_data"
if [ -f "$TREE/.deployed" ]; then
  docker volume inspect "$VOLUME_BANCO" >/dev/null 2>&1 || \
    die "o banco deste ambiente não foi encontrado (volume $VOLUME_BANCO), mas $TREE/.deployed existe.
       Publicar agora criaria um banco vazio. Confira 'docker volume ls' antes de seguir."
  info "ok: volume do banco $VOLUME_BANCO"
fi

ENV_STAGING_ARGS=()
if [ "$AMBIENTE" = "production" ]; then
  if [ -f "$STAGING_TREE/deploy/.env" ]; then
    ENV_STAGING_ARGS=("$STAGING_TREE/deploy/.env")
  else
    warn "não achei o .env do staging ($STAGING_TREE/deploy/.env): a comparação de segredos foi pulada"
  fi
fi
# O compose recebe --env-file .env e, se existir, --env-file sentry.env; o segundo vence o primeiro.
# A conferência lê os dois na mesma ordem (env_valor fica com a última ocorrência). O arquivo
# combinado mora no LIB_DIR (mktemp -d, só o dono lê) e sai junto com ele.
ENV_EFETIVO="$DEPLOY_DIR/.env"
if [ -f "$DEPLOY_DIR/sentry.env" ]; then
  ENV_EFETIVO="$LIB_DIR/env-efetivo"
  ( umask 077; { cat "$DEPLOY_DIR/.env"; printf '\n'; cat "$DEPLOY_DIR/sentry.env"; } > "$ENV_EFETIVO" )
fi
if ! bash "$LIB_DIR/deploy/conferir-env.sh" "$AMBIENTE" "$ENV_EFETIVO" "${ENV_STAGING_ARGS[@]}"; then
  die "o .env de $AMBIENTE tem erros (acima, só os nomes das variáveis) — deploy abortado antes de alterar qualquer coisa"
fi
info "ok: .env de $AMBIENTE conferido"

if [ "$AMBIENTE" = "production" ]; then
  promocao_conferir "$STAGING_TREE/.deployed" "$SHA" || die "deploy abortado antes de alterar qualquer coisa"
  info "ok: o commit $SHA é o que está no staging"
fi

# ---------------------------------------------------------------- travas
# Uma trava só para a VPS inteira: os builds dos dois ambientes disputam a mesma CPU.
exec 9>"$CASA/.deploy.lock"
flock -n 9 || die "já existe um deploy em andamento (lock em $CASA/.deploy.lock)"

# ------------------------------------------------------- espaço em disco
# Um build consome ~2-3 GB. Sem espaço, o `dc up -d --build` morre no meio do
# caminho e o deploy reverte por um motivo que não tem a ver com o código.
MIN_FREE_GB="${FOODIE_MIN_FREE_GB:-8}"
FREE_GB="$(df -BG / | awk 'NR==2{gsub(/[^0-9]/,"",$4); print $4}')"
if [ -z "${FREE_GB:-}" ] || [ "$FREE_GB" -lt "$MIN_FREE_GB" ]; then
  die "só há ${FREE_GB:-?} GB livres em / e o mínimo é ${MIN_FREE_GB} GB (cada build consome ~2-3 GB).
       Libere espaço e tente de novo:   bash $DEPLOY_DIR/limpar-disco.sh
       Para publicar sem limpar:        FOODIE_ENV=$AMBIENTE FOODIE_MIN_FREE_GB=0 bash $0 $PACKAGE $SHA"
fi

info "disco  : ${FREE_GB} GB livres (mínimo ${MIN_FREE_GB} GB)"

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
  --filter label=com.docker.compose.project="$PROJETO" \
  --filter label=com.docker.compose.service=caddy \
  --format '{{.Names}}')"
if [ -n "$CADDY_RUNNING" ]; then
  PROFILE_ARGS=(--profile public)
  info "perfil 'public' (Caddy) está ativo — será mantido"
fi

ENV_FILE_ARGS=(--env-file "$DEPLOY_DIR/.env")
if [ -f "$DEPLOY_DIR/sentry.env" ]; then
  ENV_FILE_ARGS+=(--env-file "$DEPLOY_DIR/sentry.env")
  info "sentry.env: carregado de $DEPLOY_DIR/sentry.env"
fi
dc() { ( cd "$DEPLOY_DIR" && docker compose "${PROFILE_ARGS[@]}" "${ENV_FILE_ARGS[@]}" "$@" ); }

normalize() { local v; v="$(printf '%s' "$1" | sed 's/^0*//')"; if [ -z "$v" ]; then v=0; fi; printf '%s' "$v"; }

# ------------------------------------------------- 1. imagens de rollback
say "Marcando imagens atuais como pre-$SHA"
for svc in api web migrate seed; do
  img="$PROJETO-$svc:latest"
  if docker image inspect "$img" >/dev/null 2>&1; then
    if docker tag "$img" "$PROJETO-$svc:pre-$SHA"; then
      info "$img -> :pre-$SHA"
    else
      warn "não consegui marcar $img"
    fi
  fi
done

# ------------------------------------------------- 2. backup do banco
say "Backup do banco (deploy/backup.sh)"
BANCO_NO_AR="$(docker ps -q \
  --filter label=com.docker.compose.project="$PROJETO" \
  --filter label=com.docker.compose.service=db)"
if [ ! -f "$TREE/.deployed" ] && [ -z "$BANCO_NO_AR" ]; then
  # Primeira publicação do ambiente: ainda não há banco para guardar.
  warn "primeira publicação de $AMBIENTE: sem banco no ar, o backup foi pulado"
# Os .sh do repositório estão em modo 644 (sem bit de execução), então
# './backup.sh' daria "Permission denied". Chame sempre com 'bash'.
elif ! ( cd "$DEPLOY_DIR" && bash backup.sh ); then
  die "o backup falhou — deploy abortado antes de alterar qualquer coisa"
fi

# ------------------------------------------------- 3. snapshot do código
mkdir -p "$RELEASES/$SHA"
SNAP="$RELEASES/$SHA/backup-source.tar"
say "Guardando o código atual em $SNAP"
tar -cf "$SNAP" -C "$TREE" \
  --exclude=./deploy/backups \
  --exclude=./deploy/.env \
  --exclude=./deploy/*.env \
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
    --exclude='deploy/*.env' \
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
DOMAIN="$(env_valor "$DEPLOY_DIR/.env" FOODIE_DOMAIN)"
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
ambiente=$AMBIENTE
sha256=$ACTUAL_SHA256
schema=$GOT
previous=$PREVIOUS
deployed_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
package=$PACKAGE
log=$LOG
EOF

# ------------------------------------------------- 7b. regras de alerta
# O Prometheus monta prometheus.yml e alerts.yml como arquivo; o rsync troca o arquivo e o container
# segue lendo a versão antiga até reiniciar (o FoodieWebhookRejected de 23/10 ficaria parado). Só mexe se
# a pilha de monitoramento estiver no ar, e uma falha aqui só avisa: o deploy já está no ar.
# A pilha mora na árvore do staging (platform/monitoring), então só o deploy do staging a reinicia.
PROM=""
if [ "$AMBIENTE" = "staging" ]; then
  PROM="$(docker ps --format '{{.Names}}' | grep -E '^foodie-monitoring-prometheus' | head -1 || true)"
fi
if [ -n "$PROM" ]; then
  if docker restart "$PROM" >/dev/null 2>&1; then
    sleep 5
    if [ "$(docker inspect -f '{{.State.Running}}' "$PROM" 2>/dev/null)" = "true" ]; then
      info "Prometheus reiniciado com as regras de alerta deste release"
    else
      warn "Prometheus NÃO voltou depois do restart — confira: docker logs $PROM (regra inválida em monitoring/alerts.yml?)"
    fi
  else
    warn "não foi possível reiniciar o Prometheus ($PROM); as regras de alerta antigas seguem valendo"
  fi
fi

say "Deploy concluído ($AMBIENTE)"
info "release no ar : $SHA"
info "schemaVersion : $GOT"
info "registro      : $TREE/.deployed"
info "log           : $LOG"
info "rollback de imagem disponível: $PROJETO-{api,web}:pre-$SHA"
info "para voltar o código: tar -xf $SNAP -C $TREE && (cd $DEPLOY_DIR && docker compose up -d --build)"

# ------------------------------------------------- 8. higiene de disco
# Não é para limpar sempre: o cache de build acelera o próximo deploy. Só
# quando o disco passa do limite.
LIMPEZA_EM="${FOODIE_LIMPEZA_EM:-70}"
USO_PCT="$(df / | awk 'NR==2{gsub(/[^0-9]/,"",$5); print $5}')"
if [ -n "${USO_PCT:-}" ] && [ "$USO_PCT" -ge "$LIMPEZA_EM" ]; then
  say "Disco em ${USO_PCT}% (limite ${LIMPEZA_EM}%) — limpando"
  # Guarda só o rollback deste release: o pre-$SHA acabou de ser criado.
  if FOODIE_MANTER_ROLLBACK=1 bash "$DEPLOY_DIR/limpar-disco.sh"; then
    info "limpeza concluída"
  else
    warn "a limpeza falhou — o deploy já está no ar, isso não derruba nada"
  fi
else
  info "disco  : ${USO_PCT}% (limpeza automática a partir de ${LIMPEZA_EM}%)"
fi
