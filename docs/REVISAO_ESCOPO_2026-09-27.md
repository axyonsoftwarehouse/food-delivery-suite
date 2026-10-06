# Revisão de escopo — plataforma Foodie

> Atualização em 28/09/2026: veja `PENDENCIAS_IMPLEMENTACAO_2026-09-28.md` para o estado após a descontinuação da carteira do restaurante e a nova home pública. As tabelas abaixo são a fotografia da revisão de 27/09.

Data: 27/09/2026. Comparação do código atual com `PLANO_MODELO_NEGOCIO.md`,
`PLANO_RECONSTRUCAO_PROPRIA.md` e `PLANO_EPICOS.md`.

## Critério

O modelo de negócio define quem decide e quem vê os dados. O plano de épicos
detalha funcionalidades, mas sua meta de absorver o legado não substitui essa
fronteira. Itens listados como *follow-up* são lacunas planejadas, não entregas
completas.

## Alinhado ao projeto

| Capacidade | Evidência |
| --- | --- |
| Fluxo próprio de pedidos e papéis cliente, restaurante, admin e entregador | `platform/apps/api-java/src/main/java/com/foodie/api/orders`, `platform/apps/web/app/loja`, `platform/apps/web/app/painel` |
| Financeiro, despesas, estoque e marketing do lojista (fatias A–C) | `RestaurantFinanceController`, `InventoryController`, `RestaurantMarketingController` |
| Saúde agregada das lojas para admin (fatia D) | `TenantHealthController` |
| Módulos por loja e CMS da loja, previstos como evolução | `ModuleController`, `StorefrontController`, página `/restaurantes/[id]` |

## Lacunas previstas que ainda precisam de entrega

| Prioridade | Capacidade | Estado atual |
| --- | --- | --- |
| Resolvida nesta atualização | Geração automática dos pedidos recorrentes | E21 agora gera pedidos por ciclo, registra execuções e pausa com motivo quando o pedido não pode ser criado. |
| Resolvida nesta atualização | Aplicar campanhas no checkout | Campanhas ativas entram no total do pedido e na cotação do carrinho; o pedido guarda a campanha e o valor aplicado. |
| Média | Carteira como forma de pagamento | Há saldo e extrato, mas o plano registra o uso no checkout como follow-up. |
| Média, antes de cobrar módulos | Vínculo entre módulos e contrato/assinatura | Há catálogo e habilitação manual por loja, mas `modules.price_cents` está zerado e a habilitação não consulta a assinatura. O modelo de custo ainda depende de decisão comercial. |
| Média | Interface de chat | Há endpoints `/chat/*`; o plano registra a interface dedicada como follow-up. |
| Condicionada ao canal | Apple Sign In, cache offline e importação em massa | Explicitamente pendentes em E47, E42 e E23. Não tratar o respectivo épico como 100% concluído. |
| Antes de publicar | SMTP, domínio/origem/CSRF finais e validação integrada | `platform/README.md` ainda os aponta como preparação necessária; `PLANO_RECONSTRUCAO_PROPRIA.md` exige homologação e rollback. |

## Funcionalidades presentes que conflitam com a fronteira de negócio

| Prioridade | Conflito | Evidência e ação proposta |
| --- | --- | --- |
| Alta | Admin altera desconto próprio do restaurante | `RestaurantAdminController` expõe `PATCH /admin/restaurants/{id}/discount`; `admin-operation-panel.tsx` oferece o controle. O modelo de negócio atribui desconto e marketing ao lojista. Remover o controle e a rota após confirmar como tratar descontos já configurados. |
| Alta | Admin consulta extrato e saldo individual de qualquer restaurante | `FinanceController` expõe `/admin/finance/ledger?party=restaurant&partyId=...` e `/admin/finance/balances`; `finance-panel.tsx` permite selecionar a parte. O modelo limita o super-admin a GMV, assinatura, repasses e saúde agregada. Definir uma visão contratual/de repasse que não exponha o extrato interno do lojista. |
| Média | Admin edita cardápio e preço de qualquer restaurante | Rotas de catálogo admin e `CatalogManager.tsx` permitem essa edição. O plano técnico inicial autorizava o admin a montar dados, mas o modelo de negócio posterior põe cardápio e preço sob decisão do lojista, com curadoria global apenas opcional. Decidir se a edição admin fica restrita a suporte auditado. |
| Média | O seed volta a definir aprovação e desconto de lojas demonstrativas | `platform/apps/api/src/seed.ts` ainda atualiza esses campos ao repetir o seed. Mesmo em contas de demonstração, isso pode sobrescrever uma escolha feita no painel; preservar valores já configurados antes de usar o seed em ambientes compartilhados. |

Os conflitos acima são decisões de produto e dados existentes; esta revisão não
remove rotas nem altera preços já cadastrados. As correções técnicas deste ciclo
tratam exclusivamente os quatro defeitos identificados na revisão anterior.

## Documentação a harmonizar

`platform/README.md` ainda contém trechos históricos dizendo que faltam pagamentos
e notificações, que não há SMTP e que o Java não aplica migrations. O mesmo arquivo
descreve depois pagamentos, push, SMTP configurável e Flyway. Consolidar essas
seções antes de usar o README como lista de funcionalidades disponíveis.

## Verificação desta revisão

`VERIFY_INTEGRATION=1 pnpm verify` passou: testes TypeScript e Java, tipos e build
do site, tipos e testes do app da cozinha, migrations até V051, seed em MariaDB
efêmero e os quatro smokes (fluxo completo, carrinho, exceções e contas).

## Atualização: recorrências, campanhas e carteira do restaurante

As duas primeiras lacunas da tabela acima receberam implementação posterior nesta
mesma revisão: execução de recorrências com histórico/idempotência e campanhas
calculadas no pedido e apresentadas no carrinho. A carteira de saque do restaurante
foi analisada em `AVALIACAO_CARTEIRA_RESTAURANTE.md`. Se a Foodie cobrar somente
assinatura e a loja receber todos os pedidos diretamente, essa carteira deve sair
do produto após a conciliação e a mudança do pagamento online para liquidação por
loja. A análise não altera lançamentos ou solicitações de repasse existentes.

Verificação posterior: `VERIFY_INTEGRATION=1 pnpm verify` passou com migrations
até V052, testes Java e TypeScript, build e smokes. O smoke do carrinho confirmou
campanha no total do pedido e cadastro/gestão de recorrência; o teste unitário
do agendador confirmou a criação de um pedido e o registro do ciclo devido.
