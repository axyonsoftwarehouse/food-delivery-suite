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

## Opção A — implementada (07/10/2026)

O job `Verificação` do `.github/workflows/ci.yml` passou a rodar o verify
**integrado** (`VERIFY_INTEGRATION=1`): banco + API efêmeros, seed demonstrativo
e os smokes de carrinho, exceções, contas e cobertura por CEP. O job gera
`platform/.env` e `platform/tools/.env` com senhas descartáveis (secrets
opcionais `CI_DB_PASSWORD`, `CI_DB_ROOT_PASSWORD`, `CI_DEMO_PASSWORD`; sem eles,
valores só de CI). O tempo-limite subiu de 30 para 45 min.

### Proteção da `main` — bloqueada pelo plano

A intenção de exigir PR + CI verde antes do merge **não pôde ser aplicada**: a
organização `axyonsoftwarehouse` está no plano **free** e o repositório é privado,
então *branch protection* e *rulesets* retornam `HTTP 403` ("Upgrade to GitHub
Pro or make this repository public"). Não é configuração errada — é recurso pago.

Quando houver GitHub Pro (ou o repositório virar público), habilitar:

```bash
gh api -X PUT repos/axyonsoftwarehouse/food-delivery-suite/branches/main/protection \
  -H "Accept: application/vnd.github+json" \
  -f 'required_status_checks[strict]=true' \
  -f 'required_status_checks[contexts][]=Verificação (Java, tipos, build, app da cozinha)' \
  -F 'enforce_admins=false' \
  -f 'required_pull_request_reviews[required_approving_review_count]=1' \
  -F 'restrictions=' \
  -F 'allow_force_pushes=false' \
  -F 'allow_deletions=false'
```

Enquanto isso, o gate é **social + visível**: o CI roda em todo PR e no push da
`main`, há um `pull_request_template.md` com o checklist, e a equipe combina
merge só por PR com o check verde.

## Medição na VPS (07/10/2026)

Leitura direta no host (`free`, `df`, `docker stats`, `nproc`), com staging e
monitoração no ar.

| Recurso | Total | Em uso | Disponível |
| --- | --- | --- | --- |
| CPU | 2 vCPU | ~3–4% em repouso | — |
| RAM | 3,7 GiB | 1,3 GiB | **2,4 GiB** |
| Swap | 2,0 GiB | 0,22 GiB | 1,8 GiB |
| Disco `/` | 38 GB | 16 GB (43%) | **21 GB** |

Em repouso, por contêiner: `api` 301 MiB, `web` 64 MiB, `db` 37 MiB, `caddy`
18 MiB (staging ≈ 420 MiB no total) e a monitoração (Prometheus + Alertmanager +
Blackbox) ≈ 97 MiB. O Docker ocupa 9,7 GB em imagens (**7,2 GB recuperáveis**) e
7,1 GB de build cache (**5,1 GB recuperáveis**).

### Veredito

**Dá, com folga no disco e atenção na RAM.** Um segundo stack de QA fica em
~420–500 MiB em repouso — cabe nos 2,4 GiB livres. O pico é o **build** de cada
deploy (`mvn package` + `next build`), que já roda com sucesso neste mesmo host
para o staging; além disso o `deploy.sh` serializa as publicações com `flock`,
então staging e QA não compilam ao mesmo tempo.

Pré-requisitos para a Opção B:

1. **Não subir um segundo Caddy** nas portas 80/443. Reaproveitar o Caddy do
   staging numa **rede externa compartilhada** e adicionar um site block
   `qa.{$FOODIE_DOMAIN} → foodie-qa-web:3001`. Hoje o Caddy está em
   `foodie-staging_default`; seria preciso uma rede comum aos dois projetos.
2. **Limitar recursos** do QA (ex.: `mem_limit` e `-Xmx` do Java ≈ 512m) para o
   pior caso não empurrar para swap.
3. **Limpar o Docker** com regularidade (hoje há ~12 GB recuperáveis entre
   imagens e cache; sem isso o disco some com o tempo).
4. **Parametrizar** `release.ps1`/`deploy.sh`, hoje amarrados ao nome fixo
   `foodie-staging`, além de separar `.env`, segredos e banco.

Só valeria considerar **aumentar a RAM da VPS** (para 8 GB) se o QA for receber
carga real ou rodar testes de carga — em repouso, não é necessário.

## Perguntas em aberto (para quando for decidir)

1. Os smokes de integração devem ser **obrigatórios** em todo PR ou só na `main`?
2. O aceite antes de publicar pode ser feito no próprio staging (hoje) ou precisa
   mesmo de um ambiente isolado?
3. Se o QA for para frente, aceitar o aperto de RAM (~0,5 GiB a mais em repouso)
   ou já subir a VPS para 8 GB? (medição acima: 2,4 GiB livres hoje)
