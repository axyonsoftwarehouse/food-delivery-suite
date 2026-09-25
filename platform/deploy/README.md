# Homologação da plataforma Foodie

Esta composição publica somente a plataforma independente: MariaDB, migration transitória, API Java, web Next e Caddy. O projeto Docker é nomeado `foodie-staging`, portanto não reutiliza redes, volumes, banco, imagens, domínio nem arquivos do Foodie legado em `../../deploy`.

## Preparação na VPS

1. Copie `.env.example` para `.env` e informe o domínio de homologação e duas senhas exclusivas.
2. Copie o diretório `platform/` para uma pasta nova na VPS; não o coloque dentro da pasta da composição `foodie`.
3. Na primeira validação interna, na pasta `platform/deploy`, execute `docker compose up -d --build`. O Caddy fica desativado por padrão e não ocupa as portas públicas da VPS. Só depois da aprovação do domínio execute `docker compose --profile public up -d`.

## Dados de demonstração

A migration cria somente a estrutura do banco. Para uma homologação que precise dos quatro perfis de demonstração, execute o seed uma única vez com uma senha temporária fornecida somente no ambiente do comando: `DEMO_PASSWORD='uma-senha-forte' docker compose run --rm -e DEMO_PASSWORD migrate pnpm --filter @foodie/api seed`. Não grave `DEMO_PASSWORD` em arquivos versionados nem reutilize essas contas em produção.

Quando outra composição já é proprietária das portas 80/443, não inicie o perfil `public`. Publique a rota de homologação no proxy existente e conecte-o à rede `foodie-staging_default`; valide a configuração e faça backup do proxy antes de recarregá-lo. Na VPS atual, essa rota usa um host `sslip.io` temporário e não substitui o domínio do legado.
4. Aguarde `migrate` terminar com código zero e confira `docker compose ps` e `https://api.<domínio>/health`.
5. Valide manualmente os quatro papéis, execute backup e restauração do volume `foodie_platform_data` em ambiente de teste e só então planeje a troca de domínio.

## Limites antes da produção pública

Esta é uma base de homologação, não autorização para exposição comercial. Permanecem abertos os requisitos de proteção contra abuso e recuperação de conta, pagamentos, estados excepcionais de pedido, paginação, observabilidade e plano de backup/restauração descritos em `../../docs/AVALIACAO_E_PLANO_DE_EVOLUCAO.md`.

## Desativação do Foodie legado

Não execute `docker compose down --volumes` contra a composição antiga antes de a nova publicar e passar a validação. Na janela aprovada, capture um backup final e o inventário dos recursos legados, pare a composição `foodie`, valide o domínio da nova plataforma e só depois remova volumes, imagens e arquivos antigos. A remoção requer inspeção da VPS para confirmar os nomes e evitar atingir outros projetos.
