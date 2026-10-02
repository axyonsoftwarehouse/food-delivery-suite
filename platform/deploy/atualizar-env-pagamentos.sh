#!/usr/bin/env bash
# Aplica linhas KEY=VALUE de um arquivo em outro .env, preservando o resto do arquivo.
#
# Uso: atualizar-env-pagamentos.sh <arquivo-com-as-chaves> <env-de-destino>
#
# Feito para receber o arquivo de chaves por scp (o valor nunca aparece em argv nem em log),
# atualizar a chave existente no lugar ou acrescentar a que faltar, e deixar copia de
# seguranca do destino antes de mexer.
set -euo pipefail

origem="${1:?informe o arquivo com as chaves}"
destino="${2:?informe o .env de destino}"

[ -f "$origem" ] || { echo "origem nao encontrada: $origem" >&2; exit 1; }
[ -f "$destino" ] || { echo "destino nao encontrado: $destino" >&2; exit 1; }

backup="${destino}.bak-$(date +%Y%m%d%H%M%S)"
cp -a "$destino" "$backup"
echo "copia de seguranca: $backup"

aplicadas=0
while IFS= read -r linha || [ -n "$linha" ]; do
  linha="${linha%$'\r'}"          # arquivo vindo do Windows pode trazer CR no fim do valor
  case "$linha" in ''|'#'*) continue ;; esac
  chave="${linha%%=*}"
  [ -n "$chave" ] || continue
  if grep -qE "^[[:space:]]*${chave}[[:space:]]*=" "$destino"; then
    tmp="${destino}.novo"
    awk -v chave="$chave" -v nova="$linha" '
      BEGIN { pat = "^[[:space:]]*" chave "[[:space:]]*=" }
      $0 ~ pat { print nova; next }
      { print $0 }
    ' "$destino" > "$tmp"
    mv "$tmp" "$destino"
  else
    printf '%s\n' "$linha" >> "$destino"
  fi
  aplicadas=$((aplicadas + 1))
done < "$origem"

echo "chaves aplicadas: $aplicadas"
