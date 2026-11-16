#!/usr/bin/env bash
#
# lib-ambiente.sh — o que muda entre o staging e a produção na VPS.
#
# Só define funções: carregar com `source` não executa nada. Quem usa: deploy.sh e conferir-env.sh.
# Testes: platform/tests/deploy/lib-ambiente.test.mjs.
#
# Os dois ambientes são projetos Compose separados, com o mesmo docker-compose.yml:
#
#   ambiente     projeto             árvore                           releases
#   staging      foodie-staging      /home/deploy/foodie-platform     /home/deploy/releases
#   production   foodie-production   /home/deploy/foodie-production   /home/deploy/releases-production

ambiente_validar() {
  case "${1:-}" in
    staging|production) return 0 ;;
    *) printf '[erro] ambiente inválido: "%s" (use staging ou production)\n' "${1:-}" >&2; return 1 ;;
  esac
}

ambiente_projeto() {
  ambiente_validar "${1:-}" || return 1
  printf 'foodie-%s\n' "$1"
}

ambiente_arvore() {
  ambiente_validar "${1:-}" || return 1
  if [ -n "${FOODIE_TREE:-}" ]; then printf '%s\n' "$FOODIE_TREE"; return 0; fi
  case "$1" in
    staging)    printf '/home/deploy/foodie-platform\n' ;;
    production) printf '/home/deploy/foodie-production\n' ;;
  esac
}

ambiente_releases() {
  ambiente_validar "${1:-}" || return 1
  if [ -n "${FOODIE_RELEASES:-}" ]; then printf '%s\n' "$FOODIE_RELEASES"; return 0; fi
  case "$1" in
    staging)    printf '/home/deploy/releases\n' ;;
    production) printf '/home/deploy/releases-production\n' ;;
  esac
}

# env_valor <arquivo> <VARIAVEL> — imprime o valor como o Compose o leria: a última ocorrência vale,
# sem CR, sem espaços nas pontas e sem as aspas em volta. Variável ausente imprime vazio.
env_valor() {
  local arquivo="${1:-}" nome="${2:-}" linha valor
  [ -f "$arquivo" ] || { printf '\n'; return 0; }
  linha="$(grep -E "^[[:space:]]*(export[[:space:]]+)?${nome}=" "$arquivo" | tail -n 1 || true)"
  valor="${linha#*=}"
  valor="${valor%$'\r'}"
  valor="${valor#"${valor%%[![:space:]]*}"}"
  valor="${valor%"${valor##*[![:space:]]}"}"
  case "$valor" in
    \"*\") valor="${valor#\"}"; valor="${valor%\"}" ;;
    \'*\') valor="${valor#\'}"; valor="${valor%\'}" ;;
  esac
  printf '%s\n' "$valor"
}

# ambiente_conferir_projeto <ambiente> <arquivo .env> — o nome do projeto decide os volumes. Sem a
# variável, o Compose usaria o nome da pasta (`deploy`) e subiria um banco novo e vazio.
ambiente_conferir_projeto() {
  local esperado atual
  esperado="$(ambiente_projeto "${1:-}")" || return 1
  if [ ! -f "${2:-}" ]; then
    printf '[erro] COMPOSE_PROJECT_NAME: arquivo de ambiente não encontrado (%s)\n' "${2:-}" >&2
    return 1
  fi
  atual="$(env_valor "$2" COMPOSE_PROJECT_NAME)"
  if [ "$atual" != "$esperado" ]; then
    printf '[erro] COMPOSE_PROJECT_NAME: esperado "%s", encontrado "%s" em %s.\n' "$esperado" "$atual" "$2" >&2
    printf '       Sem o nome certo o Compose cria volumes novos e o ambiente sobe com um banco vazio.\n' >&2
    printf '       Corrija com: COMPOSE_PROJECT_NAME=%s\n' "$esperado" >&2
    return 1
  fi
}

# promocao_conferir <.deployed do staging> <sha> — a produção só recebe o commit que está no staging.
# Compara só o commit: o `git archive` grava o horário, então o sha256 do pacote muda a cada geração.
promocao_conferir() {
  local registro="${1:-}" sha="${2:-}" no_staging="" motivo
  if [ -f "$registro" ]; then
    no_staging="$(sed -n 's/^sha=//p' "$registro" | head -n 1 | tr -d '\r')"
  fi
  if [ -n "$no_staging" ] && [ "$no_staging" = "$sha" ]; then
    return 0
  fi
  if [ -z "$no_staging" ]; then
    motivo="staging sem publicação registrada (${registro})"
  else
    motivo="o staging está no commit ${no_staging} e o pedido é ${sha}"
  fi
  if [ "${FOODIE_PROMOCAO_FORCADA:-}" = "1" ]; then
    printf '[aviso] promoção forçada (FOODIE_PROMOCAO_FORCADA=1): %s\n' "$motivo" >&2
    return 0
  fi
  printf '[erro] promoção recusada: %s.\n' "$motivo" >&2
  printf '       A produção só recebe o commit que já está publicado no staging.\n' >&2
  return 1
}
