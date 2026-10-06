# Runbook da VPS — Foodie

Documento operacional. Última atualização: **30/09/2026 02:45 UTC**
(29/09 23:45 no horário de Brasília) — verificado por SSH na VPS.
Público: quem opera a VPS (hoje, Werner).
Objetivo: permitir subir, atualizar, diagnosticar e recuperar a plataforma
na VPS **sem depender de IA** para cada passo.

> A VPS usa **UTC**. Se um horário deste documento não bater com o seu relógio,
> some 3 horas (Brasília = UTC−3).

> **Convenção.** Todo fato abaixo foi lido da própria VPS em 30/09/2026, exceto
> o que estiver marcado como **`(a confirmar)`**. Não preencher lacuna por dedução.

## 0. Estado verificado da VPS (30/09/2026)

| Item | Valor |
| --- | --- |
| Código implantado | **`e226cb2`** — deploy feito pelo `release.ps1` em 30/09/2026 03:46 UTC |
| Schema (`/ready`) | **`053`** |
| `main` local | `e226cb2` — **VPS e `main` alinhados** |
| Registro de deploy | `/home/deploy/foodie-platform/.deployed` (sha, sha256, schema, data, release anterior) |
| Web público | `https://staging.2.29.42.104.sslip.io` → HTTP 200 |
| API | `https://api.staging.2.29.42.104.sslip.io/ready` → `{"status":"ready","schemaVersion":"053"}` |
| Contêineres | api `healthy`, db `healthy`, web e caddy `Up`, migrate `Exited (0)` |
| Reinícios | `0` em todos os contêineres do `foodie-staging` |
| Disco | `16G` de `38G` (**43%**), `21G` livres — era 79% antes da limpeza de 30/09 |
| Memória | `3.7Gi` no total, `~2.4Gi` disponíveis, swap `2.0Gi` |
| Firewall | `ufw` ativo: apenas 22, 80, 443 |
| SSH root | **desabilitado** (`PermitRootLogin no`, `PasswordAuthentication no`) |

> **A VPS está alinhada com o `main`.** Publicar agora é um comando só, no seu
> PC: `.\platform\deploy\release.ps1` (seção 4).
>
> **Como conferir o que está no ar:** `cat /home/deploy/foodie-platform/.deployed`.
> Para uma verificação independente (sem confiar no registro), compare o hash de
> blob de um arquivo — foi assim que identificamos que a VPS rodava `bf8a6a3`:
>
> ```bash
> f=/home/deploy/foodie-platform/apps/web/app/painel/layout.tsx
> { printf 'blob %s\0' "$(stat -c%s "$f")"; cat "$f"; } | sha1sum
> # compare com: git rev-parse HEAD:platform/apps/web/app/painel/layout.tsx
> ```

## 1. Informações da VPS

| Item | Valor |
| --- | --- |
| Provedor | Hetzner (KVM) — `sys_vendor=Hetzner` |
| Instance ID | `165969506` |
| Nome do host | `production-01` |
| Região / zona | `eu-central` / `hel1-dc2` — o console da Hetzner rotula **"Helsinki DC Park 1"** |
| IPv4 | `2.29.42.104` |
| IPv6 | `2a01:4f9:c013:5baf::1/64` |
| Sistema | Ubuntu 24.04, kernel `6.8.0-138-generic` |
| Docker / Compose | `29.1.3` / `2.40.3` |
| Plano | **`(a confirmar)`** — recursos (4 GB / 40 GB) compatíveis com CX23; leia o plano no console da Hetzner |
| Usuário de operação | `deploy` (com `sudo` sem senha) |
| Chave SSH (no seu PC) | `C:\Users\werne\.ssh\foodie_vps` |
| **Pasta da plataforma** | `/home/deploy/foodie-platform` (= o conteúdo de `platform/` do repositório) |
| **Pasta de deploy** | `/home/deploy/foodie-platform/deploy` |
| Pasta de monitoramento | `/home/deploy/foodie-platform/monitoring` |
| Releases preparados | `/home/deploy/releases/<commit>/` |
| Projeto Compose (staging) | `foodie-staging` |
| Domínio (web) | `staging.2.29.42.104.sslip.io` |
| API | `api.staging.2.29.42.104.sslip.io` |
| Portas 80/443 | ocupadas pelo **Caddy do `foodie-staging`** (via `docker-proxy`) |

