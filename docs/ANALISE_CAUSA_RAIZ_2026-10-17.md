# Análise de causa raiz — 17/10/2026

Leitura estática do código em `origin/main` (`31b1457`), sem alterações de comportamento.
O objetivo não é listar erros de sintaxe, e sim identificar **modelos lógicos** que produzem
a próxima classe de bugs. As correções de 16/10 reduziram a superfície (trava por assinatura,
reserva durável, 3DS, `V070`), mas alguns padrões continuam presentes.

## Resumo

| # | Padrão | Risco |
| --- | --- | --- |
| 1 | Segurança derivada de `NULL` (fail-open) | **P0** — escalada de privilégio |
| 2 | Estado local e tentativas do provedor na mesma linha | **P0/P1** — dinheiro capturado sem pedido pago |
| 3 | Estoque revertido pelo estado atual, não pelo debitado | **P1** — divergência de inventário |
| 4 | Instante, hora local e delta relativo misturados | **P1** — agendamento/cupom/recorrência errados |
| 5 | I/O de rede dentro de transação e de lock, sem timeout | **P1** — locks presos e indisponibilidade |
| 6 | Leitura que escreve e apaga estado do cliente | **P2** — perda de carrinho |
| 7 | Falhas silenciosas e auditoria best-effort | **P2** — incidente sem alerta |
| 8 | Contadores sem ledger/constraint | **P2** — cupom e recompensas divergentes |

---

## 1. Segurança modelada por ausência (`NULL` = poder total) — P0

O sistema trata “não ter papel” como “ser dono” ou “ser admin total”:

- `AdminPermissionService.effective` devolve **todas** as permissões quando
  `admin_role_id` é nulo
  (`platform/apps/api-java/src/main/java/com/foodie/api/admin/AdminPermissionService.java:21`).
- No restaurante, dono é `role = restaurant` **e** `staff_role_id IS NULL`
  (`platform/apps/api-java/src/main/java/com/foodie/api/permissions/RoleController.java:110`).
- A FK de admin continua `ON DELETE SET NULL`
  (`platform/apps/api-java/src/main/resources/db/migration/V034__admin_rbac_audit.sql:15`).
  O `V070` corrigiu apenas `users.staff_role_id`.

A corrida corrigida para funcionários continua no admin: `AdminAccessService.deleteRole`
verifica `roleAssigned` e depois apaga, sem trava nem tratamento de conflito
(`platform/apps/api-java/src/main/java/com/foodie/api/admin/AdminAccessService.java:46`).
Uma atribuição concorrente deixa `admin_role_id = NULL` e a conta passa a ter acesso total.

Além disso, um admin com `admin.manage` pode editar o próprio acesso para papel nulo
(`AdminAccessService.java:73`), porque a checagem de “último admin total” só existe ao
**restringir**, não ao **promover**. Na prática, `admin.manage` equivale a superadmin.

**Causa raiz:** autorização derivada de ausência de dado, com default fail-open.
**Correção estrutural:** papel explícito e não nulo para quem tem poder; default
`sem papel = sem permissão`; FK `RESTRICT` também no admin; migração que classifique os
registros existentes; separar “gerenciar papéis” de “conceder acesso total”.

## 2. Pagamento: estado local e tentativas do provedor na mesma linha — P0/P1

`order_payments` tem uma linha por pedido (`UNIQUE(order_id)`,
`platform/apps/api-java/src/main/resources/db/migration/V012__order_payments.sql:20`). Essa
linha representa ao mesmo tempo o pagamento do pedido e a “tentativa atual” no provedor.
Cada retry sobrescreve `external_id`
(`platform/apps/api-java/src/main/java/com/foodie/api/payments/OnlinePaymentService.java:163`)
e não há histórico de tentativas. O webhook usa o `external_id` atual para tentar distinguir
tentativas antigas (`OnlinePaymentService.java:197`).

Consequências lógicas:

- **Divergência de valor não estorna.** Se o provedor confirma “pago” com valor diferente,
  o código marca `rejected` e segue (`OnlinePaymentService.java:159` e `:236`). O dinheiro
  foi capturado, o pedido não é considerado pago e nenhum estorno é disparado.
- **Assinatura fica em retry infinito.** O mesmo caso em `applyPaid` lança `409`
  (`platform/apps/api-java/src/main/java/com/foodie/api/subscriptions/SubscriptionBillingService.java:203`);
  o provedor reenvia indefinidamente, sem estado terminal de reconciliação.
