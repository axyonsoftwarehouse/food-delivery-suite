# Homologação da plataforma Foodie

Esta composição publica a plataforma independente: MariaDB, migrations (Flyway, aplicadas pelo contêiner Java), API Java, web Next e Caddy. O projeto Docker é nomeado `foodie-staging` (linha 1 do `docker-compose.yml`), portanto não reutiliza redes, volumes, banco, imagens, domínio nem arquivos de outros projetos na VPS. **Esse `name:` fixo é o que garante que mover ou republicar a pasta não crie um banco novo e vazio.**

## Endereços (homologação)

| Host | Destino |
| --- | --- |
| `staging.<domínio>` | home pública Foodie em `/`; login dos papéis em `/entrar` |
| `api.<domínio>` | API Java |
| `cliente.<domínio>`, `restaurante.<domínio>`, `entregador.<domínio>`, `admin.<domínio>` | o mesmo site (endereços por papel; o login ainda define o papel) |

Com `FOODIE_DOMAIN=staging.2.29.42.104.sslip.io`, o perfil `public` (Caddy) está **ativo na VPS desde 25/09/2026** e é ele quem ocupa as portas 80/443. O `sslip.io` é temporário: para produção, aponte um domínio próprio para a VPS.

## Onde o código fica na VPS

O que é implantado é o **conteúdo da pasta `platform/`** deste repositório (via `git archive <sha>:platform`), extraído diretamente em:

```
/home/deploy/foodie-platform/          # = conteúdo de platform/
/home/deploy/foodie-platform/deploy/   # = platform/deploy/
```

Não existe `/home/deploy/foodie-platform/platform/`. E **não há `.git`** nessa árvore: a VPS recebe pacotes, não um clone.

## Publicar um release (um comando)

No seu PC:

```powershell
cd C:\Users\werne\WebstormProjects\food-delivery-suite\platform
.\deploy\release.ps1                 # publica o commit HEAD
.\deploy\release.ps1 -PackageOnly    # só gera o .tar e mostra os hashes
.\deploy\release.ps1 -Sha bf8a6a3    # republica um commit anterior
```

**Ou sem abrir terminal:** dois cliques em `platform/deploy/publicar.bat` (atalho que chama o `release.ps1` e mantém a janela aberta no fim, mostrando o código de saída). Para o ensaio, `platform/deploy/publicar-ensaio.bat` — gera o pacote e confere os hashes sem tocar na VPS.

> **`release.ps1` não abre com duplo clique** — o Windows não tem associação para `.ps1` e manda o arquivo para o editor de texto. Não é defeito do script: clique em `.bat`, ou chame pelo PowerShell (`.\deploy\release.ps1`).

O `release.ps1`:

1. resolve o commit (e **aborta se houver arquivos versionados modificados não commitados** — o pacote vem do commit, não da pasta de trabalho);
2. gera `platform-release-<sha>.tar` com `git archive` (só arquivos versionados: nada de `node_modules`, `.next` ou `.env`);
3. envia o pacote e o `deploy.sh` para `/home/deploy/`;
4. chama o `deploy.sh` na VPS e devolve o resultado.

Na VPS, o `deploy.sh` ([deploy.sh](deploy.sh)) executa, em ordem:

| # | Etapa | Detalhe |
| --- | --- | --- |
| 1 | Valida o pacote | sha256, conteúdo mínimo e `name: foodie-staging` |
| 2 | Marca imagens atuais | `foodie-staging-{api,web,migrate,seed}:pre-<sha>` |
| 3 | **Backup do banco** | `bash backup.sh` → `deploy/backups/` |
| 4 | Snapshot do código | `releases/<sha>/backup-source.tar` |
| 5 | Aplica | `rsync -a --delete`, preservando `deploy/.env` e `deploy/backups/` |
| 6 | Reconstrói | `docker compose up -d --build` (mantém o perfil `public` se o Caddy está no ar) |
| 7 | Verifica | espera `/ready` e compara `schemaVersion` com a maior migration do pacote |
| 8 | Registra | `<tree>/.deployed` com sha, sha256, schema, data e o release anterior |
| 9 | **Reverte** | se 6, 7 ou 8 falharem, restaura o snapshot, reconstrói e sai com erro |

Uma trava (`flock`) impede dois deploys simultâneos, e tudo é registrado em `/home/deploy/deploy-<sha>-<data>.log`.

Depois de publicar, confira:

```bash
cat /home/deploy/foodie-platform/.deployed
curl -fsS https://api.staging.2.29.42.104.sslip.io/ready
```

### Limites do rollback

O rollback automático devolve o **código**. O **banco não volta**: migrations aplicadas pelo Flyway permanecem aplicadas. Se o release novo migrou o schema e você precisa voltar de verdade, restaure o dump:

```bash
cd /home/deploy/foodie-platform/deploy
bash restore.sh backups/foodie_platform-XXXXXXXX-XXXXXX.sql
```

## Publicação manual (plano B)

