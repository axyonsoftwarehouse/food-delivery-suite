# Modelo de negócio — fronteira plataforma × lojista

Decisões registradas em 27/09/2026. Norte: o **Foodie é o ponto de encontro** entre clientes e
restaurantes; **a plataforma decide o que é da plataforma, o lojista decide o que é do negócio dele**.

## Fronteira de responsabilidade

| Decisão | Plataforma (admin/super-admin) | Lojista |
| --- | --- | --- |
| Onboarding | Aprovar restaurantes e entregadores | Perfil e acessos da loja |
| Rede de entrega | Zonas, CEP, política de entrega | Horários; entrega própria no futuro |
| Dinheiro | **Assinatura/comissão** contratada, repasses e conciliação | Ganhos, extrato, **despesas**, estoque |
| Catálogo | Curadoria global opcional | **Cardápio, preços, estoque, nutrição** |
| Marketing | Programa de fidelidade (base global) | **Cupons, campanhas, anúncios, cashback, desconto** |
| Relatórios | Consolidado da rede e saúde das lojas | **Vendas, pratos, clientes, taxas** |
| Equipe | Funcionários do admin (E01) | **Equipe e papéis da loja** |
| Conteúdo | Pages/CMS, analytics/i18n do site | Banners/promoções próprios; CMS da loja (futuro) |
| Sistema | Configurações, manutenção, integrações, auditoria | Configurações da loja |
| Pagamento presencial | Trilhos online da plataforma | **Métodos presenciais próprios (PDV já existe)** |

## O que o super-admin deve ver (observar, não controlar)

**Vê:**
1. Operação via Foodie: pedidos da plataforma, status, aceite, cancelamentos, atrasos; entregadores.
2. Saúde financeira **agregada**: GMV da plataforma, assinatura devida e status, inadimplência,
   repasses. Tendência sem abrir margem/custo/lucro.
3. Risco e suporte: loja parada, queda de volume, avaliação em queda, ocorrências/suspensões.
4. Contratual: CNPJ, responsável, plano contratado, documentos.
5. Conteúdo público: avaliações e vitrine.

**Não vê:** estoque, fornecedores, custos, despesas, margem; vendas offline/PDV; CRM/clientes da
loja; campanhas/cupons privados e configurações internas.

## Monetização

- Comissão/assinatura definidas **na contratação**; cenário provável: **assinatura mensal**
  (previsível para o lojista).
- **Módulos/serviços** como evolução (ex.: fidelidade, CMS, marketplace) — planejamento futuro.
- Programa de fidelidade: **base global** criada agora (config); modelo de custo a decidir depois.

## Roadmap desta reconstrução

- **Fatia A** — Financeiro e relatórios do lojista (resumo, extrato, relatórios, despesas).
- **Fatia B** — Estoque e fornecedores (insumos, movimentações, alerta de mínimo).
- **Fatia C** — Pagamentos presenciais e marketing do lojista (cupons, campanhas, anúncios,
  cashback, desconto).
- **Fatia D** — Painel "Saúde das lojas" do super-admin (visão agregada que respeita a privacidade).

**Futuro:** assinaturas + módulos; CMS da página da loja; modelo de custo da fidelidade.

## Entregue em 27/09/2026

Migração `V048__business_operations.sql`; `pnpm verify` verde (213 testes Java); schema em `048`.

- **Fatia A — Financeiro/relatórios do lojista**: `RestaurantFinanceController`
  (`/restaurant/finance/summary|earnings|reports/*|expenses`), painel com abas Resumo, Ganhos,
  Relatórios e Despesas no menu **Financeiro** do restaurante.
- **Fatia B — Estoque e fornecedores**: `suppliers`, `inventory_items`, `inventory_movements`;
  `InventoryController` (`/restaurant/suppliers`, `/restaurant/inventory*`); painel **Estoque**
  (Insumos, Fornecedores, Baixo estoque); permissão `inventory.manage`.
- **Fatia C — Marketing e pagamentos presenciais do lojista**: `RestaurantMarketingController`
  (`/restaurant/marketing/discount|coupons|campaigns|advertisements|cashback-rules|offline-methods`);
  painel **Promoções** do restaurante; métodos presenciais agora são do lojista.
- **Fatia D — Saúde das lojas (super-admin)**: `TenantHealthController`
  (`/admin/tenants/health`) e painel **Lojas**, com operação via Foodie, GMV, assinatura e alertas —
  sem expor custos, estoque, despesas ou clientes da loja.

Follow-ups: assinaturas + módulos, CMS da página da loja e modelo de custo da fidelidade seguem no
planejamento futuro.