- **Parse inválido vira zero.** `amountCents` devolve `0` quando não consegue ler o valor
  (`platform/apps/api-java/src/main/java/com/foodie/api/payments/MercadoPagoGateway.java:467`),
  transformando “desconhecido” em “valor zero” e acionando o fluxo acima.

**Causa raiz:** não existe uma entidade de **tentativa de pagamento** separada da projeção
do pedido; o estado local tenta representar o provedor e o pedido ao mesmo tempo.
**Correção estrutural:** `payment_attempts` com `external_id` único e estado por tentativa;
o pedido mantém uma projeção; webhook aplica na tentativa e reconcilia; divergência de valor
é estado terminal explícito com estorno/reconciliação, nunca “rejected” silencioso.

## 3. Estoque revertido pelo estado atual, não pelo debitado — P1

O pedido só debita estoque se `products.stock != null`
(`platform/apps/api-java/src/main/java/com/foodie/api/orders/OrderService.java:351`). No
cancelamento, `restoreStock` soma para **todo produto cujo `stock` não é nulo agora**
(`OrderService.java:620`).

Cenário: produto ilimitado (`stock = NULL`) no checkout; a loja define `stock = 10`; o pedido
é cancelado → o estoque vira `10 + quantidade`, embora nada tenha sido debitado. O inverso
também ocorre: `stock` finito no checkout, `NULL` antes do cancelamento → nada é devolvido.

**Causa raiz:** não há ledger de movimentação de estoque; a reversão depende de uma condição
atual, não do fato histórico.
**Correção estrutural:** registrar a movimentação no checkout (ou um flag por item) e
reverter exatamente aquilo; estoque como ledger append-only.

## 4. Tempo: instante, hora local e delta relativo misturados — P1

- O agendamento é calculado como segundos a partir de `Instant.now()` e gravado como
  `DATE_ADD(NOW(), INTERVAL ? SECOND)` (`OrderService.java:158` e `:322`). A resposta devolve
  o instante calculado pela aplicação (`OrderService.java:376`). Latência, retry ou diferença
  de relógio entre app e banco deslocam o horário real.
- `RecurringOrderRunner` compara `Timestamp` do banco com `LocalDateTime.now()`
  (`platform/apps/api-java/src/main/java/com/foodie/api/subscriptions/RecurringOrderRunner.java:63`),
  assumindo que JVM e banco compartilham o mesmo fuso.
- `CouponService` compara `expires_at.toLocalDateTime()` com `LocalDateTime.now()`
  (`platform/apps/api-java/src/main/java/com/foodie/api/orders/CouponService.java:127`).
- `RestaurantHoursService` é inconsistente em falha: timezone inválido faz `isOpen` retornar
  `false` (loja fechada), mas `zone()` cai para `America/Fortaleza`
  (`platform/apps/api-java/src/main/java/com/foodie/api/hours/RestaurantHoursService.java:61`
  e `:87`).

**Causa raiz:** não há uma fronteira única “UTC no banco, zona explícita na borda”.
**Correção estrutural:** persistir instantes absolutos, injetar `Clock`, converter para o
fuso da loja apenas na leitura/validação e falhar explicitamente em timezone inválido.

## 5. I/O de rede dentro de transação e de lock, sem timeout — P1

`OrderService.create` é `@Transactional` (`OrderService.java:104`), trava produtos com
`FOR UPDATE` (`OrderService.java:166`) e, ainda dentro da transação, chama o Mapbox
(`OrderService.java:218`). O `CartService.checkout` mantém a linha do usuário travada
(`platform/apps/api-java/src/main/java/com/foodie/api/orders/CartService.java:126`).

Os clientes HTTP usam `RestClient.builder()` sem timeout de leitura explícito
(`platform/apps/api-java/src/main/java/com/foodie/api/routing/MapboxRoutingProvider.java:20`,
`platform/apps/api-java/src/main/java/com/foodie/api/payments/MercadoPagoGateway.java:47`);
a configuração só define timeout de conexão do Hikari
(`platform/apps/api-java/src/main/resources/application.yml:122`).

