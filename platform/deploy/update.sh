#!/usr/bin/env bash
# Atualiza um ambiente do Foodie na VPS, rodando dentro da pasta deploy/ da árvore dele.
# Uso: bash deploy/update.sh
#
# Plano B: o caminho normal é o deploy.sh (backup, conferências e rollback). Este script só sobe o
# Compose, então ao menos confere o nome do projeto: sem ele, o Compose usaria o nome da pasta
# (`deploy`) e subiria um banco novo e vazio (docs/RUNBOOK_VPS.md, seção 13).
set -euo pipefail
cd "$(dirname "$0")"

# shellcheck source=lib-ambiente.sh
. ./lib-ambiente.sh
PROJETO="$(env_valor .env COMPOSE_PROJECT_NAME)"
case "$PROJETO" in
  foodie-staging|foodie-production) export COMPOSE_PROJECT_NAME="$PROJETO" ;;
  *)
    printf '[erro] COMPOSE_PROJECT_NAME: esperado foodie-staging ou foodie-production em %s/.env, encontrado "%s".\n' "$PWD" "$PROJETO" >&2
    printf '       Sem o nome certo o Compose cria volumes novos e o ambiente sobe com um banco vazio.\n' >&2
    exit 1
    ;;
esac
echo "==> Projeto Compose: $PROJETO"

echo "==> Atualizando o código"
if [ -d ../.git ]; then
  git -C .. pull --ff-only
else
  echo "sem repositório git em platform/; copie o código atualizado antes de continuar"
fi

echo "==> Reconstruindo e subindo (db, migrate, api, web)"
docker compose up -d --build

echo "==> Status"
docker compose ps

cat <<'EOF'

Verifique a saúde (schema migrado):
  docker compose exec -T api curl -fsS http://localhost:4001/ready

Para (re)semear as contas de demonstração, uma vez, sem gravar a senha em arquivo:
  DEMO_PASSWORD='sua-senha-forte' docker compose --profile tools run --rm seed

Se o perfil público (Caddy) ainda não estiver ativo:
  docker compose --profile public up -d
EOF
