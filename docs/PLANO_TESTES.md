# Plano de testes — contratos e ida-e-volta

Documento vivo. Criado em **15/10/2026**, depois de um bug que passou por toda a
verificação e só apareceu na mão do lojista: a lista de produtos do painel não
trazia a **janela de horário** (nem estoque/combo), então o formulário abria em
branco e, ao salvar, **apagava** a janela — o "Executivo do almoço" (11:00–15:00)
sumia do cardápio ao ser editado (corrigido na PR #50).

O objetivo deste plano é pegar **essa classe de bug**: a tela e a API discordando
sobre quais campos existem, de um jeito que o compilador e os testes unitários
não veem.

## A classe do problema

Chamamos de **deriva de contrato** (contract drift):

- um campo existe no banco e no formulário, mas **não** vem na resposta de leitura;
- ou vem com nome/formato diferente do que o formulário lê;
- ou o formulário **envia** um campo que a API grava errado (ou sobrescreve por vazio).

O sintoma típico é **perda silenciosa**: abrir → salvar sem mudar → o dado muda (ou
some). Nada quebra, nada loga, o CI fica verde — só o humano percebe.

Por que escapa hoje: os testes Java são unitários (mockam o banco) e o `tsc` só
confere tipos. Nenhum dos dois exercita o **ciclo real** leitura→formulário→gravação.

## Estratégia em três camadas

| Camada | O que pega | Onde roda |
| --- | --- | --- |
| **Unitária** (Java JUnit, web `node --test`, cozinha Jest) | lógica, cálculo, regras | sempre (`pnpm verify`) |
| **Contrato / ida-e-volta** (smoke HTTP) | deriva de contrato entre tela e API | `VERIFY_INTEGRATION=1` (e no CI) |
| **Homologação nas telas** | o que nenhum teste automatizado vê (layout, fluxo) | roteiro manual no staging |

## Regra de ouro (para todo campo editável)

Todo campo que o formulário **edita** precisa cumprir as três:

1. **Vem na leitura.** A resposta da lista/detalhe que preenche o formulário inclui
   o campo (mesmo que nulo).
2. **Sobrevive à ida-e-volta.** Carregar o formulário e salvar **sem mudar** não
   altera o valor (nem apaga).
3. **Tem smoke.** Existe um teste HTTP que exercita leitura→gravação para esse
   recurso.

Se um campo novo entra no formulário, ele entra também na query de leitura **e** no
smoke do recurso — no mesmo PR.

## Teste de ida-e-volta (o formato)

Para cada recurso editável, o smoke faz:

1. `GET` a lista/detalhe; conferir que **todos** os campos que o formulário usa
   estão presentes (ex.: `Object.hasOwn(produto, 'available_from')`).
2. `PATCH/PUT` gravando valores conhecidos; `GET` de novo e conferir que voltaram.
3. `PATCH/PUT` com **os mesmos valores que a leitura devolveu**; `GET` de novo e
   conferir que **nada mudou** (a ida-e-volta).
4. Restaurar o estado.

O exemplo pronto é `platform/tests/catalog-smoke.mjs`.

## Onde ficam e como rodar

- **Sempre** (sem banco): `cd platform && pnpm verify`
  — testes Java, tipos (`tsc`), testes web (`node --test`), build do Next, tipos e
  testes da cozinha, tipos do api-client, checagem dos Dockerfiles.
- **Integrado** (banco + seed + smokes): `VERIFY_INTEGRATION=1 pnpm verify`
  — sobe `docker-compose.test.yml`, roda o seed e os smokes e derruba tudo.
  Exige `DB_PASSWORD`/`DB_ROOT_PASSWORD` em `platform/.env` e `DEMO_PASSWORD` em
  `platform/tools/.env`.
  - smokes: `pnpm smoke:cart`, `smoke:catalog`, `smoke:exceptions`, `smoke:auth`,
    `smoke:coverage`, `smoke:subscription`.
- **Carga:** `pnpm load` (contra a API).

O **CI** (`.github/workflows/ci.yml`) roda `pnpm verify` com `VERIFY_INTEGRATION=1`,
então os smokes rodam no GitHub a cada PR.

## Cobertura atual e lacunas

| Recurso | Contrato/ida-e-volta? |
| --- | --- |
| Carrinho (itens, checkout) | ✅ `smoke:cart` |
| Catálogo da loja (produto: janela, estoque, combo; imagens) | ✅ `smoke:catalog` (novo) |
| Exceções do pedido | ✅ `smoke:exceptions` |
| Contas (login/OTP) | ✅ `smoke:auth` |
| Cobertura por CEP | ✅ `smoke:coverage` |
| Pagamentos online | parcial (teste TS `online-payment` + roteiro em `PAGAMENTOS_MODO_TESTE.md`) |
| Assinatura (plano atual, recibo e cobrança viva) | ✅ `smoke:subscription` |
| Entregador (turno, entrega) | ❌ sem smoke |
| Loja — página, horários, zonas, equipe, promoções | ❌ sem smoke |

As lacunas são candidatas ao mesmo formato de ida-e-volta.

## Checklist para um PR que mexe em formulário/API

- [ ] O campo novo aparece na **query de leitura** que preenche o formulário.
- [ ] Salvar sem mudar **não altera** o valor (testado).
- [ ] O **smoke do recurso** cobre o campo (ou o recurso ganhou um smoke).
- [ ] Nomes/formatos batem entre API e tela (ex.: hora `HH:MM` × `HH:MM:SS`).
- [ ] `VERIFY_INTEGRATION=1 pnpm verify` verde local, se mexeu em contrato.

## Backlog (próximos passos)

1. **Smoke da assinatura** (criar plano, cobrar, renovar) — o fluxo de dinheiro mais novo.
2. **Smoke de horários da loja** (abrir/fechar por dia) — outro lugar onde campo
   vazio pode apagar configuração.
3. **Checagem automática de contrato**: comparar as chaves que o formulário lê com
   as chaves que a API devolve (um teste que lê os tipos do `web` e bate com o
   OpenAPI), para pegar deriva sem depender de alguém lembrar do smoke.
4. **Job de integração dedicado** no CI (hoje a integração roda junto do `verify`;
   um job separado deixa o retorno mais claro quando falha um smoke).
