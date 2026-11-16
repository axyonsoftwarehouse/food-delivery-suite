#!/usr/bin/env bash
#
# conferir-env.sh — confere o .env de um ambiente ANTES de publicar.
#
#   bash conferir-env.sh <staging|production> <arquivo .env> [<.env do staging>]
#
# O docker-compose.yml é o mesmo nos dois ambientes e os padrões dele são os do staging (conta de
# teste do Mercado Pago, e-mail só no log, site fechado a buscadores). Na produção, esquecer uma
# variável não pode virar "subiu com o padrão do staging": este script para o deploy.
#
# Saída: uma linha por achado — `[erro] VARIAVEL: motivo` ou `[aviso] VARIAVEL: motivo`.
# Retorno: 1 se houver algum erro; 0 se não houver (avisos não param o deploy).
#
# NUNCA imprime o valor de uma variável, nem em mensagem de erro. Não ligue `set -x` aqui.
# O terceiro argumento serve para recusar, na produção, um segredo igual ao do staging
# (decisão de 14/10/2026: nenhuma credencial de teste vai para a produção).
#
# Testes: platform/tests/deploy/conferir-env.test.mjs.
set -u

# shellcheck source=lib-ambiente.sh
. "$(dirname "$0")/lib-ambiente.sh"

AMBIENTE="${1:-}"
ARQUIVO="${2:-}"
ARQUIVO_STAGING="${3:-}"
ERROS=0

erro()  { printf '[erro] %s: %s\n' "$1" "$2"; ERROS=$((ERROS + 1)); }
aviso() { printf '[aviso] %s: %s\n' "$1" "$2"; }
valor() { env_valor "$ARQUIVO" "$1"; }

if [ -z "$AMBIENTE" ] || [ -z "$ARQUIVO" ]; then
  printf 'uso: bash %s <staging|production> <arquivo .env> [<.env do staging>]\n' "$0" >&2
  exit 1
fi
ambiente_validar "$AMBIENTE" 2>&1 || exit 1
if [ ! -f "$ARQUIVO" ]; then
  erro ARQUIVO "arquivo de ambiente não encontrado"
  exit 1
fi

exigir() {
  local nome
  for nome in "$@"; do
    [ -n "$(valor "$nome")" ] || erro "$nome" "obrigatória neste ambiente e está vazia ou ausente"
  done
}

# Vale para os dois ambientes: o nome do projeto decide os volumes (ver lib-ambiente.sh).
if ! ambiente_conferir_projeto "$AMBIENTE" "$ARQUIVO" 2>&1; then
  ERROS=$((ERROS + 1))
fi
exigir DB_PASSWORD DB_ROOT_PASSWORD

