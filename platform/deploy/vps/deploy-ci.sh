#!/usr/bin/env bash
#
# deploy-ci.sh — o ÚNICO comando que a chave do CI consegue executar nesta VPS.
#
# Cópia versionada de /home/deploy/deploy-ci.sh (ver o README.md desta pasta). Quem instala na VPS é
# a operação, à mão; o deploy não sobrescreve este arquivo.
#
# A chave pública do GitHub Actions está no `authorized_keys` do usuário `deploy`
# com `command="/home/deploy/deploy-ci.sh"`, `no-pty`, `no-port-forwarding` e
# `no-agent-forwarding`. Ou seja: mesmo com a chave, ninguém abre shell aqui — só
# dá para pedir "aplica este pacote", e o pacote chega por stdin (nem `scp` passa).
#
# O cliente manda um destes:
#     ... | ssh deploy@<host> "apply <sha> <sha256>"              (staging)
#     ... | ssh deploy@<host> "apply-production <sha> <sha256>"   (produção)
#
# e este script lê o pacote, confere o sha256 e chama o `deploy.sh` de sempre com
# FOODIE_ENV — que faz backup do banco, aplica, espera `/ready`, confere o schema e
# reverte sozinho se algo falhar. Na produção, o deploy.sh só aceita o commit que já
# está no staging.
#
# Variáveis (só para teste; na VPS ficam no padrão):
#   FOODIE_CI_INCOMING     onde o pacote é gravado     (padrão /home/deploy/ci-incoming)
#   FOODIE_CI_DEPLOY_CMD   script chamado no lugar do deploy.sh do pacote
set -uo pipefail

INCOMING_DIR="${FOODIE_CI_INCOMING:-/home/deploy/ci-incoming}"

log() { printf '[ci] %s\n' "$*" >&2; }

CMD="${SSH_ORIGINAL_COMMAND:-}"
# A validação abaixo é o que impede esta chave de virar um shell: só passa
# `apply` ou `apply-production`, com sha de 40 e sha256 de 64 caracteres hexadecimais.
# shellcheck disable=SC2086
set -- $CMD
ACAO="${1:-}"
SHA="${2:-}"
SHA256="${3:-}"

case "$ACAO" in
  apply)            AMBIENTE=staging ;;
  apply-production) AMBIENTE=production ;;
  *)
    log 'uso: ssh deploy@<host> "apply|apply-production <sha> <sha256>" < pacote.tar'
    exit 64
    ;;
esac
case "$SHA" in ""|*[!0-9a-fA-F]*) log "sha inválido"; exit 65 ;; esac
[ "${#SHA}" -eq 40 ] || { log "o sha precisa ter 40 caracteres"; exit 65; }
case "$SHA256" in ""|*[!0-9a-fA-F]*) log "sha256 inválido"; exit 65 ;; esac
[ "${#SHA256}" -eq 64 ] || { log "o sha256 precisa ter 64 caracteres"; exit 65; }

mkdir -p "$INCOMING_DIR"
PKG="$INCOMING_DIR/ci-$AMBIENTE-$SHA.tar"

log "recebendo o pacote do release $SHA ($AMBIENTE)"
if ! cat > "$PKG"; then
  log "falha ao receber o pacote"
  rm -f "$PKG"
  exit 70
fi

GOT="$(sha256sum "$PKG" | awk '{print $1}')"
if [ "$GOT" != "$SHA256" ]; then
  log "sha256 do pacote não confere (esperado $SHA256, obtido $GOT)"
  rm -f "$PKG"
  exit 71
fi
log "pacote conferido ($(du -h "$PKG" | cut -f1))"

# O deploy.sh muda junto com o release, então usa o do pacote que acabou de
# chegar — é o que o release.ps1 faz no caminho do PC. Quem executa (este
# script) mora fora da árvore e não é tocado.
DEPLOY_SH="/home/deploy/deploy.sh"
NEW_SH="$INCOMING_DIR/deploy-$AMBIENTE-$SHA.sh"
if [ -n "${FOODIE_CI_DEPLOY_CMD:-}" ]; then
  DEPLOY_SH="$FOODIE_CI_DEPLOY_CMD"
elif tar -xOf "$PKG" deploy/deploy.sh > "$NEW_SH" 2>/dev/null && [ -s "$NEW_SH" ]; then
  chmod +x "$NEW_SH"
  DEPLOY_SH="$NEW_SH"
  log "usando o deploy.sh do próprio pacote"
else
  log "o pacote não trouxe deploy/deploy.sh — usando o que já está na VPS"
fi

log "aplicando o release $SHA no ambiente $AMBIENTE"
FOODIE_ENV="$AMBIENTE" bash "$DEPLOY_SH" "$PKG" "$SHA" "$SHA256"
STATUS=$?

rm -f "$PKG" "$NEW_SH"
log "deploy terminou com código $STATUS"
exit "$STATUS"
