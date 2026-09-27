#!/usr/bin/env bash
# Atualiza a homologação (foodie-staging) na VPS.
# Uso: bash deploy/update.sh
set -euo pipefail
cd "$(dirname "$0")"

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
