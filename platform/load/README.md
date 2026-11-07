# Teste de carga (k6)

Como rodar o teste descrito em `docs/PLANO_TESTE_CARGA.md`. Os resultados de cada rodada vão em
`docs/RESULTADO_TESTE_CARGA_<data>.md`.

| Arquivo | O que faz |
| --- | --- |
| `cenarios.js` | Os cinco perfis (visitante, cliente logado, comprador, tela da loja, entregador), os níveis e as metas |
| `../tools/src/load-setup.ts` | Cria lojas, clientes e entregadores de carga numa zona própria (pode rodar de novo sem duplicar) |
| `../tools/src/load-clean.ts` | Apaga tudo o que o teste criou (só simula sem `LOAD_CLEAN_CONFIRM=sim`) |

Tudo o que o teste cria é identificável: contas `@carga.local`, lojas `carga-foodie-NN`, zona
`carga-foodie` e CEPs `69999000`–`69999999`. As lojas de carga não têm push inscrito: pedido novo não
toca o celular de ninguém.

## 1. Preparar (uma vez por ambiente)

Na VPS, na pasta do deploy (`/home/deploy/foodie-platform/deploy`), depois de publicar o commit que
traz estes arquivos:

```bash
docker compose --profile tools run --rm --build seed pnpm --filter @foodie/tools load:setup
```

Padrão: 20 lojas, 500 clientes e 90 entregadores. Para mudar, passe `-e LOAD_STORES=…`,
`-e LOAD_CUSTOMERS=…`, `-e LOAD_COURIERS=…`. Para medir a lista de pedidos com histórico, passe
`-e LOAD_HISTORY_PER_STORE=2000`. As contas usam a senha da demonstração (`DEMO_PASSWORD`, que já está
no ambiente do serviço `seed`).

## 2. Rodar (do PC, nunca da VPS)

O gerador de carga não pode disputar CPU com o sistema medido, por isso o teste roda no PC. Primeiro,
a validação (1 usuário por perfil, 1 minuto), que precisa passar inteira antes de qualquer nível:

```bash
k6 run -e NIVEL=0 -e LOAD_PASSWORD="<senha da demonstração>" platform/load/cenarios.js
```

Depois, um nível por vez (`NIVEL=1` esperado, `2` folga 3×, `3` ruptura, `4` resistência de 60 min).
Guarde o resumo:

```bash
k6 run -e NIVEL=1 -e LOAD_PASSWORD="<senha da demonstração>" --summary-export=resultado-nivel1.json platform/load/cenarios.js
```

Opções:

- `BASE_URL` — o site (padrão: o staging). Local: `http://127.0.0.1:3001`.
- `SO=visitantes,compradores` — roda só esses perfis (nomes: `visitantes`, `clientes_logados`,
  `compradores`, `telas_loja`, `entregadores`).
- `LOAD_STORES`, `LOAD_CUSTOMERS`, `LOAD_COURIERS` — se a preparação usou números diferentes do padrão.

O teste falha sozinho (código de saída diferente de 0) quando fura uma meta: p95 das leituras ≥ 800 ms,
das páginas ≥ 1,5 s, do fechamento de pedido ≥ 2 s, ou mais de 1% de erros. No nível 3 ele **para
sozinho** com mais de 5% de erros, para não insistir num servidor que já caiu.

## 3. Acompanhar a VPS durante a rodada

Em outra janela, por SSH:

```bash
docker stats
```

```bash
vmstat 5
```

Anote a cada nível: CPU e memória por container, uso de swap, reinícios (`docker ps`) e erros novos no
Sentry (`foodie-api` e `foodie-web`).

## 4. Limpar

Primeiro só conferir o que seria apagado; depois apagar:

```bash
docker compose --profile tools run --rm --build seed pnpm --filter @foodie/tools load:clean
```

```bash
docker compose --profile tools run --rm --build -e LOAD_CLEAN_CONFIRM=sim seed pnpm --filter @foodie/tools load:clean
```

A limpeza descobre sozinha as tabelas que dependem de pedidos, contas e lojas (pelas chaves
estrangeiras do banco) e apaga tudo numa transação só.