> **Atenção ao caminho.** Não existe `/home/deploy/foodie-platform/platform/`.
> O que foi implantado na VPS é o **conteúdo da pasta `platform/`** do
> repositório, então o deploy fica em `.../foodie-platform/deploy`. Isso importa
> para todo comando deste runbook.

O domínio `sslip.io` é **temporário de homologação**.

## 2. Como logar na VPS

**No seu PC (PowerShell), não na VPS.**

```powershell
ssh -i "$env:USERPROFILE\.ssh\foodie_vps" deploy@2.29.42.104
```

Depois de logado, o prompt muda para algo como `deploy@production-01:~$`.

Se der `Permission denied`:

- A chave existe? `Test-Path "$env:USERPROFILE\.ssh\foodie_vps"`
- O Windows às vezes bloqueia a permissão do arquivo — ajuste com `icacls`.

**Nunca logue como root** — e você não conseguiria: o SSH do root está
desabilitado (seção 11). Use `deploy` e, quando precisar, `sudo <comando>`
(o `sudo` do `deploy` é sem senha).

## 3. Verificar a saúde da plataforma

```bash
curl -fsS https://api.staging.2.29.42.104.sslip.io/ready
# {"status":"ready","schemaVersion":"053"}
```

Ou, dentro da VPS, direto no contêiner:

```bash
cd /home/deploy/foodie-platform/deploy
docker compose exec -T api curl -fsS http://localhost:4001/ready
```

Para saber **qual código está no ar** (gravado pelo `deploy.sh` a cada release):

```bash
cat /home/deploy/foodie-platform/.deployed
```

**Como saber se o `schemaVersion` está certo** — não decore o número. Ele é a
última migration aplicada (lida de `flyway_schema_history`) e deve bater com a
**maior** `V0xx__*.sql` presente em
`/home/deploy/foodie-platform/apps/api-java/src/main/resources/db/migration`.
Referência:

| Commit | Maior migration | `schemaVersion` |
| --- | --- | --- |
| `88efbe8` | `V048__business_operations.sql` | `048` |
| `75dfe8c` | `V053__retire_restaurant_wallet.sql` | `053` |
| `bf8a6a3` (**na VPS hoje**) | `V053__retire_restaurant_wallet.sql` | `053` |
| `aafb220` (`main` local) | `V053__retire_restaurant_wallet.sql` | `053` |

**Duas coisas que parecem erro e não são:**

- `foodie-staging-migrate-1` aparece como **`Exited (0)`** — é um contêiner de
  execução única (`restart: "no"`). Saiu com sucesso; o `docker ps` normal nem
  o mostra. Só é problema se o código de saída não for `0`.
- `migrate` com `health=unhealthy` no `docker inspect` — contêiner parado não
  tem healthcheck válido.

## 4. Atualizar a plataforma

> **O código na VPS não é um clone do Git.** Não há `.git` em lugar nenhum de
> `/home/deploy/foodie-platform`. Atualizar = enviar um pacote novo. Por isso o
> `update.sh` **não** faz `git pull` (ver seção 10).

### Método recomendado — um comando

No seu PC:

```powershell
cd C:\Users\werne\WebstormProjects\food-delivery-suite\platform
.\deploy\release.ps1                 # publica o commit HEAD
.\deploy\release.ps1 -PackageOnly    # só gera o pacote e mostra os hashes
.\deploy\release.ps1 -Sha bf8a6a3    # republica um commit anterior
```

O `release.ps1` empacota o commit (`git archive`), envia e chama o `deploy.sh`
na VPS. O `deploy.sh` então: **faz backup do banco → guarda o código atual →
aplica → reconstrói → espera `/ready` → confere o `schemaVersion` contra as
migrations do pacote → grava `.deployed`** — e **reverte o código sozinho** se
qualquer etapa falhar.