```powershell
# no PC, na raiz do repositório. O -c core.autocrlf=false é obrigatório: sem ele
# o git archive grava CRLF e os .sh quebram no Linux.
git -c core.autocrlf=false -c core.eol=lf archive --format=tar -o platform-release-<sha>.tar <sha>:platform
scp -i "$env:USERPROFILE\.ssh\foodie_vps" platform-release-<sha>.tar deploy@2.29.42.104:/home/deploy/
```

```bash
# na VPS
bash /home/deploy/deploy.sh /home/deploy/platform-release-<sha>.tar <sha> <sha256>
```

Sem o script, o equivalente manual é: `bash backup.sh` → extrair o tar sobre `/home/deploy/foodie-platform` → `docker compose up -d --build` → conferir `/ready`. Note que `tar -xf` **não remove** arquivos que saíram do repositório; o `rsync --delete` do `deploy.sh` remove.

## Dados de demonstração

O serviço `migrate` (imagem Java em modo `MIGRATE_ONLY`) aplica as migrations via Flyway e encerra com código zero; a API só sobe depois disso. Para uma homologação que precise dos quatro perfis de demonstração, rode o seed uma única vez, sem gravar a senha em arquivos versionados:

```bash
DEMO_PASSWORD='uma-senha-forte' docker compose --profile tools run --rm seed
```

## Operação, backup e restauração

- Confirme `GET /ready` (não só `/health`): ele só responde 200 com banco acessível e schema migrado.
- Backup: `bash backup.sh` gera um dump em `deploy/backups/` (fora do Git); mantenha uma cópia **fora da VPS**.
- Restauração: `bash restore.sh <arquivo.sql>`, confira `/ready`.
- Faça um ensaio de restauração em ambiente de teste antes de considerar o piloto pronto.

> **Chame os scripts com `bash`.** Os `.sh` do repositório estão em modo 644 (sem bit de execução), então `./backup.sh` falha com `Permission denied`. O `deploy.sh` aplica `chmod +x` depois de cada release, mas `bash backup.sh` funciona sempre.

## Observabilidade e alertas

- A API expõe `/actuator/health`, `/actuator/metrics` e `/actuator/prometheus` (Micrometer) na porta interna 4001. Não publique `/actuator/*` diretamente: colete pela rede interna (ex.: Prometheus/agente no mesmo host).
- O stack `foodie-monitoring` (Prometheus + Alertmanager + Blackbox) roda em `../monitoring/` e envia alertas para o Telegram.
- Toda resposta traz `X-Request-Id` (aceito do cliente ou gerado) e o log de acesso registra método, rota, status, duração e o ID — útil para correlacionar um pedido com o servidor.
- Alertas sugeridos: `/ready` indisponível, taxa de respostas 5xx acima do limiar e contêineres `db`/`api` fora de `healthy`.
- Metas de carga do piloto: p95 < 800 ms e erros < 1% (`pnpm load`).

## Limites antes da produção pública

Esta é uma base de homologação, não autorização para exposição comercial. Proteção de contas, recuperação, pagamento na entrega, estados excepcionais do pedido, observabilidade e backup/restauração já estão implementados. Antes de expor comercialmente ainda faltam: um provedor de email real (hoje o link de verificação/recuperação sai no log), a revisão de origem/CSRF nos domínios finais, o ensaio de restauração e um teste de carga na própria VPS.

## Legado StackFood: já removido

Executada em **25/09/2026**: o projeto legado `deploy` foi derrubado com `down -v` (contêineres, volumes e rede) e removidos os diretórios `/opt/food-delivery-suite` e a recriação antiga `/opt/foodie`, junto das imagens não usadas e do cache de build. O código do legado também saiu do repositório na mesma data (commit `04e5686`).

Em **01/10/2026** a limpeza foi completada: a pasta `reference/` e a tag `legacy-stackfood-v9` foram removidas (a tag também do remoto). O pacote comercial, que não tem licença, não está mais no repositório nem acessível por atalho; o código permanece apenas no **histórico** do Git. Registro em `docs/AUDITORIA_LEGADO_2026-10-01.md`.

**Não há legado sobrando na VPS para limpar.** Em 30/09/2026 também foi removido o scaffold `production` (Postgres/pgbouncer/Redis criados pelo provisionamento em 15/09, com banco vazio — ver `docs/RUNBOOK_VPS.md` §8). O que ainda existe além do Foodie é:

| Caminho / recurso | O que é | Pode mexer? |
| --- | --- | --- |
| `/home/deploy/foodie-env-backup` | cópia do `.env` de homologação (18/09) | Não (contém segredos) |
| `/root/bootstrap-production.sh` | script que provisionou e endureceu a VPS | Não apagar — é o único registro do setup |
| `/home/deploy/production-scaffold-20260930.tar.gz` | config do scaffold removido (697 bytes) | Só se não precisar recriar |
| `docker builder prune` | cache de build (~12 GB em 30/09) | Sim |

Espaço em disco: `docs/RUNBOOK_VPS.md` §6 — a limpeza de 30/09 levou o disco de 79% para 43%.