Uma lentidão ou hang do Mapbox prende locks de estoque e do carrinho, além de uma thread e
uma conexão do pool. O mesmo padrão aparece no estorno do Mercado Pago dentro da transação do
pedido e na criação de cobrança da assinatura dentro da transação que trava assinatura/cobrança.

**Causa raiz:** a fronteira transacional foi desenhada em volta da chamada de rede.
**Correção estrutural:** timeouts obrigatórios, chamada externa fora da transação (ou pelo
menos fora do lock), com estado “pendente de confirmação” e reconciliação.

## 6. Leitura que escreve e apaga estado do cliente — P2

`GET /cart` chama `lock` e `prune` (`CartService.java:38`), e `prune` **apaga** linhas de
produtos/lojas indisponíveis (`CartService.java:182`). Um `GET` pode destruir o carrinho se a
loja estiver temporariamente inativa. Além disso, a regra de disponibilidade do carrinho
(`prune`/`rows`, `CartService.java:208`) não é a mesma do checkout: variação indisponível,
janela de horário e estoque só são validados no checkout.

No cliente, `refreshCart` e `mutateCart` não têm sequência/versão; uma resposta antiga de
refresh pode sobrescrever uma mutação mais nova
(`platform/apps/web/app/loja/customer-context.tsx:238` e `:281`).

**Causa raiz:** carrinho tratado como derivado do catálogo, sem política única de
disponibilidade; leitura e escrita compartilham o mesmo caminho.
**Correção estrutural:** `GET` sem escrita; marcar indisponível em vez de apagar; uma única
política de disponibilidade para carrinho e checkout; versionar/abortar respostas antigas.

## 7. Falhas silenciosas e auditoria best-effort — P2

- `SubscriptionBillingService.generateForSubscription` captura `RuntimeException` e devolve
  vazio (`SubscriptionBillingService.java:80`). Um NPE ou erro de SQL vira “tentativa
  pendente para reconciliação”, sem alerta.
- `RestaurantHoursService` engole exceção e decide “fechado”; `MercadoPagoGateway.amountCents`
  engole parse e devolve `0`.
- `AdminAuditService.record` engole qualquer erro
  (`platform/apps/api-java/src/main/java/com/foodie/api/admin/AdminAuditService.java:26`):
  a operação administrativa pode concluir **sem trilha**.

**Causa raiz:** erros esperados (rede/transiente) e inesperados (bug/dado) são tratados
igualmente; valores sentinela (`0`, `false`, `null`) substituem “desconhecido”.
**Correção estrutural:** separar exceções, propagar falhas de lógica, métricas/alertas e nunca
usar zero como “não sei”.

## 8. Contadores sem ledger — P2

`coupons.used_count` é incrementado/decrementado diretamente
(`CouponService.java:61` e `:97`), sem uma tabela de resgates por pedido; o vínculo é só
`orders.coupon_code`. Um release repetido ou um pedido removido manualmente desalinha o
contador. Em recompensas, `loyaltyAwarded`/`cashbackPosted` são “consulta e depois insere”
(`platform/apps/api-java/src/main/java/com/foodie/api/rewards/RewardsService.java:47` e `:145`)
sem constraint única por `(order_id, kind)`; as migrações não têm essa invariante.

**Causa raiz:** contadores derivados sem fonte da verdade append-only.
**Correção estrutural:** ledger de resgates/movimentações com chave única por pedido e
contadores calculados/atualizados a partir dele.

---

## O que já está bom

- As transições de pedido estão centralizadas em `OrderWorkflow` e não são espalhadas por
  controllers.
- As correções de 16/10 trouxeram travas explícitas para assinatura, reserva durável antes da
  rede e testes de concorrência com MariaDB real.
- O contrato OpenAPI é regerado a partir do backend e há testes de contrato.

## Onde começar

1. **Fail-closed de privilégios** (admin e restaurante), com FK `RESTRICT` e migração de
   classificação — é a mesma classe do `V070`, ainda aberta para admin.
2. **Modelo de tentativas de pagamento + estado terminal de divergência de valor** — é onde
   há risco de dinheiro capturado sem pedido pago.
3. **Ledger de estoque e um único `Clock`/UTC** — elimina duas famílias de bugs silenciosos.
4. Depois: tirar I/O de rede de dentro de transações/locks, tornar `GET /cart` sem escrita e
   adicionar constraints únicas nos ledgers de cupom/recompensa.

Cada item pode virar um PR independente, com regressões próprias.
