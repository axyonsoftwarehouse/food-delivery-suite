# Foodie — revisão das pendências de implementação

Data: 28/09/2026. Base: modelo de negócio, planos de reconstrução e épicos do repositório; inspeção de API, web, migrations e composição de homologação. Esta lista descreve capacidade observável no código, não valida operação em produção.

## Decisão aplicada nesta revisão

- A Foodie cobra assinatura da loja. A venda do pedido pertence ao restaurante. Novos pedidos não geram crédito sacável nem comissão no razão da loja; o financeiro privado calcula vendas pagas e concluídas pelos pedidos.
- A carteira de saque, métodos e novas solicitações do restaurante saem da interface e da API. Lançamentos e repasses antigos permanecem no banco; o admin pode concluir solicitações já abertas. A carteira de recompensas do cliente continua; a de repasse do entregador foi retirada do fluxo ativo em 07/10/2026.
- Cobranças online com a conta global da plataforma ficam bloqueadas para novos pedidos. Intenções antigas e webhooks continuam consultáveis/processáveis. Pagamento na entrega e meios manuais configurados pela loja continuam disponíveis. **Atualização de 02/10/2026:** o bloqueio virou configuração (`PAYMENTS_ALLOW_DIRECT_ONLINE_CHARGES`, desligada por padrão) e o trecho que faltava — criar a cobrança no provedor e gravar QR/id externo — foi implementado, para permitir teste com credenciais de teste do Mercado Pago sem alterar o comportamento de produção. A decisão de quem recebe o dinheiro continua pendente.
- `/` passa a apresentar a home pública; o acesso dos papéis fica em `/entrar`.
- **Decisão de 05/10/2026 (dono do produto): a Foodie só trabalha com a assinatura e o suporte. Os valores
  do pedido são de responsabilidade exclusiva da loja:** venda, frete, gorjeta, cashback e **estorno**. A
  obrigação de estornar é da loja. A Foodie não recebe, não guarda e não devolve dinheiro de pedido. Isso
  fecha as pendências "quem recebe o dinheiro", "quem financia entrega e gorjeta" e "quem paga o
  cashback". O que muda no código está nas linhas abaixo.

## Prioridade alta — antes de operação comercial

