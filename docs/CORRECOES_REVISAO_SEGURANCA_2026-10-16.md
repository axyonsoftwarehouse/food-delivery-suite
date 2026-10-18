# Correções da revisão de segurança — 16/10/2026

Base local: `d97271b`. Branch: `fix/seguranca-e-consistencia`.
Este documento descreve alterações locais; publicação e validação no staging **(a confirmar)**.

## Comportamento corrigido

1. **Equipe.** Criar, alterar e excluir papéis, criar acessos e atribuir papéis são
   ações exclusivas do dono (`restaurant` sem papel de funcionário). `staff.manage`
   permite consultar a equipe, mas não promover acessos. Funcionários do painel
   precisam de um papel; remover esse papel não os transforma em donos. A atribuição
   também não modifica contas de dono. A cozinha continua podendo usar seu padrão.
   O painel acompanha as restrições. Corrigida também a leitura JDBC de papel nulo,
   que podia apresentar `0` em vez de `null` na lista da equipe.
2. **Pagamento do pedido.** `POST /orders/{id}/payment/online` só cobra pedidos
   criados com modalidade `online`. Pedidos `on_delivery` ou `offline` recebem 409,
   antes de chamar o provedor. Não há conversão implícita de modalidade.
3. **Webhook da assinatura.** Uma cobrança conhecida cuja consulta falha, retorna
   vazia ou fica sem credencial responde 503. O evento não é reconhecido como
   processado; o provedor pode reenviá-lo. A revisão complementar abaixo acrescenta
   recuperação de eventos antecipados e reconciliação no runner existente.
4. **Concorrência da assinatura.** O pagamento trava a assinatura e relê a cobrança
   com `FOR UPDATE` antes de decidir. Pagamentos do mesmo ciclo são serializados,
   inclusive cobranças diferentes. Uma transição que não ocorreu não grava razão
   nem notifica. Cobranças pagas/estornadas não repetem efeitos, e notificações
   atrasadas de expiração/cancelamento não sobrescrevem esses estados.
5. **Fatura.** Exige as mesmas permissões de leitura do detalhe: admin precisa de
   `orders.manage` ou `support.view`; equipe da loja precisa de `orders.view`.
   A checagem de propriedade/loja/entregador continua no serviço do pedido.
6. **Chat.** `GET /chat/messages?withUser=<id>` retorna as 200 mensagens mais
   recentes em ordem cronológica. `beforeId=<menor id da página>` busca a página
   anterior, com cursor exclusivo. Só mensagens recebidas presentes na resposta
   são marcadas como lidas; mensagens mais novas ou fora da página permanecem
   intactas. O formato da resposta continua sendo uma lista.

## Validação

Testes de regressão cobrem as restrições da equipe, a modalidade da cobrança,
autorização da fatura, falha/reenvio do webhook, decisões concorrentes e paginação
do chat. O contrato OpenAPI e os tipos do cliente são regerados.

`pnpm verify` passou em 16/10/2026, sem integração: **789 testes Java**, testes do
site, tipos e build Next.js, tipos e **21 testes da cozinha**, tipos do cliente da
API e conferência dos **13 COPYs** dos Dockerfiles. A geração do OpenAPI também
passou. O Java foi executado com o agente Mockito explícito para evitar a limitação
de autoattach deste ambiente Windows.

Com o Docker ligado, `VERIFY_INTEGRATION=1 pnpm verify` também passou em
16/10/2026: **793 testes Java no total, sem falhas, erros ou testes ignorados**
(789 da suíte habitual + 4 regressões com MariaDB real), tipos/builds e os cinco
smokes de fluxo completo, carrinho, exceções, contas e cobertura por CEP.

As quatro regressões integradas verificam duas notificações simultâneas da mesma
cobrança, duas cobranças simultâneas do mesmo período (um pagamento e um estorno),
a proteção do dono/funcionário no SQL e uma conversa com 205 mensagens paginadas.
Os testes usam conexões e transações MariaDB em `REPEATABLE READ`; o gateway
externo continua simulado, sem movimentar dinheiro. A primeira tentativa revelou
uma ordem incorreta de limpeza do fixture (papel antes da loja); corrigida, a
execução completa foi repetida e passou.

