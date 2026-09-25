# Homologação da plataforma Foodie

Esta composição publica somente a plataforma independente: MariaDB, migrations (Flyway, aplicadas pelo contêiner Java), API Java, web Next e Caddy. O projeto Docker é nomeado `foodie-staging`, portanto não reutiliza redes, volumes, banco, imagens, domínio nem arquivos do Foodie legado em `../../deploy`.

## Preparação na VPS

1. Copie `.env.example` para `.env` e informe o domínio de homologação e duas senhas exclusivas.
2. Copie o diretório `platform/` para uma pasta nova na VPS; não o coloque dentro da pasta da composição `foodie`.
3. Na primeira validação interna, na pasta `platform/deploy`, execute `docker compose up -d --build`. O Caddy fica desativado por padrão e não ocupa as portas públicas da VPS. Só depois da aprovação do domínio execute `docker compose --profile public up -d`.

## Dados de demonstração

O serviço `migrate` (imagem Java em modo `MIGRATE_ONLY`) aplica as migrations via Flyway e encerra com código zero; a API só sobe depois disso. Para uma homologação que precise dos quatro perfis de demonstração, rode o seed uma única vez, sem gravar a senha em arquivos versionados:

```
DEMO_PASSWORD='uma-senha-forte' docker compose --profile tools run --rm seed
```

## Operação, backup e rollback

- Confirme `GET /ready` (não só `/health`): ele só responde 200 com banco acessível e schema migrado.
- Backup: `./backup.sh` gera um dump em `deploy/backups/` (fora do Git); mantenha uma cópia **fora da VPS**.
- Restauração/rollback: `./restore.sh <arquivo.sql>`, confira `/ready` e, se necessário, republique a revisão anterior (`docker compose up -d --build api web`).
- Faça um ensaio de restauração em ambiente de teste antes de considerar o piloto pronto.
- Quando outra composição já é proprietária das portas 80/443, não inicie o perfil `public`. Publique a rota de homologação no proxy existente e conecte-o à rede `foodie-staging_default`; valide a configuração e faça backup do proxy antes de recarregá-lo. Na VPS atual, essa rota usa um host `sslip.io` temporário e não substitui o domínio do legado.

## Observabilidade e alertas

- A API expõe `/actuator/health`, `/actuator/metrics` e `/actuator/prometheus` (Micrometer) na porta interna 4001. Não publique `/actuator/*` diretamente: colete pela rede interna (ex.: Prometheus/agente no mesmo host).
- Toda resposta traz `X-Request-Id` (aceito do cliente ou gerado) e o log de acesso registra método, rota, status, duração e o ID — útil para correlacionar um pedido com o servidor.
- Alertas sugeridos: `/ready` indisponível, taxa de respostas 5xx acima do limiar e contêineres `db`/`api` fora de `healthy`. Aponte um monitor externo para `/ready` e para `/actuator/prometheus`.
- Metas de carga do piloto: p95 < 800 ms e erros < 1% (`pnpm load`).

## Virada de tráfego (janela controlada)

1. Gere um backup com `./backup.sh`, guarde cópia **fora da VPS** e faça um ensaio de restauração.
2. Confirme `/ready` = `ready` e rode `pnpm load` contra o host de homologação com a carga esperada.
3. Escolha a janela de baixo movimento e capture um backup final do legado antes de mexer no domínio.
4. Aponte o domínio/proxy para a nova pilha (perfil `public` do Caddy ou rota no proxy existente), sem remover os 80/443 do legado.
5. Valide os quatro papéis pelo domínio novo e acompanhe `/ready`, 5xx e latência por alguns minutos.
6. Rollback: se houver problema, reverta o DNS/proxy para o legado e, se o banco novo já tiver recebido dados, restaure o último backup com `./restore.sh`.
7. Só depois de estabilizar remova os recursos do legado (seção seguinte).

## Limites antes da produção pública

Esta é uma base de homologação, não autorização para exposição comercial. Proteção de contas, recuperação, pagamento na entrega, estados excepcionais do pedido, observabilidade e backup/restauração já estão implementados. Antes de expor comercialmente ainda faltam: um provedor de email real (hoje o link de verificação/recuperação sai no log), a revisão de origem/CSRF nos domínios finais e um teste de carga na própria VPS.

## Desativação do Foodie legado

Executada em **2026-09-25**: o projeto legado `deploy` foi derrubado com `down -v` (containers, volumes e rede) e removidos os diretórios `/opt/food-delivery-suite` e a recriação antiga `/opt/foodie`, junto das imagens não usadas e do cache de build. As portas 80/443 ficaram livres, mas o perfil `public` **não** foi iniciado — a plataforma Foodie segue **interna** até a janela de virada de domínio. Antes de reexpor qualquer coisa, capture backup, confirme `/ready` e siga o roteiro de virada acima.