if [ "$AMBIENTE" = "production" ]; then
  DOMINIO="$(valor FOODIE_DOMAIN)"
  case "$DOMINIO" in
    "")                    erro FOODIE_DOMAIN "obrigatória neste ambiente e está vazia ou ausente" ;;
    *://*)                 erro FOODIE_DOMAIN "informe só o domínio, sem https://" ;;
    *sslip.io*|*staging*)  erro FOODIE_DOMAIN "é um domínio de staging; a produção usa o domínio real" ;;
  esac

  [ "$(valor PAYMENTS_REQUIRE_TEST_ACCOUNTS)" = "false" ] || \
    erro PAYMENTS_REQUIRE_TEST_ACCOUNTS "tem de ser false; o padrão (true) só aceita contas de teste do Mercado Pago"
  [ "$(valor SENTRY_ENVIRONMENT)" = "production" ] || \
    erro SENTRY_ENVIRONMENT "tem de ser production; o padrão é staging"
  [ "$(valor SITE_INDEXABLE)" = "true" ] || \
    erro SITE_INDEXABLE "tem de ser true; o padrão fecha o site inteiro para buscadores"

  case "$(valor MAIL_PROVIDER)" in
    ""|log) erro MAIL_PROVIDER "o modo log não envia e-mail; a produção precisa de um provedor real" ;;
  esac
  exigir MAIL_FROM SPRING_MAIL_HOST

  exigir PAYMENTS_TOKEN_KEY MERCADOPAGO_CLIENT_ID MERCADOPAGO_CLIENT_SECRET MERCADOPAGO_WEBHOOK_SECRET \
    SUBSCRIPTIONS_ACCOUNT_ACCESS_TOKEN SUBSCRIPTIONS_ACCOUNT_PUBLIC_KEY WEBPUSH_PUBLIC_KEY WEBPUSH_PRIVATE_KEY

  # Vazio, o Compose cai no padrão mailto:contato@foodie.local, e serviços de push recusam esse remetente.
  case "$(valor WEBPUSH_SUBJECT)" in
    "")      erro WEBPUSH_SUBJECT "obrigatória neste ambiente; vazia, vale o padrão do staging (endereço .local)" ;;
    *.local*) erro WEBPUSH_SUBJECT "usa um endereço .local; a produção usa um e-mail ou site real" ;;
  esac

  # Qualquer endereço do staging copiado para a produção (origens aceitas, retorno do OAuth do Mercado
  # Pago, URL de retorno da conta da loja...). O FOODIE_DOMAIN já foi conferido acima.
  # Uma passada só (awk): vale a última ocorrência de cada variável, como no env_valor.
  for nome in $(awk '
      /^[[:space:]]*#/ { next }
      match($0, /^[[:space:]]*(export[[:space:]]+)?[A-Za-z_][A-Za-z0-9_]*=/) {
        chave = substr($0, 1, RLENGTH - 1); sub(/^[[:space:]]*(export[[:space:]]+)?/, "", chave)
        ultimo[chave] = substr($0, RLENGTH + 1)
      }
      END { for (chave in ultimo) if (chave != "FOODIE_DOMAIN" && index(ultimo[chave], "sslip.io")) print chave }
    ' "$ARQUIVO" | sort); do
    erro "$nome" "aponta para um endereço do staging (sslip.io)"
  done

  [ -z "$(valor DEMO_PASSWORD)" ] || \
    erro DEMO_PASSWORD "tem de ficar vazia; a produção não tem contas de demonstração"

  # Segredo repetido: compara em variáveis locais; só o nome vai para a saída.
  if [ -n "$ARQUIVO_STAGING" ] && [ -f "$ARQUIVO_STAGING" ]; then
    for nome in DB_PASSWORD DB_ROOT_PASSWORD PAYMENTS_TOKEN_KEY MERCADOPAGO_CLIENT_SECRET MERCADOPAGO_WEBHOOK_SECRET \
      SUBSCRIPTIONS_ACCOUNT_ACCESS_TOKEN WEBPUSH_PRIVATE_KEY; do
      daqui="$(valor "$nome")"
      de_la="$(env_valor "$ARQUIVO_STAGING" "$nome")"
      if [ -n "$daqui" ] && [ "$daqui" = "$de_la" ]; then
        erro "$nome" "é igual à do staging; a produção tem credenciais próprias"
      fi
    done
    unset daqui de_la
  fi

  case "$(valor SMS_PROVIDER)" in
    ""|local) aviso SMS_PROVIDER "o modo local não envia SMS; o login por código não funciona" ;;
  esac
  [ -n "$(valor GOOGLE_CLIENT_ID)" ] || aviso GOOGLE_CLIENT_ID "vazia; o botão de login com Google não funciona"
  [ -n "$(valor FACEBOOK_APP_ID)" ]  || aviso FACEBOOK_APP_ID "vazia; o botão de login com Facebook não funciona"
  [ "$(valor SUBSCRIPTIONS_ALLOW_REAL_CHARGES)" = "true" ] || \
    aviso SUBSCRIPTIONS_ALLOW_REAL_CHARGES "não é true; a assinatura das lojas não é cobrada de verdade"
  [ -n "$(valor SENTRY_DSN)" ] || aviso SENTRY_DSN "vazia; os erros da API não chegam ao Sentry"
  for nome in API_MEMORY DB_MEMORY WEB_MEMORY FOODIE_CPUS DB_BUFFER_POOL; do
    [ -n "$(valor "$nome")" ] || aviso "$nome" "ausente; fica o limite do staging (ver .env.production.example)"
  done
fi

if [ "$ERROS" -gt 0 ]; then
  printf '%s erro(s) no ambiente %s.\n' "$ERROS" "$AMBIENTE"
  exit 1
fi
exit 0