O runner agora aceita `DB_PASSWORD`, `DB_ROOT_PASSWORD` e `DEMO_PASSWORD` do
ambiente antes de recorrer a arquivos locais. Nesta validação, foram geradas
credenciais temporárias e desativada a leitura automática de arquivos pelo
Compose/dotenv. O banco/API efêmeros usaram 3308/4101 e foram removidos ao final;
os serviços de desenvolvimento 3307/4001 permaneceram ativos.

Log local: `platform/apps/api-java/target/review-verify-integration-final.log`
(não versionado). Publicação e prova no staging **(a confirmar)**.

Contas `restaurant` antigas sem papel já eram consideradas donas. As correções
impedem criar novos funcionários assim, mas não reclassificam automaticamente
contas existentes: é necessário conferir com o responsável quais são donos reais.

## Revisão complementar OWASP e concorrência

Oito falhas adicionais corrigidas, sem publicação:

1. **Exclusão concorrente de papel.** A migration `V070` muda a FK de
   `users.staff_role_id` para `ON DELETE RESTRICT`. Mesmo que uma atribuição ocorra
   depois da consulta inicial, excluir o papel não deixa o funcionário sem papel
   nem o transforma em dono. O serviço responde 409 ao conflito.
2. **Login concorrente com troca de senha.** A criação da sessão trava o usuário
   e exige que o hash validado ainda seja o atual e a conta continue habilitada.
   Uma troca de senha entre a verificação e a criação da sessão invalida o login.
3. **3DS abandonado e comprovante.** Pedidos online não podem mudar para pagamento
   por comprovante, inclusive depois de uma recusa posterior. Uma tentativa antiga
   pode continuar ativa no provedor. Para usar comprovante, cancelar e criar outro
   pedido; nenhuma tentativa online é interpretada como offline.
4. **Identidade do ciclo de assinatura.** Reemissões mantêm `period_start` ancorado
   no vencimento renovado. Dois pagamentos tardios do mesmo ciclo geram uma única
   renovação e o estorno da duplicata, mesmo que as cobranças sejam distintas.
5. **Reserva durável antes da chamada externa.** A tentativa é persistida e
   confirmada antes da rede. Reenvios usam a mesma chave `sub-<chargeId>`; a
   referência enviada ao provedor também identifica essa reserva. Webhooks
   antecipados recuperam a vinculação. Geradores concorrentes são serializados.
   O runner existente recupera tentativas pendentes/falhas e consulta o provedor;
   só libera uma reemissão após confirmar estado terminal, sem presumir que a
   expiração local impediu um pagamento. Valor divergente não renova a assinatura.
6. **Inadimplência concorrente com pagamento.** Suspensão e expiração revalidam
   vencimento, estado e carência no próprio `UPDATE`. Uma leitura antiga não
   sobrescreve uma renovação; só há notificação quando a transição ocorre.
7. **Arquivos privados e administradores restritos.** Acessar arquivo privado de
   terceiro exige `files.view`; excluí-lo exige `files.manage`. As permissões
   aparecem no catálogo administrativo. O autor mantém acesso aos próprios arquivos.
8. **Limite de SMS atômico.** As cotas de telefone e origem são reservadas na mesma
   transação antes do envio. Se qualquer limite for excedido, ambas são revertidas.
   A aquisição inicial usa upsert com bloqueio exclusivo para evitar o deadlock
   observado no teste com 20 solicitações simultâneas.

As novas regressões com MariaDB real cobrem exclusão concorrente de papel, troca
de senha durante login, 20 solicitações simultâneas de OTP, dois geradores de
cobrança, timeout após criação, webhook antecipado, reemissão com pagamento duplo
e pagamento entre a leitura e a aplicação da inadimplência. O gateway financeiro
é simulado: disponibilidade, retenção de idempotência e comportamento do provedor
em homologação **(a confirmar)** antes de publicar.

