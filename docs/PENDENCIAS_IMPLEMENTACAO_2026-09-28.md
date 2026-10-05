# Foodie — revisão das pendências de implementação

Data: 28/09/2026. Base: modelo de negócio, planos de reconstrução e épicos do repositório; inspeção de API, web, migrations e composição de homologação. Esta lista descreve capacidade observável no código, não valida operação em produção.

## Decisão aplicada nesta revisão

- A Foodie cobra assinatura da loja. A venda do pedido pertence ao restaurante. Novos pedidos não geram crédito sacável nem comissão no razão da loja; o financeiro privado calcula vendas pagas e concluídas pelos pedidos.
- A carteira de saque, métodos e novas solicitações do restaurante saem da interface e da API. Lançamentos e repasses antigos permanecem no banco; o admin pode concluir solicitações já abertas. A carteira de recompensas do cliente e a de repasse do entregador continuam.
- Cobranças online com a conta global da plataforma ficam bloqueadas para novos pedidos. Intenções antigas e webhooks continuam consultáveis/processáveis. Pagamento na entrega e meios manuais configurados pela loja continuam disponíveis. **Atualização de 02/10/2026:** o bloqueio virou configuração (`PAYMENTS_ALLOW_DIRECT_ONLINE_CHARGES`, desligada por padrão) e o trecho que faltava — criar a cobrança no provedor e gravar QR/id externo — foi implementado, para permitir teste com credenciais de teste do Mercado Pago sem alterar o comportamento de produção. A decisão de quem recebe o dinheiro continua pendente.
- `/` passa a apresentar a home pública; o acesso dos papéis fica em `/entrar`.

## Prioridade alta — antes de operação comercial

| Lacuna | Evidência | Próximo passo |
| --- | --- | --- |
| Cobrança real da assinatura | `SubscriptionController` cria `subscription_transactions` ao atribuir plano, sem cobrar um provedor, conciliar liquidação ou automatizar renovação/inadimplência. | Integrar billing, webhook idempotente, renovação e suspensão por estado pago. Tratar a transação atual como registro administrativo, não recibo. |
| Pix/cartão online direto para a loja | `OnlinePaymentService` resolve gateway global, sem conta do restaurante. Novas cobranças seguem bloqueadas **por configuração** (desde 02/10/2026); o caminho de criação (Pix e preferência de cartão) e a gravação de QR/id externo existem para **teste com credencial de teste**. | Conectar contas de cada restaurante ou usar provedor com recebedor por loja; testar cobrança, cancelamento, estorno e conciliação por tenant antes de reabrir o checkout online. Teste técnico em staging (credencial de teste + webhook público) em andamento em 02/10. |
| Acerto do passivo antigo de carteira | `ledger_entries` e `payout_requests` antigos não são apagados; o admin ainda decide repasses históricos. | Inventariar saldos, pedidos online existentes e solicitações em aberto; conciliar e registrar quitação com evidência. |
| Quem financia entrega e gorjeta | O razão ainda credita o entregador, mas a plataforma deixa de receber a venda do restaurante. | Definir em contrato quem recebe frete/gorjeta e quem deve ao entregador; adequar confirmação, razão e repasses. |
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
| Quem paga o cashback | As regras de cashback do admin (`/admin/rewards/cashback-rules`) ainda podem valer para todas as lojas. | Decidir quem financia o cashback no modelo em que a venda é da loja; restringir à loja se for ela quem paga. |
| Documentação de operação | `platform/README.md` mistura limitações históricas com funcionalidades posteriores. | Consolidar uma única matriz de funcionalidades e requisitos de deploy. |

Pedidos recorrentes e aplicação de campanhas no checkout foram implementados no ciclo anterior e verificados em testes automatizados; precisam de homologação funcional no staging após publicação.

## Verificação desta atualização

`VERIFY_INTEGRATION=1 pnpm verify` passou: 225 testes Java, testes TypeScript, tipos e build Next, migration V053 e seed em MariaDB efêmero, além dos smokes de pedido, carrinho, exceções e contas. A home pública e o link para `/entrar` foram conferidos em navegador local; a lista de restaurantes depende da API do ambiente.
