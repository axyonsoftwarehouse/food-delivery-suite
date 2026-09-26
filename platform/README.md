# Plataforma Foodie independente

Plataforma própria da Foodie — **a única base de código do produto** desde que o pacote comercial StackFood (legado) foi removido em 2026-09-25. Contém uma API TypeScript **transitória** com banco MariaDB próprio e um site Next.js para os papéis de cliente, restaurante, administração e entregador. O backend definitivo é **Java 21/Spring Boot**; a transição está descrita em `docs/PLANO_RECONSTRUCAO_PROPRIA.md`.

## Preparar localmente

1. Copie `platform/.env.example` para `platform/.env` e defina duas senhas fortes diferentes.
2. Copie `platform/apps/api/.env.example` para `platform/apps/api/.env`. Use em `DB_PASSWORD` o valor definido para o usuário `foodie` no passo anterior e escolha uma senha demonstrativa de pelo menos 12 caracteres em `DEMO_PASSWORD`.
3. Na pasta `platform`, execute `docker compose up -d db` (Docker Desktop precisa estar ativo).
4. Execute `pnpm install`.
5. Execute `docker compose --profile java up -d --build` para iniciar a API Java. Na subida, o **Flyway** aplica as migrations pendentes (as migrations agora pertencem ao Java; o `migrate` do TypeScript foi removido).
6. Aguarde `GET /ready` responder `{"status":"ready"}` e execute `pnpm --filter @foodie/api seed` para criar os dados demonstrativos.
7. Execute `pnpm dev` e abra `http://127.0.0.1:3001`. O site usa a API Java em `127.0.0.1:4001` por padrão.

As contas demonstrativas são `admin@demo.local`, `restaurante@demo.local`, `entregador@demo.local` e `cliente@demo.local`, todas com a senha escolhida em `DEMO_PASSWORD`. O seed cria apenas dados de demonstração e não redefine senhas de usuários já cadastrados.

## Fluxo verificável

1. Entre como **admin** para criar uma zona com taxa e pedido mínimo, cadastrar uma faixa de CEP para ela, cadastrar restaurante, categoria, produto e entregador, e vincular o restaurante à zona. Um entregador novo precisa ser aprovado antes de receber pedidos; a tela também permite suspender ou reativar acessos, e a suspensão encerra as sessões em andamento. As migrations `007_restaurant_hours.sql` e `008_restaurant_hours_overnight.sql` guardam horários semanais por restaurante com fuso explícito e aceitam intervalos que terminam depois da meia-noite. O admin edita o horário de qualquer loja e o restaurante edita o próprio; o catálogo marca cada loja como aberta ou fechada no fuso configurado e o checkout recusa pedidos fora do horário. Sem nenhum intervalo cadastrado, o restaurante aparece sempre aberto. A zona `Fortaleza • demonstração` e a `Cozinha Demo` já vêm vinculadas pelo seed. O seed também cadastra a faixa **demonstrativa** `60000000`–`60000999`, que não representa uma promessa de cobertura real.
2. Entre como **cliente**, cadastre um endereço com CEP coberto e peça um produto de restaurante que atende à zona encontrada. A API calcula subtotal, taxa e total; o endereço fica registrado no pedido.
3. Entre como **restaurante** e aceite o pedido, depois marque como pronto.
4. Entre como **admin** e atribua o entregador.
5. Entre como **entregador**, confirme a retirada, **receba o pagamento** (confirme o valor) e conclua a entrega.
6. Entre novamente como cliente ou admin e confira o estado. A API registra cada transição em `order_events`.
7. Imprevistos: recuse um pedido novo (restaurante), cancele antes do aceite (cliente), cancele ou troque/remova o entregador (admin) ou registre falha de entrega (entregador). Cada ação exige um motivo, que aparece no histórico. Um pedido `placed` sem aceite expira em 15 minutos.