Cobranças antigas que já tenham ficado sem vínculo e contas de funcionários já
deixadas sem papel exigem conferência dos dados existentes **(a confirmar)**.
As correções protegem as novas operações; não inferem automaticamente a identidade
de donos nem recuperam cobranças históricas sem uma referência verificável.

### Validação final da revisão complementar

`VERIFY_INTEGRATION=1 pnpm verify` passou novamente em 16/10/2026: **805 testes
Java (793 habituais + 12 com MariaDB real), sem falhas, erros ou testes ignorados**;
22 testes do site, 21 testes da cozinha, tipos/builds, 13 COPYs dos Dockerfiles e
os cinco smokes. A API de teste iniciou com a migration `V070` e a regressão
confirmou que a FK impede a exclusão concorrente do papel.

A primeira execução complementar revelou um deadlock no limitador de OTP sob
concorrência real. Corrigida a aquisição do bloqueio, a execução completa foi
repetida e passou: 20 solicitações simultâneas autorizam exatamente cinco
reservas, sem ultrapassar a cota nem falhar por deadlock.

Log local: `platform/apps/api-java/target/owasp-verify-integration-final.log`
(não versionado). Banco/API temporários removidos; serviços locais 3307/4001
preservados. Sem commit, push ou deploy. Homologação no provedor e publicação
**(a confirmar)**.

## Ajustes após a revisão do diff

Três problemas introduzidos pela revisão complementar, corrigidos com regressões:

1. **Webhook de pedido dependia da conta da assinatura.** Todo webhook desconhecido
   consultava o provedor com a credencial da Foodie antes de seguir para pedidos, e
   uma falha nessa consulta respondia 503 ao webhook do pedido. Agora a consulta só
   acontece quando há reserva de assinatura sem `external_id` e a cobrança não é um
   pagamento de pedido conhecido. Falha na consulta segue para pedidos; a reserva é
   reconciliada pelo runner com a mesma chave de idempotência.
2. **Tentativas `failed` antigas eram reaproveitadas.** O runner passou a pegar
   assinaturas com cobranças `failed`, e a mais recente era reenviada mesmo quando
   outra cobrança já tinha pago aquele ciclo (gerava Pix novo e aviso "Assinatura a
   pagar"). Agora essas tentativas são encerradas (`expired`, motivo
   `superseded_paid_period`) sem chamada ao provedor nem notificação.
3. **Reserva presa em assinatura não cobrável.** Reserva sem `external_id` de
   assinatura cancelada/expirada ficava `pending` para sempre e voltava ao lote do
   runner a cada rodada. Agora é encerrada (`subscription_not_chargeable`). Com
   `external_id`, a decisão continua sendo do provedor (reconciliação).

`VERIFY_INTEGRATION=1 pnpm verify` passou em 16/10/2026: **811 testes Java (799
habituais + 12 com MariaDB real), sem falhas, erros ou testes ignorados**, incluindo
o webhook antecipado com as novas consultas; site, cozinha, tipos/builds, COPYs e
os cinco smokes. Banco/API efêmeros removidos. Log local:
`platform/apps/api-java/target/fix-achados-verify-integration.log` (não versionado).

## Conferência das contas `restaurant` no ambiente publicado

Conferência somente leitura no banco do staging (schema `069`, deploy `71d50ee`), em
17/10/2026:

- 3 contas com papel base `restaurant`, todas com `staff_role_id` nulo: Cozinha Demo
  (`restaurante@demo.local`), Cantina do Bairro (`restaurante2@demo.local`) e Doceria
  Estrela (`doceria@demo.local`). Cada uma é a única conta `restaurant` da própria
  loja, então não há funcionário antigo sem papel a reclassificar nem mais de um
  candidato a dono na mesma loja.
- Nenhum `restaurant_role` cadastrado e nenhuma conta `kitchen` no ambiente.
- As lojas Sushi Nori, Verde Vivo e Burger House não têm conta `restaurant` (dados de
  demonstração).

Nenhum dado foi alterado. Outros bancos/ambientes de produção, se existirem, exigem a
mesma conferência **(a confirmar)**.
