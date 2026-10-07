#!/usr/bin/env bash
#
# limpar-disco.sh — libera espaço na VPS do Foodie sem derrubar nada e sem
# perder o rollback dos últimos releases.
#
# RODA NA VPS (é lá que estão as imagens). No PC não serve para nada.
#
#   bash limpar-disco.sh              limpa de verdade
#   bash limpar-disco.sh --ensaio     só mostra o que faria
#
# Variáveis:
#   FOODIE_MANTER_ROLLBACK   quantos releases guardar (padrão 2: atual e anterior)
#
# O que faz, em ordem:
#   1. remove as tags pre-<sha> dos releases mais antigos (guarda as N últimas)
#   2. remove as camadas sem tag
#   3. remove o cache de build (é o que mais ocupa e não é usado em runtime)
#
# Nunca usa `docker system prune -a` nem nada com `--volumes`: a regra está em
# docs/RUNBOOK_VPS.md §5 — o `prune -a` leva junto as imagens de rollback e o
# `--volumes` leva o banco. Nada aqui esconde erro: o que falhar aparece.
set -u

ENSAIO=0
if [ "${1:-}" = "--ensaio" ]; then ENSAIO=1; fi
MANTER="${FOODIE_MANTER_ROLLBACK:-2}"

say()   { printf '\n==> %s\n' "$*"; }
info()  { printf '    %s\n' "$*"; }
aviso() { printf '    [aviso] %s\n' "$*" >&2; }

uso_disco() { df -h / | awk 'NR==2{printf "%s de %s usados (%s), %s livres", $3, $2, $5, $4}'; }

roda() {
  if [ "$ENSAIO" = "1" ]; then
    info "[ensaio] $*"
  else
    "$@"
  fi
}

say "Disco antes: $(uso_disco)"

# ------------------------------------------------- 1. tags pre-<sha> antigas
# A tag pre-<sha> guarda as imagens do release ANTERIOR ao sha — é o que o
# deploy.sh cria antes de aplicar. Ordenamos pela data de criação da imagem,
# da mais nova para a mais antiga, e guardamos as MANTER primeiras.
say "Tags pre-* (guardando os $MANTER releases mais recentes)"
TAGS="$(docker images --format '{{.CreatedAt}}|{{.Repository}}:{{.Tag}}' | grep ':pre-' | sort -r || true)"
if [ -z "$TAGS" ]; then
  info "nenhuma tag pre-* no disco"
else
  SHAS="$(printf '%s\n' "$TAGS" | awk -F'|' '{print $2}' | awk -F: '{print $2}' | awk '!v[$0]++')"
  info "$(printf '%s\n' "$SHAS" | grep -c .) releases com tag"
  info "guardando: $(printf '%s' "$(printf '%s\n' "$SHAS" | head -n "$MANTER")" | tr '\n' ' ')"
  for sha in $(printf '%s\n' "$SHAS" | tail -n +$((MANTER + 1))); do
    for svc in api web migrate seed; do
      img="foodie-staging-$svc:$sha"
      if docker image inspect "$img" >/dev/null 2>&1; then
        roda docker image rm "$img"
      fi
    done
  done
fi

# ------------------------------------------------- 2. camadas sem tag
say "Camadas sem tag"
if [ "$ENSAIO" = "1" ]; then
  info "[ensaio] docker image prune -f"
else
  docker image prune -f
fi

# ------------------------------------------------- 3. cache de build
say "Cache de build"
if [ "$ENSAIO" = "1" ]; then
  info "[ensaio] docker builder prune -f"
else
  docker builder prune -f
fi

say "Disco depois: $(uso_disco)"
info "containers:"
docker ps --format '- {{.Names}} | {{.Status}}'

if [ "$ENSAIO" = "1" ]; then
  aviso "modo ensaio: nada foi removido"
fi
