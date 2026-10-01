# Auditoria do legado StackFood no repositório — 01/10/2026

Feita antes de decidir a migração do repositório para a organização Axyon, para
responder a uma pergunta objetiva: **sobrou código ou material do pacote comercial
StackFood v9 / do kit visual Foodie dentro do repositório?**

Método: comparação por **hash de blob** (arquivo byte-idêntico), comparação por
**similaridade de conteúdo** (arquivo copiado e depois editado), busca por
**marcas e nomes de pacote** e comparação dos **nomes de tabela** do schema.
Tudo executado contra `HEAD` e contra a tag `legacy-stackfood-v9`, com o
repositório em `9a63a28` + PRs de 01/10.

## 1. O kit visual Foodie: nada no repositório

| Item | Situação |
| --- | --- |
| `.fig` e 214 PNGs do kit (213 MB) | **fora do repositório** — em `C:\1.Arquivos Gerais\Foodie - Food Delivery App UI Kit` |
| Assets, ícones ou imagens do kit | **nenhum** — as únicas imagens do produto são 7 ícones Expo/Foodie e `foodie-burger-hero.png` |
| Fonte do kit (Urbanist) | **não usada** — o produto usa DM Sans + Manrope |
| Menções restantes | comentário em `tokens.css` (removido em 01/10) e o doc `REFERENCIA_UI_FOODIE.md` (removido em 01/10) |

## 2. O pacote StackFood: o que existia e foi removido

Antes da limpeza de 01/10/2026, o pacote comercial (que **não tem licença**
localizada) sobrevivia em três lugares:

| Onde | Conteúdo | Volume |
| --- | --- | --- |
| `reference/flutter-apps/` | os três apps Flutter do pacote, versionados na árvore atual | 2.349 arquivos (~31 MB) |
| **tag `legacy-stackfood-v9`** | snapshot completo, **publicada no GitHub** | 8.711 blobs / 194,2 MB (`admin-panel` 115,4 MB, `web` 35,9 MB, `app-user` 17,9 MB, `app-restaurant` 8,3 MB, `_local_server` 7,3 MB, `app-delivery` 4,7 MB, `payment-gateway` 2,2 MB) |
| histórico do Git | os mesmos arquivos, em todos os 91 commits anteriores a `04e5686` | ~117 MB no `.git` |

A tag era o ponto crítico: mesmo sem a pasta `reference/`, qualquer clone
recuperava o pacote inteiro com `git checkout legacy-stackfood-v9 -- admin-panel`.

**Nenhum arquivo de licença** (`LICENSE`, `NOTICE`, `COPYING`) existe em nenhum
commit do repositório.

## 3. O que os testes mostraram sobre o produto novo

| Teste | Resultado |
| --- | --- |
| Blobs idênticos entre a tag e o `HEAD` | 2.111 — **todos** dentro de `reference/flutter-apps`; nenhum em `platform/` ou `svg/` |
| Cópias modificadas (similaridade > 85%) | **0 pares** em 30 comparações por nome + conteúdo |
| Marcas do pacote no produto (`stackfood`, `foodie`, `6amtech`, `food_e*`, `efood`, `dinehub`, `tiffinking`) | só **texto**: uma frase no `platform/README.md` e o registro no `deploy/README.md` |
| Dump do banco legado | **não existe** no Git (o `database.sql` nunca foi commitado) |
| Schema copiado? | legado: 132 tabelas; produto: 88; **27 nomes em comum**, todos substantivos genéricos do domínio (`orders`, `users`, `categories`, `coupons`, `zones`…) e 105 tabelas do legado sem correspondência |

Conclusão da parte técnica: **nada do pacote comercial foi copiado para o código
do produto.** O que existia era material de consulta, não código em uso.

## 4. O que foi removido em 01/10/2026

- `reference/` (incluindo o `README.md` que ensinava a recuperar o pacote);
- a tag `legacy-stackfood-v9`, **local e no remoto**
  (`git push origin :refs/tags/legacy-stackfood-v9`);
- documentos derivados do código do pacote: `INVENTARIO_LEGADO_STACKFOOD.md`,
  `INVENTARIO_LACUNAS_LEGADO.md`, `JAVA_MIGRATION_PLAN.md`,
  `REFERENCIA_FUNCIONAL.md`, `AUDITORIA_IMPEDIMENTOS.md`,
  `REFERENCIA_INSPIRACOES.md`, `PLANO_EVOLUCAO_INSPIRACOES.md`,
  `REFERENCIA_UI_FOODIE.md`;
- o comentário do `tokens.css` que citava o kit visual de terceiros (a paleta do
  produto não mudou).

## 5. Ressalva importante: o histórico continua

Apagar a tag **não apaga os blobs**: os commits anteriores a `04e5686` seguem na
história da `main`, e o código do pacote continua acessível por
`git log`/`git checkout <commit>`. Para o repositório não carregar o pacote de
forma alguma, só há dois caminhos:

1. **Reescrever a história** (`git filter-repo --path admin-panel --path web --path app-user ... --invert-paths`),
   que troca **todos** os hashes — invalida referências a commits antigos e exige
   `push --force`; ou
2. **Começar um repositório novo** com a árvore atual, levando só o `docs/` como
   registro.

Nenhum dos dois foi feito: são decisões de quem administra o repositório.

## 6. Como reproduzir esta auditoria

```bash
# 1) arquivos idênticos entre a tag e a árvore atual
git ls-tree -r legacy-stackfood-v9 | awk '{print $3}' | sort -u > /tmp/tag.txt
git ls-tree -r HEAD                 | awk '{print $3}' | sort -u > /tmp/head.txt
comm -12 /tmp/tag.txt /tmp/head.txt | wc -l          # -> 2111

# 2) cada blob comum e onde ele vive
#    (todos os caminhos do HEAD caem em reference/flutter-apps/)

# 3) marcas do pacote dentro do produto
git grep -in -E "stackfood|foodie|6amtech|efood|dinehub|tiffinking" -- platform svg

# 4) nenhum arquivo de licença
git log --all --diff-filter=A --name-only | grep -iE "license|notice|copying"
```

Ressalva sobre a auditoria: o teste de similaridade cobre arquivos com o **mesmo
nome** do pacote; cópias renomeadas e reescritas por completo não seriam
detectadas por ele. Os testes de marcas, de schema e de blobs (independentes de
nome) não encontraram nada, e é essa a base da conclusão.
