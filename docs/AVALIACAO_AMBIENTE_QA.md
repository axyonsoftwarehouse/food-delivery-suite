# Avaliação — ambiente de QA

Documento de avaliação. Registrado em **07/10/2026** a pedido do dono do produto.
**Não é decisão nem backlog** — é o diagnóstico e o plano para referência futura.

## Objetivo

Ter um ambiente onde uma mudança possa ser exercitada **antes** de ir para o
staging/homologação, sem trocar o processo provado de publicação.

## Retrato de hoje

| Peça | Situação |
| --- | --- |
| Homologação | **único ambiente persistente** na VPS: projeto Compose `foodie-staging` (MariaDB + API Java + web Next + Caddy nas portas 80/443), domínio `staging.2.29.42.104.sslip.io` |
| Publicação | `platform/deploy/release.ps1` (PC → VPS) ou o workflow `deploy.yml`; aplica migração em ambiente único, com backup e rollback |
| Banco | um só (`foodie_platform`), volume `foodie_platform_data` |
| CI | `.github/workflows/ci.yml` roda `pnpm verify` (Java, tipos, build, app da cozinha) a cada PR e push na `main` |
| Stack efêmero | `platform/docker-compose.test.yml` (`foodie-test`) sobe **só** banco + API, em `127.0.0.1:3308` e `4101` |
| Verificação integrada | `scripts/verify.mjs:60` já faz o ciclo completo **quando `VERIFY_INTEGRATION=1`**: sobe o stack efêmero, roda o seed e os smokes (carrinho, exceções, contas, cobertura por CEP) e derruba tudo ao fim |
| Smokes | `pnpm smoke:cart`, `smoke:exceptions`, `smoke:auth`, `smoke:coverage` |

## O buraco

O CI roda o `verify` **sem** `VERIFY_INTEGRATION=1` (só `VERIFY_ONLINE=1`), então
os smokes de integração **não rodam no GitHub** — apenas localmente, por escolha
de quem roda. Não existe um ambiente separado do staging para exercitar uma
mudança com URL clicável.

## Opções

### A. QA efêmero no CI (recomendado como primeiro passo)

Ligar `VERIFY_INTEGRATION=1` num job do GitHub Actions, com os secrets que o
`verify.mjs` exige (`DB_PASSWORD`, `DB_ROOT_PASSWORD`, `DEMO_PASSWORD`).

- **Prós:** custo de infra zero; exercita banco + seed + os quatro smokes em todo
  PR; usa exatamente o que já existe; nada novo para operar.
- **Contras:** não gera URL para um humano navegar; aumenta o tempo do CI.
- **Requisitos:** cadastrar os três secrets e adicionar o passo/job.

### B. QA persistente na VPS

Um segundo projeto Compose (`foodie-qa`) na mesma VPS, com volume, `.env`,
subdomínio (`qa.<domínio>`) e Caddy próprios.

- **Prós:** URL clicável e isolada; bom para aceite manual antes de publicar.
- **Contras:** consome disco/RAM da VPS; exige **parametrizar** `release.ps1` e
  `deploy.sh` (hoje amarrados ao nome fixo `foodie-staging`, que é o que garante
  que publicar não cria um banco vazio — ver `deploy/README.md` linha 3); precisa
  de DNS/`FOODIE_DOMAIN` próprios e de um conjunto separado de segredos.
- **Atenção:** o portão 80/443 hoje é do Caddy de staging; dois Caddy no mesmo
  host exigem roteamento por subdomínio ou portas diferentes.

### C. Preview por PR (ambientes dinâmicos)

Um ambiente por pull request, criado e destruído automaticamente.

- **Contras:** precisa de DNS dinâmico e roteamento por PR; complexidade e custo
  desproporcionais ao porte atual. **Não recomendado agora.**

## Recomendação

Começar pela **Opção A**: é o menor passo que fecha o buraco de regressão, sem
tocar no processo de deploy provado e sem infra nova. Só avaliar a **Opção B** se
surgir a necessidade real de um humano navegar num ambiente de QA separado do
staging.

## Perguntas em aberto (para quando for decidir)

1. Os smokes de integração devem ser **obrigatórios** em todo PR ou só na `main`?
2. O aceite antes de publicar pode ser feito no próprio staging (hoje) ou precisa
   mesmo de um ambiente isolado?
3. Há orçamento de RAM/disco na VPS para um segundo stack completo? (checar
   `docs/RUNBOOK_VPS.md` §6 — disco estava em 21% em 05/10)