Ele **aborta se houver arquivos versionados modificados e não commitados**,
porque o pacote vem do commit: o que está só na pasta de trabalho não seria
publicado. Confira depois com `cat /home/deploy/foodie-platform/.deployed`.

Os métodos manuais abaixo continuam válidos como plano B.

### Passo 0 — sempre: backup

```bash
cd /home/deploy/foodie-platform/deploy
bash backup.sh
```

### Método A — pacote completo (o que foi usado em 28/09)

No seu PC, na raiz do repositório:

```powershell
# O -c core.autocrlf=false é obrigatório: com autocrlf=true (padrão no Windows)
# o git archive grava CRLF, e os .sh quebram no Linux.
git -c core.autocrlf=false -c core.eol=lf archive --format=tar `
    -o platform-release-$(git rev-parse --short HEAD).tar HEAD:platform
scp -i "$env:USERPROFILE\.ssh\foodie_vps" `
    platform-release-*.tar `
    deploy@2.29.42.104:/home/deploy/
```

> **Use `git archive`, não `tar` da pasta de trabalho.** O `git archive` gera
> exatamente o layout implantado (o conteúdo de `platform/` na raiz do pacote),
> respeita os fins de linha e **deixa de fora** `node_modules`, `.next` e o
> `.env` local. Um `tar` da pasta levaria tudo isso para a VPS — inclusive
> segredos. É por isso que o `.env` da VPS sobreviveu intacto desde 25/09.
>
> ⚠️ **`core.autocrlf=true` (padrão no Windows) quebra este passo.** Com ele, o
> `git archive` grava CRLF nos arquivos de texto, e no Linux
> `set -euo pipefail\r` morre com `invalid option name` — os `.sh` deixam de
> funcionar. Por isso o comando acima força `core.autocrlf=false`. O `deploy.sh`
> confere isso e **recusa pacotes com CRLF** antes de aplicar qualquer coisa.

Na VPS:

```bash
cd /home/deploy/foodie-platform
tar -xf /home/deploy/platform-release-XXXXXXXX.tar   # sobrescreve o código
cd deploy
docker compose up -d --build
docker compose ps
curl -fsS http://localhost:4001/ready
```

> Duas limitações deste método:
>
> 1. **Não é atômico** e não tem rollback automático. Tenha o pacote anterior
>    à mão (é para isso que serve `/home/deploy/releases/`).
> 2. **Ele não remove arquivos apagados no repositório.** Se o release excluiu
>    um arquivo, ele continua na VPS. Compare `git diff --stat` antes e apague
>    à mão, ou use a Opção 2 da seção 10.

### Método B — deploy cirúrgico de arquivos (usado em 29/09)

Foi assim que a VPS recebeu `bf8a6a3`: só os 3 arquivos alterados foram
copiados, guardando os antigos antes.

```bash
# na VPS, antes de copiar
mkdir -p /home/deploy/releases/<sha>/backup /home/deploy/releases/<sha>/incoming
cp <arquivos que serão sobrescritos> /home/deploy/releases/<sha>/backup/
# ... enviar os novos para incoming/ e copiar por cima ...
cd /home/deploy/foodie-platform/deploy
docker compose up -d --build
```

É rápido, mas **não é atômico** e depende de você acertar a lista de arquivos.
Compare com o `git diff --stat` do commit antes de aplicar.

### Método C — git (depois da seção 10)

```bash
cd /home/deploy/foodie-platform
git pull --ff-only
cd platform/deploy
bash update.sh
```

### Depois de qualquer método

```bash
cd /home/deploy/foodie-platform/deploy
docker compose ps
curl -fsS https://api.staging.2.29.42.104.sslip.io/ready
curl -fsS -o /dev/null -w '%{http_code}\n' https://staging.2.29.42.104.sslip.io
```

### Aplicar o `aafb220` que já está na VPS

O pacote está em `/home/deploy/releases/aafb220/platform-release.tar` e é
byte a byte igual ao `platform-release-aafb220.tar` gerado no seu PC
(md5 `d58a3dd2d7bfa93c8e78bab9bc3196d5`).

```bash
cd /home/deploy/foodie-platform/deploy
bash backup.sh                                    # 1. backup do banco
cd /home/deploy/foodie-platform
tar -xf /home/deploy/releases/aafb220/platform-release.tar   # 2. aplicar
cd deploy
docker compose up -d --build                   # 3. reconstruir
docker compose ps && curl -fsS http://localhost:4001/ready   # 4. conferir
```

O `backup-source.tar` na mesma pasta guarda os arquivos que serão
sobrescritos — é o seu rollback manual.

## 5. Backup e restauração

### Backup manual

```bash
cd /home/deploy/foodie-platform/deploy
bash backup.sh
# Gera deploy/backups/foodie_platform-YYYYMMDD-HHMMSS.sql (mariadb-dump)
```

Para gravar em outro diretório: `BACKUP_DIR=/caminho bash backup.sh`.

**Estado atual dos backups (01/10/2026):** os seis dumps da VPS têm cópia no
PC, em `platform/deploy/backups/` do checkout principal (ignorado pelo git), com
md5 idêntico ao da VPS:

| Arquivo | md5 |
| --- | --- |
| `foodie_platform-20260927-224841.sql` | `40bcca68...` |
| `foodie_platform-20260928-181435.sql` | `2af1ae45...` |
| `foodie_platform-20260930-025817.sql` | `207ea697...` |
| `foodie_platform-20260930-034122.sql` | `2eaf2343...` |
| `foodie_platform-20260930-034312.sql` | `a1560786...` |
| `foodie_platform-20261001-185611.sql` | `998ee962...` (deploy do E48) |

**Regra de ouro:** copie o backup para **fora da VPS** depois de cada deploy
(o `deploy.sh` gera um novo a cada publicação). O PC é a única cópia externa:
backup externo de verdade (outro local/nuvem) e ensaio de restauração seguem na
lista de prontidão de publicação.

```powershell
# No seu PC, na raiz do repositório
scp -i "$env:USERPROFILE\.ssh\foodie_vps" `
    deploy@2.29.42.104:/home/deploy/foodie-platform/deploy/backups/*.sql `
    .\platform\deploy\backups\
