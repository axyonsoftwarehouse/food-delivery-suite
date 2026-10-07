# Como trabalhar no Foodie

Padrão de trabalho do projeto, a partir de 07/10/2026. Vale para todo mundo que
mexe no repositório.

## Fluxo de PR (a `main` é protegida)

Nada entra na `main` por push direto. Todo trabalho passa por PR:

1. Crie uma branch a partir da `main`, com prefixo pelo tipo:
   `feat/…`, `fix/…`, `docs/…`, `ci/…`, `chore/…`.
2. Rode a verificação local, na pasta `platform/`:
   - `pnpm verify` — testes Java, tipos e build;
   - `VERIFY_INTEGRATION=1 pnpm verify` — o ciclo completo, com banco/API
     efêmeros + seed + smokes (é o mesmo que o CI roda).
3. Abra o PR contra a `main`. O check `Verificação` roda sozinho.
4. O merge exige **CI verde + 1 aprovação**. Force-push e exclusão da branch
   estão bloqueados.

> `enforce_admins=false`: um admin consegue contornar numa emergência — use só
> quando não houver mesmo como esperar.

## Commits

- Conventional Commits em português, no imperativo: `feat(painel): …`,
  `fix(api): …`, `docs(qa): …`, `ci: …`.
- Um assunto por commit. Explique o **porquê** no corpo quando não for óbvio.

## Deploy

- Não é automático. Publica-se pelo `platform/deploy/release.ps1` (no PC) ou pelo
  workflow **Deploy** (botão no GitHub, exige confirmação). O processo tem backup,
  conferência de `/ready` e rollback — ver `platform/deploy/README.md` e
  `docs/RUNBOOK_VPS.md`.
- Só publicar depois do PR mesclado na `main`.

## Segredos

- Nunca commitar `.env*` nem credenciais. Os `.env.example` listam as chaves
  necessárias.
- O repositório é **público**: trate tudo que for commitado como visível a
  qualquer pessoa.

## Documentação

- Estado do projeto: `docs/ESTADO_ATUAL.md`. Operação da VPS: `docs/RUNBOOK_VPS.md`.
- QA/homologação: `docs/AVALIACAO_AMBIENTE_QA.md`.
- Ao mudar comportamento, atualize a documentação no **mesmo PR**.