O pedido é demonstrativo. A zona é determinada por faixas de CEP sem sobreposição cadastradas pelo admin. O cadastro de endereço exige CEP coberto e o checkout confere novamente a cobertura, inclusive para endereços anteriores à mudança. Endereços antigos sem CEP precisam ser recadastrados. A verificação por faixa **não confirma a existência da rua, número ou CEP**, nem substitui perímetros geográficos ou cálculo de distância. A taxa configurada para a zona é fixa. Também faltam pagamentos, notificações e integração com os aplicativos Flutter. O banco foi desenhado novo e isolado. A importação de dados do projeto anterior exigirá um mapeamento e backup próprios.

## Interface do cliente

O cliente pode criar uma conta pela página inicial. Após o login, a home tem uma apresentação móvel inspirada no [Figma Foodie](https://www.figma.com/design/mlPWwBrTwJ53AHH4zC1gsT/Foodie---Food-Delivery-App-UI-Kit?node-id=727-25421): endereço em destaque, faixa com fotografia, categorias, busca por prato ou restaurante, cardápio e carrinho com vários itens do mesmo restaurante. A home consulta `/catalog/meta` e carrega pratos por zona em `/catalog/search`, com filtro de categoria, limite de 12 por página e cursor para "Ver mais pratos"; `/catalog` permanece disponível para as telas operacionais. O carrinho fica salvo no MariaDB por cliente, pode ser retomado em outro navegador/dispositivo e permite atualização manual para buscar mudanças feitas em outra sessão. Ao entrar pela primeira vez, a página importa itens válidos do antigo carrinho local se a conta ainda não tiver itens no servidor. A API remove produtos indisponíveis e limita quantidade e restaurante. O checkout é transacional: confirma o total mostrado na tela, cria o
pedido e limpa o carrinho juntos; se o preço ou a taxa mudaram, pede atualização antes de continuar.
O carrinho devolve um `version` (hash da composição e preços); o checkout envia `expectedVersion` e
recusa (409) se o carrinho mudou, mesmo com total igual. O checkout aceita `idempotencyKey`: repetir a
mesma operação devolve o mesmo pedido (tabela `order_idempotency`, `V023`). O endpoint transitório
`POST /orders` foi retirado — o único caminho de criação é o carrinho. A listagem `GET /orders`
devolve **todos os pedidos ativos** mais os 100 terminais mais recentes, e `GET /orders/history`
pagina o histórico por cursor (`after`, `status`, `limit`). Cliente, restaurante, admin e entregador podem abrir cada pedido para conferir itens, totais, endereço e histórico de estados; o cliente pode expandir a lista além dos cinco pedidos mais recentes. O restaurante pode pausar ou reativar os próprios produtos na interface. O catálogo é editável por completo: o admin mexe em qualquer restaurante em `/admin/restaurants/{id}/catalog` e o restaurante nas rotas equivalentes `/restaurant/...` — criar, renomear e excluir categorias, criar, editar (nome, preço, descrição, categoria), pausar e excluir produtos. Pausar sempre é permitido; excluir é bloqueado quando o produto já foi usado em pedidos (409) ou quando a categoria ainda tem produtos. O banner usa a imagem original gerada em `apps/web/public/foodie-burger-hero.png` (prompt: fotografia editorial de hambúrguer artesanal, fundo marfim e espaço à esquerda para texto; ferramenta integrada de geração de imagens). Nenhum recurso visual foi copiado do kit. A interface se atualiza sozinha a cada **8 segundos** (pausa quando a aba fica oculta e retoma ao voltar), exibe o estado da conexão com a hora da última sincronização, avisa restaurante e admin quando entra **novo pedido** (sinal sonoro e prefixo no título da aba) e marca pedidos aguardando aceite há mais de 10 minutos como atrasados. Se a API falhar, a tela mantém o último estado e tenta reconectar, voltando ao normal quando a conexão retorna.

## Primeira base Java

`apps/api-java` contém Spring Boot 3.5 e Java 21. Implementa os endpoints usados pelo protótipo: saúde, catálogo, zonas, login/sessão, cadastros administrativos, endereços, pedidos e transições. Acrescenta `POST /auth/signup` para cadastro exclusivo de clientes, `GET /restaurant/products` para o cardápio do restaurante e `PATCH /restaurant/products/{id}/availability` para pausar ou reativar um item. Os horários semanais ficam em `GET`/`POST`/`DELETE /restaurant/hours` (próprio restaurante) e `GET`/`POST`/`DELETE /admin/restaurants/{id}/hours` (admin), com `PATCH /admin/restaurants/{id}/timezone` para o fuso horário. Imprevistos do pedido também são tratados: o restaurante recusa (`reject`), o cliente cancela antes do aceite, o admin cancela em qualquer estado ativo e remove ou troca o entregador (`unassign`/`assign`), o entregador registra falha de entrega (`fail`) e pedidos sem aceite **expiram em 15 minutos**, verificado ao listar ou consultar (sem agendador). Recusa, cancelamento e falha exigem um motivo, gravado em `order_events.reason`. O catálogo público já omite itens indisponíveis. Para contas, expõe `POST /auth/verify-email`, `POST /auth/forgot-password`, `POST /auth/reset-password` e `GET /auth/security`. **Pagamento na entrega**: o cliente escolhe `cash`, `card` ou `pix` no checkout (dinheiro aceita valor de troco); o pagamento nasce `pending` e o entregador ou o admin confirma o recebimento em `PATCH /orders/{id}/payment`; **a entrega só é concluída com o pagamento confirmado**. O admin estorna em `POST /orders/{id}/payment/refund` e concilia em `GET /admin/payments?from=&to=` (totais por forma e estado). Recusa, cancelamento, expiração e falha cancelam o pagamento pendente. **Pagamento online (base pronta para Mercado Pago, cobrindo Pix e cartão)**: no checkout o cliente escolhe `modality=online` e a forma (`pix` ou `card`); `POST /orders/{id}/payment/online` cria a intenção — Pix devolve QR Code/“copia e cola” e cartão devolve o link do Checkout Pro. O `POST /webhooks/mercadopago` reconcilia consultando o provedor (não confia no corpo do webhook), valida assinatura HMAC quando `MERCADOPAGO_WEBHOOK_SECRET` está definido, é idempotente e marca `rejected` em valor divergente. Sem `MERCADOPAGO_ACCESS_TOKEN` esses endpoints respondem **503 (não configurado)**. Defina `MERCADOPAGO_ACCESS_TOKEN`, `MERCADOPAGO_WEBHOOK_SECRET` e `MERCADOPAGO_NOTIFICATION_URL` para ativar; o pagamento na entrega continua o padrão. O Java verifica e cria hashes scrypt no formato do Node e usa a mesma tabela `sessions`, inclusive para reconhecer sessões criadas pela API TypeScript. A API Java usa o **mesmo banco independente** e aplica as migrations via **Flyway** na subida; não acessa banco algum do legado (removido). Em 24/09/2026, o fluxo completo de pedido foi validado localmente contra MariaDB 11.4 com a API Java no Docker (`pnpm --filter @foodie/api smoke`, pedido #1). O site também foi verificado no navegador com Java: login, endereço, catálogo, carrinho, pedido #2 e disponibilidade do produto pelo restaurante. `API_INTERNAL_URL` permite alterar o destino explicitamente.

Para executar localmente com JDK 21 e o banco demonstrativo em funcionamento, configure as variáveis de `apps/api-java/.env.example` no terminal e rode `mvn spring-boot:run` dentro de `apps/api-java`. O servidor Java escuta em `127.0.0.1:4001` e pode ser validado em `/health`, `/catalog` e `/zones`. Para compilar e testar: `mvn test`.

Com Docker Desktop ativo e `platform/.env` configurado, `docker compose --profile java up -d --build` inicia o MariaDB e a API Java em paralelo. O MariaDB precisa estar previamente migrado; esse comando não cria nem altera tabelas. Em ambiente HTTPS, defina `COOKIE_SECURE=true` para enviar o cookie apenas por HTTPS. Para validar o fluxo completo em um banco **somente de teste**, mantenha a API Java na porta 4001 e execute `pnpm --filter @foodie/api smoke` com `API_PORT=4001` e `DEMO_PASSWORD` configurados no terminal. Execute `pnpm smoke:cart` para testar duas sessões, isolamento entre clientes, incrementos simultâneos, importação local e checkout; os dois comandos criam usuários e pedidos de teste. A migration `003_cart.sql` adiciona a tabela do carrinho. As migrations passarão para o Java quando ele assumir a propriedade do schema, evitando dois sistemas de migração simultâneos.

## Preparação para os apps móveis

A autenticação continua por cookie de sessão no web, e os apps móveis usam o **mesmo token** via
`Authorization: Bearer`. No login e no cadastro, a API devolve o token também no cabeçalho
`X-Foodie-Token`; o filtro `MobileSessionFilter` aceita esse Bearer e o reaproveita como a sessão
existente, sem tocar nos controladores. O contrato OpenAPI está em `GET /v3/api-docs` e a interface
em `/swagger-ui.html`.

Para push nativo, `POST /notifications/device-tokens` registra o token do dispositivo
(`{ "token": "...", "platform": "android|ios|web" }`) e `DELETE` remove. A tabela `device_tokens`
vem da migration `V016__device_tokens.sql` e as entregas em `device_deliveries` (V017). O envio usa
o **Firebase Cloud Messaging HTTP v1** (`FirebaseFcmSender` + `FcmDispatcher`): configure
`FCM_SERVICE_ACCOUNT_JSON` (JSON da conta de serviço em uma linha) e, se quiser, `FCM_PROJECT_ID`.
Sem essa variável o envio fica desativado e tokens inválidos são descartados automaticamente.

O retorno do pagamento online em app usa `MOBILE_PAYMENT_RETURN_URL`; quando definido, ele entra
como `back_urls` na preferência do Mercado Pago (Pix não usa `back_urls`).

## Catálogo com imagens e variações

Produtos podem ter imagens e variações (`V018__catalog_variants.sql`). A leitura pública
(`GET /catalog`, `/catalog/search`) inclui `image_url`, `variation_count` e `from_price_cents`;
`GET /catalog/products/{id}` traz as imagens e variações disponíveis. Admin e restaurante gerenciam
por `GET`/`POST`/`PATCH`/`DELETE .../products/{id}/variations[/{variationId}]` e
`GET`/`PUT .../products/{id}/images`. No carrinho, `PATCH /cart/items/{productId}` aceita
`variationId` opcional, e o pedido guarda a variação escolhida (`order_items.variation_name`) com o
preço recalculado no servidor. O seed de demonstração cria duas variações e uma imagem para o
"Prato da casa". No painel, cada produto tem "Variações e imagens" para editar tamanhos e capas; no
catálogo do cliente, pratos com variações abrem um seletor e a capa aparece no card.

**Adicionais (Fase 2)** vêm de `V019__catalog_addons.sql`. Admin e restaurante gerenciam grupos e
itens por `GET`/`POST`/`PATCH`/`DELETE /admin/addon-groups[/{id}][/addons[/{addonId}]]` (restaurante
na mesma rota com prefixo `/restaurant`), vinculam grupos ao prato por
`GET`/`PUT .../products/{id}/addon-groups` e o cliente seleciona no modal. O servidor valida
mínimo/máximo/obrigatório, recalcula o preço e grava os adicionais em `order_item_addons`.

**Tags, cupons e avaliações (Fases 3 e 4)** vêm de `V020__catalog_tags.sql`,
`V021__coupons.sql` e `V022__reviews.sql`. Tags têm CRUD por
`/admin|restaurant/tags`, vínculo por `GET`/`PUT .../products/{id}/tags`, filtro em
`/catalog/search?tagId=` e lista em `GET /catalog/tags?zoneId=`. Cupons têm CRUD em
`/admin/coupons`, validação em `POST /coupons/validate` e aplicação no checkout (desconto gravado
no pedido). Avaliações: `POST /orders/{id}/review` (só pedido entregue), `GET /orders/{id}/review`
e `GET /restaurants/{id}/reviews` (público). O seed cria a tag "Destaque" e o cupom `BEMVINDO` (10%).

**Combos, estoque e horário (`V024__catalog_combos_stock.sql`).** Um produto pode ser combo
(`is_combo`) com composição em `combo_items` (`GET`/`PUT .../products/{id}/combo-items`), ter
estoque finito (`stock`) e janela de disponibilidade (`available_from`/`available_until`). O catálogo
oculta itens fora do horário ou sem estoque; o checkout valida e baixa o estoque. O seed traz **3
restaurantes** com cardápios completos (variações, adicionais, combos, tags), cupons e pedidos
entregues com avaliações.

**Adicionais por variação e agendamento (`V025`/`V026`).** Grupos de adicionais podem ser vinculados
a uma variação específica (`?variationId=`, 0 = todas) e o cliente só vê/aplica os grupos da variação
escolhida. O checkout aceita `scheduledFor` (ISO local, >= 15 min e até 7 dias, respeitando o horário
do restaurante) e grava `orders.scheduled_at`; pedidos agendados não expiram antes da hora.

## Organização

- `apps/api`: protótipo TypeScript de autenticação por sessão, permissões, catálogo, zonas, endereços, pedidos e transições; porta 4000. Fica disponível para comparação com `pnpm dev:legacy-api` e `API_INTERNAL_URL=http://127.0.0.1:4000`.
- `apps/api-java`: API Java/Spring Boot com os fluxos demonstrativos de cliente, restaurante, admin e entregador; porta 4001.
- `apps/web`: interface Next.js responsiva com home própria do cliente e painel operacional dos outros papéis; porta 3001. `/backend/*` é encaminhado à API pelo servidor Next.
- `docker-compose.yml`: MariaDB local em `127.0.0.1:3307` e API Java opcional no perfil `java` em `127.0.0.1:4001`. `docker-compose.test.yml` sobe um banco **efêmero** isolado em `127.0.0.1:3308` + API de teste em `4101` para a verificação integrada.

O código deste diretório foi escrito para a nova plataforma. O sistema anterior continua separado até que os fluxos reais sejam cobertos e validados. **Proteção de contas (A01)** implementada: limite de tentativas no login por conta (5) e por origem (30), e de cadastro/recuperação por IP; verificação de email e recuperação de senha com **token de uso único** na tabela `auth_action_tokens`; sessões expiradas são limpas a cada hora; suspender um usuário revoga suas sessões; cookies HttpOnly/SameSite=Lax com `Secure` via `COOKIE_SECURE`. **Não há SMTP**: o `MailService` padrão registra o link de confirmação/redefinição no log da API — troque-o por um provedor real antes de publicar. A verificação de email é opcional no piloto (não bloqueia pedido) e as contas já existentes foram marcadas como verificadas. Antes de publicar ainda falta: revisar origem/CSRF nos domínios finais e configurar o provedor de email (SMTP).

Migrations ficam em `apps/api-java/src/main/resources/db/migration` e são aplicadas pelo **Flyway** na subida da API (com baseline automático para bancos criados pelo antigo `migrate` do TypeScript, que foi removido — dono único agora é o Java). O seed continua em TypeScript. `GET /health` devolve a versão do schema e `GET /ready` só responde 200 quando o banco está acessível e migrado (use-o como readiness). A homologação separada está em `deploy/` (`foodie-staging`), com serviço `migrate` (Java em modo `MIGRATE_ONLY`) e scripts `deploy/backup.sh` / `deploy/restore.sh`. Para verificar tudo: `pnpm verify` (testes TS, testes Java, tipos e build do site) e `VERIFY_INTEGRATION=1 pnpm verify` para subir o banco efêmero, rodar o seed e os quatro smokes dos papéis. O smoke principal cria dados de teste e deve rodar só no banco de demonstração/teste. As rotas públicas incluem `GET /zones/resolve?postalCode=...` e `GET`/`POST`/`DELETE /admin/postal-ranges`.

Observabilidade: `GET /actuator/health`, `/actuator/metrics` e **`/actuator/prometheus`** (Micrometer); cada requisição recebe um `X-Request-Id` (aceito do cliente ou gerado) que aparece no log de acesso da API. Teste de carga: `pnpm load`, configurável por `LOAD_CONCURRENCY`, `LOAD_DURATION_MS`, `LOAD_P95_MS` e `LOAD_ERROR_RATE` — as metas padrão são p95 < 800 ms e erros < 1% (medição local: ~758 req/s com p95 ≈ 50 ms a 20 conexões). A composição `deploy/` foi validada localmente (`/ready` respondendo `ready` com o schema migrado, web em 200 e `/backend/*` chegando à API pela rede interna).
