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

## Limites antes da produção pública

Esta é uma base de homologação, não autorização para exposição comercial. Permanecem abertos os requisitos de proteção contra abuso e recuperação de conta, pagamentos, estados excepcionais de pedido, paginação, observabilidade e plano de backup/restauração descritos em `../../docs/AVALIACAO_E_PLANO_DE_EVOLUCAO.md`.

## Desativação do Foodie legado

Não execute `docker compose down --volumes` contra a composição antiga antes de a nova publicar e passar a validação. Na janela aprovada, capture um backup final e o inventário dos recursos legados, pare a composição `foodie`, valide o domínio da nova plataforma e só depois remova volumes, imagens e arquivos antigos. A remoção requer inspeção da VPS para confirmar os nomes e evitar atingir outros projetos.