```

### Restauração

```bash
cd /home/deploy/foodie-platform/deploy
bash restore.sh backups/foodie_platform-20260928-181435.sql
curl -fsS http://localhost:4001/ready
```

Faça um **ensaio de restauração** em um banco separado antes de precisar dele
de verdade — está na lista de prontidão de publicação.

## 6. Diagnóstico rápido

```bash
# 1. O que está rodando (e o que já rodou)
docker ps -a

# 2. Visão por projeto Compose
docker compose ls -a

# 3. Algum contêiner unhealthy?
docker ps --filter health=unhealthy

# 4. Logs (últimas 100 linhas)
cd /home/deploy/foodie-platform/deploy
docker compose logs --tail=100 api
docker compose logs --tail=100 web
docker compose logs --tail=100 db
docker compose logs --tail=100 migrate

# 5. Espaço em disco
df -h
sudo du -sh /var/lib/containerd /var/lib/docker /var/log

# 6. Memória
free -h

# 7. Endpoints
curl -fsS https://api.staging.2.29.42.104.sslip.io/ready
curl -fsS -o /dev/null -w '%{http_code}\n' https://staging.2.29.42.104.sslip.io
```

### Espaço em disco

**Situação em 30/09/2026:** a limpeza levou o disco de **29 GB (79%)** para
**16 GB (43%)** — 13 GB recuperados, 21 GB livres. O que rendeu:

| Ação | Ganho |
| --- | --- |
| `docker builder prune -f` (cache de build) | ~11,8 GB |
| Imagens de staging com o nome antigo do projeto | ~2,4 GB |
| Imagens do scaffold `production` (`postgres`, `redis`, `pgbouncer`) | ~0,5 GB |
| Imagem `foodie-staging-web:pre-bf8a6a3` + dangling | ~1 GB |

**O que sobrou é intencional:** as imagens `:pre-e226cb2` (rollback do último
deploy, 2,6 GB) e as bases em uso (`mariadb`, `node`, `prom/*`). Não há mais
nada grande e descartável. **Cada deploy novo volta a custar ~2–3 GB** — quando
o disco passar de ~70%, repita:

```bash
# 1. ver o que dá para recuperar
docker system df

# 2. cache de build (nunca é usado em runtime) — o maior ganho
docker builder prune -f

# 3. imagens de rollback antigas: mantenha SÓ a última (a do deploy atual)
docker images --format '{{.Repository}}:{{.Tag}}' | grep ':pre-' | sort
docker image rm foodie-staging-web:pre-<sha-antigo>

# 4. confirme que a imagem não é usada por nenhum contêiner antes de remover
docker ps -a --format '{{.Image}}' | sort -u
```

> ⚠️ **Nunca rode `docker system prune -a` nem nada com `--volumes` aqui.** O
> legado já foi retirado em 25/09 e o scaffold em 30/09 (seção 8): não há mais
> resíduo de outros projetos. Os volumes existentes são os do `foodie-staging`
> e do `foodie-monitoring`, e todos contêm dados em uso.

> **Não existe legado na VPS para remover.** O pacote legado foi retirado em
> 25/09/2026 (`down -v` nos contêineres, volumes e rede, mais os diretórios
> `/opt/food-delivery-suite` e `/opt/foodie`) — ver `platform/deploy/README.md`.
> Não há contêiner, volume, rede ou diretório do legado sobrando. Os únicos
> itens fora do projeto atual que existiram — o scaffold `production` e as imagens
> com o nome antigo do projeto — foram removidos em 30/09.

**Sinais de alarme:**

- Disco acima de 90%. **O consumo mora em `/var/lib/containerd`** (22 GB dos
  26 GB usados em 30/09), não em `/var/lib/docker` (2,6 GB). Limpe com
  `docker image prune -a` e `docker builder prune` — confira antes o que sai
  (`docker images`, `docker system df`). Atenção: `docker builder prune
  --dry-run` **não** é aceito pela versão instalada.
- Contêiner `Restarting` em loop → veja os logs dele.
- `/ready` sem resposta → `docker compose logs api` e `migrate`.
- `df -h` em `/` acima de 90% com contêineres saudáveis → quase sempre lixo de
  imagem/build, não dado.

**Baseline (30/09/2026, pós-deploy)** para comparar depois: disco 79%, memória
1,3 Gi em uso de 3,7 Gi, api `healthy`, 0 reinícios.

## 7. O que NÃO fazer

- ❌ `docker compose down -v` sem backup — o `-v` apaga volumes, ou seja, **o banco**.
- ❌ `docker system prune -a` sem conferir o que será removido.
- ❌ Remover qualquer recurso do projeto Compose `production` (ver seção 8).
- ❌ Editar arquivos à mão em `/home/deploy/foodie-platform/` para "testar" — o
  próximo deploy sobrescreve e você perde o rastro.
- ❌ **Assumir que o que está em `/home/deploy/releases/<sha>/` foi aplicado.**
  Em 30/09, `releases/aafb220/` estava lá e a VPS rodava `bf8a6a3`.
- ❌ Rodar como `root` o que pode rodar como `deploy`.
- ❌ Deixar senha em script versionado.
- ❌ Rodar `docker system prune` no meio de um deploy.

## 8. Scaffold `production` — investigado e removido em 30/09/2026

**Não era projeto de terceiros nem tinha dados.** Era um andaime criado pelo
próprio provisionamento do servidor, e foi removido depois de confirmado vazio.

**Quem criou:** `/root/bootstrap-production.sh` (15/09 01:11) — o **mesmo script
que criou o usuário `deploy`, endureceu o SSH (`PermitRootLogin no`,
`AllowUsers deploy`) e configurou o `ufw`**. A última seção dele diz
"Create an isolated application stack. Database and cache have no host ports."
e grava `/opt/production/{.env,compose.yaml}` com `POSTGRES_DB=platform`.

**O que era:** `production-postgres-1` (`postgres:16-alpine`),
`production-redis-1` (`redis:7-alpine`) e `production-pgbouncer-1`, em rede
`internal: true`, sem nenhuma porta publicada no host.

**Evidência de que nunca foi usado** (colhida antes de remover):

- banco `platform` com **0 tabelas** e 7519 kB — o tamanho exato de um banco
  vazio;
- Redis com **0 chaves** (`DBSIZE` = 0);
- **nenhuma conexão** em 5432/6379 e logs parados em 15/09 01:32 — só a subida;
- consumo somado dos três contêineres: **~19 MiB de RAM**.

**O que foi feito, em ordem:**

```bash
# 1. backup do config (o script de provisionamento continua em /root)
sudo tar -czf /home/deploy/production-scaffold-20260930.tar.gz -C /opt production

# 2. derrubar o projeto (contêineres e rede), depois os volumes
cd /opt/production && docker compose down
docker volume rm production_postgres_data production_redis_data

# 3. remover o diretório e as imagens que só ele usava
sudo rm -rf /opt/production
docker image rm postgres:16-alpine redis:7-alpine edoburu/pgbouncer:v1.25.2-p0
```

Resultado: `/opt` ficou só com `containerd`, o projeto saiu do
`docker compose ls`, e a remoção rendeu ~0,5 GB (o grosso dos 13 GB veio do
cache de build — seção 6).

**Se precisar recriar algum dia**, tudo está preservado:

- o script de provisionamento: `/root/bootstrap-production.sh` — contém a seção
  que recria o stack; cópia idêntica versionada em `platform/deploy/vps/` (01/10);
- o config usado: `/home/deploy/production-scaffold-20260930.tar.gz` (697 bytes,
  inclui o `.env` — **trate como segredo**).

> ⚠️ **Uma versão anterior deste runbook mandava `docker rm`, `docker volume rm`
> e `sudo rm -rf /opt/production /opt/containers` "porque era legado".** Estava
> errada: o projeto não era legado, e o comando era destrutivo e sem
> verificação. A remoção de 30/09 só aconteceu **depois** de comprovar que o
> banco estava vazio — a ordem acima (conferir → copiar o config → derrubar →
> remover) é o procedimento correto para qualquer projeto desconhecido.

### Como identificar um projeto Compose desconhecido

```bash
docker compose ls -a
docker inspect <container> \
  --format '{{.Name}} | projeto={{index .Config.Labels "com.docker.compose.project"}} | dir={{index .Config.Labels "com.docker.compose.project.working_dir"}}'
```

O rótulo `working_dir` diz de qual pasta o stack subiu — e o nome da pasta
costuma dizer de quem é.

## 9. Perfil público (Caddy)

Confirmado: o Caddy do `foodie-staging` é **quem ocupa as portas 80/443**
(`docker-proxy`), publicado desde 25/09/2026. O legado não disputa mais as
portas.

Desativar:

```bash
cd /home/deploy/foodie-platform/deploy
docker compose --profile public down
```

Reativar:

```bash
docker compose --profile public up -d
```

**Antes de ativar, confira quem já ocupa 80/443:**

```bash
sudo ss -lntp | grep -E ':80|:443'
```

> **Se você mudar o `Caddyfile`, recrie o contêiner:**
> `docker compose --profile public up -d --force-recreate caddy`.
> O `Caddyfile` é montado como **bind de arquivo**; o `rsync` do deploy troca
> arquivos por *rename*, e um bind de arquivo pode continuar apontando para o
> inode antigo depois da troca. O `up -d --build` não detecta mudança de
> conteúdo no `Caddyfile` (ele não faz parte da config do serviço), então a
> recriação explícita é a forma segura.

Se aparecer outro proxy, **não** ative o perfil `public`: publique a rota no
proxy existente e conecte-o à rede `foodie-staging_default`
(`platform/deploy/README.md`).

## 10. Migrar o deploy para git (recomendado)

**Situação:** `/home/deploy/foodie-platform` é uma **cópia** — não existe `.git`
nem na raiz nem em `deploy/`. O deploy já é automatizado e verificável pelo
`release.ps1` + `deploy.sh` (seção 4), com rollback de código e registro em
`.deployed`. O que o clone ainda acrescenta é ter o **histórico do código** na
VPS (`git log`, `git diff`, `git blame`) em vez de pacotes soltos.

**Detalhe que engana:** `update.sh` testa `[ -d ../.git ]`, que a partir de
`deploy/` significa `/home/deploy/foodie-platform/.git` — ausente hoje. Por
isso o script sempre cai no aviso "sem repositório git em platform/" e apenas
reconstrói o que já está lá.

### Opção 1 — clonar o repositório (layout passa a ter `platform/`)

```bash
cd /home/deploy
mv foodie-platform foodie-platform.bak
git clone https://github.com/axyonsoftwarehouse/food-delivery-suite foodie-platform
cd foodie-platform/platform/deploy
cp /home/deploy/foodie-platform.bak/deploy/.env .env   # o .env não vai no git
cp -r /home/deploy/foodie-platform.bak/deploy/backups ./backups
docker compose up -d --build
```

Dois pontos que tornam isso seguro e que você deve conferir:

1. `platform/deploy/docker-compose.yml` começa com `name: foodie-staging` — o
   nome do projeto é **fixo**, então volumes e contêineres são reaproveitados
   mesmo mudando a pasta. Sem isso, você ganharia um banco novo e vazio.
2. O stack de monitoramento tem o mesmo cuidado? Confira antes de mover:
   `head -3 /home/deploy/foodie-platform/monitoring/docker-compose.yml`

Depois, ajuste o `update.sh` para apontar um nível acima:

```bash
if [ -d ../../.git ]; then
  git -C ../.. pull --ff-only
else
  echo "sem repositório git; envie o código manualmente"
fi
```

A partir daí, atualizar é:

```bash
cd /home/deploy/foodie-platform
git pull --ff-only
cd platform/deploy
bash update.sh
```

### Opção 2 — manter o layout atual e versionar o script

Clonar o repositório em outro lugar (ex.: `/home/deploy/foodie-src`), gerar o
pacote com `git archive` a partir de `platform/` e aplicar em
`/home/deploy/foodie-platform` com backup e rollback. **Esse script precisa
entrar no repositório** — hoje o que existe em `/home/deploy/releases/` é um
procedimento ad hoc, sem código versionado que o reproduza.

## 11. Segurança

Verificado em 30/09/2026 — a postura está boa:

| Item | Estado |
| --- | --- |
| `PermitRootLogin` | `no` |
| `PasswordAuthentication` | `no` |
| `PubkeyAuthentication` | `yes` |
| `KbdInteractiveAuthentication` | `no` |
| `ufw` | ativo, apenas `22`, `80`, `443` (v4 e v6) |
| `sudo` do `deploy` | sem senha |

- **Login root por SSH está desabilitado.** O root tem senha definida
  (`passwd -S root` → `P`, alterada em 16/09/2026), usável apenas pelo console
  da Hetzner. Rotacionar é higiene opcional, não urgente:
  `sudo passwd root` (ou `sudo passwd -l root` se quiser travar de vez).
- **Não há regra de firewall redundante** para limpar — as três regras abertas
  são as necessárias. Uma versão anterior deste runbook previa "limpeza de
  regras redundantes": não se aplica.
- `(a confirmar)` — se alguma senha de root já circulou em texto plano. Não há
  registro disso no repositório; se houve, rotacione por precaução.

## 12. Onde pedir ajuda

- **Documentação do projeto:** `docs/ESTADO_ATUAL.md`,
  `docs/PLANO_EPICOS.md`
- **Publicar um release:** `platform/deploy/release.ps1` (no PC) e
  `platform/deploy/deploy.sh` (na VPS) — seção 4
- **Deploy em detalhe:** `platform/deploy/README.md`
- **Contexto e regras do projeto:** `.hermes.md`
- **Estado do sistema:** `curl .../ready`, `docker compose ps`,
  `docker compose ls -a`
- **Assistente (IA):** descreva o sintoma, o que já foi tentado e cole a saída
  dos comandos da seção 6.
