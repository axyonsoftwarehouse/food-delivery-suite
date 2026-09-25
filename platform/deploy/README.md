# HomologaÃ§Ã£o da plataforma Foodie

Esta composiÃ§Ã£o publica a plataforma independente: MariaDB, migrations (Flyway, aplicadas pelo contÃªiner Java), API Java, web Next e Caddy. O projeto Docker Ã© nomeado `foodie-staging`, portanto nÃ£o reutiliza redes, volumes, banco, imagens, domÃ­nio nem arquivos de outros projetos na VPS.

## EndereÃ§os (homologaÃ§Ã£o)

Com `FOODIE_DOMAIN=staging.2.29.42.104.sslip.io`, o Caddy publica (perfil `public`):

| Host | Destino |
| --- | --- |
| `staging.<domÃ­nio>` | site (todos os papÃ©is; o login define) |
| `api.<domÃ­nio>` | API Java |
| `cliente.<domÃ­nio>`, `restaurante.<domÃ­nio>`, `entregador.<domÃ­nio>`, `admin.<domÃ­nio>` | o mesmo site (endereÃ§os por papel; o login ainda define o papel) |

O `sslip.io` Ã© temporÃ¡rio e serve apenas homologaÃ§Ã£o; para produÃ§Ã£o, troque por um domÃ­nio prÃ³prio apontando o DNS para a VPS.

## PreparaÃ§Ã£o na VPS

1. Copie `.env.example` para `.env` e informe o domÃ­nio de homologaÃ§Ã£o e duas senhas exclusivas.
2. Copie o diretÃ³rio `platform/` para uma pasta nova na VPS; nÃ£o o coloque dentro da pasta da composiÃ§Ã£o `foodie`.
3. Na primeira validaÃ§Ã£o interna, na pasta `platform/deploy`, execute `docker compose up -d --build`. O Caddy fica desativado por padrÃ£o e nÃ£o ocupa as portas pÃºblicas da VPS. SÃ³ depois da aprovaÃ§Ã£o do domÃ­nio execute `docker compose --profile public up -d`.

## Dados de demonstraÃ§Ã£o

O serviÃ§o `migrate` (imagem Java em modo `MIGRATE_ONLY`) aplica as migrations via Flyway e encerra com cÃ³digo zero; a API sÃ³ sobe depois disso. Para uma homologaÃ§Ã£o que precise dos quatro perfis de demonstraÃ§Ã£o, rode o seed uma Ãºnica vez, sem gravar a senha em arquivos versionados:

```
DEMO_PASSWORD='uma-senha-forte' docker compose --profile tools run --rm seed
```

## OperaÃ§Ã£o, backup e rollback

- Confirme `GET /ready` (nÃ£o sÃ³ `/health`): ele sÃ³ responde 200 com banco acessÃ­vel e schema migrado.
- Backup: `./backup.sh` gera um dump em `deploy/backups/` (fora do Git); mantenha uma cÃ³pia **fora da VPS**.
- RestauraÃ§Ã£o/rollback: `./restore.sh <arquivo.sql>`, confira `/ready` e, se necessÃ¡rio, republique a revisÃ£o anterior (`docker compose up -d --build api web`).
- FaÃ§a um ensaio de restauraÃ§Ã£o em ambiente de teste antes de considerar o piloto pronto.
- Quando outra composiÃ§Ã£o jÃ¡ Ã© proprietÃ¡ria das portas 80/443, nÃ£o inicie o perfil `public`. Publique a rota de homologaÃ§Ã£o no proxy existente e conecte-o Ã  rede `foodie-staging_default`; valide a configuraÃ§Ã£o e faÃ§a backup do proxy antes de recarregÃ¡-lo. Na VPS atual, essa rota usa um host `sslip.io` temporÃ¡rio e nÃ£o substitui o domÃ­nio do legado.

## Observabilidade e alertas

- A API expÃµe `/actuator/health`, `/actuator/metrics` e `/actuator/prometheus` (Micrometer) na porta interna 4001. NÃ£o publique `/actuator/*` diretamente: colete pela rede interna (ex.: Prometheus/agente no mesmo host).
- Toda resposta traz `X-Request-Id` (aceito do cliente ou gerado) e o log de acesso registra mÃ©todo, rota, status, duraÃ§Ã£o e o ID â€” Ãºtil para correlacionar um pedido com o servidor.
- Alertas sugeridos: `/ready` indisponÃ­vel, taxa de respostas 5xx acima do limiar e contÃªineres `db`/`api` fora de `healthy`. Aponte um monitor externo para `/ready` e para `/actuator/prometheus`.
- Metas de carga do piloto: p95 < 800 ms e erros < 1% (`pnpm load`).

## Virada de trÃ¡fego (janela controlada)

1. Gere um backup com `./backup.sh`, guarde cÃ³pia **fora da VPS** e faÃ§a um ensaio de restauraÃ§Ã£o.
2. Confirme `/ready` = `ready` e rode `pnpm load` contra o host de homologaÃ§Ã£o com a carga esperada.
3. Escolha a janela de baixo movimento e capture um backup final do legado antes de mexer no domÃ­nio.
4. Aponte o domÃ­nio/proxy para a nova pilha (perfil `public` do Caddy ou rota no proxy existente), sem remover os 80/443 do legado.
5. Valide os quatro papÃ©is pelo domÃ­nio novo e acompanhe `/ready`, 5xx e latÃªncia por alguns minutos.
6. Rollback: se houver problema, reverta o DNS/proxy para o legado e, se o banco novo jÃ¡ tiver recebido dados, restaure o Ãºltimo backup com `./restore.sh`.
7. SÃ³ depois de estabilizar remova os recursos do legado (seÃ§Ã£o seguinte).

## Limites antes da produÃ§Ã£o pÃºblica

Esta Ã© uma base de homologaÃ§Ã£o, nÃ£o autorizaÃ§Ã£o para exposiÃ§Ã£o comercial. ProteÃ§Ã£o de contas, recuperaÃ§Ã£o, pagamento na entrega, estados excepcionais do pedido, observabilidade e backup/restauraÃ§Ã£o jÃ¡ estÃ£o implementados. Antes de expor comercialmente ainda faltam: um provedor de email real (hoje o link de verificaÃ§Ã£o/recuperaÃ§Ã£o sai no log), a revisÃ£o de origem/CSRF nos domÃ­nios finais e um teste de carga na prÃ³pria VPS.

## DesativaÃ§Ã£o do Foodie legado

Executada em **2026-09-25**: o projeto legado `deploy` foi derrubado com `down -v` (containers, volumes e rede) e removidos os diretÃ³rios `/opt/food-delivery-suite` e a recriaÃ§Ã£o antiga `/opt/foodie`, junto das imagens nÃ£o usadas e do cache de build. As portas 80/443 ficaram livres, mas o perfil `public` **nÃ£o** foi iniciado â€” a plataforma Foodie segue **interna** atÃ© a janela de virada de domÃ­nio. Antes de reexpor qualquer coisa, capture backup, confirme `/ready` e siga o roteiro de virada acima.