| Lacuna | Evidência | Próximo passo |
| --- | --- | --- |
| Cobrança real da assinatura | `SubscriptionController` cria `subscription_transactions` ao atribuir plano, sem cobrar um provedor, conciliar liquidação ou automatizar renovação/inadimplência. | Integrar billing, webhook idempotente, renovação e suspensão por estado pago. Tratar a transação atual como registro administrativo, não recibo. |
| Pix/cartão online direto para a loja | `OnlinePaymentService` resolve gateway global, sem conta do restaurante. Novas cobranças seguem bloqueadas **por configuração** (desde 02/10/2026); o caminho de criação (Pix e preferência de cartão) e a gravação de QR/id externo existem para **teste com credencial de teste**. | Conectar contas de cada restaurante ou usar provedor com recebedor por loja; testar cobrança, cancelamento, estorno e conciliação por tenant antes de reabrir o checkout online. **Atualização de 05/10/2026:** com uma conta só (a do vendedor de teste), cobrança, webhook e estorno pelo Mercado Pago foram provados no staging (PRs #37 a #48). Pela decisão de 05/10, a conta tem de ser **a da loja**: falta guardar as credenciais (ou a vinculação) de cada restaurante e usá-las na cobrança, na consulta, no webhook (cada conta tem o próprio segredo de assinatura) e no estorno. Produção não pode usar conta global. **Atualização de 06/10/2026: implementado** — a loja conecta a própria conta Mercado Pago (OAuth) e cobrança, consulta, webhook, estorno e checkout usam essa conta; a conta global foi removida (parte 1 na PR #53, parte 2 neste PR). **Pendente:** teste de ponta a ponta no staging (Pix, cartão e estorno com a conta da loja conectada). |
| Acerto do passivo antigo de carteira | `ledger_entries` e `payout_requests` antigos não são apagados; o admin ainda decide repasses históricos. | Inventariar saldos, pedidos online existentes e solicitações em aberto; conciliar e registrar quitação com evidência. |
| Entrega e gorjeta são da loja (decidido em 05/10/2026) | **Implementado em 07/10/2026**: o razão não credita mais frete/gorjeta ao entregador; a carteira de repasse (saque, métodos e solicitações) saiu da API e da interface, e o entregador vê um extrato informativo calculado dos pedidos (`/me/earnings`). | Nada — a loja remunera o entregador fora da plataforma. |
| Estorno é da loja (decidido em 05/10/2026) | **Implementado em 06/10/2026**: a loja estorna e decide reembolsos em Pedidos (`payments.manage`); o admin age só como suporte (`support.act`, motivo, trilha da loja). | Testar no staging com a conta da loja. |
| Recebimento livre por restaurante | A loja configura métodos manuais, porém a confirmação de pagamento na entrega ainda passa pelo entregador/admin em alguns fluxos. | Revisar permissões por modalidade e permitir confirmação/estorno ao titular da loja conforme a operação acordada. |
| Prontidão de publicação | `platform/deploy/README.md` exige SMTP real, origem/CSRF finais, backup externo, ensaio de restauração e teste de carga na VPS. | Executar homologação operacional e registrar resultados antes da abertura comercial. |

## Prioridade média — produto e governança

| Lacuna | Evidência | Próximo passo |
| --- | --- | --- |
| Contrato dos módulos | A habilitação por restaurante é manual; não deriva do plano/assinatura. | Definir preço e direito de cada módulo e aplicar a vigência da assinatura no acesso. |
| Fronteira do super-admin | Admin ainda pode consultar extrato histórico individual e controlar descontos/cardápio por rotas administrativas. | Restringir acesso a suporte auditado ou remover; manter gestão de preços e campanhas com a loja. **Descontos resolvidos em 03/10/2026:** campanhas e cupons são só da loja e o admin só os lê (V056). Falta: cashback. |
| Carteira do cliente no checkout | Recompensas/saldo existem, mas o uso como meio de pagamento está pendente no plano técnico. | Decidir se continua no escopo; se sim, integrar com limites, devolução e conciliação. |
| Chat | Há endpoints, mas falta interface dedicada para clientes/lojas. | Implementar experiência e autorização por pedido. |
| Apple Sign In, cache offline e importação em massa | Pendências registradas nos épicos E47, E42 e E23. | Priorizar conforme canais realmente lançados; não declarar épicos completos antes disso. |
| Desconto da loja sem efeito (registrado em 01/10/2026) | `restaurants.discount_percent` (V041, E08) era gravado pela loja e pelo admin, mas nenhum código o lia: não entrava no carrinho, no checkout, no pedido nem no catálogo. | **Resolvido em 03/10/2026** — o campo foi removido (migration `V056`); o desconto da loja é a campanha `basic`, que já era aplicada e registrada no pedido. Nenhuma tela promete mais "Desconto da loja" e o admin deixou de criar/editar campanhas e cupons. |
| Cashback é da loja (decidido em 05/10/2026) | **Implementado em 07/10/2026**: as regras globais foram desativadas (V060) e o fallback global (`restaurant_id IS NULL`) saiu do cálculo; só a loja cria regras e o admin só lê (`/admin/rewards/cashback-rules`). | Nada nesta rodada. Fora de escopo: a contrapartida contábil do cashback no razão (a loja "bancar" o crédito). |
| Documentação de operação | `platform/README.md` mistura limitações históricas com funcionalidades posteriores. | Consolidar uma única matriz de funcionalidades e requisitos de deploy. |

Pedidos recorrentes e aplicação de campanhas no checkout foram implementados no ciclo anterior e verificados em testes automatizados; precisam de homologação funcional no staging após publicação.

## Verificação desta atualização

`VERIFY_INTEGRATION=1 pnpm verify` passou: 225 testes Java, testes TypeScript, tipos e build Next, migration V053 e seed em MariaDB efêmero, além dos smokes de pedido, carrinho, exceções e contas. A home pública e o link para `/entrar` foram conferidos em navegador local; a lista de restaurantes depende da API do ambiente.
